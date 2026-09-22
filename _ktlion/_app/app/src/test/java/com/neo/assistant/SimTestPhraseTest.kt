package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class SimTestPhraseTest {
    @Test fun recognizesWordsAndDigitsButNotAnotherTest() {
        assertTrue(SimTestPhrase.matches("Hello, Neo test 789.", SimTestPhrase.FULL))
        assertTrue(SimTestPhrase.matches("Neo test 4 7 2", SimTestPhrase.CAPTURE))
        assertTrue(SimTestPhrase.matches("Neo test four seven two", SimTestPhrase.CAPTURE))
        assertFalse(SimTestPhrase.matches("Neo test seven four two", SimTestPhrase.CAPTURE))
        assertFalse(SimTestPhrase.matches("Hello Neo test seven eight", SimTestPhrase.FULL))
        assertFalse(SimTestPhrase.matches("", SimTestPhrase.FULL))
    }
    @Test fun partialWordDoesNotCountAsNeo() {
        assertFalse(SimTestPhrase.matches("Hello neoplasm test 789", SimTestPhrase.FULL))
        assertFalse(SimTestPhrase.matches("other speech", ""))
    }
    @Test fun speakerAndOneWayEvidenceCannotEnableAcousticDuplex() {
        assertEquals(SimCallCapability.CALL_CONTROL_ONLY, CallAudioCapabilities(true, requiresSpeaker = true).mode())
        assertEquals(SimCallCapability.CALL_CONTROL_ONLY, CallAudioCapabilities(true, acousticRxVerified = true).mode())
        assertEquals(SimCallCapability.CALL_CONTROL_ONLY, CallAudioCapabilities(true, acousticTxVerified = true).mode())
        assertEquals(SimCallCapability.ACOUSTIC_DUPLEX, CallAudioCapabilities(true, acousticRxVerified = true, acousticTxVerified = true).mode())
    }
}
