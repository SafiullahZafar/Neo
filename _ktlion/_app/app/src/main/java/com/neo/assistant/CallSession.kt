package com.neo.assistant

enum class CallState { RINGING, OWNER_ANSWERED, ASSISTANT, REJECTED, ENDED, TIMED_OUT }

/** Platform-independent policy, timed with a monotonic clock supplied by the caller. */
class CallSession(val knownContact: Boolean, val startedAt: Long, val assistanceEnabled: Boolean = true, val answerDelayMs: Long = WAIT_MS) {
    init { require(answerDelayMs in 1000L..60000L) }
    val maxDurationMs = maxOf(MAX_DURATION_MS, answerDelayMs + 30_000L)
    var state: CallState = CallState.RINGING
        private set

    fun tick(now: Long): Boolean {
        if (state != CallState.RINGING) return false
        if (now - startedAt >= maxDurationMs) { state = CallState.TIMED_OUT; return false }
        if (!assistanceEnabled || !knownContact || now - startedAt < answerDelayMs) return false
        state = CallState.ASSISTANT
        return true
    }

    fun ownerAnswer() {
        if (state == CallState.RINGING || state == CallState.ASSISTANT) state = CallState.OWNER_ANSWERED
    }

    fun reject() {
        if (state == CallState.RINGING) state = CallState.REJECTED
    }

    fun end() { state = CallState.ENDED }

    companion object {
        const val WAIT_MS = 6_000L
        const val MAX_DURATION_MS = 60_000L
    }
}
