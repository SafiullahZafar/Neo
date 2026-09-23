package com.neo.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.*

class AssistantSettingsActivity : Activity() {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var foreground = false
    private lateinit var speakerStatus: TextView
    private lateinit var voiceList: Spinner
    private lateinit var voiceStatus: TextView
    private lateinit var preview: Button
    private var available = emptyList<Voice>()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences("neo_demo", MODE_PRIVATE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(28, 32, 28, 32); setBackgroundColor(Color.rgb(246, 249, 245))
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        fun label(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.rgb(28, 55, 48)); setPadding(0, 14, 0, 14); layout.addView(this)
        }
        fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; layout.addView(this); setOnClickListener { action() } }
        button("Back to Neo") { finish() }
        label("Make Neo yours", 26f)
        label("Auto-answer delay", 21f)
        val seconds = label("")
        val delay = SeekBar(this).apply { max = 59; progress = (AssistantPreferences.delayMs(this@AssistantSettingsActivity) / 1000).toInt() - 1; layout.addView(this) }
        fun updateDelay() { seconds.text = "${delay.progress + 1} seconds" }
        updateDelay()
        delay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar?, value: Int, fromUser: Boolean) { updateDelay() }
            override fun onStartTrackingTouch(view: SeekBar?) {}
            override fun onStopTrackingTouch(view: SeekBar?) {}
        })
        button("Save answer delay") {
            prefs.edit().putInt("answer_delay_seconds", delay.progress + 1).apply()
            Toast.makeText(this, "Saved for new calls", Toast.LENGTH_SHORT).show()
        }
        label("Applies to SIM, eligible WhatsApp calls and practice calls. A call already ringing keeps its original timer. If the caller hangs up or voicemail takes over first, Neo cannot answer it.")
        button("My voice: record and clone") { startActivity(Intent(this, MyVoiceActivity::class.java)) }
        button("SIM Call Audio Diagnostics") { startActivity(Intent(this, SimAudioDiagnosticsActivity::class.java)) }
        label("Experimental speaker greeting", 21f)
        label("Optional acoustic test after Neo requests pickup. It plays a greeting locally; echo cancellation may prevent the caller hearing it. It cannot listen to, record or answer the caller's reply. No cloned voice or Gemini key is needed.")
        layout.addView(Switch(this).apply {
            text = "Enable experimental speaker greeting"
            isChecked = SpeakerGreeting.enabled(this@AssistantSettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("speaker_experiment", checked).apply()
                if (!checked) SpeakerGreeting.stop()
                speakerStatus.text = if (checked) "Enabled for the next eligible auto-answered call." else "Speaker greeting disabled."
            }
        })
        speakerStatus = label(prefs.getString("speaker_status", "No speaker experiment yet.") ?: "")
        label("SIM: Neo requests speakerphone for its auto-answered greeting, then restores the previous route if still applicable. WhatsApp: tap Speaker yourself after pickup. Disconnect Bluetooth/headsets and use a comfortable volume. Only the other phone can confirm whether it hears the greeting.")
        button("Try greeting during current call") {
            engine?.stop()
            if (!SpeakerGreeting.enabled(this)) speakerStatus.text = "Enable the speaker experiment first."
            else if (!SpeakerGreeting.inCall(this)) speakerStatus.text = "No active call audio mode detected. Connect a test call and turn its Speaker on first."
            else SpeakerGreeting.start(this, "manual-speaker-test",
                alive = { SpeakerGreeting.inCall(this) }, ready = { SpeakerGreeting.inCall(this) },
                event = { if (!isDestroyed && foreground) speakerStatus.text = it })
        }
        button("Stop speaker greeting") {
            SpeakerGreeting.stop()
            speakerStatus.text = prefs.getString("speaker_status", "No greeting is playing.")
        }
        label("Offline voices", 21f)
        label("Neo adds no voice subscription or API charges. Choose from offline voices already installed in your Android speech engine. The available voices and languages depend on your phone; Neo does not purchase or download voices.")
        voiceStatus = label("Loading installed voices?")
        voiceList = Spinner(this).apply { layout.addView(this) }
        button("Use Android offline default") {
            val tts = engine
            if (ready && tts != null) {
                prefs.edit().remove("tts_voice").putBoolean("voice", true).apply()
                if (AssistantPreferences.applyVoice(this, tts)) {
                    loadVoices()
                    val index = available.indexOfFirst { it.name == tts.voice?.name }
                    if (index >= 0) voiceList.setSelection(index)
                    voiceStatus.text = "Using Android offline speech for previews and practice replies. Voice cloning is not required."
                } else voiceStatus.text = "No usable offline voice. Check Android speech settings."
            } else voiceStatus.text = "Speech engine is not ready. Try again after it loads."
        }
        button("Save selected voice") {
            val voice = available.getOrNull(voiceList.selectedItemPosition)
            val tts = engine
            if (ready && voice != null && tts != null && tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                prefs.edit().putString("tts_voice", voice.name).apply()
                voiceStatus.text = "Saved: ${voice.locale.displayName} / ${voice.name}"
            } else voiceStatus.text = "No usable offline voice selected. Check Android speech settings."
        }
        preview = button("Preview selected voice") {
            val voice = available.getOrNull(voiceList.selectedItemPosition)
            val tts = engine
            if (!ready || voice == null || tts == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
                voiceStatus.text = "Voice unavailable. Refresh voices or check Android speech settings."
            } else {
                val greeting = CallRules.greeting(prefs.getString("owner_name", "") ?: "", prefs.getString("owner_situation", "Unknown") ?: "Unknown")
                if (tts.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, "preview") != TextToSpeech.SUCCESS) voiceStatus.text = "Preview could not start."
            }
        }
        preview.isEnabled = false
        button("Stop preview") { engine?.stop() }
        button("Refresh installed voices") { loadVoices() }
        button("Android speech settings") {
            try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            catch (_: Exception) { voiceStatus.text = "Open Android Settings and search for Text-to-speech output." }
        }
        label("Voice selection applies to previews, practice replies, spoken reports and the optional speaker experiment. Direct SIM/WhatsApp caller audio is still not connected. Greetings are currently written in English; selecting another voice does not translate them.")
        engine = TextToSpeech(this) { status ->
            runOnUiThread {
                if (!isDestroyed) {
                    ready = status == TextToSpeech.SUCCESS
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {}
                        @Deprecated("Legacy callback") override fun onError(id: String?) {
                            runOnUiThread { if (foreground) voiceStatus.text = ErrorHistory.describe(this@AssistantSettingsActivity, NeoProblems.speech(-1)) }
                        }
                    })
                    loadVoices()
                }
            }
        }
    }
    private fun loadVoices() {
        val tts = engine
        if (!ready || tts == null) { voiceStatus.text = ErrorHistory.describe(this, NeoProblems.noVoice); return }
        available = AssistantPreferences.voices(tts)
        voiceList.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, available.map { "${it.locale.displayName} / ${it.name}" })
        val selected = getSharedPreferences("neo_demo", MODE_PRIVATE).getString("tts_voice", null)
        val index = available.indexOfFirst { it.name == selected }
        if (index >= 0) voiceList.setSelection(index)
        preview.isEnabled = available.isNotEmpty()
        voiceStatus.text = when {
            available.isEmpty() -> "No installed offline voices found. Check Android speech settings, then refresh."
            selected != null && index < 0 -> "Saved voice is unavailable. Select and save another voice."
            else -> "${available.size} installed offline voice(s) available. Preview before saving."
        }
    }
    override fun onResume() {
        super.onResume(); foreground = true
        speakerStatus.text = getSharedPreferences("neo_demo", MODE_PRIVATE).getString("speaker_status", "No speaker experiment yet.")
        if (ready) loadVoices()
    }
    override fun onPause() { foreground = false; engine?.stop(); super.onPause() }
    override fun onDestroy() { engine?.shutdown(); engine = null; super.onDestroy() }
}
