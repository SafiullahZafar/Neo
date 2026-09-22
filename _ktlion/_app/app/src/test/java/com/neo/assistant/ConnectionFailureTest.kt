package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class ConnectionFailureTest {
    @Test fun separatesNetworkFailureFromRejectedPairingAndOldServer() {
        assertTrue(ConnectionFailure.message(java.net.ConnectException()).contains("adb reverse"))
        assertTrue(ConnectionFailure.message(ApiHttpException(401)).contains("token was rejected"))
        assertTrue(ConnectionFailure.message(ApiHttpException(404)).contains("endpoint is missing"))
        assertTrue(ConnectionFailure.message(java.net.SocketTimeoutException()).contains("timed out"))
    }
    @Test fun neverEchoesSensitiveExceptionDetails() {
        val secret = "secret-token-in-a-url"
        for (error in listOf(Exception(secret), IllegalArgumentException(secret), java.net.UnknownHostException(secret))) {
            assertFalse(ConnectionFailure.message(error).contains(secret))
        }
    }
}
