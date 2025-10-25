package dev.brahmkshatriya.echo.extension.api.utils

import dev.brahmkshatriya.echo.extension.api.models.APIAlbum
import dev.brahmkshatriya.echo.extension.api.models.APIArtist
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Utilities for parsing JSON responses safely
 */
object JsonParser {

    /**
     * Parse JSON element safely with error handling
     */
    fun <T> parseJsonSafely(payload: String): T? {
        return try {
            kotlinx.serialization.json.Json.parseToJsonElement(payload) as? T
        } catch (error: Exception) {
            logMessage("Failed to parse JSON payload: $error")
            null
        }
    }

    /**
     * Deserialize JSON element to specific type
     */
    private inline fun <reified T> deserializeElement(element: JsonElement): T? {
        return try {
            Json.decodeFromJsonElement<T>(element)
        } catch (e: Exception) {
            logMessage("Failed to deserialize element: $e")
            null
        }
    }

    private fun logMessage(message: String) {
        // Use global logging function from Utils.kt
        println(message)
    }
}

