package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CallAudioEvidenceTest {
    @Test fun deviceEnumerationAndLocalPlaybackDoNotProveDirectAudio() {
        val caps = CallAudioCapabilities(true, canPlayLocalTts = true, telephonyDeviceDetected = true)
        assertEquals(SimCallCapability.CALL_CONTROL_ONLY, caps.mode())
        assertFalse(caps.canCaptureRemoteCaller)
        assertFalse(caps.canSendAudioToRemoteCaller)
    }
    @Test fun remoteConfirmationDoesNotProveDirectPathOrCapture() {
        val evidence = AudioTestEvidence(1, "Telephony preference", true, true)
        assertTrue(evidence.description().contains("direct versus acoustic path UNVERIFIED"))
        assertEquals("REMOTE_UNVERIFIED", evidence.copy(remoteHeard = null).description())
        assertEquals("INCOMPLETE", evidence.copy(localCompleted = false).description())
    }
    @Test fun oneDirectionNeverEnablesFullDuplex() {
        assertEquals(SimCallCapability.DIRECT_RX_ONLY, CallAudioCapabilities(true, canCaptureRemoteCaller = true).mode())
        assertEquals(SimCallCapability.DIRECT_TX_ONLY, CallAudioCapabilities(true, canSendAudioToRemoteCaller = true).mode())
        assertEquals(SimCallCapability.FULL_DUPLEX_DIRECT, CallAudioCapabilities(true, canCaptureRemoteCaller = true, canSendAudioToRemoteCaller = true).mode())
    }
    private fun wav(): ByteArray = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(40); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(4); putShort(100); putShort(-100)
    }.array()
    @Test fun acceptsBoundedPcmWave() {
        val parsed = DiagnosticPcm.read(wav())
        assertEquals(16000, parsed.rate); assertEquals(1, parsed.channels); assertEquals(4, parsed.bytes.size)
    }
    @Test fun rejectsTruncatedAndOversizedChunks() {
        for (bytes in listOf(wav().copyOf(46), wav().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Int.MAX_VALUE) })) {
            try { DiagnosticPcm.read(bytes); fail("Invalid WAV accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun rejectsCompressedSpeechInsteadOfPlayingGarbage() {
        val bytes = wav().also { it[20] = 3 }
        try { DiagnosticPcm.read(bytes); fail("Float WAV accepted") } catch (_: IllegalArgumentException) { }
    }
}
