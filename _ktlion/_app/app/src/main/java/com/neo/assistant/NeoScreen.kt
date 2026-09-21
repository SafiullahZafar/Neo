package com.neo.assistant

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.Editable
import android.text.TextWatcher

/** Native Android views, with no network or design-library dependencies. */
class NeoScreen(private val activity: Activity, private val hasActiveCall: () -> Boolean) {
    private val ink = Color.rgb(29, 46, 48)
    private val muted = Color.rgb(93, 111, 114)
    private val teal = Color.rgb(19, 108, 96)
    private val mint = Color.rgb(226, 243, 233)
    private val paper = Color.rgb(247, 249, 246)
    private val pages = linkedMapOf<String, LinearLayout>()
    private val tabs = linkedMapOf<String, TextView>()
    lateinit var status: TextView
    lateinit var history: TextView
    lateinit var message: EditText
    lateinit var actions: LinearLayout
    lateinit var scenarios: LinearLayout
    lateinit var save: Button
    lateinit var end: Button
    lateinit var connectionStatus: TextView
    lateinit var callReadinessStatus: TextView
    lateinit var contactsStatus: TextView
    lateinit var cameraStatus: TextView
    lateinit var microphoneStatus: TextView
    lateinit var presenceStatus: TextView
    lateinit var answerButton: Button
    lateinit var rejectButton: Button
    lateinit var dictateButton: Button
    lateinit var notificationStatus: TextView
    lateinit var syncOverview: TextView
    private lateinit var reportList: LinearLayout

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() }
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun space(parent: LinearLayout, height: Int = 16) { parent.addView(View(activity), LinearLayout.LayoutParams(1, dp(height))) }
    private fun text(parent: LinearLayout, value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) =
        TextView(activity).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(5), 0, dp(5)); parent.addView(this)
        }

    private fun card(parent: LinearLayout, color: Int = Color.WHITE) = column().apply {
        background = rounded(color); setPadding(dp(20), dp(18), dp(20), dp(18))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }

    private fun button(parent: LinearLayout, title: String, primary: Boolean = true, action: () -> Unit) = Button(activity).apply {
        text = title; textSize = 14f; isAllCaps = false; minHeight = dp(52)
        setTextColor(if (primary) Color.WHITE else teal)
        backgroundTintList = ColorStateList.valueOf(if (primary) teal else mint)
        setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    fun build(start: (Boolean) -> Unit, answer: () -> Unit, reject: () -> Unit, hangup: () -> Unit,
              saveMessage: () -> Unit, readReport: () -> Unit, clearReports: () -> Unit, voiceChanged: (Boolean) -> Unit,
              voiceEnabled: Boolean, connect: () -> Unit, sync: () -> Unit,
              permission: (String) -> Unit, manageAccess: () -> Unit, presenceCheck: () -> Unit,
              contactTest: () -> Unit, dictate: () -> Unit,
              assistanceEnabled: Boolean, assistanceChanged: (Boolean) -> Unit,
              notificationAccess: () -> Unit, notificationTest: () -> Unit,
              notificationChanged: (Boolean) -> Unit, notificationCategory: (String, Boolean) -> Unit,
              notificationSettings: () -> Unit,
              reportSearchChanged: (String) -> Unit, reportFilterChanged: (ReportFilter) -> Unit,
              autoSyncChanged: (Boolean) -> Unit) {
        activity.window.statusBarColor = paper
        activity.window.navigationBarColor = Color.WHITE
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val shell = column().apply { setBackgroundColor(paper); fitsSystemWindows = true }
        val container = FrameLayout(activity)
        shell.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(activity).apply {
            setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        shell.addView(nav)
        for (name in listOf("Home", "Test", "Reports", "Settings")) {
            val page = column().apply { setPadding(dp(22), dp(20), dp(22), dp(28)) }
            pages[name] = page
            container.addView(ScrollView(activity).apply { addView(page); tag = name })
            text(page, "neo  /  YOUR PERSONAL ASSISTANT", 11f, teal, true)
            space(page)
            val tab = TextView(activity).apply {
                text = name; textSize = 14f; gravity = Gravity.CENTER; minHeight = dp(52)
                setOnClickListener { show(name) }
            }
            tabs[name] = tab
            nav.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val home = pages.getValue("Home")
        text(home, "A little less\ninterruption.", 34f, ink, true)
        text(home, "Your calls, thoughtfully handled.", 16f, muted)
        space(home)
        val hero = card(home, mint)
        text(hero, "LOCAL PREVIEW", 11f, teal, true)
        text(hero, "Meet your call assistant", 23f, ink, true)
        text(hero, "Try Neo's response before connecting real calls. You're always in control.", 15f, muted)
        button(hero, "Try a call  â†’") { show("Test") }
        val connection = card(home)
        text(connection, "Your Python connection", 19f, ink, true)
        connectionStatus = text(connection, "Not connected yet. Pair with your local Python service.", 14f, muted)
        syncOverview = text(connection, "", 12f, muted)
        button(connection, "Connect to Python", false, connect)
        button(connection, "Sync pending reports", false, sync)
        val rules = card(home)
        text(rules, "Made around your rules", 19f, ink, true)
        for ((title, description) in listOf(
            "01   Six seconds for you" to "Neo waits so you can answer first.",
            "02   Known contacts only" to "Unknown callers are never auto-answered.",
            "03   Catch up in your time" to "See the caller's message and Neo's reply.")) {
            space(rules, 10); text(rules, title, 16f, teal, true); text(rules, description, 14f, muted)
        }
        val preferences = card(home)
        text(preferences, "Quiet when you want it", 19f, ink, true)
        preferences.addView(Switch(activity).apply {
            text = "Speak demo greetings"; textSize = 15f; setTextColor(ink); minHeight = dp(56)
            isChecked = voiceEnabled
            setOnCheckedChangeListener { _, enabled -> voiceChanged(enabled) }
        })
        text(preferences, "Reports are read aloud only when you ask.", 13f, muted)
        text(home, "No real calls connected Â· No background monitoring", 12f, muted)

        val test = pages.getValue("Test")
        text(test, "Give Neo a try.", 30f, ink, true)
        text(test, "A practice call. No phone numbers are dialed.", 15f, muted)
        space(test)
        val state = card(test, mint)
        text(state, "PRACTICE CALL", 11f, teal, true)
        status = text(state, "Ready when you are.", 23f, teal, true)
        scenarios = column().apply { test.addView(this) }
        val known = card(scenarios)
        text(known, "Someone you know", 21f, ink, true)
        text(known, "Neo uses your selected answer delay. You can take the call yourself.", 14f, muted)
        button(known, "Test saved contact") { start(true) }
        button(known, "Choose a phone contact", false, contactTest)
        val unknown = card(scenarios)
        text(unknown, "A number you don't know", 21f, ink, true)
        text(unknown, "Neo will leave this call to you.", 14f, muted)
        button(unknown, "Test unknown number", false) { start(false) }
        actions = column().apply { test.addView(this) }
        answerButton = button(actions, "I'll take this call", action = answer)
        rejectButton = button(actions, "Decline call", false, reject)
        message = EditText(activity).apply {
            hint = "Type the practice caller's messageâ€¦"; textSize = 16f; minLines = 3
            setTextColor(ink); setHintTextColor(muted); gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(4000))
            test.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        save = button(test, "Save message & finish", action = saveMessage)
        dictateButton = button(test, "Dictate a practice message", false, dictate)
        end = button(test, "End test call", false, hangup)
        space(test)
        text(test, "Audio plays on this phone. Messages are typed for this preview. Leaving the app ends the test; each test lasts up to one minute.", 13f, muted)

        val reports = pages.getValue("Reports")
        text(reports, "All caught up.", 30f, ink, true)
        text(reports, "Your practice calls and conversations.", 15f, muted)
        space(reports)
        val reportCard = card(reports)
        text(reportCard, "CALL JOURNAL", 11f, teal, true)
        reportCard.addView(EditText(activity).apply {
            hint = "Search contact, message or date"; textSize = 15f; setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { reportSearchChanged(s?.toString() ?: "") }
                override fun afterTextChanged(s: Editable?) {}
            })
        })
        reportCard.addView(Spinner(activity).apply {
            contentDescription = "Filter reports by sync status or messages"
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf("All reports", "Pending upload", "Saved to Python", "With a message"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    reportFilterChanged(ReportFilter.entries[position])
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        history = text(reportCard, "", 15f)
        reportList = column().apply { reports.addView(this) }
        button(reports, "Read latest report aloud", false, readReport)
        button(reports, "Sync pending reports", false, sync)
        button(reports, "Clear local reports", false, clearReports)
        space(reports)
        text(reports, "Up to 100 local reports. Paired reports also save to your Python service. Real calls are not connected.", 12f, muted)
        val settings = pages.getValue("Settings")
        text(settings, "You're in control.", 30f, ink, true)
        text(settings, "Choose your access. Change your mind anytime.", 15f, muted)
        space(settings)
        val automation = card(settings, mint)
        text(automation, "Assistant preferences", 20f, ink, true)
        automation.addView(Switch(activity).apply {
            text = "Enable demo assistance"; textSize = 15f; minHeight = dp(56)
            isChecked = assistanceEnabled
            setOnCheckedChangeListener { _, enabled -> assistanceChanged(enabled) }
        })
        text(automation, "When enabled, Neo waits for your selected delay and handles known practice callers only. You can take over at any point.", 14f, muted)
        automation.addView(Switch(activity).apply {
            text = "Retry pending uploads automatically"; textSize = 14f; minHeight = dp(56)
            isChecked = activity.getSharedPreferences("neo_demo", Activity.MODE_PRIVATE).getBoolean("auto_sync", true)
            setOnCheckedChangeListener { _, enabled -> autoSyncChanged(enabled) }
        })
        text(automation, "Retries connection failures while Neo is open, up to five times. Wrong tokens and rejected reports need your attention. Retries pause when you leave the app.", 13f, muted)
        val notices = card(settings)
        val preferencesStore = activity.getSharedPreferences("neo_demo", Activity.MODE_PRIVATE)
        text(notices, "Notifications", 20f, ink, true)
        notificationStatus = text(notices, "", 13f, teal)
        text(notices, "Updates appear when practice calls ring, Neo starts a reply, a report is ready, or sync finishes. Caller names and message text stay out of notifications.", 14f, muted)
        notices.addView(Switch(activity).apply {
            text = "Show Neo notifications"; textSize = 15f; minHeight = dp(52)
            isChecked = preferencesStore.getBoolean("notifications", true)
            setOnCheckedChangeListener { _, enabled -> notificationChanged(enabled) }
        })
        for ((key, title) in listOf(NeoNotifications.CALLS to "Practice call updates",
            NeoNotifications.REPORTS to "Report ready alerts", NeoNotifications.SYNC to "Python sync updates")) {
            notices.addView(Switch(activity).apply {
                text = title; textSize = 14f; minHeight = dp(48)
                isChecked = preferencesStore.getBoolean(key, true)
                setOnCheckedChangeListener { _, enabled -> notificationCategory(key, enabled) }
            })
        }
        button(notices, "Allow notifications", false, notificationAccess)
        button(notices, "Android notification settings", false, notificationSettings)
        button(notices, "Send a test notification", false, notificationTest)
        text(notices, "These are local app events, not alerts for real SIM calls. Android notification settings and Do Not Disturb can silence them.", 13f, muted)
        val contacts = card(settings)
        text(contacts, "Contacts", 20f, ink, true)
        contactsStatus = text(contacts, "", 13f, teal)
        text(contacts, "Choose a person for a practice call. Your address book and phone numbers stay on the phone. The selected name can appear in a synced demo report.", 14f, muted)
        button(contacts, "Allow / manage contacts", false) { permission(android.Manifest.permission.READ_CONTACTS) }
        val camera = card(settings)
        text(camera, "Camera", 20f, ink, true)
        cameraStatus = text(camera, "", 13f, teal)
        text(camera, "Optional, on-screen presence check. No images are saved or uploaded. A face does not prove that you are available.", 14f, muted)
        button(camera, "Allow / manage camera", false) { permission(android.Manifest.permission.CAMERA) }
        button(camera, "Check presence now", false, presenceCheck)
        presenceStatus = text(camera, "No presence check yet.", 13f, muted)
        val microphone = card(settings)
        text(microphone, "Microphone", 20f, ink, true)
        microphoneStatus = text(microphone, "", 13f, teal)
        text(microphone, "Optional dictation for practice messages. On-device recognition is preferred when available; otherwise your phone's recognition service may process audio online. This does not capture SIM-call audio.", 14f, muted)
        button(microphone, "Allow / manage microphone", false) { permission(android.Manifest.permission.RECORD_AUDIO) }
        val timing = card(settings)
        text(timing, "Answer delay and voice", 20f, ink, true)
        text(timing, "Choose 1?60 seconds and an installed offline voice. Changes apply to new calls.", 14f, muted)
        button(timing, "Choose delay and preview voices", false) {
            activity.startActivity(android.content.Intent(activity, AssistantSettingsActivity::class.java))
        }
        val live = card(settings)
        text(live, "Owner status and real call activity", 20f, ink, true)
        text(live, "Review real call events and set your greeting preview. Call audio and conversation recording are not connected.", 14f, muted)
        button(live, "Open call activity and owner status", false) {
            activity.startActivity(android.content.Intent(activity, CallJournalActivity::class.java))
        }
        val calls = card(settings)
        text(calls, "Phone-call control", 20f, ink, true)
        callReadinessStatus = text(calls, CallReadiness.summary(activity), 14f, muted)
        text(calls, "Manage telephony & calls", 13f, teal, true)
        text(calls, "SIM answering requires the default dialer role. Only verified contacts are eligible after your selected delay. WhatsApp requires notification access and a verifiable caller identity.", 14f, muted)
        calls.addView(Switch(activity).apply {
            text = "Enable known-contact auto-answer"; textSize = 14f; minHeight = dp(52)
            isChecked = activity.getSharedPreferences("neo_demo", Activity.MODE_PRIVATE).getBoolean("bg_call_monitoring", false)
            setOnCheckedChangeListener { _, enabled ->
                activity.getSharedPreferences("neo_demo", Activity.MODE_PRIVATE).edit().putBoolean("bg_call_monitoring", enabled).apply()
                if (enabled) NeoForegroundService.start(activity) else NeoForegroundService.stop(activity)
            }
        })
        button(calls, "Allow / manage phone call access", false) { permission(android.Manifest.permission.READ_PHONE_STATE) }
        button(calls, "ðŸ’¬ Enable WhatsApp Call Auto-Answer (Notification Access)", false) {
            try {
                val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                activity.startActivity(intent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(activity, "Open Android Settings -> Special Access -> Notification Access and enable Neo!", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        button(calls, "âš¡ Set Neo as Default Phone App", false) {
            var alreadyDefault = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                    alreadyDefault = true
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val telecomManager = activity.getSystemService(android.telecom.TelecomManager::class.java)
                if (telecomManager?.defaultDialerPackage == activity.packageName) {
                    alreadyDefault = true
                }
            }

            if (alreadyDefault) {
                android.widget.Toast.makeText(activity, "Neo is ALREADY set as your default phone app!", android.widget.Toast.LENGTH_LONG).show()
                return@button
            }

            var launched = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                        activity.startActivityForResult(intent, 801)
                        launched = true
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NeoScreen", "RoleManager request failed: ${e.message}")
                }
            }

            if (!launched && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    val intent = android.content.Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
                    activity.startActivity(intent)
                    launched = true
                } catch (e: Exception) {
                    android.util.Log.w("NeoScreen", "TelecomManager default dialer intent failed: ${e.message}")
                }
            }

            if (!launched) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(activity, "Open Android Settings -> Default Apps -> Phone app and select Neo!", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        button(settings, "Open Android app permissions", false, manageAccess)
        text(settings, "In Android settings, open Permissions to allow or deny access. Neo refreshes access status when you return.", 13f, muted)
        activity.setContentView(shell)
        show("Home")
    }

    fun show(name: String) {
        if (hasActiveCall() && name != "Test") {
            Toast.makeText(activity, "Finish your test call before switching tabs.", Toast.LENGTH_SHORT).show()
            return
        }
        for ((key, page) in pages) (page.parent as View).visibility = if (key == name) View.VISIBLE else View.GONE
        for ((key, tab) in tabs) {
            tab.background = rounded(if (key == name) mint else Color.WHITE)
            tab.setTextColor(if (key == name) teal else muted)
            tab.typeface = if (key == name) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tab.contentDescription = "$key tab${if (key == name) ", selected" else ""}"
        }
    }

    fun showReportCards(rows: List<ReportRow>) {
        reportList.removeAllViews()
        for (row in rows) {
            val item = card(reportList)
            text(item, row.date, 12f, muted)
            text(item, row.caller, 20f, ink, true)
            text(item, if (row.synced) "SAVED TO PYTHON" else "PENDING UPLOAD", 11f,
                if (row.synced) teal else Color.rgb(143, 88, 23), true)
            text(item, row.outcome, 14f, muted)
            if (row.message.isNotBlank()) {
                space(item, 6); text(item, "CALLER'S MESSAGE", 11f, teal, true)
                text(item, row.message, 15f).setTextIsSelectable(true)
            }
            if (row.reply.isNotBlank()) {
                space(item, 6); text(item, "NEO'S REPLY", 11f, muted, true)
                text(item, row.reply, 14f).setTextIsSelectable(true)
            }
        }
    }
}
