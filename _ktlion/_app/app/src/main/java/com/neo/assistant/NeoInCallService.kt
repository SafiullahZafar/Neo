package com.neo.assistant

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class NeoInCallService : InCallService() {
    companion object {
        private const val TAG = "NeoInCallService"
        const val ANSWER_DELAY_MS = 3000L
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: state=${call.state}")
        val prefs = getSharedPreferences("neo_demo", MODE_PRIVATE)
        if (!prefs.getBoolean("bg_call_monitoring", false)) return

        if (call.state == Call.STATE_RINGING) {
            handler.postDelayed({
                try {
                    if (call.state == Call.STATE_RINGING) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            call.answer(0)
                        } else {
                            @Suppress("DEPRECATION")
                            call.answer(0)
                        }
                        Log.d(TAG, "Call auto-answered after 3s via InCallService")
                        try { NeoNotifications(this).assistantStarted() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to answer call via InCallService: ${e.message}", e)
                }
            }, ANSWER_DELAY_MS)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        handler.removeCallbacksAndMessages(null)
    }
}
