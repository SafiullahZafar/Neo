package com.neo.assistant

import android.app.Notification
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.Person
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID

class NeoNotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private data class Pending(val id: String, val task: Runnable)
    private val pending = mutableMapOf<String, Pending>()
    private val packages = setOf("com.whatsapp", "com.whatsapp.w4b")
    @Suppress("DEPRECATION")
    private fun answer(notification: Notification): PendingIntent? {
        val explicit = notification.extras.getParcelable<PendingIntent>("android.answerIntent")
        if (explicit != null) return explicit
        return notification.actions?.firstOrNull {
            it.title?.toString()?.trim()?.lowercase() in setOf("answer", "accept", "receive")
        }?.actionIntent
    }
    @Suppress("DEPRECATION")
    private fun knownCaller(notification: Notification): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        val people = notification.extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST) ?: return null
        // Display names are not identities. Only a supplied phone URI can be verified against contacts.
        return people.asSequence().mapNotNull { person ->
            val uri = person.uri ?: return@mapNotNull null
            if (!uri.startsWith("tel:")) null else LiveCallJournal.contactName(this, android.net.Uri.parse(uri).schemeSpecificPart)
        }.firstOrNull()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in packages || !LiveCallJournal.enabled(this)) return
        val notification = sbn.notification
        if (notification.category != Notification.CATEGORY_CALL) return
        answer(notification) ?: return
        if (pending.containsKey(sbn.key)) return
        val name = knownCaller(notification)
        val delayMs = AssistantPreferences.delayMs(this)
        val id = UUID.randomUUID().toString()
        LiveCallJournal.event(this, id, "WhatsApp", name ?: "Unverified caller",
            if (name == null) "Incoming notification: contact unverified; auto-answer blocked" else "Incoming notification: waiting ${delayMs / 1000} seconds")
        val task = Runnable {
            val current = try { activeNotifications?.firstOrNull { it.key == sbn.key } } catch (_: SecurityException) { null }
            if (current != null && CallRules.mayAnswer(LiveCallJournal.enabled(this), knownCaller(current.notification) != null, answer(current.notification) != null)) {
                try {
                    val answerIntent = answer(current.notification) ?: return@Runnable
                    // Delegate launch privileges only to the verified WhatsApp answer action.
                    // A bare send() on Android 14+ can return without throwing while the OS
                    // blocks the activity launch, so it is not proof of a connected call.
                    if (Build.VERSION.SDK_INT >= 34) {
                        val options = ActivityOptions.makeBasic()
                        if (Build.VERSION.SDK_INT >= 36) {
                            options.setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            options.setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        }
                        answerIntent.send(this, 0, null, null, null, null, options.toBundle())
                    } else {
                        answerIntent.send()
                    }
                    LiveCallJournal.event(this, id, "WhatsApp", name ?: "", "Answer action sent; connection not confirmed; direct caller audio unavailable")
                    SpeakerGreeting.start(this, id,
                        alive = { LiveCallJournal.enabled(this) && activeNotifications?.any { it.key == sbn.key } == true },
                        ready = {
                            val ongoing = activeNotifications?.firstOrNull { it.key == sbn.key }?.notification
                            ongoing != null && answer(ongoing) == null && SpeakerGreeting.inCall(this)
                        }, event = { LiveCallJournal.event(this, id, "WhatsApp", name ?: "", it) })
                } catch (_: Exception) {
                    LiveCallJournal.event(this, id, "WhatsApp", name ?: "", "Answer action failed or expired")
                }
            }
        }
        pending[sbn.key] = Pending(id, task)
        if (name != null) handler.postDelayed(task, delayMs)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        pending.remove(sbn.key)?.let {
            handler.removeCallbacks(it.task)
            SpeakerGreeting.cancel(it.id)
            LiveCallJournal.event(this, it.id, "WhatsApp", "", "Call notification removed; final call outcome unknown")
        }
    }
    override fun onListenerDisconnected() { handler.removeCallbacksAndMessages(null); pending.values.forEach { SpeakerGreeting.cancel(it.id) }; pending.clear() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); pending.values.forEach { SpeakerGreeting.cancel(it.id) }; pending.clear(); super.onDestroy() }
}
