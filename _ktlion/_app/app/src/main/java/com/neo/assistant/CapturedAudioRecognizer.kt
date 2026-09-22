package com.neo.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.*
import android.speech.*
import java.io.File

/** Only on-device recognition of the bounded diagnostic sample; no network fallback. */
class CapturedAudioRecognizer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var file: File? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var timeout: Runnable? = null
    private var generation = 0
    fun recognize(pcm: ByteArray, phrase: String = SimTestPhrase.CAPTURE, result: (CapturedRecognition) -> Unit) {
        stop()
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            result(CapturedRecognition(message = "Offline STT of captured audio unavailable on this device. No remote speech claim can be made.")); return
        }
        val id = generation
        fun finish(message: String, text: String = "", matched: Boolean? = null) {
            if (generation != id) return
            stop(); result(CapturedRecognition(text, matched, message))
        }
        try {
            file = File.createTempFile("neo-capture-", ".pcm", context.cacheDir).also { it.writeBytes(pcm) }
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(value: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { finish("Offline STT failed (code $error); remote capture UNVERIFIED") }
                override fun onResults(results: Bundle?) {
                    val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    // Persist only the fixed phrase match, never incidental caller speech.
                    val matched = SimTestPhrase.matches(recognized, phrase)
                    finish("Fixed phrase match=$matched. Input handling depends on the recognition engine; a match is NOT proof of a direct call stream.", recognized.take(500), matched)
                }
                override fun onPartialResults(results: Bundle?) {}
                override fun onEvent(type: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            timeout = Runnable { finish("Offline STT timed out; remote capture UNVERIFIED") }.also { handler.postDelayed(it, 15000) }
            recognizer?.startListening(intent)
        } catch (_: Exception) { finish("Offline STT unavailable for captured input") }
    }
    fun stop() {
        generation++
        timeout?.let { handler.removeCallbacks(it) }; timeout = null
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        descriptor?.close(); descriptor = null
        file?.delete(); file = null
    }
}
