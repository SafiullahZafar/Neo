package com.neo.assistant

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import java.util.UUID

@android.annotation.TargetApi(23)
class NeoInCallService : InCallService() {
    private val handler = Handler(Looper.getMainLooper())
    private data class Tracked(val callback: Call.Callback, val answer: Runnable)
    private val tracked = mutableMapOf<Call, Tracked>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (!LiveCallJournal.enabled(this) || call.state != Call.STATE_RINGING) return
        val delayMs = AssistantPreferences.delayMs(this)
        val id = UUID.randomUUID().toString()
        val number = call.details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        val name = LiveCallJournal.contactName(this, number)
        val caller = name ?: "Unknown caller"
        fun record(status: String) = LiveCallJournal.event(this, id, "SIM", caller, status)
        var requested = false
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
                when (state) {
                    Call.STATE_ACTIVE -> record(if (requested) "Connected after Neo request; greeting not transmitted; audio unavailable" else "Connected without Neo request")
                    Call.STATE_DISCONNECTED -> record("Call ended; no audio or transcript captured")
                }
            }
        }
        tracked[call] = Tracked(callback, answer)
        call.registerCallback(callback, handler)
        if (name != null) handler.postDelayed(answer, delayMs)
    }
    override fun onCallRemoved(call: Call) {
        tracked.remove(call)?.let { handler.removeCallbacks(it.answer); call.unregisterCallback(it.callback) }
        super.onCallRemoved(call)
    }
    override fun onDestroy() {
        tracked.forEach { (call, entry) -> call.unregisterCallback(entry.callback) }
        tracked.clear(); handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
