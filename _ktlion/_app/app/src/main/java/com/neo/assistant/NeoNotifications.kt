package com.neo.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

class NeoNotifications(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE)
    private val syncPolicy = SyncNoticePolicy()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannels(listOf(
                NotificationChannel(CALLS, "Practice call status", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Practice ringing and assistant-response updates. Not real SIM calls."
                },
                NotificationChannel(REPORTS, "Reports", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A practice report is ready to review."
                },
                NotificationChannel(LIVE, "Real call activity", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(SYNC, "Python sync", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Report uploads and pending-sync reminders."
                }
            ))
        }
    }

    fun permitted(): Boolean {
        val permissionOk = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val enabledOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.areNotificationsEnabled()
        } else {
            true
        }
        return permissionOk && enabledOk
    }

    fun state(): String {
        if (!prefs.getBoolean("notifications", true)) return "Paused in Neo"
        if (!permitted()) return "Not allowed in Android · enable notifications below"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val blocked = listOf(CALLS, REPORTS, SYNC).count { manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }
            if (blocked > 0) return "Allowed · $blocked notification categories blocked in Android"
        }
        return "Allowed · delivery follows your Android sound and Do Not Disturb settings"
    }

    fun settings() {
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun enable(enabled: Boolean) {
        prefs.edit().putBoolean("notifications", enabled).apply()
        if (!enabled) manager.cancelAll()
    }

    fun category(channel: String, enabled: Boolean) {
        prefs.edit().putBoolean(channel, enabled).apply()
        if (!enabled) manager.cancel(id(channel))
    }

    fun ringing(known: Boolean, enabled: Boolean) {
        val description = when {
            !known -> "Unknown practice caller. Neo will not auto-answer."
            !enabled -> "Demo assistance is paused. This practice call is yours."
            else -> "Known practice caller. Neo gives you ${AssistantPreferences.delayMs(context) / 1000} seconds to answer."
        }
        post(CALLS, "Practice call ringing", description, "Test", ongoing = true)
    }

    fun assistantStarted() = post(CALLS, "Neo started a demo reply", "The practice greeting is ready. Open Neo to add a message or take over.", "Test", ongoing = true)
    fun reportReady(outcome: String) {
        manager.cancel(id(CALLS))
        post(REPORTS, "Practice call report ready", "$outcome Open Neo to review the report.", "Reports")
    }
    fun syncResult(success: Boolean, uploaded: Int, pending: Int, now: Long) {
        if (success) manager.cancel(id(SYNC))
        when (syncPolicy.result(success, uploaded, pending, now)) {
            SyncNotice.SAVED -> post(SYNC, "Reports saved to Python", "$uploaded practice report(s) uploaded successfully.", "Reports")
            SyncNotice.PENDING -> post(SYNC, "Reports are waiting to sync", "Your reports are safe on this phone. Open Neo to check the connection and retry.", "Home")
            null -> Unit
        }
    }

    fun liveSummary(count: Int): Boolean {
        if (count <= 0) return false
        return post(LIVE, "Neo call activity", "$count call record(s) to review. Open Neo for details.", "Live")
    }
    fun clearLive() { manager.cancel(id(LIVE)) }

    fun test(): Boolean = post(REPORTS, "Neo notifications are ready", "This is a test notification. Tap to return to Settings.", "Settings")
    fun clearReports() { manager.cancel(id(REPORTS)); manager.cancel(id(SYNC)) }
    fun clearActiveCall() { manager.cancel(id(CALLS)) }

    private fun post(channel: String, title: String, text: String, page: String, ongoing: Boolean = false): Boolean {
        if (!prefs.getBoolean("notifications", true) || !prefs.getBoolean(channel, true) || !permitted()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        val intent = Intent(context, if (page == "Live") CallJournalActivity::class.java else MainActivity::class.java)
            .setAction("com.neo.assistant.OPEN.$page")
            .putExtra("neo_page", page)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val tap = PendingIntent.getActivity(context, id(channel), intent, flags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.setSmallIcon(R.drawable.ic_stat_neo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setStyle(Notification.BigTextStyle().bigText(text))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val publicBuilder = Notification.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Neo update")
                .setContentText("Open Neo to review.")
            builder.setPublicVersion(publicBuilder.build())
            builder.setCategory(if (channel == CALLS) Notification.CATEGORY_STATUS else Notification.CATEGORY_EVENT)
            if (ongoing) builder.setTimeoutAfter(60_000) else builder.setTimeoutAfter(86_400_000)
        }

        return try { manager.notify(id(channel), builder.build()); true } catch (_: Exception) {
            ErrorHistory.describe(context, NeoProblem("NOTIFICATION_POST_FAILED", "Notification could not be posted", "Android rejected the notification request; the exact cause is unknown.", "Check Neo notification permission and channel settings. Open Neo to review locally saved reports."))
            false
        }
    }

    private fun id(channel: String) = when (channel) { CALLS -> 401; REPORTS -> 402; LIVE -> 404; else -> 403 }
    companion object {
        const val LIVE = "neo_live_activity"
        const val CALLS = "neo_practice_calls"
        const val REPORTS = "neo_reports"
        const val SYNC = "neo_sync"
    }
}
