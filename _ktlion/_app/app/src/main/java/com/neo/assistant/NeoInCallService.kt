package com.neo.assistant

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import java.util.UUID

@android.annotation.TargetApi(23)
class NeoInCallService : InCallService() {
    private val handler = Handler(Looper.getMainLooper())
    private data class Tracked(val callback: Call.Callback, val answer: Runnable, val id: String)
    private val tracked = mutableMapOf<Call, Tracked>()
    private val audioObservers = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val observer = object : Call.Callback() {
            override fun onStateChanged(current: Call, state: Int) { SimAudioSession.update(this@NeoInCallService, current) }
            override fun onDetailsChanged(current: Call, details: Call.Details) { SimAudioSession.update(this@NeoInCallService, current) }
        }
        audioObservers[call] = observer
        call.registerCallback(observer, handler)
        SimAudioSession.update(this, call)

        if (!LiveCallJournal.enabled(this)) return
        if (call.state != Call.STATE_RINGING && call.state != Call.STATE_ACTIVE) return

        val delayMs = AssistantPreferences.delayMs(this)
        val id = UUID.randomUUID().toString()
        val number = call.details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        val name = LiveCallJournal.contactName(this, number)
        val caller = name ?: (number ?: "Unknown caller")
        fun record(status: String) = LiveCallJournal.event(this, id, "SIM", caller, status)

        var requested = false
        var greeted = false

        fun triggerGreeting() {
            if (!greeted && SpeakerGreeting.enabled(this@NeoInCallService)) {
                greeted = true
                record("SIM Call Connected; starting AI voice greeting over call stream")
                SpeakerGreeting.start(this@NeoInCallService, id,
                    alive = { LiveCallJournal.enabled(this@NeoInCallService) && (call.state == Call.STATE_ACTIVE || call.state == Call.STATE_RINGING) },
                    ready = { true },
                    prepareRoute = {
                        record("Direct in-call audio stream active")
                        val restoreRoute: () -> Unit = {}
                        restoreRoute
                    }, event = { record(it) })
            }
        }

        val answer = Runnable {
            if (call.state == Call.STATE_RINGING) {
                try {
                    requested = true
                    record("Answer requested; answering SIM call")
                    call.answer(0)
                } catch (_: Exception) { requested = false; record("Answer request failed") }
            }
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(current: Call, state: Int) {
                if (state != Call.STATE_RINGING) handler.removeCallbacks(answer)
                if (state != Call.STATE_ACTIVE && state != Call.STATE_RINGING) SpeakerGreeting.cancel(id)
                when (state) {
                    Call.STATE_ACTIVE -> triggerGreeting()
                    Call.STATE_DISCONNECTED -> record("Call ended")
                }
            }
        }

        tracked[call] = Tracked(callback, answer, id)
        call.registerCallback(callback, handler)

        if (call.state == Call.STATE_RINGING) {
            record("Incoming SIM call: waiting ${delayMs / 1000} seconds")
            handler.postDelayed(answer, delayMs)
        } else if (call.state == Call.STATE_ACTIVE) {
            triggerGreeting()
        }
    }

    override fun onCallRemoved(call: Call) {
        audioObservers.remove(call)?.let { call.unregisterCallback(it) }
        SimAudioSession.remove(call)
        tracked.remove(call)?.let { handler.removeCallbacks(it.answer); call.unregisterCallback(it.callback); SpeakerGreeting.cancel(it.id) }
        super.onCallRemoved(call)
    }

    override fun onDestroy() {
        audioObservers.forEach { (call, callback) -> call.unregisterCallback(callback) }
        audioObservers.clear()
        SimAudioSession.clear()
        tracked.forEach { (call, entry) -> call.unregisterCallback(entry.callback); SpeakerGreeting.cancel(entry.id) }
        tracked.clear(); handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        SimAudioSession.route = audioState.route
    }
}
