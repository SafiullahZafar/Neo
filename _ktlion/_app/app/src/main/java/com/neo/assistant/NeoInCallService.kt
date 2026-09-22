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
        if (!LiveCallJournal.enabled(this) || call.state != Call.STATE_RINGING) return
        val delayMs = AssistantPreferences.delayMs(this)
        val id = UUID.randomUUID().toString()
        val number = call.details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        val name = LiveCallJournal.contactName(this, number)
        val caller = name ?: "Unknown caller"
        fun record(status: String) = LiveCallJournal.event(this, id, "SIM", caller, status)
        var requested = false
        var greeted = false
        record(if (name == null) "Ringing: unverified contact; auto-answer blocked" else "Ringing: waiting ${delayMs / 1000} seconds")
        val answer = Runnable {
            if (CallRules.mayAnswer(LiveCallJournal.enabled(this), LiveCallJournal.contactName(this, number) != null, call.state == Call.STATE_RINGING)) {
                try {
                    requested = true
                    record("Answer requested; waiting for call connection")
                    call.answer(0)
                } catch (_: Exception) { requested = false; record("Answer request failed") }
            }
        }
        val callback = object : Call.Callback() {
            override fun onStateChanged(current: Call, state: Int) {
                if (state != Call.STATE_RINGING) handler.removeCallbacks(answer)
                if (state != Call.STATE_ACTIVE && state != Call.STATE_RINGING) SpeakerGreeting.cancel(id)
                when (state) {
                    Call.STATE_ACTIVE -> {
                        record(if (requested) "Connected after Neo request; direct caller audio unavailable" else "Connected without Neo request")
                        if (requested && !greeted && SpeakerGreeting.enabled(this@NeoInCallService)) {
                            greeted = true
                            SpeakerGreeting.start(this@NeoInCallService, id,
                                alive = { LiveCallJournal.enabled(this@NeoInCallService) && call.state == Call.STATE_ACTIVE },
                                ready = { call.state == Call.STATE_ACTIVE },
                                prepareRoute = {
                                    val previous = callAudioState?.route
                                    check(((callAudioState?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_SPEAKER) != 0)
                                    setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                                    record("Speaker route requested for acoustic test; no volume change")
                                    val restoreRoute: () -> Unit = {
                                        if (previous != null && call.state == Call.STATE_ACTIVE && callAudioState?.route == CallAudioState.ROUTE_SPEAKER) {
                                            setAudioRoute(previous)
                                        }
                                    }
                                    restoreRoute
                                }, event = { record(it) })
                        }
                    }
                    Call.STATE_DISCONNECTED -> record("Call ended; no audio or transcript captured")
                }
            }
        }
        tracked[call] = Tracked(callback, answer, id)
        call.registerCallback(callback, handler)
        if (name != null) handler.postDelayed(answer, delayMs)
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
