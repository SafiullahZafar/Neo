package com.neo.assistant

enum class GreetingDecision { WAIT, START, STOP }

/** One greeting per attempt; a stale ringing notification is never sufficient. */
class SpeakerGreetingGate(private val startedAt: Long) {
    private var started = false
    private var stopped = false
    fun tick(now: Long, enabled: Boolean, alive: Boolean, ready: Boolean): GreetingDecision {
        if (stopped || !enabled || !alive) { stopped = true; return GreetingDecision.STOP }
        if (started) {
            if (!ready) { stopped = true; return GreetingDecision.STOP }
            return GreetingDecision.WAIT
        }
        val age = now - startedAt
        if (age >= 12_000L) { stopped = true; return GreetingDecision.STOP }
        if (age >= 2500L && ready) { started = true; return GreetingDecision.START }
        return GreetingDecision.WAIT
    }
}
