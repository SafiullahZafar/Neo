package com.neo.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.*
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SimAudioDiagnosticsActivity : Activity() {
    private lateinit var bridge: StandardSimCallAudioBridge
    private lateinit var recognizer: CapturedAudioRecognizer
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var history: TextView
    private lateinit var consent: CheckBox
    private lateinit var yes: Button
    private lateinit var no: Button
    private lateinit var permissionStatus: TextView
    private lateinit var systemStatus: TextView
    private lateinit var backendStatus: TextView
    private var fullTest = false
    private var fullRxMatched: Boolean? = null
    private var checkedBackend = false
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var running = false
    private var generation = 0L
    private var route = 0
    private var evidence: AudioTestEvidence? = null
    private var attempt = ""
    private var configuration = ""
    private val sources = listOf("MIC" to MediaRecorder.AudioSource.MIC, "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION, "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION, "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL, "VOICE_DOWNLINK" to MediaRecorder.AudioSource.VOICE_DOWNLINK, "VOICE_UPLINK" to MediaRecorder.AudioSource.VOICE_UPLINK)
    private val strategies = listOf("Standard media TTS", "Communication attributes TTS", "Preferred telephony AudioTrack")
    private val tick = object : Runnable {
        override fun run() {
            if (!foreground) return
            if ((running || evidence != null) && (!SimAudioSession.isActive || generation != SimAudioSession.generation || route != SimAudioSession.route)) stop("Test invalidated: call or route changed. Start a new test.")
            refresh(); handler.postDelayed(this, 250)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only our bounded, app-private diagnostic files; recover after process death.
        cacheDir.listFiles()?.filter { it.isFile && (it.name.startsWith("neo-capture-") && it.extension == "pcm" || it.name.startsWith("neo-route-") && it.extension == "wav") }
            ?.forEach { it.delete() }
        bridge = StandardSimCallAudioBridge(this); recognizer = CapturedAudioRecognizer(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 30, 28, 30); setBackgroundColor(Color.rgb(246, 249, 245)) }
        setContentView(ScrollView(this).apply { addView(layout) })
        fun label(text: String, size: Float = 16f) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(28, 55, 48)); setPadding(0, 12, 0, 12); layout.addView(this) }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; layout.addView(this); setOnClickListener { action() } }
        fun spinner(values: List<String>) = Spinner(this).apply { adapter = ArrayAdapter(this@SimAudioDiagnosticsActivity, android.R.layout.simple_spinner_dropdown_item, values); layout.addView(this) }
        button("Back") { finish() }
        label("SIM Call Assistant", 25f)
        label("A local TTS success does not mean the remote caller heard Neo. Remote audio must be confirmed from the other phone. Direct audio remains UNVERIFIED; no automatic AI conversation is enabled.")
        label("Permissions and phone role", 21f)
        permissionStatus = label(SimAccessStatus.summary(this))
        val accessFeedback = label("Select an access action below to review or change settings.")
        button("Request missing SIM permissions") { accessFeedback.text = SimAccessStatus.requestMissing(this) }
        button("Manage permissions in Android") { PermissionAccess(this).settings() }
        button("Choose default phone app") { accessFeedback.text = SimAccessStatus.defaultSettings(this) }
        label("SIM Call Audio Diagnostics", 21f)
        status = label("")
        systemStatus = label(SimAccessStatus.systemSummary(this))
        backendStatus = label("Python SIM connection: not checked. Phone audio diagnostics run locally without Python or an API key.")
        button("Check paired Python SIM readiness") { checkBackend() }
        button("Refresh audio devices") { refresh() }
        label("Connect one SIM test call first. Set Speaker ON or OFF using call controls before each test. Neo will not change the route or volume. Stay on this screen; leaving it stops diagnostics without hanging up.")
        consent = CheckBox(this).apply { text = "Both participants agree to this short audio test"; layout.addView(this) }
        label("Caller capture", 21f)
        label("Owner stays silent. Ask the other caller to say '${SimTestPhrase.CAPTURE}' during the five-second capture. Audio stays on this phone and is deleted after offline recognition. A phrase match may be acoustic leakage, even with speaker OFF.")
        val source = spinner(sources.map { it.first })
        label("App recording effects only (does not control call processing)")
        val aec = spinner(listOf("AEC default", "AEC enabled", "AEC disabled"))
        val ns = spinner(listOf("NS default", "NS enabled", "NS disabled"))
        fun effect(position: Int): Boolean? = when (position) { 1 -> true; 2 -> false; else -> null }
        button("Test caller capture · 5 seconds") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                PermissionAccess(this).request(Manifest.permission.RECORD_AUDIO, 84, "Record a five-second sample during the agreed SIM test. This does not grant protected call-audio access.")
            } else if (begin()) {
                val selected = sources[source.selectedItemPosition]
                configuration += "; source=${selected.first}; AEC=${aec.selectedItemPosition}; NS=${ns.selectedItemPosition}"
                output.text = "Capturing now: caller, say '${SimTestPhrase.CAPTURE}'."
                bridge.startListening(selected.second, effect(aec.selectedItemPosition), effect(ns.selectedItemPosition)) { report, pcm ->
                    if (foreground && running) {
                        output.text = report; save(report)
                        if (pcm != null && pcm.isNotEmpty()) recognizer.recognize(pcm) { recognized ->
                            if (foreground && running) { output.append("\nRecognized: ${recognized.text}\n${recognized.message}"); save(recognized.message); running = false }
                        } else running = false
                    }
                }
            }
        }
        label("Remote playback", 21f)
        val strategy = spinner(strategies)
        label("Communication attributes do not replace cellular IN_CALL mode. Telephony preference may be rejected or routed elsewhere. Neither proves uplink access.")
        label("Guided full SIM AI audio test", 21f)
        label("1. Call this phone from a saved contact and let Neo answer.\n2. Keep this screen visible; owner stays silent.\n3. Choose a source and playback strategy above. Press Run, then the remote caller says '${SimTestPhrase.FULL}'.\n4. Only a phrase match triggers '${SimTestPhrase.REPLY}'.\n5. Confirm audibility from the other phone. This tests one round trip, not a general AI conversation or a proven direct route.")
        button("Run Full SIM AI Test") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                PermissionAccess(this).request(Manifest.permission.RECORD_AUDIO, 84, "A five-second agreed test sample is recognized locally; no audio is uploaded. Run the test again after granting access.")
            } else if (begin()) {
                fullTest = true
                val selected = sources[source.selectedItemPosition]
                val playback = strategy.selectedItemPosition
                configuration += "; guided=true; source=${selected.first}; strategy=${strategies[playback]}; AEC=${aec.selectedItemPosition}; NS=${ns.selectedItemPosition}"
                output.text = "LISTENING: remote caller, say '${SimTestPhrase.FULL}' now."
                bridge.startListening(selected.second, effect(aec.selectedItemPosition), effect(ns.selectedItemPosition)) { report, pcm ->
                    if (foreground && running) {
                        save(report)
                        if (pcm == null || pcm.isEmpty()) {
                            running = false; output.text = "$report\nCaller -> Neo: UNVERIFIED\nNeo -> caller: UNTESTED\nFull two-way: UNVERIFIED"
                            save(output.text.toString())
                        } else {
                            output.text = "TRANSCRIBING the test sample locally..."
                            recognizer.recognize(pcm, SimTestPhrase.FULL) { recognized ->
                                if (foreground && running) {
                                    fullRxMatched = recognized.phraseMatched
                                    save(recognized.message)
                                    if (recognized.phraseMatched != true) {
                                        running = false
                                        output.text = "Recognized: ${recognized.text}\n${recognized.message}\nCaller -> Neo: ${if (recognized.phraseMatched == false) "FAIL (phrase not recognized)" else "UNVERIFIED"}\nNeo -> caller: UNTESTED\nFull two-way: UNVERIFIED"
                                    } else {
                                        if (!testStillValid()) { stop("Call or route changed before reply; no audio sent."); return@recognize }
                                        output.text = "Recognized: ${recognized.text}\nSPEAKING test response. Confirm from the remote phone."
                                        bridge.playReply(playback, SimTestPhrase.REPLY) { played, completed ->
                                            if (foreground && running) {
                                                running = false; save(played)
                                                if (completed) {
                                                    evidence = AudioTestEvidence(route, strategies[playback], true)
                                                    yes.isEnabled = true; no.isEnabled = true
                                                    output.append("\nLocal playback complete. Could the other phone hear Neo? Select YES or NO.")
                                                } else output.append("\n$played\nNeo -> caller: UNVERIFIED\nFull two-way: UNVERIFIED")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        button("Play test greeting") {
            if (begin()) {
                val selected = strategy.selectedItemPosition
                configuration += "; strategy=${strategies[selected]}"
                output.text = "Playing test. Listen on the other phone."
                bridge.playReply(selected) { report, completed ->
                    if (foreground && running) {
                        running = false; output.text = report; save(report)
                        if (completed) {
                            evidence = AudioTestEvidence(route, strategies[selected], true)
                            yes.isEnabled = true; no.isEnabled = true
                            output.append("\nCould the other phone hear Neo? Confirm below.")
                        }
                    }
                }
            }
        }
        yes = button("YES · other phone heard Neo") { verify(true) }.apply { isEnabled = false }
        no = button("NO · other phone did not hear Neo") { verify(false) }.apply { isEnabled = false }
        button("Stop test / Take over") { stop("Stopped. SIM call remains active for you.") }
        output = label("No test run. Caller capture and remote transmission are UNVERIFIED.")
        label("Recent tests on this device", 21f)
        history = label("")
        button("Delete diagnostic results") { getSharedPreferences("neo_audio_diagnostics", MODE_PRIVATE).edit().clear().apply(); showHistory() }
        showHistory()
    }
    private fun begin(): Boolean {
        if (running) { output.text = "Stop the current test first."; return false }
        if (!consent.isChecked || !SimAudioSession.isActive) { output.text = "Confirm participant agreement and connect a single verified ACTIVE SIM call first."; return false }
        bridge.release(); recognizer.stop(); SpeakerGreeting.stop()
        fullTest = false; fullRxMatched = null
        evidence = null; yes.isEnabled = false; no.isEnabled = false
        running = true; generation = SimAudioSession.generation; route = SimAudioSession.route
        attempt = UUID.randomUUID().toString()
        configuration = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; build=${Build.DISPLAY}; Neo ${BuildConfig.VERSION_NAME}; speaker=${speaker()}; route=$route; ${bridge.initialize()}"
        return true
    }
    private fun speaker() = when (SimAudioSession.route) { CallAudioState.ROUTE_SPEAKER -> "ON"; 0 -> "UNKNOWN"; else -> "OFF" }
    private fun testStillValid() = foreground && SimAudioSession.isActive && generation == SimAudioSession.generation && route == SimAudioSession.route
    private fun verify(heard: Boolean) {
        val previous = evidence ?: return
        if (!SimAudioSession.isActive || generation != SimAudioSession.generation || route != SimAudioSession.route) { stop("Call or route changed; result invalidated."); return }
        val report = previous.copy(remoteHeard = heard).description() + if (fullTest) {
            "\nCaller -> Neo: ${if (fullRxMatched == true) "phrase recognized; origin UNVERIFIED" else "UNVERIFIED"}\n" +
                "Neo -> caller: ${if (heard) "PASS (user-confirmed audibility)" else "FAIL (not heard)"}\n" +
                "Full AI conversation: UNVERIFIED. A single phrase/response does not establish a reliable direct or acoustic bridge."
        } else ""
        getSharedPreferences("neo_audio_diagnostics", MODE_PRIVATE).edit()
            .putString("device_build", Build.FINGERPRINT)
            .putString("capability", SimCallCapability.CALL_CONTROL_ONLY.name)
            .putString("last_roundtrip", report).apply()
        output.text = report; save(report); evidence = null; yes.isEnabled = false; no.isEnabled = false
    }
    private fun refresh() {
        val dialer = getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
        val caps = bridge.capabilities()
        permissionStatus.text = SimAccessStatus.summary(this)
        systemStatus.text = SimAccessStatus.systemSummary(this)
        status.text = "SIM auto-answer setting: ${if (LiveCallJournal.enabled(this)) "ENABLED" else "DISABLED"}\nAuto-answer hardware result: UNVERIFIED in this test\nCall: ${SimAudioSession.state}\nDefault dialer: $dialer\nCapability: ${caps.mode()}\nAI conversation: UNAVAILABLE\nMic permission: ${caps.canCaptureLocalMic} (capture not guaranteed)\nTelephony device: ${caps.telephonyDeviceDetected}\nSpeaker: ${speaker()}\nLocal TTS completed this session: ${caps.canPlayLocalTts}\nCaller audio: UNVERIFIED\nNeo audio to caller: UNVERIFIED\n${bridge.initialize()}"
    }
    private fun save(result: String) {
        val prefs = getSharedPreferences("neo_audio_diagnostics", MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString("results", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray().put(JSONObject().put("attempt", attempt).put("time", System.currentTimeMillis()).put("config", configuration).put("result", result))
        for (i in 0 until minOf(old.length(), 39)) next.put(old.getJSONObject(i))
        prefs.edit().putString("results", next.toString()).apply(); showHistory()
    }
    private fun showHistory() {
        val list = try { JSONArray(getSharedPreferences("neo_audio_diagnostics", MODE_PRIVATE).getString("results", "[]")) } catch (_: Exception) { JSONArray() }
        history.text = (0 until list.length()).joinToString("\n\n") { i -> val item = list.getJSONObject(i); "${java.util.Date(item.getLong("time"))}\n${item.getString("config")}\n${item.getString("result")}" }.ifEmpty { "Speaker OFF: UNVERIFIED\nSpeaker ON: UNVERIFIED" }
    }
    private fun checkBackend() {
        if (checkedBackend) return
        checkedBackend = true; backendStatus.text = "Checking the paired Python server..."
        Thread {
            val message = try {
                val connection = ConnectionStore(this)
                val body = JSONObject(ApiTransport(connection.url, connection.token(), BuildConfig.DEBUG).request("GET", "/v1/sim/readiness"))
                "Python connected. SIM audio transport: ${body.optString("sim_audio_transport", "UNKNOWN")}.\n" +
                    "Live conversation: ${body.optString("conversation", "UNKNOWN")}. Desktop model files: Vosk=${body.optBoolean("vosk_files_present")}, Piper=${body.optBoolean("piper_files_present")}. Files alone do not prove model readiness.\nNo call audio was sent."
            } catch (e: Exception) { ErrorHistory.describe(this, ConnectionFailure.problem(e)) }
            runOnUiThread { checkedBackend = false; if (!isDestroyed) backendStatus.text = message }
        }.start()
    }
    private fun stop(message: String) {
        if (running) save("CANCELLED: $message")
        running = false; evidence = null; yes.isEnabled = false; no.isEnabled = false
        bridge.release(); recognizer.stop(); output.text = message
    }
    override fun onResume() { super.onResume(); foreground = true; handler.post(tick) }
    override fun onPause() { foreground = false; handler.removeCallbacks(tick); stop("Paused: test stopped; call unaffected."); super.onPause() }
    override fun onDestroy() { bridge.release(); recognizer.stop(); super.onDestroy() }
}
