package dev.brahmkshatriya.echo.extension.api.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.*

/**
 * Utilities for parsing DASH manifests and stream URLs
 */
object ManifestParser {

    /**
     * Extract stream URL from base64-encoded manifest
     */
    fun extractStreamUrlFromManifest(manifest: String): String? {
        return try {
            val decoded = String(Base64.getDecoder().decode(manifest))
            try {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(decoded) as? JsonObject
                val urls = json?.get("urls")?.jsonArray
                if (urls != null && urls.isNotEmpty()) {
                    urls[0].jsonPrimitive.content
                } else null
            } catch (jsonError: Exception) {
                logMessage("Manifest JSON parse failed, falling back to pattern match: $jsonError")
                val regex = Regex("""https?://[\w\-.~:?#@!$&'()*+,;=%/]+""")
                regex.find(decoded)?.value
            }
        } catch (error: Exception) {
            logMessage("Failed to decode manifest: $error")
            null
        }
    }

    /**
     * Check if payload is a DASH manifest (XML)
     */
    fun isDashManifestPayload(payload: String, contentType: String?): Boolean {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return false
        if (contentType != null && contentType.lowercase().contains("xml")) {
            return trimmed.startsWith("<")
        }
        return Regex("^<\\?xml", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("^<MPD[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("^<\\w+", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
    }

    /**
     * Check if content type is XML
     */
    fun isXmlContentType(contentType: String?): Boolean {
        if (contentType == null) return false
        return Regex("(application|text)/(?:.+\\+)?xml", RegexOption.IGNORE_CASE).containsMatchIn(contentType) ||
                Regex("dash\\+xml|mpd", RegexOption.IGNORE_CASE).containsMatchIn(contentType)
    }

    /**
     * Check if content type is JSON
     */
    fun isJsonContentType(contentType: String?): Boolean {
        if (contentType == null) return false
        return Regex("json", RegexOption.IGNORE_CASE).containsMatchIn(contentType) ||
                Regex("application/vnd\\.tidal\\.bts", RegexOption.IGNORE_CASE).containsMatchIn(contentType)
    }

    /**
     * Extract URLs from DASH JSON payload
     */
    fun extractUrlsFromDashJsonPayload(payload: Any?): List<String> {
        if (payload !is JsonObject) return emptyList()
        val candidate = payload["urls"]
        if (candidate !is kotlinx.serialization.json.JsonArray) return emptyList()
        return candidate.mapNotNull { entry ->
            entry.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

