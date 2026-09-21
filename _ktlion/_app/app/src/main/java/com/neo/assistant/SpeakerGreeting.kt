package com.neo.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** Experimental acoustic playback only: no microphone capture, focus theft or call-audio injection. */
object SpeakerGreeting {
    private val handler = Handler(Looper.getMainLooper())
    private var current: Attempt? = null
    fun enabled(context: Context) = context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE).getBoolean("speaker_experiment", false)
    fun inCall(context: Context): Boolean {
        val mode = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }
    fun start(context: Context, key: String, alive: () -> Boolean, ready: () -> Boolean,
              prepareRoute: () -> (() -> Unit) = { {} }, event: (String) -> Unit = {}) {
        if (!enabled(context)) return
        if (current != null) {
            event("Speaker experiment skipped: another greeting is active")
            return
        }
        val attempt = Attempt(context.applicationContext, key, alive, ready, prepareRoute, event)
        current = attempt
        attempt.begin()
    }
    fun cancel(key: String) { current?.takeIf { it.key == key }?.finish("Speaker experiment stopped: call state changed") }
    fun stop() { current?.finish("Speaker experiment stopped") }

    private class Attempt(val context: Context, val key: String, val alive: () -> Boolean,
                          val ready: () -> Boolean, val prepareRoute: () -> (() -> Unit), val event: (String) -> Unit) {
        private val gate = SpeakerGreetingGate(SystemClock.elapsedRealtime())
        private var engine: TextToSpeech? = null
        private var restore: (() -> Unit)? = null
        private var closed = false
        private val timeout = Runnable { finish("Speaker experiment timed out; caller hearing is unverified") }
        private fun report(message: String) {
            context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE).edit().putString("speaker_status", message).apply()
            event(message)
        }
        private val monitor = object : Runnable {
            override fun run() {
                if (closed) return
                val decision = try { gate.tick(SystemClock.elapsedRealtime(), enabled(context), alive(), ready()) }
                    catch (_: Exception) { GreetingDecision.STOP }
                when (decision) {
                    GreetingDecision.START -> speak()
                    GreetingDecision.STOP -> { finish("Speaker experiment stopped: disabled, call ended or connection could not be verified"); return }
                    GreetingDecision.WAIT -> Unit
                }
                if (!closed) handler.postDelayed(this, 250)
            }
        }
        fun begin() {
            report("Speaker experiment waiting for call activity; caller hearing is unverified")
            handler.post(monitor)
            handler.postDelayed(timeout, 45_000)
        }
        private fun speak() {
            try {
                restore = prepareRoute()
                engine = TextToSpeech(context) { result -> handler.post {
                    if (!closed) {
                        try {
                            val tts = engine
                            if (result != TextToSpeech.SUCCESS || tts == null || !AssistantPreferences.applyVoice(context, tts)) {
                                finish("Speaker experiment unavailable: select an installed offline Android voice")
                            } else if (!enabled(context) || !alive() || !ready()) {
                                finish("Speaker experiment stopped: call is no longer ready")
                            } else {
                                tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                    override fun onStart(id: String?) { handler.post { if (!closed) report("Android speech started locally; caller hearing is unverified") } }
                                    override fun onDone(id: String?) { handler.post { finish("Local greeting completed; caller hearing unverified; no caller audio recorded") } }
                                    @Deprecated("Legacy TTS callback") override fun onError(id: String?) { handler.post { finish("Android speech failed during speaker experiment") } }
                                })
                                val prefs = context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE)
                                val text = CallRules.greeting(prefs.getString("owner_name", "") ?: "", prefs.getString("owner_situation", "Unknown") ?: "Unknown") +
                                    " This is a speakerphone audio test. I cannot hear or record your reply."
                                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speaker-test") != TextToSpeech.SUCCESS) finish("Android speech could not start")
                            }
                        } catch (_: Exception) { finish("Speaker experiment unavailable on this device") }
                    }
                } }
            } catch (_: Exception) { finish("Speaker route or Android speech is unavailable") }
        }
        fun finish(message: String) {
            if (closed) return
            closed = true
            handler.removeCallbacks(monitor); handler.removeCallbacks(timeout)
            engine?.stop(); engine?.shutdown(); engine = null
            try { restore?.invoke() } catch (_: Exception) {}
            restore = null
            if (current === this) current = null
            report(message)
        }
    }
}
