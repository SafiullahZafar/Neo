package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class SyncNoticePolicyTest {
    @Test fun successfulHealthChecksDoNotGenerateReportNotifications() {
        assertNull(SyncNoticePolicy().result(true, 0, 0, 0))
    }
    @Test fun offlineHealthChecksWithoutReportsStayQuiet() {
        assertNull(SyncNoticePolicy().result(false, 0, 0, 0))
    }
    @Test fun repeatedOfflineRetriesAreThrottled() {
        val policy = SyncNoticePolicy()
        assertEquals(SyncNotice.PENDING, policy.result(false, 0, 2, 100))
        assertNull(policy.result(false, 0, 2, 500))
        assertNull(policy.result(false, 0, 2, 300_099))
        assertEquals(SyncNotice.PENDING, policy.result(false, 0, 2, 300_100))
    }
    @Test fun recoveryNotifiesOnlyConfirmedUploadsAndResetsFailureThrottle() {
        val policy = SyncNoticePolicy()
        policy.result(false, 0, 1, 0)
        assertEquals(SyncNotice.SAVED, policy.result(true, 1, 0, 1000))
        assertEquals(SyncNotice.PENDING, policy.result(false, 0, 1, 2000))
    }
    @Test fun partialFailureDoesNotClaimEverythingWasSaved() {
        assertEquals(SyncNotice.PENDING, SyncNoticePolicy().result(false, 1, 2, 0))
    }
}
