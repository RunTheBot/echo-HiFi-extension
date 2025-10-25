package dev.brahmkshatriya.echo.extension.api.utils

import dev.brahmkshatriya.echo.extension.HiFiSession
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

const val RATE_LIMIT_ERROR_MESSAGE = "Rate limited"

/**
 * HTTP utilities for making requests and handling responses
 */
class HttpUtils(private val httpClient: OkHttpClient = OkHttpClient()) {

    suspend fun buildUrl(path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        // Log if base URL is missing
        val baseUrl = HiFiSession.getInstance().settings?.getString("api_endpoint")

        if (baseUrl == null) {
            logMessage("Warning: API endpoint is not configured in settings.")
            throw Exception("API endpoint is not configured.")
        }

        return "$baseUrl$normalizedPath"
    }

    suspend fun fetch(url: String, options: Request.Builder.() -> Unit = {}): Response {
        val requestBuilder = Request.Builder().url(url)
        requestBuilder.options()
        val request = requestBuilder.build()
        return httpClient.newCall(request).execute()
    }

    fun ensureNotRateLimited(response: Response) {
        if (response.code == 429) {
            throw Exception(RATE_LIMIT_ERROR_MESSAGE)
        }
    }

    fun createDashUnavailableError(message: String): Exception {
        return Exception(message)
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

