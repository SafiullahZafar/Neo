package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class CallSessionTest {
    @Test fun savedContactWaitsSixSecondsAndRespondsOnce() {
        val call = CallSession(true, 100)
        assertFalse(call.tick(6099))
        assertTrue(call.tick(6100))
        assertFalse(call.tick(9000))
        assertEquals(CallState.ASSISTANT, call.state)
    }

    @Test fun unknownNumberNeverTriggersAssistant() {
        val call = CallSession(false, 0)
        assertFalse(call.tick(6000))
        assertFalse(call.tick(Long.MAX_VALUE))
        assertEquals(CallState.TIMED_OUT, call.state)
    }

    @Test fun ownerAnswerCancelsPendingResponse() {
        val call = CallSession(true, 0)
        call.ownerAnswer()
        assertFalse(call.tick(6000))
        assertEquals(CallState.OWNER_ANSWERED, call.state)
    }

    @Test fun rejectionCancelsPendingResponse() {
        val call = CallSession(true, 0)
        call.reject()
        assertFalse(call.tick(6000))
    }

    @Test fun callerHangupCancelsPendingResponse() {
        val call = CallSession(true, 0)
        call.end()
        assertFalse(call.tick(6000))
    }

    @Test fun delayedCallbackNeverAnswersExpiredCall() {
        val call = CallSession(true, 0)
        assertFalse(call.tick(60_000))
        assertEquals(CallState.TIMED_OUT, call.state)
        assertFalse(call.tick(70_000))
    }

    @Test fun pausedAssistantDoesNotRespondToKnownContact() {
        val call = CallSession(true, 0, assistanceEnabled = false)
        assertFalse(call.tick(6000))
        assertEquals(CallState.RINGING, call.state)
    }

    @Test fun userCanTakeOverAfterAssistantStarts() {
        val call = CallSession(true, 0)
        assertTrue(call.tick(6000))
        call.ownerAnswer()
        assertEquals(CallState.OWNER_ANSWERED, call.state)
        assertFalse(call.tick(7000))
    }

    @Test fun backwardsClockDoesNotTriggerEarlyResponse() {
        val call = CallSession(true, 5000)
        assertFalse(call.tick(4000))
    }
}
