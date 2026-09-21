package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class ConfigurableDelayTest {
    @Test fun selectedDelayControlsBoundary() {
        for (seconds in listOf(1, 6, 20, 60)) {
            val call = CallSession(true, 1000, answerDelayMs = CallRules.delayMs(seconds))
            assertFalse(call.tick(1000 + seconds * 1000L - 1))
            assertTrue(call.tick(1000 + seconds * 1000L))
            assertFalse(call.tick(1000 + seconds * 1000L + 1))
        }
    }
    @Test fun longDelayStillLeavesTimeForDemoMessage() {
        val call = CallSession(true, 0, answerDelayMs = 60000)
        assertTrue(call.tick(60000))
        assertEquals(90000L, call.maxDurationMs)
    }
    @Test fun ownerPickupAndUnknownCallerRemainProtected() {
        val owner = CallSession(true, 0, answerDelayMs = 1000)
        owner.ownerAnswer()
        assertFalse(owner.tick(1000))
        assertFalse(CallSession(false, 0, answerDelayMs = 1000).tick(1000))
        assertFalse(CallSession(true, 0, false, 1000).tick(1000))
    }
    @Test fun invalidPersistedDelaysAreBounded() {
        assertEquals(1000L, CallRules.delayMs(-4))
        assertEquals(60000L, CallRules.delayMs(Int.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { CallSession(true, 0, answerDelayMs = 0) }
    }
}
