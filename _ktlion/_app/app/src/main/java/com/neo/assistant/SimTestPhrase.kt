package com.neo.assistant

object SimTestPhrase {
    const val CAPTURE = "Neo test four seven two"
    const val FULL = "Hello Neo test seven eight nine"
    const val REPLY = "I can hear you. This is Neo."
    fun matches(text: String, expected: String): Boolean {
        fun normalize(value: String): List<String> {
            val numbers = mapOf("four" to "4", "seven" to "7", "two" to "2", "eight" to "8", "nine" to "9")
            return value.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ").trim()
                .split(Regex("\\s+")).flatMap { word ->
                    val token = numbers[word] ?: word
                    if (token.all { it.isDigit() }) token.map { it.toString() } else listOf(token)
                }
        }
        val actual = normalize(text); val target = normalize(expected)
        return target.isNotEmpty() && actual.windowed(target.size).any { it == target }
    }
}

data class CapturedRecognition(val text: String = "", val phraseMatched: Boolean? = null, val message: String)
