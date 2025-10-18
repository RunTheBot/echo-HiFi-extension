package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mappers for HiFi API responses to Echo data models
 */
object HiFiMapper {

    /**
     * Parse track from HiFi API response
     */
    fun parseTrack(json: JsonObject): Track? {
        return try {
            val id = json["id"]?.jsonPrimitive?.content ?: return null
            val title = json["title"]?.jsonPrimitive?.content ?: "Unknown"
            val duration = json["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            
            val artistArray = json["artists"]?.jsonArray
            val artists = artistArray?.mapNotNull { item ->
                val artistObj = item.jsonObject
                Artist(
                    id = artistObj["id"]?.jsonPrimitive?.content ?: "",
                    name = artistObj["name"]?.jsonPrimitive?.content ?: "Unknown Artist"
                )
            } ?: emptyList()
            
            val albumObj = json["album"]?.jsonObject
            val album = if (albumObj != null) {
                Album(
                    id = albumObj["id"]?.jsonPrimitive?.content ?: "",
                    title = albumObj["title"]?.jsonPrimitive?.content ?: "Unknown Album",
                    artists = artists
                )
            } else {
                null
            }

            // Create streamable servers for different quality levels
            val streamables = listOf(
                Streamable.server(
                    id = "$id-lossless",
                    quality = 320,
                    title = "LOSSLESS",
                    extras = mapOf("trackId" to id, "quality" to "LOSSLESS")
                ),
                Streamable.server(
                    id = "$id-hi-res",
                    quality = 500,
                    title = "HI_RES",
                    extras = mapOf("trackId" to id, "quality" to "HI_RES")
                ),
                Streamable.server(
                    id = "$id-hi-res-lossless",
                    quality = 640,
                    title = "HI_RES_LOSSLESS",
                    extras = mapOf("trackId" to id, "quality" to "HI_RES_LOSSLESS")
                ),
                Streamable.server(
                    id = "$id-high",
                    quality = 160,
                    title = "HIGH",
                    extras = mapOf("trackId" to id, "quality" to "HIGH")
                ),
                Streamable.server(
                    id = "$id-low",
                    quality = 96,
                    title = "LOW",
                    extras = mapOf("trackId" to id, "quality" to "LOW")
                )
            )

            Track(
                id = id,
                title = title,
                duration = duration,
                cover = albumObj?.get("cover")?.jsonPrimitive?.content?.let {
                    buildImageHolder(it)
                },
                album = album,
                artists = artists,
                streamables = streamables
            )
        } catch (e: Exception) {
            println("Error parsing track: ${e.message}")
            null
        }
    }

    /**
     * Parse album from HiFi API response
     */
    fun parseAlbum(json: JsonObject): Album? {
        return try {
            val id = json["id"]?.jsonPrimitive?.content ?: return null
            val title = json["title"]?.jsonPrimitive?.content ?: "Unknown"

            val artistArray = json["artists"]?.jsonArray
            val artists = artistArray?.mapNotNull { artistElem ->
                val artistObj = artistElem.jsonObject
                Artist(
                    id = artistObj["id"]?.jsonPrimitive?.content ?: "",
                    name = artistObj["name"]?.jsonPrimitive?.content ?: ""
                )
            } ?: emptyList()

            Album(
                id = id,
                title = title,
                cover = json["cover"]?.jsonPrimitive?.content?.let { buildImageHolder(it) },
                artists = artists
            )
        } catch (e: Exception) {
            println("Error parsing album: ${e.message}")
            null
        }
    }

    /**
     * Parse artist from HiFi API response
     */
    fun parseArtist(json: JsonObject): Artist? {
        return try {
            val id = json["id"]?.jsonPrimitive?.content ?: return null
            val name = json["name"]?.jsonPrimitive?.content ?: "Unknown"

            Artist(
                id = id,
                name = name,
                cover = json["picture"]?.jsonPrimitive?.content?.let { buildImageHolder(it) }
            )
        } catch (e: Exception) {
            println("Error parsing artist: ${e.message}")
            null
        }
    }

    /**
     * Parse playlist from HiFi API response
     */
    fun parsePlaylist(json: JsonObject): Playlist? {
        return try {
            val id = json["uuid"]?.jsonPrimitive?.content ?: json["id"]?.jsonPrimitive?.content ?: return null
            val title = json["title"]?.jsonPrimitive?.content ?: "Unknown Playlist"
            val description = json["description"]?.jsonPrimitive?.content

            Playlist(
                id = id,
                title = title,
                isEditable = false,
                description = description,
                cover = json["image"]?.jsonPrimitive?.content?.let { buildImageHolder(it) }
            )
        } catch (e: Exception) {
            println("Error parsing playlist: ${e.message}")
            null
        }
    }

    /**
     * Build ImageHolder from UUID (Tidal uses UUID for images)
     */
    private fun buildImageHolder(uuid: String, size: String = "750x750"): ImageHolder? {
        return try {
            val formattedUuid = uuid.replace("-", "/")
            val url = "https://resources.tidal.com/images/$formattedUuid/$size.jpg"
            ImageHolder.ResourceUriImageHolder(url, false)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse search results into QuickSearchItem objects
     * @param json Search response JSON
     */
    fun parseSearchResults(json: JsonObject): List<QuickSearchItem> {
        val results = mutableListOf<QuickSearchItem>()
        
        try {
            // Parse tracks
            json["songs"]?.jsonArray?.forEach { item ->
                parseTrack(item.jsonObject)?.let { track ->
                    results.add(QuickSearchItem.Media(track, searched = true))
                }
            }
            
            // Parse artists
            json["artists"]?.jsonArray?.forEach { item ->
                parseArtist(item.jsonObject)?.let { artist ->
                    results.add(QuickSearchItem.Media(artist, searched = true))
                }
            }
            
            // Parse albums
            json["albums"]?.jsonArray?.forEach { item ->
                parseAlbum(item.jsonObject)?.let { album ->
                    results.add(QuickSearchItem.Media(album, searched = true))
                }
            }
            
            // Parse playlists
            json["playlists"]?.jsonArray?.forEach { item ->
                parsePlaylist(item.jsonObject)?.let { playlist ->
                    results.add(QuickSearchItem.Media(playlist, searched = true))
                }
            }
        } catch (e: Exception) {
            println("Error parsing search results: ${e.message}")
        }
        
        return results
    }
}

