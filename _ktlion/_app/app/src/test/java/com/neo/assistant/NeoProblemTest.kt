package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class NeoProblemTest {
    @Test fun deniedCaptureIsNotReportedAsOrdinaryMicPermission() {
        assertNotEquals(NeoProblems.micPermission.code, NeoProblems.protectedAudio.code)
        assertTrue(NeoProblems.protectedAudio.next.contains("cannot grant"))
        assertEquals("AUDIO_OS_DENIED", NeoProblems.audioFailure(SecurityException("private")).code)
    }
    @Test fun recognitionFailuresDistinguishSilenceBusyAndPermission() {
        assertTrue(NeoProblems.recognition(6).cause.contains("No speech"))
        assertTrue(NeoProblems.recognition(8).cause.contains("busy"))
        assertTrue(NeoProblems.recognition(9).cause.contains("permission"))
        assertTrue(NeoProblems.recognition(999).next.contains("unknown"))
    }
    @Test fun voiceValidationDoesNotPretendToKnowWhichFieldFailed() {
        assertTrue(NeoProblems.voiceHttp(422)!!.next.contains("does not identify"))
        assertNull(NeoProblems.voiceHttp(500))
        assertFalse(NeoProblems.audioFailure(Exception("secret-transcript")).display().contains("secret-transcript"))
    }
}
