package com.neo.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

class NeoCallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NeoCallReceiver"
        const val ANSWER_DELAY_MS = 3000L
        private var isRinging = false
        private val handler = Handler(Looper.getMainLooper())
        private var answerRunnable: Runnable? = null

        /** Try every available method to answer the ringing call */
        fun answerCall(context: Context) {
            var answered = false

            // Ensure sound stream and speakerphone are configured while preserving silent mode
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                audioManager?.let { audio ->
                    // Do NOT change ringerMode (preserve silent mode if enabled by user)
                    audio.mode = android.media.AudioManager.MODE_IN_CALL
                    val maxVol = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL)
                    audio.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, maxVol, 0)
                    audio.isSpeakerphoneOn = true
                }
                Log.d(TAG, "Audio stream set to MODE_IN_CALL, speakerphone ON (silent mode preserved)")
            } catch (e: Exception) {
                Log.w(TAG, "Audio mode adjustment warning: ${e.message}")
            }

            // Strategy 1: Direct ServiceManager reflection to com.android.internal.telephony.ITelephony$Stub (works on Android 5.1 / Oppo)
            try {
                val serviceManagerClass = Class.forName("android.os.ServiceManager")
                val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
                val binder = getServiceMethod.invoke(null, "phone") as? android.os.IBinder
                if (binder != null) {
                    val iTelephonyClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
                    val asInterfaceMethod = iTelephonyClass.getMethod("asInterface", android.os.IBinder::class.java)
                    val iTelephony = asInterfaceMethod.invoke(null, binder)
                    val answerRingingCallMethod = iTelephony.javaClass.getMethod("answerRingingCall")
                    answerRingingCallMethod.invoke(iTelephony)
                    answered = true
                    Log.d(TAG, "Answered via ServiceManager -> ITelephony.Stub.asInterface")
                }
            } catch (e: Exception) {
                Log.w(TAG, "ServiceManager ITelephony.Stub reflection failed: ${e.message}")
            }

            // Strategy 2: TelephonyManager getITelephony() hidden API reflection
            if (!answered) {
                try {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val getITelephony = tm?.javaClass?.getDeclaredMethod("getITelephony")
                    getITelephony?.isAccessible = true
                    val iTelephony = getITelephony?.invoke(tm)
                    val answerRingingCall = iTelephony?.javaClass?.getDeclaredMethod("answerRingingCall")
                    answerRingingCall?.isAccessible = true
                    answerRingingCall?.invoke(iTelephony)
                    answered = true
                    Log.d(TAG, "Answered via TelephonyManager.getITelephony reflection")
                } catch (e: Exception) {
                    Log.w(TAG, "TelephonyManager reflection failed: ${e.message}")
                }
            }

            // Strategy 3: TelecomManager (API 26+)
            if (!answered && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    if (context.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        @Suppress("DEPRECATION")
                        tm?.acceptRingingCall()
                        answered = true
                        Log.d(TAG, "Answered via TelecomManager.acceptRingingCall()")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "TelecomManager failed: ${e.message}")
                }
            }

            // Strategy 4: Shell keyevent 79 (HEADSETHOOK) & 5 (CALL) execution (Lollipop / Oppo system hook)
            try {
                Runtime.getRuntime().exec("input keyevent 79") // KEYCODE_HEADSETHOOK
                Runtime.getRuntime().exec("input keyevent 5")  // KEYCODE_CALL
                Log.d(TAG, "Executed input keyevent 79 and 5")
            } catch (e: Exception) {
                Log.w(TAG, "Shell input keyevent execution failed: ${e.message}")
            }

            // Strategy 5: Ordered ACTION_MEDIA_BUTTON broadcast
            try {
                val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
                }
                val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
                }
                context.sendOrderedBroadcast(downIntent, null)
                context.sendOrderedBroadcast(upIntent, null)
                Log.d(TAG, "Sent ACTION_MEDIA_BUTTON broadcast")
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_MEDIA_BUTTON broadcast failed: ${e.message}")
            }

            // Notify user Neo answered
            try { NeoNotifications(context).assistantStarted() } catch (_: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val prefs = context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("bg_call_monitoring", false)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d(TAG, "Phone State Changed: state=$state")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (isRinging) return  // already handling
                isRinging = true

                // Cancel any existing timer
                answerRunnable?.let { handler.removeCallbacks(it) }

                // Schedule auto-answer after 3 seconds
                answerRunnable = Runnable {
                    if (isRinging) {
                        Log.d(TAG, "3-second timer fired. Auto-answering call.")
                        answerCall(context)
                    }
                }
                handler.postDelayed(answerRunnable!!, ANSWER_DELAY_MS)
                Log.d(TAG, "Call ringing. Auto-answer scheduled in 3 seconds.")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                isRinging = false
                answerRunnable?.let { handler.removeCallbacks(it) }
                Log.d(TAG, "Call off-hook. User or Neo answered.")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                isRinging = false
                answerRunnable?.let { handler.removeCallbacks(it) }
                Log.d(TAG, "Call ended or idle.")
            }
        }
    }
}
