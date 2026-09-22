package com.neo.assistant

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager

/** One permission audit for the SIM page. No privileged permission requests. */
object SimAccessStatus {
    fun granted(context: Context, name: String) = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    fun defaultDialer(context: Context) = context.getSystemService(TelecomManager::class.java).defaultDialerPackage == context.packageName
    fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    fun summary(context: Context): String {
        fun state(permission: String) = if (granted(context, permission)) "GRANTED" else "DENIED"
        val notifications = context.getSystemService(NotificationManager::class.java)
        val notice = if (Build.VERSION.SDK_INT >= 33) state(Manifest.permission.POST_NOTIFICATIONS)
            else "NOT_REQUIRED (runtime permission before Android 13)"
        val enabled = if (Build.VERSION.SDK_INT >= 24) notifications.areNotificationsEnabled().toString() else "check Android Settings"
        return "Phone access: ${state(Manifest.permission.READ_PHONE_STATE)}\n" +
            "Microphone: ${state(Manifest.permission.RECORD_AUDIO)}\nContacts: ${state(Manifest.permission.READ_CONTACTS)}\n" +
            "Notifications: $notice; app notifications enabled=$enabled\n" +
            "Default dialer: ${if (defaultDialer(context)) "GRANTED" else "DENIED"} (Android role)\n" +
            "Audio settings: ${state(Manifest.permission.MODIFY_AUDIO_SETTINGS)} (install-time)\n" +
            "Foreground service: ${state(Manifest.permission.FOREGROUND_SERVICE)} (install-time)\n" +
            "CALL_PHONE: NOT_REQUIRED; Neo does not originate calls here.\n" +
            "ANSWER_PHONE_CALLS: NOT_REQUIRED for default-dialer Call.answer().\n" +
            "Camera: NOT_REQUIRED for SIM audio. No normal permission grants direct call audio."
    }
    fun requestMissing(activity: Activity): String {
        val access = PermissionAccess(activity)
        val missing = requiredPermissions().filter { !access.allowed(it) }.toTypedArray()
        if (missing.isEmpty()) return "All required SIM permissions are already granted. No new permission prompt is needed. Use Manage permissions in Android to change them."
        access.requestMultiple(missing, 84,
            "Phone state observes calls; contacts enforce known-contact pickup; microphone enables explicit audio tests; notifications show call activity. These permissions do not unlock protected SIM audio. You can deny or revoke them.")
        return "Android access requested for ${missing.size} missing permission(s). The status above refreshes after your choice."
    }
    fun defaultSettings(activity: Activity): String {
        val alreadyDefault = defaultDialer(activity)
        return try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            if (alreadyDefault) "Neo is already your default phone app. Android settings opened so you can review or change it."
            else "Android settings opened. Choose Phone app, then Neo."
        } catch (_: Exception) {
            if (alreadyDefault) "Neo is already your default phone app."
            else "Open Android Settings > Apps > Default apps > Phone app, and choose Neo."
        }
    }
    fun systemSummary(context: Context): String {
        val system = context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val capture = granted(context, "android.permission.CAPTURE_AUDIO_OUTPUT")
        return "Root access for Neo: ${if (Process.myUid() == 0) "AVAILABLE" else "NOT_AVAILABLE"}\n" +
            "Device root installation: UNKNOWN (no su command or root prompt is executed)\n" +
            "System app flag: $system; privileged installation: ${if (capture) "audio privilege GRANTED" else "UNVERIFIED"}\n" +
            "Protected audio capture permission: ${if (capture) "GRANTED; route still UNTESTED" else "BLOCKED"}\n" +
            "Telephony RX / TX route: UNTESTED / UNTESTED. Root alone does not prove either direction."
    }
}
