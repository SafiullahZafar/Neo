package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class ReportSearchTest {
    private val pending = ReportRow("Mom", "Neo took a message", "Call me tomorrow", "Hello", "Sep 19, 2026", false)
    @Test fun searchMatchesContactMessageReplyAndDateIgnoringCase() {
        for (query in listOf(" mom ", "TOMORROW", "hello", "2026")) assertTrue(pending.matches(query, ReportFilter.ALL))
        assertFalse(pending.matches("unrelated", ReportFilter.ALL))
    }
    @Test fun filtersCombineWithSearchRatherThanOverridingIt() {
        assertTrue(pending.matches("mom", ReportFilter.PENDING))
        assertFalse(pending.matches("dad", ReportFilter.PENDING))
        assertFalse(pending.matches("mom", ReportFilter.SYNCED))
        assertTrue(pending.copy(synced = true).matches("mom", ReportFilter.SYNCED))
    }
    @Test fun messageFilterExcludesEmptyAndWhitespaceMessages() {
        assertTrue(pending.matches("", ReportFilter.WITH_MESSAGE))
        assertFalse(pending.copy(message = "  ").matches("", ReportFilter.WITH_MESSAGE))
    }
}
