package com.neo.assistant

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import android.content.SharedPreferences
import java.text.DateFormat
import java.util.Date

class CallJournalActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var rows: LinearLayout
    private val journal by lazy { getSharedPreferences("neo_live_calls", MODE_PRIVATE) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 36, 24, 24); setBackgroundColor(Color.rgb(246, 249, 245))
        }
        setContentView(ScrollView(this).apply { addView(container) })
        fun label(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.rgb(28, 55, 48)); setPadding(0, 12, 0, 12); container.addView(this)
        }
        fun button(value: String, action: () -> Unit) { container.addView(Button(this).apply { text = value; setOnClickListener { action() } }) }
        button("Back to Neo") { finish() }
        label("Your call assistant", 26f)
        label("Set only what you want callers to know. Camera observations do not identify you or prove your availability.")
        val settings = getSharedPreferences("neo_demo", MODE_PRIVATE)
        val owner = EditText(this).apply {
            hint = "Your preferred name"; setSingleLine(); filters = arrayOf(android.text.InputFilter.LengthFilter(60))
            setText(settings.getString("owner_name", "")); container.addView(this)
        }
        val options = listOf("Unknown", "Busy", "Away")
        val situation = Spinner(this).apply {
            adapter = ArrayAdapter(this@CallJournalActivity, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(options.indexOf(settings.getString("owner_situation", "Unknown")).coerceAtLeast(0)); container.addView(this)
        }
        val preview = label(CallRules.greeting(owner.text.toString(), situation.selectedItem.toString()))
        button("Save availability and preview greeting") {
            settings.edit().putString("owner_name", owner.text.toString().trim()).putString("owner_situation", situation.selectedItem.toString()).apply()
            preview.text = CallRules.greeting(owner.text.toString(), situation.selectedItem.toString())
        }
        label("Greeting preview only. SIM and WhatsApp caller audio is not connected. Neo does not transmit this greeting, record conversations or create transcripts.")
        label("Real call activity", 24f)
        label("Latest 100 calls, stored on this phone. These event records are separate from Python practice reports. Notifications contain no caller details.")
        button("Mark reviewed") { LiveCallJournal.markRead(this); NeoNotifications(this).clearLive(); render() }
        button("Delete local call activity") {
            AlertDialog.Builder(this).setTitle("Delete local call activity?").setMessage("This removes Neo's event records, not your phone or WhatsApp call history.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> LiveCallJournal.clear(this); NeoNotifications(this).clearLive(); render() }.show()
        }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; container.addView(this) }
        render()
    }
    private fun render() {
        rows.removeAllViews()
        val records = LiveCallJournal.records(this)
        if (records.length() == 0) rows.addView(TextView(this).apply { text = "No real call events recorded yet." })
        for (i in records.length() - 1 downTo 0) {
            val row = records.getJSONObject(i)
            val events = row.getJSONArray("events")
            rows.addView(TextView(this).apply {
                textSize = 16f; setTextColor(Color.rgb(28, 55, 48)); setPadding(16, 24, 16, 24)
                text = buildString {
                    append(if (row.optBoolean("unread")) "NEW - " else "")
                    append(row.optString("source")); append(" / "); append(row.optString("caller")); append("\n")
                    for (j in 0 until events.length()) {
                        val event = events.getJSONObject(j)
                        append(DateFormat.getDateTimeInstance().format(Date(event.getLong("time")))); append("\n")
                        append(event.getString("status")); append("\n")
                    }
                    append("Prepared greeting (not sent): "); append(row.optString("greeting"))
                }
                setTextIsSelectable(true)
            })
        }
    }
    override fun onResume() { super.onResume(); journal.registerOnSharedPreferenceChangeListener(this); render() }
    override fun onPause() { journal.unregisterOnSharedPreferenceChangeListener(this); super.onPause() }
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) { if (key == "records") render() }
}
