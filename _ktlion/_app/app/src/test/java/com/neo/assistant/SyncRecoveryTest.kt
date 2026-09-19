package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException

class SyncRecoveryTest {
    @Test fun automaticRetriesStopAfterFiveAttempts() {
        val schedule = SyncRetrySchedule()
        assertEquals(listOf(5000L, 15000L, 30000L, 60000L, 120000L), (1..5).map { schedule.nextDelay(true) })
        assertNull(schedule.nextDelay(true))
        schedule.reset()
        assertEquals(5000L, schedule.nextDelay(true))
    }
    @Test fun wrongTokensAndInvalidReportsAreNotAutomaticallyRetried() {
        for (status in listOf(401, 403, 404, 409, 422, 429)) {
            assertFalse(ConnectionProblem.from(ApiHttpException(status)).retryable)
        }
        assertNull(SyncRetrySchedule().nextDelay(false))
    }
    @Test fun transientNetworkAndServerFailuresCanRecover() {
        assertTrue(ConnectionProblem.from(ConnectException()).retryable)
        assertTrue(ConnectionProblem.from(ApiHttpException(503)).retryable)
    }
    @Test fun secureConnectionErrorsNeverSuggestBypassingCertificates() {
        val problem = ConnectionProblem.from(SSLHandshakeException("sensitive server detail"))
        assertFalse(problem.retryable)
        assertFalse(problem.instruction.contains("sensitive server detail"))
        assertTrue(problem.instruction.contains("will not bypass"))
    }
}
