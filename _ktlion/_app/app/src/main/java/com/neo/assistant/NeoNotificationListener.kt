package com.neo.assistant

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.KeyEvent

class NeoNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "NeoNotificationListener"
        const val ANSWER_DELAY_MS = 3000L
        private val handler = Handler(Looper.getMainLooper())

        private val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        // Phrases that mean DECLINE / CALLBACK / NOT-ANSWER — skip these
        private val SKIP_PHRASES = listOf(
            "decline", "reject", "dismiss", "ignore", "cancel", "end",
            "call back",   // ← "Call back" was wrongly matched before
            "callback",
            "reply", "message", "msg", "text",
            "inkaar", "radd", "nahi"
        )

        // Phrases that mean ANSWER
        private val ANSWER_PHRASES = listOf(
            "answer", "accept", "receive",
            "qabool", "ubhaar",
            "جواب", "قبول"
            // NOTE: "call" alone is intentionally NOT here to avoid "Call back" matching
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val pkg = sbn?.packageName ?: return
        if (pkg !in WA_PACKAGES) return

        val prefs = getSharedPreferences("neo_demo", MODE_PRIVATE)
        if (!prefs.getBoolean("bg_call_monitoring", false)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val actions = notification.actions
        val isCallCategory = notification.category == Notification.CATEGORY_CALL
        val hasAnswerKey = extras.containsKey("android.answerIntent")

        Log.d(TAG, "WA notification: pkg=$pkg cat=${notification.category} " +
                "isCall=$isCallCategory hasAnswerKey=$hasAnswerKey actions=${actions?.size ?: 0}")
        actions?.forEachIndexed { i, a ->
            Log.d(TAG, "  Action[$i]: '${a.title}' intent=${a.actionIntent != null}")
        }

        var answerPendingIntent: PendingIntent? = null

        // ── Strategy 1: Android 12+ CallStyle EXTRA_ANSWER_INTENT ────────────
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                answerPendingIntent = extras.getParcelable("android.answerIntent", PendingIntent::class.java)
                if (answerPendingIntent != null) Log.d(TAG, "Got answerIntent via API31 getParcelable")
            } catch (_: Exception) {}
        }
        if (answerPendingIntent == null) {
            @Suppress("DEPRECATION")
            answerPendingIntent = extras.get("android.answerIntent") as? PendingIntent
            if (answerPendingIntent != null) Log.d(TAG, "Got answerIntent via deprecated get()")
        }

        // ── Strategy 2: Match action titles ──────────────────────────────────
        if (answerPendingIntent == null && actions != null) {
            // Pass A: explicit answer keywords
            for (action in actions) {
                val title = action.title?.toString()?.trim()?.lowercase() ?: ""
                if (SKIP_PHRASES.any { title.contains(it) }) {
                    Log.d(TAG, "  Skipping action (skip-phrase match): '$title'")
                    continue
                }
                if (ANSWER_PHRASES.any { title.contains(it) }) {
                    answerPendingIntent = action.actionIntent
                    Log.d(TAG, "Matched answer action (keyword): '$title'")
                    break
                }
            }

            // Pass B: exactly 2 actions → take the one that is NOT decline/skip
            if (answerPendingIntent == null && actions.size == 2) {
                for (action in actions) {
                    val title = action.title?.toString()?.trim()?.lowercase() ?: ""
                    if (SKIP_PHRASES.none { title.contains(it) } && action.actionIntent != null) {
                        answerPendingIntent = action.actionIntent
                        Log.d(TAG, "Matched answer action (2-action heuristic): '${action.title}'")
                        break
                    }
                }
            }

            // Pass C: call-category + any non-skip action
            if (answerPendingIntent == null && (isCallCategory || hasAnswerKey)) {
                for (action in actions) {
                    val title = action.title?.toString()?.trim()?.lowercase() ?: ""
                    if (SKIP_PHRASES.none { title.contains(it) } && action.actionIntent != null) {
                        answerPendingIntent = action.actionIntent
                        Log.d(TAG, "Matched answer action (call-cat fallback): '${action.title}'")
                        break
                    }
                }
            }
        }

        // ── Strategy 3: fullScreenIntent for call-category ───────────────────
        if (answerPendingIntent == null && (isCallCategory || hasAnswerKey)) {
            answerPendingIntent = notification.fullScreenIntent
            if (answerPendingIntent != null) Log.d(TAG, "Using fullScreenIntent")
        }

        if (answerPendingIntent != null) {
            val pi = answerPendingIntent
            Log.d(TAG, "Scheduling WhatsApp auto-answer in ${ANSWER_DELAY_MS}ms")
            handler.postDelayed({ fireAnswer(pi) }, ANSWER_DELAY_MS)
        } else {
            Log.w(TAG, "No answer PendingIntent found. actions=${actions?.map { it.title }}")
            // Headset hook fallback — works for some VOIP stacks even without a PI
            if (isCallCategory || hasAnswerKey) {
                Log.d(TAG, "Scheduling headset-hook-only fallback")
                handler.postDelayed({ dispatchHeadsetHook() }, ANSWER_DELAY_MS)
            }
        }
    }

    /** Acquire WakeLock, then fire PendingIntent + headset hook. */
    private fun fireAnswer(pi: PendingIntent) {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val wl = pm?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "$TAG:answerWakeLock"
        )
        wl?.acquire(10_000L)

        try {
            // Primary: PendingIntent with Android 14+ background-activity-start permission
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    // Android 14+ — must explicitly allow background activity starts
                    val opts = ActivityOptions.makeBasic().apply {
                        @Suppress("NewApi")
                        setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    pi.send(this, 0, null, null, null, null, opts.toBundle())
                    Log.d(TAG, "Fired answerIntent with API34 bg-activity-start opts")
                } else {
                    pi.send(this, 0, null)
                    Log.d(TAG, "Fired answerIntent with send(ctx,0,null)")
                }
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "PendingIntent cancelled (call ended?): ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "PendingIntent.send failed: ${e.message}")
            }

            // Belt-and-braces: headset hook (answers VOIP on most devices)
            dispatchHeadsetHook()

            try { NeoNotifications(this).assistantStarted() } catch (_: Exception) {}
        } finally {
            try { wl?.release() } catch (_: Exception) {}
        }
    }

    private fun dispatchHeadsetHook() {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
            audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
            Log.d(TAG, "Dispatched HEADSETHOOK via AudioManager")
        } catch (e: Exception) {
            Log.w(TAG, "HeadsetHook dispatch failed: ${e.message}")
        }
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "input keyevent 79"))
            Log.d(TAG, "Shell keyevent 79 sent")
        } catch (e: Exception) {
            Log.w(TAG, "Shell keyevent 79 failed: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val pkg = sbn?.packageName ?: return
        if (pkg in WA_PACKAGES) {
            handler.removeCallbacksAndMessages(null)
            Log.d(TAG, "WA notification removed — pending answer cancelled")
        }
    }
}
