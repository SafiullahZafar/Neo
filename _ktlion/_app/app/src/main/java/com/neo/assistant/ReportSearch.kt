package com.neo.assistant

enum class ReportFilter { ALL, PENDING, SYNCED, WITH_MESSAGE }

data class ReportRow(val caller: String, val outcome: String, val message: String,
                     val reply: String, val date: String, val synced: Boolean) {
    fun matches(query: String, filter: ReportFilter): Boolean {
        val categoryMatches = when (filter) {
            ReportFilter.ALL -> true
            ReportFilter.PENDING -> !synced
            ReportFilter.SYNCED -> synced
            ReportFilter.WITH_MESSAGE -> message.isNotBlank()
        }
        val term = query.trim()
        return categoryMatches && (term.isEmpty() ||
            listOf(caller, outcome, message, reply, date).any { it.contains(term, ignoreCase = true) })
    }
}
