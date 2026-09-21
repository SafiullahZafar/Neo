package com.neo.assistant

object CallRules {
    const val ANSWER_DELAY_MS = 6000L
    fun delayMs(seconds: Int) = seconds.coerceIn(1, 60) * 1000L
    fun mayAnswer(enabled: Boolean, knownContact: Boolean, ringing: Boolean) = enabled && knownContact && ringing
    fun greeting(owner: String, situation: String): String {
        val name = owner.trim().take(60).ifBlank { "the phone owner" }
        val reason = when (situation) {
            "Busy" -> "$name has set their status to busy."
            "Away" -> "$name has set their status to away."
            else -> "I cannot confirm $name's availability right now."
        }
        return "Hello, I'm Neo, an automated assistant for $name. $reason"
    }
}
