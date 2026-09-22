package com.neo.assistant

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DiagnosticPcm(val rate: Int, val channels: Int, val bytes: ByteArray) {
    companion object {
        /** Bounded PCM WAV reader; TTS engines may emit unknown chunks. */
        fun read(wav: ByteArray): DiagnosticPcm {
            require(wav.size in 44..2_000_000)
            fun tag(p: Int) = String(wav, p, 4, Charsets.US_ASCII)
            fun int(p: Int) = ByteBuffer.wrap(wav, p, 4).order(ByteOrder.LITTLE_ENDIAN).int
            fun short(p: Int) = ByteBuffer.wrap(wav, p, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            require(tag(0) == "RIFF" && tag(8) == "WAVE")
            var rate = 0; var channels = 0; var data: ByteArray? = null; var p = 12
            while (p <= wav.size - 8) {
                val size = int(p + 4)
                require(size >= 0 && size <= wav.size - p - 8)
                when (tag(p)) {
                    "fmt " -> {
                        require(size >= 16 && short(p + 8) == 1 && short(p + 22) == 16)
                        channels = short(p + 10); rate = int(p + 12)
                        require(channels in 1..2 && rate in 8000..48000)
                    }
                    "data" -> data = wav.copyOfRange(p + 8, p + 8 + size)
                }
                p += 8 + size + (size and 1)
            }
            val pcm = requireNotNull(data)
            require(rate > 0 && channels > 0 && pcm.isNotEmpty() && pcm.size % (channels * 2) == 0)
            require(pcm.size <= rate * channels * 2 * 30)
            return DiagnosticPcm(rate, channels, pcm)
        }
    }
}
