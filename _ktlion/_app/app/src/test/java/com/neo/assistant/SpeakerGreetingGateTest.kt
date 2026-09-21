package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class SpeakerGreetingGateTest {
    @Test fun ringingAloneNeverTriggersGreeting() {
        val gate = SpeakerGreetingGate(0)
        assertEquals(GreetingDecision.WAIT, gate.tick(3000, true, true, false))
        assertEquals(GreetingDecision.STOP, gate.tick(12000, true, true, false))
        assertEquals(GreetingDecision.STOP, gate.tick(13000, true, true, true))
    }
    @Test fun greetingStartsOnceAfterSettlingDelay() {
        val gate = SpeakerGreetingGate(0)
        assertEquals(GreetingDecision.WAIT, gate.tick(2499, true, true, true))
        assertEquals(GreetingDecision.START, gate.tick(2500, true, true, true))
        assertEquals(GreetingDecision.WAIT, gate.tick(3000, true, true, true))
        assertEquals(GreetingDecision.WAIT, gate.tick(15000, true, true, true))
    }
    @Test fun stoppingOrEndingCancelsEvenAfterStart() {
        for (case in 0..2) {
            val gate = SpeakerGreetingGate(0)
            assertEquals(GreetingDecision.START, gate.tick(2500, true, true, true))
            assertEquals(GreetingDecision.STOP, gate.tick(3000, case != 0, case != 1, case != 2))
            assertEquals(GreetingDecision.STOP, gate.tick(3100, true, true, true))
        }
    }
}
