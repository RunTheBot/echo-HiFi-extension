package dev.brahmkshatriya.echo.extension.api.handlers

import dev.brahmkshatriya.echo.extension.api.models.APIPlaylist
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.utils.HttpUtils
import dev.brahmkshatriya.echo.extension.api.utils.ResponseNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/**
 * Handler for playlist-related operations
 */
class PlaylistHandler(private val httpUtils: HttpUtils) {

    suspend fun getPlaylist(uuid: String): Pair<APIPlaylist, List<APITrack>> {
        val response = httpUtils.fetch(httpUtils.buildUrl("/playlist/?id=$uuid"))
        httpUtils.ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to get playlist: ${response.code}")

        val data = response.body.string()
        val json = Json.parseToJsonElement(data)

        val entries = json as? JsonArray ?: listOf(json)

        var playlistEntry: APIPlaylist? = null
        var trackCollection: JsonObject? = null

        for (entry in entries) {
            if (entry !is JsonObject) continue

            if (entry.containsKey("uuid") && entry.containsKey("title")) {
                playlistEntry = Json.decodeFromJsonElement<APIPlaylist>(entry)
                continue
            }

            if (trackCollection == null && entry.containsKey("items") && entry["items"] is JsonArray) {
                trackCollection = entry
            }
        }

        if (playlistEntry == null) {
            throw Exception("Playlist not found")
        }

        val tracks: MutableList<APITrack> = mutableListOf()
        val items = trackCollection?.get("items")?.jsonArray
        logMessage("Playlist track items: $items")
        if (items != null) {
            for (rawItem in items) {
                if (rawItem !is JsonObject) {
                    logMessage("Skipping non-object track item: $rawItem")
                    continue
                }

                var trackCandidate: APITrack? = null
                trackCandidate = if (rawItem.containsKey("item") && rawItem["item"] is JsonObject) {
                    rawItem["item"]?.let { Json.decodeFromJsonElement<APITrack>(it) }
                } else {
                    Json.decodeFromJsonElement<APITrack>(rawItem)
                }

                if (trackCandidate != null) {
                    tracks.add(ResponseNormalizer.normalizeTrack(trackCandidate))
                }
            }
        }

        return Pair(playlistEntry, tracks)
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

