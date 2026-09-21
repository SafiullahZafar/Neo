package com.neo.assistant

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.util.UUID

class ApiTransportTest {
    private val dummy = "test-token-".repeat(4)

    @Test fun cleartextOnlyAllowedOnLocalDebugAddresses() {
        for (url in listOf("http://example.com", "http://192.168.1.5", "ftp://localhost")) {
            assertThrows(IllegalArgumentException::class.java) { ApiTransport(url, dummy, true) }
        }
        assertThrows(IllegalArgumentException::class.java) { ApiTransport("http://127.0.0.1:8000", dummy, false) }
        ApiTransport("http://127.0.0.1:8000", dummy, true)
        ApiTransport("https://example.com", dummy, false)
    }

    @Test fun credentialsCannotBeEmbeddedInServerUrl() {
        for (url in listOf("https://user:pass@example.com", "https://example.com?token=secret", "https://example.com/api")) {
            assertThrows(IllegalArgumentException::class.java) { ApiTransport(url, dummy, false) }
        }
    }

    @Test fun shortTokensAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ApiTransport("https://example.com", "short", false) }
    }

    @Test fun voiceApiRoundTripWithoutChangingOwnerSample() {
        val address = System.getenv("NEO_TEST_URL")
        val token = System.getenv("NEO_TEST_TOKEN")
        assumeTrue(address != null && token != null)
        val api = ApiTransport(address!!, token!!, true)
        assertTrue(String(api.voiceRequest("GET", "/v1/voice")).contains("reference_saved"))
        assertEquals(401, assertThrows(ApiHttpException::class.java) {
            ApiTransport(address, dummy, true).voiceRequest("GET", "/v1/voice")
        }.status)
        assertEquals(422, assertThrows(ApiHttpException::class.java) {
            api.voiceRequest("PUT", "/v1/voice/reference", "invalid".toByteArray(), "audio/wav", true)
        }.status)
        assertThrows(IllegalArgumentException::class.java) { api.voiceRequest("GET", "/v1/voice/../reports") }
    }

    @Test fun realPythonRoundTrip() {
        val address = System.getenv("NEO_TEST_URL")
        val token = System.getenv("NEO_TEST_TOKEN")
        assumeTrue("Run verify_android.py with the Python service running", address != null && token != null)
        val api = ApiTransport(address!!, token!!, true)
        val policy = api.request("GET", "/v1/policy")
        assertTrue(Regex("\"answer_delay_ms\"\\s*:\\s*6000").containsMatchIn(policy))
        assertTrue(Regex("\"real_calls_enabled\"\\s*:\\s*false").containsMatchIn(policy))
        assertThrows(IOException::class.java) { ApiTransport(address, dummy, true).request("GET", "/v1/reports") }
        val id = UUID.randomUUID().toString()
        val body = """{"id":"$id","caller":"Integration test","outcome":"Client connected","reply":"Hello","message":"Kotlin to Python verified","time":${System.currentTimeMillis()},"demo":true}"""
        try {
            repeat(2) { assertTrue(api.request("POST", "/v1/reports", body).contains(id)) }
            val reports = api.request("GET", "/v1/reports")
            assertEquals(1, Regex(id).findAll(reports).count())
            assertTrue(reports.contains("Kotlin to Python verified"))
        } finally { api.request("DELETE", "/v1/reports/$id") }
        assertFalse(api.request("GET", "/v1/reports").contains(id))
    }
}
