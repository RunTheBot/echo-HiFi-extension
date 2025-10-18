package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
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

            Track(
                id = id,
                title = title,
                duration = duration,
                cover = albumObj?.get("cover")?.jsonPrimitive?.content?.let {
                    buildImageHolder(it)
                },
                album = album,
                artists = artists
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
}
