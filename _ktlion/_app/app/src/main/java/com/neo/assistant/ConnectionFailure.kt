package com.neo.assistant

/** Actionable errors without displaying URLs, tokens or exception messages. */
object ConnectionFailure {
    fun message(error: Exception): String = problem(error).display()
    fun problem(error: Exception): NeoProblem {
        val code = when (error) {
            is ApiHttpException -> "API_HTTP_${error.status}"
            is java.net.SocketTimeoutException -> "API_TIMEOUT"
            is java.net.UnknownHostException -> "API_DNS"
            is javax.net.ssl.SSLException -> "API_TLS"
            is java.net.ConnectException, is java.net.NoRouteToHostException -> "API_UNREACHABLE"
            is IllegalArgumentException -> "API_CONFIGURATION"
            else -> "API_UNKNOWN"
        }
        return NeoProblem(code, "Python request did not complete", instruction(error),
            "Follow the connection checks above, then retry. Unsynced local reports are retained.")
    }
    private fun instruction(error: Exception): String = when (error) {
        is ApiHttpException -> when (error.status) {
            401, 403 -> "Python is reachable, but the pairing token was rejected. Pair again in Neo Settings with NEO_API_TOKEN from the Python .env."
            404 -> "Python is reachable but this endpoint is missing. Restart main.py from the updated Neo project."
            else -> "Python is reachable but returned HTTP ${error.status}. Check the Python terminal."
        }
        is java.net.ConnectException, is java.net.NoRouteToHostException -> "Cannot reach Python. Start main.py on the PC and restore USB forwarding: adb reverse tcp:8765 tcp:8765. For USB, pair with http://127.0.0.1:8765. Forwarding may need restoring after reconnecting USB."
        is java.net.SocketTimeoutException -> "Python connection timed out. Check the server terminal and USB forwarding, then retry."
        is java.net.UnknownHostException -> "Server hostname could not be resolved. Check the paired server address in Neo Settings."
        is javax.net.ssl.SSLException -> "HTTPS verification failed. Check the server certificate and address. For local USB testing use the debug build's localhost address."
        is IllegalArgumentException -> "Pairing configuration is invalid. In Neo Settings, check the server address and pairing token; for USB use http://127.0.0.1:8765."
        else -> "Connection check failed. Review the paired address and token in Neo Settings, ensure Python is running, and retry."
    }
}
