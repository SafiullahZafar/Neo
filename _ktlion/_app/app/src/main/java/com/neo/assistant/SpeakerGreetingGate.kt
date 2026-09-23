package com.neo.assistant

enum class GreetingDecision { WAIT, START, STOP }

/** One greeting per attempt; start immediately when ready. */
class SpeakerGreetingGate(private val startedAt: Long) {
    private var started = false
    private var stopped = false
    fun tick(now: Long, enabled: Boolean, alive: Boolean, ready: Boolean): GreetingDecision {
        if (stopped || !enabled || !alive) { stopped = true; return GreetingDecision.STOP }
        if (started) return GreetingDecision.WAIT
        val age = now - startedAt
        if (age >= 30_000L) { stopped = true; return GreetingDecision.STOP }
        if (ready) { started = true; return GreetingDecision.START }
        return GreetingDecision.WAIT
    }
}
