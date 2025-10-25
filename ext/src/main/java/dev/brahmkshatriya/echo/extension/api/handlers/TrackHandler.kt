package dev.brahmkshatriya.echo.extension.api.handlers

import dev.brahmkshatriya.echo.extension.AudioQuality
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.models.TrackInfo
import dev.brahmkshatriya.echo.extension.api.models.TrackLookup
import dev.brahmkshatriya.echo.extension.api.utils.HttpUtils
import dev.brahmkshatriya.echo.extension.api.utils.ManifestParser
import dev.brahmkshatriya.echo.extension.api.utils.ResponseNormalizer
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for track-related operations
 */
class TrackHandler(private val httpUtils: HttpUtils) {

    suspend fun getTrack(id: Long, quality: AudioQuality = AudioQuality.LOW): TrackLookup {
        val url = httpUtils.buildUrl("/track/?id=$id&quality=${if (quality == AudioQuality.DOLBY_ATMOS) AudioQuality.LOW else quality}")
        var lastError: Exception? = null

        for (attempt in 1..3) {
            val response = httpUtils.fetch(url)
            httpUtils.ensureNotRateLimited(response)
            if (response.isSuccessful) {
                val data = response.body.string()
                val json = Json.parseToJsonElement(data) as JsonArray
                return parseTrackLookup(json)
            }

            var detail: String? = null
            var userMessage: String? = null
            var subStatus: Int? = null
            try {
                val errorData = response.body?.string() ?: ""
                val errorJson = Json.parseToJsonElement(errorData) as? JsonObject
                detail = errorJson?.get("detail")?.jsonPrimitive?.content
                userMessage = errorJson?.get("userMessage")?.jsonPrimitive?.content
                if (detail == null) detail = userMessage
                subStatus = errorJson?.get("subStatus")?.jsonPrimitive?.content?.toIntOrNull()
            } catch (e: Exception) {
                // Ignore JSON parse errors
            }

            val isTokenRetry = response.code == 401 && subStatus == 11002
            val message = detail ?: "Failed to get track (status ${response.code})"
            lastError = Exception(if (isTokenRetry) (userMessage ?: message) else message)
            val shouldRetry = isTokenRetry ||
                    (detail != null && Regex("quality not found", RegexOption.IGNORE_CASE).containsMatchIn(detail)) ||
                    response.code >= 500

            if (attempt == 3 || !shouldRetry) {
                throw lastError
            }

            delay(200L * attempt)
        }

        throw lastError ?: Exception("Failed to get track")
    }

    suspend fun getDashURL(trackId: Long, quality: AudioQuality = AudioQuality.HIRES_LOSSLESS): String {
        return httpUtils.buildUrl("/dash/?id=$trackId&quality=$quality")
    }

    private fun parseTrackLookup(data: JsonArray): TrackLookup {
        var apiTrack: APITrack? = null
        var info: TrackInfo? = null
        var originalTrackUrl: String? = null

        for (entry in data) {
            entry as JsonObject
            if (entry.containsKey("album") && entry.containsKey("artist") && entry.containsKey("duration")) {
                apiTrack = Json.decodeFromJsonElement<APITrack>(entry)
                continue
            }
            if (entry.containsKey("manifest")) {
                info = Json.decodeFromJsonElement<TrackInfo>(entry)
                continue
            }
            if (originalTrackUrl == null && entry.containsKey("OriginalTrackUrl")) {
                val candidate = entry["OriginalTrackUrl"]?.jsonPrimitive?.content
                if (candidate != null) {
                    originalTrackUrl = candidate
                }
            }
        }

        if (apiTrack == null || info == null) {
            throw Exception("Malformed track response")
        }

        return object : TrackLookup {
            override val apiTrack: APITrack = apiTrack
            override val info: TrackInfo = info
            override val originalTrackUrl: String? = originalTrackUrl
        }
    }
}

