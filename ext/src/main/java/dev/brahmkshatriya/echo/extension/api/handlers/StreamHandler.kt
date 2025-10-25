package dev.brahmkshatriya.echo.extension.api.handlers

import dev.brahmkshatriya.echo.extension.AudioQuality
import dev.brahmkshatriya.echo.extension.api.models.DashManifestResult
import dev.brahmkshatriya.echo.extension.api.utils.HttpUtils
import dev.brahmkshatriya.echo.extension.api.utils.ManifestParser
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

const val DASH_MANIFEST_UNAVAILABLE_CODE = "DASH_UNAVAILABLE"

/**
 * Handler for stream URL and DASH manifest operations
 */
class StreamHandler(
    private val httpUtils: HttpUtils,
    private val trackHandler: TrackHandler
) {

    suspend fun getStreamUrl(trackId: Long, quality: AudioQuality): String {
        if (quality == AudioQuality.HIRES_LOSSLESS) {
            try {
                return resolveHiResStreamFromDash(trackId)
            } catch (error: Exception) {
                throw Exception("Failed to resolve hi-res stream via DASH manifest: $error")
            }
        }

        var lastError: Exception? = null

        for (attempt in 1..3) {
            try {
                val lookup = trackHandler.getTrack(trackId, quality)
                if (lookup.originalTrackUrl != null) {
                    return lookup.originalTrackUrl!!
                }

                val manifestUrl = ManifestParser.extractStreamUrlFromManifest(lookup.info.manifest)
                if (manifestUrl != null) {
                    return manifestUrl
                }

                lastError = Exception("Unable to resolve stream URL for track")
            } catch (error: Exception) {
                lastError = error
            }

            if (attempt < 3) {
                delay(200L * attempt)
            }
        }

        throw lastError ?: Exception("Unable to resolve stream URL for track")
    }

    suspend fun getDashManifest(
        trackId: Long,
        quality: AudioQuality = AudioQuality.HIRES_LOSSLESS
    ): DashManifestResult {
        val url = httpUtils.buildUrl("/dash/?id=$trackId&quality=$quality")
        var lastError: Exception? = null

        for (attempt in 1..3) {
            val response = httpUtils.fetch(url)
            httpUtils.ensureNotRateLimited(response)
            val contentType = response.header("content-type")

            if (response.isSuccessful) {
                val payload = response.body.string()

                if (ManifestParser.isXmlContentType(contentType) || ManifestParser.isDashManifestPayload(payload, contentType)) {
                    return DashManifestResult(
                        kind = "dash",
                        manifest = payload,
                        contentType = contentType
                    )
                }

                if (ManifestParser.isJsonContentType(contentType) || payload.trim().startsWith("{")) {
                    val parsed = parseJsonSafely<JsonObject>(payload)
                    if (parsed != null &&
                        parsed["detail"]?.jsonPrimitive?.content?.lowercase() == "not found"
                    ) {
                        lastError = httpUtils.createDashUnavailableError("Dash manifest not found for track")
                    } else {
                        val urls = ManifestParser.extractUrlsFromDashJsonPayload(parsed)
                        return DashManifestResult(
                            kind = "flac",
                            manifestText = payload,
                            urls = urls,
                            contentType = contentType
                        )
                    }
                } else {
                    if (ManifestParser.isDashManifestPayload(payload, contentType)) {
                        return DashManifestResult(
                            kind = "dash",
                            manifest = payload,
                            contentType = contentType
                        )
                    }
                    val parsed = parseJsonSafely<Any>(payload)
                    val urls = ManifestParser.extractUrlsFromDashJsonPayload(parsed)
                    if (urls.isNotEmpty()) {
                        return DashManifestResult(
                            kind = "flac",
                            manifestText = payload,
                            urls = urls,
                            contentType = contentType
                        )
                    }
                    lastError = httpUtils.createDashUnavailableError("Received unexpected payload from dash endpoint.")
                }
            } else {
                if (response.code == 404) {
                    var detail: String? = null
                    try {
                        val errorPayload = response.body.string()
                        val errorJson = Json.parseToJsonElement(errorPayload) as? JsonObject
                        detail = errorJson?.get("detail")?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        // ignore
                    }
                    lastError = if (detail?.lowercase() == "not found") {
                        httpUtils.createDashUnavailableError("Dash manifest not found for track")
                    } else {
                        Exception("Failed to load dash manifest (status ${response.code})")
                    }
                } else {
                    lastError = Exception("Failed to load dash manifest (status ${response.code})")
                }
            }

            if (attempt < 3) {
                delay(200L * attempt)
            }
        }

        throw lastError ?: httpUtils.createDashUnavailableError("Unable to load dash manifest for track")
    }

    private suspend fun resolveHiResStreamFromDash(trackId: Long): String {
        val manifest = getDashManifest(trackId, AudioQuality.HIRES_LOSSLESS)
        if (manifest.kind == "flac") {
            val url = manifest.urls.firstOrNull { it.isNotEmpty() }
            if (url != null) {
                return url
            }
            throw Exception("DASH manifest did not include any FLAC URLs.")
        }
        throw Exception("Hi-res DASH manifest does not expose a direct FLAC URL.")
    }

    private fun <T> parseJsonSafely(payload: String): T? {
        return try {
            kotlinx.serialization.json.Json.parseToJsonElement(payload) as? T
        } catch (error: Exception) {
            logMessage("Failed to parse JSON payload from DASH response: $error")
            null
        }
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

