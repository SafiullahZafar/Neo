package com.neo.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import java.util.UUID

/** Fallback observation only: answer timers belong to a live InCallService, not a receiver. */
class NeoCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED || !LiveCallJournal.enabled(context)) return
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (android.os.Build.VERSION.SDK_INT >= 23 && telecom?.defaultDialerPackage == context.packageName) return
        val prefs = context.getSharedPreferences("neo_live_calls", Context.MODE_PRIVATE)
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (prefs.getString("sim_id", null) != null) return
                val id = UUID.randomUUID().toString()
                prefs.edit().putString("sim_id", id).apply()
                LiveCallJournal.event(context, id, "SIM", "Caller identity unavailable", "Incoming call observed; Neo is not the default dialer; no answer requested")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> prefs.getString("sim_id", null)?.let {
                LiveCallJournal.event(context, it, "SIM", "", "Phone went off-hook; audio unavailable")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> prefs.getString("sim_id", null)?.let {
                LiveCallJournal.event(context, it, "SIM", "", "Phone returned to idle")
                prefs.edit().remove("sim_id").apply()
            }
        }
    }
}
