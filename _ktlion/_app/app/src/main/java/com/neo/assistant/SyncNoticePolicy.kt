package com.neo.assistant

enum class SyncNotice { SAVED, PENDING }

/** No alert for an empty health check; throttle repeated offline warnings. */
class SyncNoticePolicy {
    private var lastFailure: Long? = null
    fun result(success: Boolean, uploaded: Int, pending: Int, now: Long): SyncNotice? {
        if (success) {
            lastFailure = null
            return if (uploaded > 0) SyncNotice.SAVED else null
        }
        if (pending <= 0) return null
        val last = lastFailure
        if (last != null && now - last < 300_000) return null
        lastFailure = now
        return SyncNotice.PENDING
    }
}
