package dev.brahmkshatriya.echo.extension.api.handlers

import dev.brahmkshatriya.echo.extension.api.models.APIAlbum
import dev.brahmkshatriya.echo.extension.api.models.APIArtist
import dev.brahmkshatriya.echo.extension.api.models.APIPlaylist
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.models.SearchResponse
import dev.brahmkshatriya.echo.extension.api.utils.HttpUtils
import dev.brahmkshatriya.echo.extension.api.utils.ResponseNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.net.URLEncoder

/**
 * Handler for search operations
 */
class SearchHandler(private val httpUtils: HttpUtils) {

    suspend fun searchTracks(query: String, limit: Long?): SearchResponse<APITrack> {
        val response = httpUtils.fetch(
            httpUtils.buildUrl("/search/?s=${URLEncoder.encode(query, "UTF-8")}${limit?.let { "&li=$it" } ?: ""}")
        )
        httpUtils.ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search tracks: ${response.code}")

        val data = response.body.string()
        val json = Json.parseToJsonElement(data) as JsonObject
        val normalized = ResponseNormalizer.normalizeSearchResponse<JsonObject>(json, "tracks")
        val deserializedItems = normalized.items.map { item ->
            try {
                ResponseNormalizer.normalizeTrack(Json.decodeFromJsonElement<APITrack>(item))
            } catch (e: Exception) {
                throw e
            }
        }
        logMessage("deserializedItems: $deserializedItems")
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    suspend fun searchArtists(query: String): SearchResponse<APIArtist> {
        val response = httpUtils.fetch(httpUtils.buildUrl("/search/?a=${URLEncoder.encode(query, "UTF-8")}"))
        httpUtils.ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search artists")

        val data = response.body.string()
        val json = (Json.parseToJsonElement(data) as JsonArray)[0] as JsonObject
        val normalized = ResponseNormalizer.normalizeSearchResponse<JsonObject>(json, "artists")
        val deserializedItems = normalized.items.map { item ->
            try {
                ResponseNormalizer.normalizeArtist(Json.decodeFromJsonElement<APIArtist>(item as kotlinx.serialization.json.JsonElement))
            } catch (e: Exception) {
                throw e
            }
        }
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    suspend fun searchAlbums(query: String): SearchResponse<APIAlbum> {
        val response = httpUtils.fetch(httpUtils.buildUrl("/search/?al=${URLEncoder.encode(query, "UTF-8")}"))
        httpUtils.ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search albums")

        val data = response.body.string()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(data) as JsonObject
        val normalized = ResponseNormalizer.normalizeSearchResponse<JsonObject>(json, "albums")
        val deserializedItems = normalized.items.map { item ->
            try {
                ResponseNormalizer.normalizeAlbum(Json.decodeFromJsonElement<APIAlbum>(item))
            } catch (e: Exception) {
                throw e
            }
        }
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    suspend fun searchPlaylists(query: String): SearchResponse<APIPlaylist> {
        val response = httpUtils.fetch(httpUtils.buildUrl("/search/?p=${URLEncoder.encode(query, "UTF-8")}"))
        httpUtils.ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search playlists")

        val data = response.body.string()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(data) as JsonObject
        val normalized = ResponseNormalizer.normalizeSearchResponse<JsonObject>(json, "playlists")
        val deserializedItems = normalized.items.map { item ->
            try {
                Json.decodeFromJsonElement<APIPlaylist>(item)
            } catch (e: Exception) {
                throw e
            }
        }
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

