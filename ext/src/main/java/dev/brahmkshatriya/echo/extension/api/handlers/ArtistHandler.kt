package dev.brahmkshatriya.echo.extension.api.handlers

import dev.brahmkshatriya.echo.extension.api.models.APIAlbum
import dev.brahmkshatriya.echo.extension.api.models.APIArtist
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.utils.HttpUtils
import dev.brahmkshatriya.echo.extension.api.utils.ResponseNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat

/**
 * Handler for artist-related operations
 */
class ArtistHandler(private val httpUtils: HttpUtils) {

    suspend fun getArtist(id: Long): Triple<APIArtist, List<APIAlbum>, List<APITrack>> {
        val response = httpUtils.fetch(httpUtils.buildUrl("/artist/?f=$id"))
        httpUtils.ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to get artist: ${response.code}")

        val data = response.body.string()
        val json = Json.parseToJsonElement(data)
        val entries = json as? JsonArray ?: listOf(json)

        val visited = mutableSetOf<Any>()
        val albumMap = mutableMapOf<Long, APIAlbum>()
        val trackMap = mutableMapOf<Long, APITrack>()
        var artist: APIArtist? = null

        fun isTrackLike(value: Any?): Boolean {
            if (value == null || value !is JsonObject) return false
            return (
                value.containsKey("id") && value["id"] is JsonPrimitive &&
                value.containsKey("title") && value["title"] is JsonPrimitive &&
                value.containsKey("duration") && value["duration"] is JsonPrimitive &&
                value.containsKey("trackNumber") &&
                value.containsKey("album") && value["album"] != null
            )
        }

        fun isAlbumLike(value: Any?): Boolean {
            if (value == null || value !is JsonObject) return false
            return (
                value.containsKey("id") && value["id"] is JsonPrimitive &&
                value.containsKey("title") && value["title"] is JsonPrimitive &&
                value.containsKey("cover")
            )
        }

        fun isArtistLike(value: Any?): Boolean {
            if (value == null || value !is JsonObject) return false
            return (
                value.containsKey("id") && value["id"] is JsonPrimitive &&
                value.containsKey("name") && value["name"] is JsonPrimitive &&
                value.containsKey("type") && value["type"] is JsonPrimitive &&
                (value.containsKey("artistRoles") || value.containsKey("artistTypes") || value.containsKey("url"))
            )
        }

        fun recordArtist(candidate: APIArtist?) {
            if (candidate == null) return
            val normalized = ResponseNormalizer.normalizeArtist(candidate)
            if (artist == null || artist!!.id == normalized.id) {
                artist = normalized
            }
        }

        fun addAlbum(candidate: APIAlbum?) {
            if (candidate == null || candidate.id <= 0) return
            val normalized = ResponseNormalizer.normalizeAlbum(candidate)
            albumMap[normalized.id] = normalized
            recordArtist(normalized.artist ?: normalized.artists.firstOrNull())
        }

        fun addTrack(candidate: APITrack?) {
            if (candidate == null || candidate.id <= 0) return
            val normalized = ResponseNormalizer.normalizeTrack(candidate)
            if (normalized.album == null) {
                return
            }
            addAlbum(normalized.album)
            val knownAlbum = albumMap[normalized.album!!.id]
            if (knownAlbum != null) {
                val enrichedTrack = normalized.copy(album = knownAlbum)
                trackMap[enrichedTrack.id] = enrichedTrack
            } else {
                trackMap[normalized.id] = normalized
            }
            recordArtist(normalized.artist)
        }

        lateinit var scanValue: (Any?) -> Unit
        lateinit var parseModuleItems: (Any?) -> Unit

        scanValue = fun(value: Any?) {
            if (value == null) return

            if (value is JsonArray) {
                val trackCandidates = value.filter { isTrackLike(it) }
                if (trackCandidates.isNotEmpty()) {
                    for (candidate in trackCandidates) {
                        try {
                            val track = Json.decodeFromJsonElement<APITrack>(candidate)
                            addTrack(track)
                        } catch (e: Exception) {
                            // Skip malformed tracks
                        }
                    }
                    return
                }
                for (entry in value) {
                    scanValue(entry)
                }
                return
            }

            if (value !is JsonObject) {
                return
            }

            if (visited.contains(value)) {
                return
            }
            visited.add(value)

            if (isArtistLike(value)) {
                try {
                    val candidateArtist = Json.decodeFromJsonElement<APIArtist>(value)
                    recordArtist(candidateArtist)
                } catch (e: Exception) {
                    // Skip malformed artists
                }
            }

            if (value.containsKey("modules") && value["modules"] is JsonArray) {
                for (moduleEntry in value["modules"]!!.jsonArray) {
                    scanValue(moduleEntry)
                }
            }

            if (value.containsKey("pagedList") && value["pagedList"] is JsonObject) {
                val pagedList = value["pagedList"]!!.jsonObject
                if (pagedList.containsKey("items")) {
                    parseModuleItems(pagedList["items"])
                }
            }

            if (value.containsKey("items") && value["items"] is JsonArray) {
                parseModuleItems(value["items"])
            }

            if (value.containsKey("rows") && value["rows"] is JsonArray) {
                parseModuleItems(value["rows"])
            }

            if (value.containsKey("listItems") && value["listItems"] is JsonArray) {
                parseModuleItems(value["listItems"])
            }

            for (nested in value.values) {
                scanValue(nested)
            }
        }

        parseModuleItems = fun(items: Any?) {
            if (items == null || items !is JsonArray) return
            for (entry in items) {
                if (entry == null || entry !is JsonObject) continue

                val candidate = if (entry.containsKey("item")) entry["item"] else entry
                if (candidate != null && isAlbumLike(candidate)) {
                    try {
                        val album = Json.decodeFromJsonElement<APIAlbum>(candidate)
                        addAlbum(album)
                        val normalizedAlbum = albumMap[album.id]
                        recordArtist(normalizedAlbum?.artist ?: normalizedAlbum?.artists?.firstOrNull())
                    } catch (e: Exception) {
                        // Skip malformed albums
                    }
                    continue
                }
                if (candidate != null && isTrackLike(candidate)) {
                    try {
                        val track = Json.decodeFromJsonElement<APITrack>(candidate)
                        addTrack(track)
                    } catch (e: Exception) {
                        // Skip malformed tracks
                    }
                    continue
                }

                scanValue(candidate)
            }
        }

        for (entry in entries) {
            scanValue(entry)
        }

        if (artist == null) {
            val trackPrimaryArtist = trackMap.values
                .map { it.artist ?: it.artists.firstOrNull() }
                .firstOrNull()
            val albumPrimaryArtist = albumMap.values
                .map { it.artist ?: it.artists.firstOrNull() }
                .firstOrNull()
            recordArtist(trackPrimaryArtist ?: albumPrimaryArtist)
        }

        if (artist == null) {
            try {
                val fallbackResponse = httpUtils.fetch(httpUtils.buildUrl("/artist/?id=$id"))
                httpUtils.ensureNotRateLimited(fallbackResponse)
                if (fallbackResponse.isSuccessful) {
                    val fallbackData = fallbackResponse.body.string()
                    val fallbackJson = Json.parseToJsonElement(fallbackData)
                    val baseArtist = fallbackJson as? JsonObject ?: (fallbackJson as? JsonArray)?.firstOrNull()
                    if (baseArtist is JsonObject) {
                        recordArtist(Json.decodeFromJsonElement<APIArtist>(baseArtist))
                    }
                }
            } catch (fallbackError: Exception) {
                logMessage("Failed to fetch base artist details: $fallbackError")
            }
        }

        if (artist == null) {
            throw Exception("Artist not found")
        }

        val albums = albumMap.values.map { album ->
            if (album.artist == null) {
                album.copy(artist = artist)
            } else {
                album
            }
        }

        val albumById = albumMap.mapKeys { it.key }

        val tracks = trackMap.values.map { track ->
            val enrichedArtist = track.artist ?: artist
            val album = track.album
            val enrichedAlbum = album?.let {
                albumById[it.id] ?: (artist?.let { a -> if (it.artist == null) it.copy(artist = a) else it })
            } ?: album
            track.copy(
                artist = enrichedArtist,
                album = enrichedAlbum
            )
        }

        val sortedAlbums = albums.sortedWith(compareBy<APIAlbum> { album ->
            val timeA = parseDate(album.releaseDate)
            if (timeA == Long.MIN_VALUE) 1 else 0
        }.thenBy { album ->
            val timeA = parseDate(album.releaseDate)
            -timeA
        }.thenBy { album ->
            -(album.popularity ?: 0)
        })

        val sortedTracks = tracks
            .sortedByDescending { it.popularity }
            .take(100)

        return Triple(
            artist,
            sortedAlbums,
            sortedTracks
        )
    }

    private fun parseDate(value: String?): Long {
        if (value == null) return Long.MIN_VALUE
        return try {
            SimpleDateFormat("yyyy-MM-dd").parse(value)?.time ?: Long.MIN_VALUE
        } catch (e: Exception) {
            Long.MIN_VALUE
        }
    }

    private fun logMessage(message: String) {
        println(message)
    }
}

