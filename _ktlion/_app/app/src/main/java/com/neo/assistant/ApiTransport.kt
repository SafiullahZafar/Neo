package com.neo.assistant

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class ApiHttpException(val status: Int) : IOException("Python returned HTTP $status")

/** Shared by Android and the JVM-to-Python integration test. No tokens in errors. */
class ApiTransport(baseUrl: String, private val token: String, allowLocalHttp: Boolean) {
    private val base = URI(baseUrl.trim().trimEnd('/'))
    init {
        require(base.userInfo == null && base.rawQuery == null && base.rawFragment == null &&
            (base.path.isNullOrEmpty() || base.path == "/")) { "Use a server address without a path or credentials." }
        val localHttp = allowLocalHttp && base.scheme == "http"
        require(base.host != null && (base.scheme == "https" || localHttp)) { "Use HTTPS, or the local USB address for this debug build." }
        require(token.length >= 32 && token.all { it.code in 33..126 }) { "Enter the pairing token from your Python .env." }
    }

    fun request(method: String, path: String, body: String? = null): String {
        require(path.startsWith("/v1/") && !path.contains("..") && !path.contains("?"))
        val connection = base.resolve(path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw ApiHttpException(code)
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 2_000_000) throw IOException("Server response is too large.")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
