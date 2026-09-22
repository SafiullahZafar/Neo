package com.neo.assistant

enum class SimCallCapability { FULL_DUPLEX_DIRECT, DIRECT_RX_ONLY, DIRECT_TX_ONLY, ACOUSTIC_DUPLEX, CALL_CONTROL_ONLY }

data class CallAudioCapabilities(
    val canCaptureLocalMic: Boolean,
    val canCaptureRemoteCaller: Boolean = false,
    val canPlayLocalTts: Boolean = false,
    val canSendAudioToRemoteCaller: Boolean = false,
    val telephonyDeviceDetected: Boolean = false,
    val requiresSpeaker: Boolean = false,
    val privilegedAccessRequired: Boolean = true,
    val acousticRxVerified: Boolean = false,
    val acousticTxVerified: Boolean = false
) {
    fun mode() = when {
        canCaptureRemoteCaller && canSendAudioToRemoteCaller -> SimCallCapability.FULL_DUPLEX_DIRECT
        canCaptureRemoteCaller -> SimCallCapability.DIRECT_RX_ONLY
        canSendAudioToRemoteCaller -> SimCallCapability.DIRECT_TX_ONLY
        acousticRxVerified && acousticTxVerified -> SimCallCapability.ACOUSTIC_DUPLEX
        else -> SimCallCapability.CALL_CONTROL_ONLY
    }
}

/** Playback confirmation is evidence of audibility, never proof of direct uplink routing. */
data class AudioTestEvidence(val route: Int, val strategy: String, val localCompleted: Boolean, val remoteHeard: Boolean? = null) {
    fun description() = when {
        !localCompleted -> "INCOMPLETE"
        remoteHeard == null -> "REMOTE_UNVERIFIED"
        remoteHeard -> "REMOTE_HEARD; direct versus acoustic path UNVERIFIED"
        else -> "REMOTE_NOT_HEARD; cause UNVERIFIED"
    }
}

interface NeoCallAudioBridge {
    fun initialize(): String
    fun startListening(source: Int, aec: Boolean?, ns: Boolean?, result: (String, ByteArray?) -> Unit)
    fun stopListening()
    fun playReply(strategy: Int, text: String = "Hello. This is Neo speaking through the call audio test.", result: (String, Boolean) -> Unit)
    fun stopReply()
    fun release()
    fun capabilities(): CallAudioCapabilities
}
