package com.neo.assistant

import android.content.Context
import android.telecom.Call
import android.telecom.PhoneAccount
import android.telecom.TelecomManager

/** Observation only: answering and call route control remain in the existing service. */
object SimAudioSession {
    private val calls = mutableMapOf<Call, Boolean>()
    @Volatile private var active: Call? = null
    @Volatile var generation = 0L
        private set
    @Volatile var route = 0
    val isActive: Boolean get() = active != null
    val state: String get() = if (isActive) "ACTIVE (verified SIM account)" else "INACTIVE / SIM account unverified / multiple calls"

    fun update(context: Context, call: Call) {
        calls[call] = try {
            context.getSystemService(TelecomManager::class.java).getPhoneAccount(call.details.accountHandle)
                ?.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) == true
        } catch (_: Exception) { false }
        refresh()
    }
    fun remove(call: Call) { calls.remove(call); refresh() }
    fun clear() { calls.clear(); refresh(); route = 0 }
    private fun refresh() {
        val connected = calls.keys.filter { it.state != Call.STATE_DISCONNECTED }
        val next = connected.singleOrNull()?.takeIf { calls[it] == true && it.state == Call.STATE_ACTIVE }
        if (next !== active) { active = next; generation++ }
    }
}
