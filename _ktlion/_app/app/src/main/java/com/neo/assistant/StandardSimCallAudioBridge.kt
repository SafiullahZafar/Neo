package com.neo.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/** Public-API diagnostic adapter. It deliberately advertises no verified direct path. */
class StandardSimCallAudioBridge(private val context: Context) : NeoCallAudioBridge {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val epoch = AtomicInteger()
    private var tts: TextToSpeech? = null
    private var localTtsWorked = false
    private var pendingFile: File? = null
    private var busy = false
    private var session = 0L
    private var route = 0
    private var timeout: Runnable? = null
    private fun permitted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun devices() = manager.getDevices(AudioManager.GET_DEVICES_INPUTS) + manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    private fun valid(token: Int) = epoch.get() == token && SimAudioSession.isActive && SimAudioSession.generation == session && SimAudioSession.route == route
    private fun begin(): Int {
        check(!busy) { "Stop the previous test first" }
        check(SimAudioSession.isActive) { "A single verified ACTIVE SIM call is required" }
        SpeakerGreeting.stop()
        busy = true; session = SimAudioSession.generation; route = SimAudioSession.route
        return epoch.incrementAndGet()
    }
    override fun capabilities() = CallAudioCapabilities(
        canCaptureLocalMic = permitted(Manifest.permission.RECORD_AUDIO),
        canPlayLocalTts = localTtsWorked,
        telephonyDeviceDetected = devices().any { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
    )
    override fun initialize(): String {
        val devices = devices().joinToString("\n") {
            "${if (it.isSource) "INPUT" else "OUTPUT"} id=${it.id} type=${deviceName(it.type)}"
        }
        val communication = if (Build.VERSION.SDK_INT >= 31) manager.communicationDevice?.let { deviceName(it.type) } ?: "UNKNOWN" else "Use Telecom route below"
        val available = if (Build.VERSION.SDK_INT >= 31) manager.availableCommunicationDevices.joinToString { "${deviceName(it.type)}#${it.id}" } else "API 31+ required"
        return "Audio mode=${manager.mode} (0 NORMAL, 2 IN_CALL, 3 IN_COMMUNICATION)\nCommunication device=$communication\nAvailable communication devices=$available\nTelecom route=${SimAudioSession.route} (1 earpiece, 2 Bluetooth, 4 wired, 8 speaker)\n$devices"
    }
    override fun startListening(source: Int, aec: Boolean?, ns: Boolean?, result: (String, ByteArray?) -> Unit) {
        if (!permitted(Manifest.permission.RECORD_AUDIO)) { result("PERMISSION_DENIED: microphone", null); return }
        if (source in listOf(MediaRecorder.AudioSource.VOICE_CALL, MediaRecorder.AudioSource.VOICE_UPLINK, MediaRecorder.AudioSource.VOICE_DOWNLINK) && !permitted("android.permission.CAPTURE_AUDIO_OUTPUT")) {
            result("PERMISSION_DENIED: this source requires privileged CAPTURE_AUDIO_OUTPUT; no recording attempted", null); return
        }
        val token = try { begin() } catch (e: IllegalStateException) { result(e.message ?: "Unavailable", null); return }
        Thread {
            var recorder: AudioRecord? = null
            val effects = mutableListOf<AudioEffect>()
            var outcome = "UNSUPPORTED"; var captured: ByteArray? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                recorder = AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 3200))
                check(recorder.state == AudioRecord.STATE_INITIALIZED)
                fun inspect(name: String, effect: AudioEffect?, requested: Boolean?): String {
                    if (effect == null) return "$name unavailable"
                    effects.add(effect)
                    val code = if (requested != null && effect.hasControl()) effect.setEnabled(requested) else null
                    return "$name enabled=${effect.enabled} control=${effect.hasControl()} setResult=$code"
                }
                fun effect(factory: () -> AudioEffect?) = try { factory() } catch (_: Exception) { null }
                val processing = listOf(
                    inspect("AEC", effect { AcousticEchoCanceler.create(recorder.audioSessionId) }, aec),
                    inspect("NS", effect { NoiseSuppressor.create(recorder.audioSessionId) }, ns),
                    inspect("AGC", effect { AutomaticGainControl.create(recorder.audioSessionId) }, null)
                ).joinToString("; ")
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                val output = ByteArrayOutputStream(); val block = ByteArray(3200)
                val end = SystemClock.elapsedRealtime() + 5000
                var energy = 0.0; var samples = 0; val routes = mutableSetOf<String>()
                while (valid(token) && SystemClock.elapsedRealtime() < end && output.size() < 160000) {
                    val read = recorder.read(block, 0, minOf(block.size, 160000 - output.size()), AudioRecord.READ_NON_BLOCKING)
                    check(read >= 0) { "AudioRecord read code $read" }
                    if (read > 0) {
                        output.write(block, 0, read)
                        for (i in 0 until read - 1 step 2) { val sample = ((block[i].toInt() and 255) or (block[i + 1].toInt() shl 8)).toShort().toInt(); energy += sample.toDouble() * sample; samples++ }
                    }
                    if (Build.VERSION.SDK_INT >= 24) routes.add(recorder.routedDevice?.let { "${deviceName(it.type)}#${it.id}" } ?: "UNKNOWN")
                    Thread.sleep(15)
                }
                val rms = if (samples == 0) 0 else sqrt(energy / samples).toInt()
                captured = output.toByteArray()
                val silenced = if (Build.VERSION.SDK_INT >= 29) recorder.activeRecordingConfiguration?.isClientSilenced?.toString() ?: "UNKNOWN" else "UNKNOWN (requires API 29+)"
                outcome = "${if (rms == 0) "SILENCE_ONLY" else "AVAILABLE: signal origin UNKNOWN"}; samples=$samples RMS=$rms; actual input=$routes; Android reports silenced=$silenced\n$processing\nApp-session effects do not control the cellular uplink DSP. Remote capture is UNVERIFIED."
            } catch (_: SecurityException) { outcome = "PERMISSION_DENIED by Android" }
            catch (e: Exception) { outcome = "UNSUPPORTED / capture failed (${e.javaClass.simpleName})" }
            finally {
                try { recorder?.stop() } catch (_: Exception) { }
                effects.forEach { try { it.release() } catch (_: Exception) { } }
                recorder?.release()
            }
            main.post { if (epoch.get() == token) { busy = false; if (valid(token)) result(outcome, captured) else result("CANCELLED: call or route changed", null) } }
        }.start()
    }

    /** 0 media TTS; 1 communication attributes (never changes cellular mode); 2 preferred telephony AudioTrack. */
    override fun playReply(strategy: Int, text: String, result: (String, Boolean) -> Unit) {
        if (text.isBlank() || text.length > 300) { result("Reply must contain 1-300 characters", false); return }
        val token = try { begin() } catch (e: IllegalStateException) { result(e.message ?: "Unavailable", false); return }
        fun finish(message: String, completed: Boolean) {
            main.post {
                if (epoch.get() == token) {
                    val accepted = valid(token) && completed
                    localTtsWorked = localTtsWorked || accepted
                    release()
                    result(if (accepted) message else "FAILED / CANCELLED: $message", accepted)
                }
            }
        }
        val device = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
        if (strategy == 2 && device == null) { finish("No exposed telephony output", false); return }
        val synthesizedFile = if (strategy == 2) try {
            File.createTempFile("neo-route-", ".wav", context.cacheDir).also { pendingFile = it }
        } catch (_: Exception) { finish("Temporary audio file unavailable", false); return } else null
        val attributes = AudioAttributes.Builder().setUsage(if (strategy == 0) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        timeout = Runnable { finish("Playback timeout", false) }.also { main.postDelayed(it, 30000) }
        tts = TextToSpeech(context) { status ->
            main.post {
                if (!valid(token)) { finish("Call or route changed", false); return@post }
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null || !AssistantPreferences.applyVoice(context, engine)) {
                    finish("Offline speech voice unavailable", false); return@post
                }
                engine.setAudioAttributes(attributes)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    @Deprecated("Legacy callback") override fun onError(id: String?) { finish("Speech engine error", false) }
                    override fun onDone(id: String?) {
                        if (!valid(token)) { finish("Call or route changed", false); return }
                        if (strategy != 2) { finish("LOCAL_PLAYBACK completed; actual TTS route unavailable; REMOTE_UNVERIFIED", true); return }
                        val file = synthesizedFile ?: return
                        Thread {
                            var track: AudioTrack? = null
                            try {
                                check(file.length() in 44..2_000_000)
                                val pcm = DiagnosticPcm.read(file.readBytes())
                                val format = AudioFormat.Builder().setSampleRate(pcm.rate).setChannelMask(if (pcm.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                                track = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format).setBufferSizeInBytes(maxOf(4096, AudioTrack.getMinBufferSize(pcm.rate, format.channelMask, format.encoding))).setTransferMode(AudioTrack.MODE_STREAM).build()
                                check(track.setPreferredDevice(device)) { "Preferred device rejected" }
                                check(valid(token)); track.play()
                                var offset = 0; val routes = mutableSetOf<String>(); val end = SystemClock.elapsedRealtime() + 25000
                                while (valid(token) && SystemClock.elapsedRealtime() < end && track.playbackHeadPosition < pcm.bytes.size / (pcm.channels * 2)) {
                                    if (offset < pcm.bytes.size) {
                                        val n = track.write(pcm.bytes, offset, pcm.bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                                        check(n >= 0); offset += n
                                    }
                                    if (Build.VERSION.SDK_INT >= 24) routes.add(track.routedDevice?.let { "${deviceName(it.type)}#${it.id}" } ?: "UNKNOWN")
                                    Thread.sleep(15)
                                }
                                val done = valid(token) && track.playbackHeadPosition >= pcm.bytes.size / (pcm.channels * 2)
                                finish("AudioTrack preferred=TELEPHONY#${device?.id}; actual=$routes; REMOTE_UNVERIFIED", done)
                            } catch (e: Exception) { finish("Telephony playback ${e.javaClass.simpleName}", false) }
                            finally { try { track?.stop() } catch (_: Exception) { }; track?.release(); file.delete() }
                        }.start()
                    }
                })
                val greeting = text
                val code = if (strategy == 2) {
                    engine.synthesizeToFile(greeting, Bundle(), synthesizedFile!!, "route-$token")
                } else engine.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, "route-$token")
                if (code != TextToSpeech.SUCCESS) finish("Speech request rejected", false)
            }
        }
    }
    override fun stopListening() { release() }
    override fun stopReply() { release() }
    override fun release() {
        epoch.incrementAndGet(); busy = false
        timeout?.let { main.removeCallbacks(it) }; timeout = null
        tts?.stop(); tts?.shutdown(); tts = null
        pendingFile?.delete(); pendingFile = null
    }
    companion object {
        fun deviceName(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
            else -> "TYPE_$type"
        }
    }
}
