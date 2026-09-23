package com.neo.assistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Foreground-only, bounded 16 kHz mono PCM recording. Never records phone-call streams. */
class PcmVoiceRecorder {
    @Volatile private var stopped = false
    @Volatile private var cancelled = false
    @Volatile var seconds = 0
        private set
    @Volatile var problem: NeoProblem? = null
        private set
    fun stop(discard: Boolean = false) { cancelled = discard; stopped = true }
    @android.annotation.SuppressLint("MissingPermission")
    fun record(destination: File, finished: (String?) -> Unit) {
        Thread {
            var recorder: AudioRecord? = null
            var error: String? = null
            var saving = false
            try {
                val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(bufferSize > 0)
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(bufferSize, 4096))
                check(recorder.state == AudioRecord.STATE_INITIALIZED)
                val pcm = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                recorder.startRecording()
                while (!stopped && pcm.size() < 640000) {
                    val count = recorder.read(buffer, 0, minOf(buffer.size, 640000 - pcm.size()))
                    check(count > 0)
                    pcm.write(buffer, 0, count)
                    seconds = pcm.size() / 32000
                }
                if (!cancelled) {
                    if (pcm.size() < 320000) error = "Record at least 10 seconds. Your previous sample was kept."
                    else {
                        val size = pcm.size()
                        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                        header.put("RIFF".toByteArray()).putInt(size + 36).put("WAVEfmt ".toByteArray())
                        header.putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
                            .putShort(2).putShort(16).put("data".toByteArray()).putInt(size)
                        saving = true
                        val temporary = File(destination.parentFile, "my-voice.tmp")
                        temporary.outputStream().use { it.write(header.array()); pcm.writeTo(it) }
                        check(temporary.renameTo(destination))
                    }
                } else error = "Recording cancelled; nothing new saved."
            } catch (failure: Exception) {
                problem = if (saving) NeoProblems.storage else NeoProblems.audioFailure(failure)
                error = problem?.display()
            }
            finally {
                try { recorder?.stop() } catch (_: Exception) {}
                recorder?.release()
            }
            finished(error)
        }.start()
    }
}
