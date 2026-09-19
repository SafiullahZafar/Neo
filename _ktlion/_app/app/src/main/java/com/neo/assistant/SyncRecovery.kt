package com.neo.assistant

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class ConnectionProblem(val title: String, val instruction: String, val retryable: Boolean) {
    companion object {
        fun from(error: Exception): ConnectionProblem = when (error) {
            is ApiHttpException -> when (error.status) {
                401, 403 -> ConnectionProblem("Pairing rejected", "Open Connect to Python and check NEO_API_TOKEN from your private .env. No automatic retries until you fix it.", false)
                404 -> ConnectionProblem("Wrong server or API address", "Use the base address without /health or /v1. Confirm that Neo's Python service is running on that port.", false)
                409, 422 -> ConnectionProblem("Report needs attention", "Python rejected a report. Keep the local copy and check the app/server versions before retrying.", false)
                408 -> ConnectionProblem("Server request timed out", "Reports stay on this phone. Retry after the server is available.", true)
                429 -> ConnectionProblem("Server rate limit reached", "Wait before retrying manually. Reports stay on this phone.", false)
                in 500..599 -> ConnectionProblem("Python service error", "Check the Python terminal. Reports stay on this phone while the service recovers.", true)
                else -> ConnectionProblem("Unexpected HTTP response", "Check that the address points to Neo and uses the correct protocol.", false)
            }
            is SSLException -> ConnectionProblem("Secure connection failed", "Check the server's HTTPS certificate. Neo will not bypass certificate validation.", false)
            is UnknownHostException -> ConnectionProblem("Server name not found", "Check the address and your network connection.", true)
            is SocketTimeoutException -> ConnectionProblem("Python did not respond in time", "Check the Python terminal and connection. Your reports remain on the phone.", true)
            is ConnectException -> ConnectionProblem("Cannot reach the Python port", "For USB: keep Python running, connect the phone, and run adb reverse for the configured port. The app cannot tell which of these steps is missing.", true)
            is IOException -> ConnectionProblem("Connection interrupted", "Check the connection and retry. Your reports remain on this phone.", true)
            else -> ConnectionProblem("Incompatible server response", "Check that the Python service and app versions match. No automatic retries for an invalid response.", false)
        }
    }
}

/** A finite retry budget per foreground recovery attempt. */
class SyncRetrySchedule {
    private var attempt = 0
    private val delays = longArrayOf(5_000, 15_000, 30_000, 60_000, 120_000)
    fun reset() { attempt = 0 }
    fun nextDelay(retryable: Boolean): Long? {
        if (!retryable || attempt >= delays.size) return null
        return delays[attempt++]
    }
}
