package com.neo.assistant

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager

object CallReadiness {
    fun summary(context: Context): String {
        val component = ComponentName(context, NeoNotificationListener::class.java)
        val notificationAccess = try {
            if (Build.VERSION.SDK_INT >= 27) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationListenerAccessGranted(component)
            } else {
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                    ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
            }
        } catch (_: Exception) { null }
        val dialer = try {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage == context.packageName
        } catch (_: Exception) { null }
        return "WhatsApp notification access: ${when (notificationAccess) { true -> "allowed"; false -> "not allowed ? enable below"; null -> "could not check" }}.\n" +
            "SIM default phone role: ${when (dialer) { true -> "Neo"; false -> "not Neo"; null -> "could not check" }}.\n" +
            "WhatsApp pickup also requires a verifiable contact and an Answer action.\n" +
            "Caller audio: not connected. Automatic pickup does not include greeting, conversation recording or transcription. Android speech and cloned voices currently play app previews only."
    }
}
