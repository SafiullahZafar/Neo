package com.neo.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

/** Device-local event records, deliberately separate from the Python demo-report API. */
object LiveCallJournal {
    private fun prefs(context: Context) = context.getSharedPreferences("neo_live_calls", Context.MODE_PRIVATE)
    @Synchronized fun records(context: Context): JSONArray = try {
        JSONArray(prefs(context).getString("records", "[]"))
    } catch (_: Exception) { JSONArray() }

    @Synchronized fun event(context: Context, id: String, source: String, caller: String, status: String) {
        val rows = records(context)
        val row = (0 until rows.length()).map { rows.getJSONObject(it) }.find { it.optString("id") == id }
            ?: JSONObject().put("id", id).put("source", source).put("caller", caller.take(160))
                .put("started", System.currentTimeMillis()).put("events", JSONArray()).also { rows.put(it) }
        val events = row.getJSONArray("events")
        if (events.length() > 0 && events.getJSONObject(events.length() - 1).optString("status") == status) return
        events.put(JSONObject().put("time", System.currentTimeMillis()).put("status", status))
        row.put("unread", true)
        val settings = context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE)
        if (!row.has("greeting")) row.put("greeting", CallRules.greeting(settings.getString("owner_name", "") ?: "",
            settings.getString("owner_situation", "Unknown") ?: "Unknown"))
        while (rows.length() > 100) rows.remove(0)
        prefs(context).edit().putString("records", rows.toString()).apply()
        NeoNotifications(context).liveSummary(unread(context))
    }
    @Synchronized fun unread(context: Context): Int {
        val rows = records(context)
        return (0 until rows.length()).count { rows.getJSONObject(it).optBoolean("unread") }
    }
    @Synchronized fun markRead(context: Context) {
        val rows = records(context)
        for (i in 0 until rows.length()) rows.getJSONObject(i).put("unread", false)
        prefs(context).edit().putString("records", rows.toString()).apply()
    }
    @Synchronized fun clear(context: Context) { prefs(context).edit().remove("records").apply() }

    fun contactName(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        if (android.os.Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null }
    }
    fun enabled(context: Context) = context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE).getBoolean("bg_call_monitoring", false)
}
