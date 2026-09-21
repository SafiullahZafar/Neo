package com.neo.assistant

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.UUID
import java.util.concurrent.Executors

/** A local scenario runner. Deliberately has no SIM call interception or answer permission. */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var session: CallSession? = null
    private var caller = ""
    private var reply = ""
    private var tts: TextToSpeech? = null
    private var speechReady = false
    private lateinit var status: TextView
    private lateinit var history: TextView
    private lateinit var message: EditText
    private lateinit var actions: LinearLayout
    private lateinit var scenarios: LinearLayout
    private lateinit var save: Button
    private val prefs by lazy { getSharedPreferences("neo_demo", MODE_PRIVATE) }

    private lateinit var screen: NeoScreen
    private lateinit var connection: ConnectionStore
    private val network = Executors.newSingleThreadExecutor()
    private var syncing = false
    private var foreground = false
    private var reportQuery = ""
    private var reportFilter = ReportFilter.ALL
    private val retrySchedule = SyncRetrySchedule()
    private val retrySync = Runnable { if (foreground && prefs.getBoolean("auto_sync", true)) syncReports(manual = false) }
    private lateinit var access: PermissionAccess
    private lateinit var notices: NeoNotifications
    private var pendingPermission: String? = null
    private var pendingPermissionAction: (() -> Unit)? = null
    private var recognizer: SpeechRecognizer? = null
    private val dictationTimeout = Runnable {
        stopDictation()
        Toast.makeText(this, "Dictation timed out. You can still type your message.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        connection = ConnectionStore(this)
        access = PermissionAccess(this)
        notices = NeoNotifications(this)
        notices.clearActiveCall()
        screen = NeoScreen(this) { session != null }
        screen.build(
            start = { startScenario(it) },
            answer = {
                val takingOver = session?.state == CallState.ASSISTANT
                session?.ownerAnswer()
                finishScenario(if (takingOver) "You took over the practice conversation." else "You answered. Neo stayed silent.")
            },
            reject = { session?.reject(); finishScenario("You declined. Neo stayed silent.") },
            hangup = { finishScenario("Call ended.") },
            saveMessage = {
                if (session?.state == CallState.ASSISTANT) {
                    val value = message.text.toString().trim()
                    if (value.isEmpty()) message.error = "Add a message first"
                    else finishScenario("Neo took a message.", value)
                }
            },
            readReport = {
                val items = records()
                if (items.length() > 0) speak(format(items.getJSONObject(0)))
                else Toast.makeText(this, "No reports yet. Try a practice call first.", Toast.LENGTH_SHORT).show()
            },
            clearReports = {
                if (syncing) {
                    Toast.makeText(this, "Wait for the current upload to finish before clearing reports.", Toast.LENGTH_SHORT).show()
                } else android.app.AlertDialog.Builder(this).setTitle("Clear local reports?")
                    .setMessage("This removes practice calls and any pending uploads from this phone. Already synced copies stay on your Python service.")
                    .setNegativeButton("Keep", null)
                    .setPositiveButton("Clear") { _, _ ->
                        handler.removeCallbacks(retrySync); retrySchedule.reset()
                        prefs.edit().remove("reports").apply(); notices.clearReports(); showReports()
                        screen.connectionStatus.text = "Local reports cleared. Existing Python copies were not deleted."
                    }
                    .show()
            },
            voiceChanged = { prefs.edit().putBoolean("voice", it).apply() },
            voiceEnabled = prefs.getBoolean("voice", true),
            connect = { connectionDialog() },
            sync = { syncReports() },
            permission = { requestAccess(it) },
            manageAccess = { access.settings() },
            presenceCheck = { ensureAccess(Manifest.permission.CAMERA) { startActivity(Intent(this, PresenceActivity::class.java)) } },
            contactTest = { ensureAccess(Manifest.permission.READ_CONTACTS) { chooseContact() } },
            dictate = { dictateMessage() },
            assistanceEnabled = prefs.getBoolean("assistance", true),
            assistanceChanged = { prefs.edit().putBoolean("assistance", it).apply() },
            notificationAccess = { requestNotificationAccess() },
            notificationSettings = { notices.settings() },
            notificationChanged = { notices.enable(it); refreshPermissions() },
            notificationCategory = { channel, enabled -> notices.category(channel, enabled); refreshPermissions() },
            notificationTest = {
                val sent = notices.test()
                Toast.makeText(this, if (sent) "Test notification sent. Check your notification shade."
                    else "Enable notifications and the Reports category in Neo and Android settings first.", Toast.LENGTH_LONG).show()
            },
            reportSearchChanged = { reportQuery = it; if (::history.isInitialized) showReports() },
            reportFilterChanged = { reportFilter = it; if (::history.isInitialized) showReports() },
            autoSyncChanged = {
                prefs.edit().putBoolean("auto_sync", it).apply()
                handler.removeCallbacks(retrySync)
                if (it && foreground && connection.token().isNotEmpty()) syncReports()
            }
        )
        status = screen.status; history = screen.history; message = screen.message
        actions = screen.actions; scenarios = screen.scenarios; save = screen.save
        renderControls()
        showReports()
        openNotificationPage(intent)
        if (connection.token().isNotEmpty()) syncReports()
    }

    private fun startScenario(known: Boolean, selectedName: String? = null) {
        if (session != null) return
        val existing = records()
        if ((0 until existing.length()).count { !existing.getJSONObject(it).optBoolean("synced") } >= 100) {
            Toast.makeText(this, "100 reports are waiting to upload. Sync or explicitly clear them before another practice call.", Toast.LENGTH_LONG).show()
            return
        }
        caller = if (known) selectedName?.take(160) ?: "Demo contact" else "Unknown number"
        reply = ""
        message.setText("")
        session = CallSession(known, SystemClock.elapsedRealtime(), prefs.getBoolean("assistance", true), AssistantPreferences.delayMs(this))
        screen.show("Test")
        renderControls()
        notices.ringing(known, prefs.getBoolean("assistance", true))
        handler.post(ticker)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val current = session ?: return
            val now = SystemClock.elapsedRealtime()
            if (current.tick(now)) {
                reply = prefs.getString("greeting", null) ?: "Hello, this is Neo, an assistant. They are unavailable right now. Please leave a message."
                status.text = "Neo's demo reply:\n\n$reply\n\nAdd the caller's message below."
                if (prefs.getBoolean("voice", true)) speak(reply)
                notices.assistantStarted()
                renderControls()
            } else if (current.state == CallState.RINGING) {
                status.text = if (!current.assistanceEnabled) "Assistance is paused. Neo will not respond to this practice call."
                else if (current.knownContact) {
                    val seconds = ((current.answerDelayMs - (now - current.startedAt)).coerceAtLeast(0) + 999) / 1000
                    "$caller is ringing. Neo responds in ${seconds}s."
                } else "Unknown number is ringing. Neo will never auto-answer."
            }
            if (now - current.startedAt >= current.maxDurationMs) {
                finishScenario(if (current.state == CallState.ASSISTANT) "No message received before timeout." else "Missed call. Neo did not answer.")
                return
            }
            handler.postDelayed(this, 100)
        }
    }

    private fun finishScenario(outcome: String, callerMessage: String = "") {
        val current = session ?: return
        stopDictation()
        current.end()
        handler.removeCallbacks(ticker)
        tts?.stop()
        val entry = JSONObject().put("caller", caller).put("outcome", outcome)
            .put("reply", reply).put("message", callerMessage).put("time", System.currentTimeMillis())
            .put("id", UUID.randomUUID().toString()).put("demo", true).put("synced", false)
        val old = records()
        val updated = JSONArray().put(entry)
        // Prefer pending uploads over older synced reports when the local journal fills.
        val pendingIndices = (0 until old.length()).filter { !old.getJSONObject(it).optBoolean("synced") }
        val syncedIndices = (0 until old.length()).filter { old.getJSONObject(it).optBoolean("synced") }
        val keep = (pendingIndices + syncedIndices).take(99).toSet()
        for (index in 0 until old.length()) if (index in keep) updated.put(old.getJSONObject(index))
        prefs.edit().putString("reports", updated.toString()).apply()
        notices.reportReady(outcome)
        session = null
        status.text = outcome
        renderControls()
        showReports()
        screen.show("Reports")
        if (connection.token().isNotEmpty()) syncReports()
    }

    private fun renderControls() {
        scenarios.visibility = if (session == null) View.VISIBLE else View.GONE
        actions.visibility = if (session?.state in listOf(CallState.RINGING, CallState.ASSISTANT)) View.VISIBLE else View.GONE
        screen.answerButton.text = if (session?.state == CallState.ASSISTANT) "Take over conversation" else "I'll take this call"
        screen.rejectButton.visibility = if (session?.state == CallState.RINGING) View.VISIBLE else View.GONE
        val recording = session?.state == CallState.ASSISTANT
        message.visibility = if (recording) View.VISIBLE else View.GONE
        save.visibility = message.visibility
        screen.dictateButton.visibility = message.visibility
        screen.end.visibility = if (session == null) View.GONE else View.VISIBLE
    }

    private fun records(): JSONArray {
        val items = try { JSONArray(prefs.getString("reports", "[]")) }
            catch (_: org.json.JSONException) { JSONArray() }
        var migrated = false
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (!item.has("id")) {
                item.put("id", UUID.randomUUID().toString()).put("demo", true).put("synced", false)
                migrated = true
            }
        }
        if (migrated) prefs.edit().putString("reports", items.toString()).apply()
        return items
    }

    private fun format(entry: JSONObject): String = buildString {
        append(entry.getString("caller")); append(". "); append(entry.getString("outcome"))
        if (entry.optString("reply").isNotEmpty()) append("\nNeo said: ${entry.getString("reply")}")
        if (entry.optString("message").isNotEmpty()) append("\nCaller said: ${entry.getString("message")}")
    }

    private fun showReports() {
        val items = records()
        val rows = (0 until items.length()).map {
            val entry = items.getJSONObject(it)
            ReportRow(entry.getString("caller"), entry.getString("outcome"), entry.optString("message"),
                entry.optString("reply"), DateFormat.getDateTimeInstance().format(java.util.Date(entry.getLong("time"))),
                entry.optBoolean("synced"))
        }
        val visible = rows.filter { it.matches(reportQuery, reportFilter) }
        val pending = rows.count { !it.synced }
        history.text = when {
            rows.isEmpty() -> "Your first practice call will appear here."
            visible.isEmpty() -> "No matching reports. Try another search or filter."
            else -> "${visible.size} of ${rows.size} reports | $pending pending upload"
        }
        screen.showReportCards(visible)
        val checkedAt = prefs.getLong("last_sync_success", 0)
        val lastCheck = if (checkedAt == 0L) "No successful check yet." else
            "Last successful check: ${DateFormat.getDateTimeInstance().format(java.util.Date(checkedAt))}"
        screen.syncOverview.text = "${rows.size - pending} saved to Python | $pending pending\n$lastCheck"
    }

    private fun speak(text: String) {
        if (!speechReady || tts == null || !AssistantPreferences.applyVoice(this, tts!!) || tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "neo") != TextToSpeech.SUCCESS)
            Toast.makeText(this, "Speech unavailable; the text is shown on screen.", Toast.LENGTH_SHORT).show()
    }

    override fun onInit(status: Int) { speechReady = status == TextToSpeech.SUCCESS }

    private fun requestAccess(permission: String) {
        pendingPermission = null; pendingPermissionAction = null
        val explanation = when (permission) {
            Manifest.permission.READ_CONTACTS -> "Allow contacts to select a real contact for a practice call. The full address book is never sent to Python."
            Manifest.permission.CAMERA -> "Allow the camera for a short presence check that you start on screen. No photos are saved or uploaded, and the camera stops when you leave."
            Manifest.permission.POST_NOTIFICATIONS -> "Allow notifications for practice call updates, saved reports and Python sync results. You can switch each category off in Settings. This does not monitor real SIM calls."
            else -> "Allow microphone access to dictate practice messages. Your phone's speech recognition service may process audio online if on-device recognition is unavailable. You can keep typing instead."
        }
        access.request(permission, 701, explanation) { pendingPermission = null; pendingPermissionAction = null }
    }

    private fun ensureAccess(permission: String, action: () -> Unit) {
        if (access.allowed(permission)) { action(); return }
        requestAccess(permission)
        pendingPermission = permission; pendingPermissionAction = action
    }

    private fun refreshPermissions() {
        screen.notificationStatus.text = notices.state()
        screen.callReadinessStatus.text = CallReadiness.summary(this)
        screen.contactsStatus.text = if (access.allowed(Manifest.permission.READ_CONTACTS)) "Allowed · device contacts available" else "Not allowed · sample calls still work"
        screen.cameraStatus.text = if (access.allowed(Manifest.permission.CAMERA)) "Allowed · used only when you start a check" else "Not allowed · presence stays uncertain"
        screen.microphoneStatus.text = if (access.allowed(Manifest.permission.RECORD_AUDIO)) "Allowed · dictation available if supported" else "Not allowed · type messages instead"
        val time = prefs.getLong("presence_time", 0)
        screen.presenceStatus.text = if (time == 0L) "No presence check yet." else
            "Last check: ${DateFormat.getDateTimeInstance().format(java.util.Date(time))}\n${prefs.getString("presence_summary", "Presence uncertain.")}\nThis is a past observation, not your current availability."
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        val unreadCalls = LiveCallJournal.unread(this)
        if (unreadCalls > 0) {
            NeoNotifications(this).liveSummary(unreadCalls)
            android.widget.Toast.makeText(this, "$unreadCalls real call record(s) to review in Settings > Call activity", android.widget.Toast.LENGTH_LONG).show()
        }
        if (::screen.isInitialized && ::access.isInitialized) {
            refreshPermissions()
            if (prefs.getBoolean("bg_call_monitoring", false)) NeoForegroundService.start(this)
            if (prefs.getBoolean("auto_sync", true) && connection.token().isNotEmpty()) {
                val items = records()
                if ((0 until items.length()).any { !items.getJSONObject(it).optBoolean("synced") }) syncReports()
            }
            val permission = pendingPermission
            if (permission != null) {
                val action = pendingPermissionAction
                pendingPermission = null; pendingPermissionAction = null
                if (access.allowed(permission)) action?.invoke()
            }
        }
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 33 && !access.allowed(Manifest.permission.POST_NOTIFICATIONS)) {
            requestAccess(Manifest.permission.POST_NOTIFICATIONS)
        } else notices.settings()
    }

    private fun openNotificationPage(source: Intent?) {
        val page = source?.getStringExtra("neo_page") ?: return
        source.removeExtra("neo_page")
        if (page in listOf("Home", "Test", "Reports", "Settings")) screen.show(page)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotificationPage(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 701) return
        refreshPermissions()
        val permission = pendingPermission
        val action = pendingPermissionAction
        pendingPermission = null; pendingPermissionAction = null
        if (permission != null && access.allowed(permission)) action?.invoke()
    }

    private fun chooseContact() {
        try {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 702)
        } catch (_: Exception) { Toast.makeText(this, "No contacts picker is available on this phone.", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 702 || resultCode != RESULT_OK || !access.allowed(Manifest.permission.READ_CONTACTS)) return
        val uri = data?.data ?: return
        // Only accept results from Android's contacts provider; never arbitrary file URIs.
        if (uri.scheme != "content" || uri.authority != ContactsContract.AUTHORITY) return
        try {
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Selected contact"
                    val number = cursor.getString(1)
                    if (!number.isNullOrBlank()) startScenario(true, name)
                }
            }
        } catch (_: Exception) { Toast.makeText(this, "Contact access changed. Check Settings and try again.", Toast.LENGTH_SHORT).show() }
    }

    private fun dictateMessage() {
        if (session?.state != CallState.ASSISTANT) return
        if (recognizer != null) { stopDictation(); return }
        if (!access.allowed(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "Enable microphone access in Settings before a test, or type your message.", Toast.LENGTH_LONG).show(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No speech recognizer is installed. You can type your message.", Toast.LENGTH_LONG).show(); return
        }
        tts?.stop()
        try {
            val engine = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (recognizer === engine) screen.dictateButton.text = "Listening… tap to cancel" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if (recognizer === engine) screen.dictateButton.text = "Processing… tap to cancel" }
                override fun onError(error: Int) {
                    if (recognizer !== engine) return
                    stopDictation()
                    Toast.makeText(this@MainActivity, "No message captured. Try again or type it below.", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== engine) return
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (session?.state == CallState.ASSISTANT && !spoken.isNullOrBlank()) {
                        val combined = listOf(message.text.toString().trim(), spoken).filter { it.isNotEmpty() }.joinToString(" ").take(4000)
                        message.setText(combined); message.setSelection(message.length())
                    }
                    stopDictation()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
            screen.dictateButton.text = "Starting microphone… tap to cancel"
            handler.postDelayed(dictationTimeout, 20_000)
        } catch (_: Exception) {
            stopDictation()
            Toast.makeText(this, "Microphone unavailable. You can type instead.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopDictation() {
        handler.removeCallbacks(dictationTimeout)
        val engine = recognizer
        recognizer = null
        engine?.cancel(); engine?.destroy()
        if (::screen.isInitialized) screen.dictateButton.text = "Dictate a practice message"
    }

    private fun connectionDialog() {
        if (syncing) {
            Toast.makeText(this, "Wait for the current sync to finish.", Toast.LENGTH_SHORT).show()
            return
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        fields.addView(TextView(this).apply { text = "For USB testing, run Python and enable adb reverse. Copy NEO_API_TOKEN from your private .env. Provider API keys stay on the server." })
        val url = EditText(this).apply {
            hint = "Server URL"; setText(connection.url); setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            fields.addView(this)
        }
        val token = EditText(this).apply {
            hint = "Pairing token"; setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(connection.token())
            if (Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            fields.addView(this)
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Connect to Python")
            .setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Connect", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val address = url.text.toString().trim().trimEnd('/')
                    val secret = token.text.toString().trim()
                    ApiTransport(address, secret, BuildConfig.DEBUG)
                    val changed = address != connection.url || secret != connection.token()
                    connection.save(address, secret)
                    if (changed) {
                        val items = records()
                        for (index in 0 until items.length()) items.getJSONObject(index).put("synced", false)
                        prefs.edit().putString("reports", items.toString()).remove("greeting").apply()
                        showReports()
                    }
                    dialog.dismiss(); syncReports()
                } catch (error: IllegalArgumentException) { url.error = error.message }
                catch (_: Exception) { token.error = "Unable to save pairing token on this phone." }
            }
        }
        dialog.show()
    }

    private fun syncReports(manual: Boolean = true) {
        if (syncing) return
        handler.removeCallbacks(retrySync)
        if (manual) retrySchedule.reset()
        val secret = connection.token()
        if (secret.isEmpty()) {
            screen.connectionStatus.text = "Pair with Python first. Your reports are saved on this phone."
            return
        }
        val client = try { ApiTransport(connection.url, secret, BuildConfig.DEBUG) }
            catch (_: Exception) { screen.connectionStatus.text = "Check your server address and pairing token."; return }
        val items = records()
        val pending = mutableListOf<JSONObject>()
        for (index in 0 until items.length()) {
            val entry = items.getJSONObject(index)
            if (!entry.optBoolean("synced")) pending.add(JSONObject(entry.toString()).apply { remove("synced") })
        }
        syncing = true
        screen.connectionStatus.text = "Connecting to Python…"
        network.execute {
            val saved = mutableSetOf<String>()
            var greeting: String? = null
            var failure: ConnectionProblem? = null
            try {
                val policy = JSONObject(client.request("GET", "/v1/policy"))
                check(policy.getInt("answer_delay_ms") == 6000 && policy.getBoolean("contacts_only") && !policy.getBoolean("real_calls_enabled")) {
                    "Server policy does not match this demo."
                }
                greeting = policy.getString("greeting")
                check(greeting.isNotBlank() && greeting.length <= 4000) { "Invalid greeting from server." }
                for (entry in pending) {
                    if (Thread.currentThread().isInterrupted) break
                    val result = JSONObject(client.request("POST", "/v1/reports", entry.toString()))
                    check(result.getBoolean("saved") && result.getString("id") == entry.getString("id")) { "Server did not confirm this report." }
                    saved.add(entry.getString("id"))
                }
            } catch (error: Exception) {
                failure = ConnectionProblem.from(error)
            }
            handler.post {
                if (isDestroyed) return@post
                syncing = false
                val latest = records()
                for (index in 0 until latest.length()) {
                    val entry = latest.getJSONObject(index)
                    if (entry.getString("id") in saved) entry.put("synced", true)
                }
                val editor = prefs.edit().putString("reports", latest.toString())
                if (failure == null && greeting != null) editor.putString("greeting", greeting)
                if (failure == null) editor.putLong("last_sync_success", System.currentTimeMillis())
                editor.apply(); showReports()
                val pendingCount = (0 until latest.length()).count { !latest.getJSONObject(it).optBoolean("synced") }
                notices.syncResult(failure == null, saved.size, pendingCount, SystemClock.elapsedRealtime())
                screen.connectionStatus.text = if (failure == null) "Python connection verified. ${saved.size} report(s) uploaded. Real calls remain disabled."
                    else "${failure?.title}\n${failure?.instruction}\n$pendingCount report(s) pending; local copies are retained."
                if (failure == null) retrySchedule.reset()
                else if (pendingCount > 0 && foreground && prefs.getBoolean("auto_sync", true)) {
                    val delay = retrySchedule.nextDelay(failure?.retryable == true)
                    if (delay != null) {
                        handler.postDelayed(retrySync, delay)
                        screen.connectionStatus.append("\nNext automatic retry in ${delay / 1000}s while Neo stays open.")
                    } else if (failure?.retryable == true) {
                        screen.connectionStatus.append("\nAutomatic retries paused. Tap Sync pending reports when ready.")
                    }
                }
                if (failure == null && foreground && session == null && !prefs.getBoolean("access_intro_seen", false)) {
                    prefs.edit().putBoolean("access_intro_seen", true).apply()
                    android.app.AlertDialog.Builder(this).setTitle("Choose Neo's access")
                        .setMessage("Python is connected. Choose notifications, contacts, camera or microphone access for optional practice features. Each permission is your choice and can be changed later in Settings.")
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Set up access") { _, _ -> screen.show("Settings") }.show()
                }
                // Upload calls completed while the previous batch was in flight.
                if (failure == null && (0 until latest.length()).any { !latest.getJSONObject(it).optBoolean("synced") }) syncReports(manual = false)
            }
        }
    }

    override fun onPause() {
        foreground = false
        handler.removeCallbacks(retrySync)
        stopDictation()
        // Demo timing must not pretend to continue as a real background phone service.
        if (session != null) finishScenario("Demo interrupted when the screen was left.")
        tts?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        network.shutdownNow()
        tts?.shutdown()
        super.onDestroy()
    }
}
