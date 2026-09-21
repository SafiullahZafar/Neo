package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class CallRulesTest {
    @Test fun answeringRequiresAllThreeConditions() {
        for (enabled in listOf(false, true)) for (known in listOf(false, true)) for (ringing in listOf(false, true)) {
            assertEquals(enabled && known && ringing, CallRules.mayAnswer(enabled, known, ringing))
        }
        assertEquals(6000L, CallRules.ANSWER_DELAY_MS)
    }
    @Test fun unknownAvailabilityIsNotInvented() {
        val greeting = CallRules.greeting("Sam", "sleeping")
        assertTrue(greeting.contains("cannot confirm"))
        assertFalse(greeting.contains("sleeping"))
        assertTrue(greeting.contains("automated assistant"))
    }
    @Test fun ownerSelectedStatusIsAttributedToOwner() {
        assertTrue(CallRules.greeting("Sam", "Busy").contains("Sam has set their status to busy"))
        assertTrue(CallRules.greeting("", "Unknown").contains("the phone owner"))
    }
}
