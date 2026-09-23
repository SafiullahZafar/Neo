package com.neo.assistant

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

object ErrorHistory {
    @Synchronized fun describe(context: Context, problem: NeoProblem): String {
        val message = problem.display()
        try {
            val prefs = context.getSharedPreferences("neo_errors", Context.MODE_PRIVATE)
            val entries = read(context)
            val previous = entries.optJSONObject(0)
            val next = JSONArray().put(JSONObject().put("code", problem.code).put("text", message).put("time", System.currentTimeMillis())
                .put("count", if (previous?.optString("code") == problem.code) previous.optInt("count", 1) + 1 else 1))
            val start = if (previous?.optString("code") == problem.code) 1 else 0
            for (i in start until minOf(entries.length(), start + 29)) next.put(entries.getJSONObject(i))
            prefs.edit().putString("items", next.toString()).apply()
        } catch (_: Exception) { /* The original failure must remain visible even if history cannot be saved. */ }
        return message
    }
    private fun read(context: Context): JSONArray = try {
        JSONArray(context.getSharedPreferences("neo_errors", Context.MODE_PRIVATE).getString("items", "[]"))
    } catch (_: Exception) { JSONArray() }
    @Synchronized fun text(context: Context): String {
        val entries = read(context)
        return (0 until entries.length()).joinToString("\n\n") { i ->
            val row = entries.getJSONObject(i)
            "${java.util.Date(row.getLong("time"))} (${row.optInt("count", 1)} occurrence(s))\n${row.getString("text")}"
        }.ifEmpty { "No recorded errors yet. This does not prove all features are working; try the relevant action first." }
    }
    @Synchronized fun clear(context: Context) { context.getSharedPreferences("neo_errors", Context.MODE_PRIVATE).edit().clear().apply() }
}

class ErrorHistoryActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,32,28,32); setBackgroundColor(Color.rgb(246,249,245)) }
        setContentView(ScrollView(this).apply { addView(layout) })
        fun label(value: String, size: Float = 16f) = TextView(this).apply { text = value; textSize = size; setPadding(0,16,0,16); setTextColor(Color.rgb(28,55,48)); layout.addView(this) }
        fun button(value: String, action: () -> Unit) { layout.addView(Button(this).apply { text = value; setOnClickListener { action() } }) }
        button("Back") { finish() }
        label("Recent errors and fixes", 25f)
        label("The latest 30 error groups stay on this phone. Codes explain what Neo observed and the next step. Unknown causes are not treated as confirmed diagnoses. No tokens, caller names, transcripts or raw exception messages are stored here.")
        val details = label(ErrorHistory.text(this)).apply { setTextIsSelectable(true) }
        button("Refresh") { details.text = ErrorHistory.text(this) }
        button("Copy error report") {
            val report = "Neo ${BuildConfig.VERSION_NAME}; Android ${android.os.Build.VERSION.RELEASE}\n\n${ErrorHistory.text(this)}"
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Neo error report", report))
            Toast.makeText(this, "Copied error report", Toast.LENGTH_SHORT).show()
        }
        button("Clear error history") { ErrorHistory.clear(this); details.text = ErrorHistory.text(this) }
    }
}
