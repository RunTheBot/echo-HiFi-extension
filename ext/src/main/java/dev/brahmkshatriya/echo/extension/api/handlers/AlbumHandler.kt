package dev.brahmkshatriya.echo.extension.api.handlers

import dev.brahmkshatriya.echo.extension.api.models.APIAlbum
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.utils.HttpUtils
import dev.brahmkshatriya.echo.extension.api.utils.ResponseNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/**
 * Handler for album-related operations
 */
class AlbumHandler(private val httpUtils: HttpUtils) {

    suspend fun getAlbum(id: Long): Pair<APIAlbum, List<APITrack>> {
        val response = httpUtils.fetch(httpUtils.buildUrl("/album/?id=$id"))
        httpUtils.ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to get album: ${response.code}")

        val data = response.body.string()
        val json = Json.parseToJsonElement(data)

        val entries = json as? JsonArray ?: listOf(json)

        var albumEntry: APIAlbum? = null
        var trackCollection: JsonObject? = null

        for (entry in entries) {
            if (entry !is JsonObject) continue

            if (entry.containsKey("title") && entry.containsKey("id") && entry.containsKey("cover")) {
                albumEntry = ResponseNormalizer.normalizeAlbum(Json.decodeFromJsonElement<APIAlbum>(entry))
                continue
            }

            if (trackCollection == null && entry.containsKey("items") && entry["items"] is JsonArray) {
                trackCollection = entry
            }
        }

        if (albumEntry == null) {
            throw Exception("Album not found")
        }

        val tracks: MutableList<APITrack> = mutableListOf()
        val items = trackCollection?.get("items")?.jsonArray
        logMessage("Album track items: $items")
        if (items != null) {
            for (rawItem in items) {
                if (rawItem !is JsonObject) {
                    logMessage("Skipping non-object track item: $rawItem")
                    continue
                }

                var trackCandidate: APITrack? = null
                if (rawItem.containsKey("item") && rawItem["item"] is JsonObject) {
                    trackCandidate = rawItem["item"]?.let { Json.decodeFromJsonElement<APITrack>(it) }
                } else {
                    trackCandidate = Json.decodeFromJsonElement<APITrack>(rawItem)
                }

                val candidateWithAlbum = if (trackCandidate?.album == null) {
                    trackCandidate?.copy(album = albumEntry)
                } else {
                    trackCandidate
                }

                if (candidateWithAlbum != null) {
                    tracks.add(ResponseNormalizer.normalizeTrack(candidateWithAlbum))
                }
            }
        }

        return Pair(albumEntry, tracks)
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

