package com.neo.assistant

data class NeoProblem(val code: String, val title: String, val cause: String, val next: String) {
    fun display() = "[$code] $title\nWhy: $cause\nNext: $next"
}

/** Only fixed explanations and numeric platform codes; never exception messages or user content. */
object NeoProblems {
    val micPermission = NeoProblem("MIC_PERMISSION", "Microphone access denied", "Android has not allowed Neo to record audio.", "Open Android app permissions and allow Microphone, then start the test again. This does not grant direct SIM audio.")
    val protectedAudio = NeoProblem("SIM_AUDIO_PROTECTED", "Direct call capture is restricted", "Neo does not hold the privileged CAPTURE_AUDIO_OUTPUT permission. No recording was attempted.", "Ordinary Allow buttons cannot grant this access. Use diagnostics to assess supported routes; direct conversation remains unavailable.")
    val noCall = NeoProblem("SIM_NOT_ACTIVE", "No eligible active SIM call", "Neo cannot verify one active call on a SIM account. A call may be ringing, ended, held, unobservable, or multiple calls may exist.", "Set Neo as default phone app, connect one SIM call, return to this screen and retry.")
    val busy = NeoProblem("AUDIO_TEST_BUSY", "An audio test is already running", "The previous capture or playback still owns Neo's test session.", "Tap Stop test before starting another test.")
    val routeChanged = NeoProblem("SIM_TEST_CANCELLED", "Audio test cancelled", "The call ended or changed, or its audio route changed during the test.", "Keep one call active with the same route and start a new test. No successful result was recorded.")
    val noTelephonyOutput = NeoProblem("SIM_NO_TX_DEVICE", "No telephony output exposed", "Android did not expose a telephony output to Neo. No reply was transmitted by this strategy.", "Inspect audio devices during an active SIM call. More ordinary permissions do not create a missing output route.")
    val capture = NeoProblem("AUDIO_CAPTURE_FAILED", "Audio could not be captured", "The recorder or selected format/source failed. The exact device cause is not known.", "Check microphone permission and Android's microphone privacy switch. Stop competing recording apps and retry a permitted source. An active call may restrict capture.")
    val captureDenied = NeoProblem("AUDIO_OS_DENIED", "Android rejected audio access", "A security check rejected this operation; microphone permission alone may not authorize the selected call source.", "Check app permissions. Protected SIM sources require system privileges and cannot be fixed with an ordinary permission prompt.")
    val noOfflineStt = NeoProblem("STT_OFFLINE_UNAVAILABLE", "Offline sample recognition unavailable", "This test needs Android 13+ and an available on-device recognition service.", "Check Android's speech-recognition service and offline language availability. No cloud fallback is used by SIM diagnostics.")
    val noVoice = NeoProblem("TTS_VOICE_UNAVAILABLE", "Offline voice unavailable", "The speech engine did not initialize or the selected offline voice is missing.", "Open Neo voice settings, refresh installed voices and preview a usable offline voice. Check Android Text-to-speech settings if none appear.")
    val playback = NeoProblem("AUDIO_PLAYBACK_FAILED", "Audio playback failed", "Android could not play this audio or use the requested route. The exact cause is not known.", "Retry an installed voice preview and inspect the route. Local playback never proves the remote caller heard the reply.")
    val storage = NeoProblem("LOCAL_STORAGE_FAILED", "Local file operation failed", "Neo could not read or save the required file. It may be missing, inaccessible or storage may be full.", "Check free phone storage, then retry. Do not delete your only saved recording or report to troubleshoot.")
    val response = NeoProblem("API_RESPONSE_INVALID", "Unexpected Python response", "The server response could not be decoded in the format Neo expects.", "Restart the matching app/server versions and retry. Existing local reports remain on the phone.")
    val monitor = NeoProblem("CALL_MONITOR_START_FAILED", "Background call monitoring could not start", "Android rejected the foreground service. The exact restriction is not known; the switch alone does not prove monitoring is running.", "Open Neo while unlocked, verify default-phone access and notifications, then retry monitoring. Check battery/background restrictions if it stops later.")
    val pickup = NeoProblem("SIM_PICKUP_FAILED", "Call answer request failed", "Android did not accept Neo's answer request. The call may have changed or phone-role access may have changed.", "Answer manually if needed. Verify Neo is the default phone app and test a new call; no automatic success is assumed.")

    fun camera(code: Int): NeoProblem {
        val cause = when (code) {
            1 -> "Another client is using the camera."
            2 -> "Android's limit on open cameras was reached."
            3 -> "Camera use is disabled by device policy."
            4 -> "The camera device reported a fatal error."
            5 -> "The camera service reported a fatal error."
            else -> "The camera reported an unknown error."
        }
        return NeoProblem("CAMERA_$code", "Presence check unavailable", cause,
            "Close other camera apps, check camera permission and privacy settings, then retry. Restart the phone if the service keeps failing. Presence remains uncertain.")
    }

    fun recognition(code: Int): NeoProblem {
        val detail = when (code) {
            1 -> "The recognition service reported a network timeout." to "Retry later. For offline SIM testing, check that the installed service supports offline input."
            2 -> "The recognition service reported a network failure." to "Check the selected recognition service. SIM diagnostics do not fall back to cloud recognition."
            3 -> "The recognition service reported an audio-recording error." to "Check microphone access and competing recordings; retry outside a call to distinguish call restrictions."
            4, 11 -> "The recognition service failed or disconnected." to "Restart the speech service or phone and retry."
            5 -> "The recognition service rejected or cancelled the client operation." to "Keep Neo visible, stop other recognition attempts, and retry. The exact service cause is unknown."
            6 -> "No speech was detected before the listening timeout." to "Speak during the listening window. During SIM testing, silence does not prove the caller's voice reached Neo."
            7 -> "Audio did not produce a recognized phrase." to "Repeat the test phrase clearly. This is not proof that the direct caller stream is available."
            8 -> "The recognition service is busy." to "Stop the other recognition attempt and retry once it releases the microphone."
            9 -> "The recognition service reported insufficient permission." to "Allow microphone access in Android app permissions and check the microphone privacy switch."
            10 -> "The service rejected too many recognition requests." to "Wait before starting another attempt; repeated tapping will not help."
            12 -> "The recognition service does not support the requested language." to "Choose a supported service/language. SIM diagnostic phrases currently use English (US)."
            13 -> "The requested language is supported but its resources are unavailable." to "Install the language resources through Android speech settings, then retry."
            14, 15 -> "The service cannot check language support or complete its model-download operation." to "Manage offline language resources in Android settings; then retry the test."
            else -> "The recognition service returned an unrecognized error code." to "Restart the service and retry. Keep this code for troubleshooting; the exact cause is unknown."
        }
        return NeoProblem("STT_$code", "Speech recognition did not complete", detail.first, detail.second)
    }
    fun speech(code: Int): NeoProblem = when (code) {
        -9 -> noVoice
        -6, -7 -> NeoProblem("TTS_$code", "Speech engine network failure", "The selected engine reported a network error or timeout.", "Choose an installed offline voice in Neo Settings and preview it.")
        -8 -> NeoProblem("TTS_REQUEST_REJECTED", "Speech request rejected", "The speech engine rejected the request parameters.", "Try a short preview with another installed offline voice.")
        else -> NeoProblem("TTS_$code", "Speech engine could not finish", "The engine reported a playback, synthesis or service error. The precise cause is not known.", "Check Android Text-to-speech settings, preview an installed voice, then retry. Caller audibility remains unverified.")
    }
    fun audioFailure(error: Exception, output: Boolean = false) = when (error) {
        is SecurityException -> captureDenied
        is java.io.IOException -> storage
        else -> if (output) playback else capture
    }
    fun voiceHttp(status: Int): NeoProblem? = when (status) {
        409 -> NeoProblem("VOICE_STATE_CONFLICT", "Voice action not ready", "A preview may be running, incomplete or missing its reference sample.", "Refresh voice status. Wait for generation to finish or upload your sample before retrying.")
        413 -> NeoProblem("VOICE_UPLOAD_TOO_LARGE", "Recording exceeds upload limit", "Python rejected the recording size.", "Record a new 10-20 second sample in Neo and upload it again.")
        415 -> NeoProblem("VOICE_FORMAT_REJECTED", "Recording format rejected", "Python requires a supported WAV recording.", "Use Neo's recorder to produce mono 16 kHz PCM16 WAV audio.")
        422 -> NeoProblem("VOICE_INPUT_REJECTED", "Voice input rejected", "Consent, recording format/duration/quality, request text or identifier failed validation.", "Confirm own-voice consent, use 10-20 seconds of clear speech, and refresh status. This status alone does not identify which field failed.")
        503 -> NeoProblem("VOICE_ENGINE_UNAVAILABLE", "Voice engine unavailable", "Python reported that the preview engine is unavailable.", "Run setup_voice.py on the PC, then refresh voice status. This does not enable live SIM voice cloning.")
        404 -> NeoProblem("VOICE_NOT_FOUND", "Voice preview or endpoint missing", "The preview may have expired after restart, or the server version may be outdated.", "Restart the updated Python server, refresh status and generate a new preview if necessary.")
        else -> null
    }
}
