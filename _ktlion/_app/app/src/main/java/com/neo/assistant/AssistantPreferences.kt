package com.neo.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

object AssistantPreferences {
    fun delayMs(context: Context) = CallRules.delayMs(context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE).getInt("answer_delay_seconds", 6))
    fun voices(tts: TextToSpeech): List<Voice> = (tts.voices ?: emptySet()).filter {
        !it.isNetworkConnectionRequired && !(it.features ?: emptySet()).contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
    }.sortedWith(compareBy({ it.locale.displayName }, { it.name }))
    fun applyVoice(context: Context, tts: TextToSpeech): Boolean {
        val available = voices(tts)
        val saved = context.getSharedPreferences("neo_demo", Context.MODE_PRIVATE).getString("tts_voice", null)
        val selected = if (saved != null) available.find { it.name == saved } else
            available.find { it.name == tts.defaultVoice?.name } ?: available.firstOrNull()
        return selected != null && tts.setVoice(selected) == TextToSpeech.SUCCESS
    }
}
