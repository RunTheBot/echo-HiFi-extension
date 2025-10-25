package dev.brahmkshatriya.echo.extension.api.utils

import dev.brahmkshatriya.echo.extension.api.models.APIAlbum
import dev.brahmkshatriya.echo.extension.api.models.APIArtist
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.models.SearchResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Normalizes and prepares API responses for consistency
 */
object ResponseNormalizer {

    /**
     * Normalize a track object
     */
    fun normalizeTrack(apiTrack: APITrack): APITrack {
        var normalized = apiTrack
        // If artist is null but artists list exists, use first artist
        if (normalized.artist == null && normalized.artists.isNotEmpty()) {
            normalized = normalized.copy(artist = normalized.artists[0])
        }
        // and the opposite: if artists list is empty but artist exists, use artist as sole entry
        if (normalized.artists.isEmpty() && normalized.artist != null) {
            normalized = normalized.copy(artists = listOf(normalized.artist))
        }
        return normalized
    }

    /**
     * Normalize an album object
     */
    fun normalizeAlbum(album: APIAlbum): APIAlbum {
        var normalized = album
        // If artist is null but artists list exists, use first artist
        if (normalized.artist == null && normalized.artists?.isNotEmpty() == true) {
            normalized = normalized.copy(artist = normalized.artists[0])
        }
        return normalized
    }

    /**
     * Normalize an artist object
     */
    fun normalizeArtist(artist: APIArtist): APIArtist {
        var normalized = artist
        // If type is null but artistTypes list exists, use first type
        if (normalized.type == null && normalized.artistTypes?.isNotEmpty() == true) {
            normalized = normalized.copy(type = normalized.artistTypes[0])
        }
        return normalized
    }

    /**
     * Build a search response from a JSON object containing items
     */
    fun <T> buildSearchResponse(section: JsonObject?): SearchResponse<T> {
        val items = section?.get("items")?.jsonArray ?: JsonArray(emptyList())
        val list = items.map { it as T }
        val limit = section?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: list.size
        val offset = section?.get("offset")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val total = section?.get("totalNumberOfItems")?.jsonPrimitive?.content?.toIntOrNull() ?: list.size

        return SearchResponse(
            items = list,
            limit = limit,
            offset = offset,
            totalNumberOfItems = total
        )
    }

    /**
     * Find a search section within the response tree
     */
    fun <T> findSearchSection(
        source: Any?,
        key: String,
        visited: MutableSet<Any>
    ): JsonObject? {
        if (source == null) return null

        if (source is JsonArray) {
            for (entry in source) {
                val found = findSearchSection<T>(entry, key, visited)
                if (found != null) return found
            }
            return null
        }

        if (source !is JsonObject) return null

        if (visited.contains(source)) return null
        visited.add(source)

        if (source.containsKey("items") && source["items"] is JsonArray) {
            logMessage("Found items array while searching for key '$key'")
            return source
        }

        if (source.containsKey(key)) {
            val nested = source[key]
            val fromKey = findSearchSection<T>(nested, key, visited)
            if (fromKey != null) return fromKey
        }

        for (value in source.values) {
            val found = findSearchSection<T>(value, key, visited)
            if (found != null) return found
        }

        return null
    }

    /**
     * Normalize search response
     */
    fun <T> normalizeSearchResponse(
        data: JsonObject,
        key: String
    ): SearchResponse<T> {
        val section = findSearchSection<T>(data, key, mutableSetOf())
        return buildSearchResponse<T>(section)
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

