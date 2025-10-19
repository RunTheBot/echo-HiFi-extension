package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Mappers for HiFi API responses to Echo data models
 */
object HiFiMapper {

    /**
     * Parse track from HiFi API response
     */
    fun parseTrack(apiTrack: APITrack): Track {
        return Track(
            id = apiTrack.id.toString(),
            title = apiTrack.title,
            cover = apiTrack.album?.let { buildImageHolder(it.cover) },
            artists = apiTrack.artists.map { artist -> parseArtist(artist) },
            album = apiTrack.album?.let { album ->
                Album(
                    id = album.id.toString(),
                    title = album.title,
                    artists = album.artists.map { artist -> parseArtist(artist) },
                    cover = buildImageHolder(album.cover),
                    trackCount = album.numberOfTracks,
                    duration = album.duration,
                    releaseDate = runCatching {
                        val date = LocalDate.parse(album.releaseDate, DateTimeFormatter.ISO_LOCAL_DATE)
                        Date(day = date.dayOfMonth, month = date.monthValue, year = date.year)
                    }.getOrNull(),
                    isExplicit = album.explicit == true
                )
            },
            duration = apiTrack.duration,
            isrc = apiTrack.isrc,
            isExplicit = apiTrack.explicit,
            albumOrderNumber = apiTrack.trackNumber,
//            isPlayable = (if (apiTrack.streamReady && apiTrack.allowStreaming) Track.Playable.Yes else Track.Playable.No) as Track.Playable
        )
    }

    /**
     * Parse album from HiFi API response
     */
    fun parseAlbum(albumPair: Pair<APIAlbum, List<APITrack>>): Pair<Album, List<Track>> {


        return albumPair.let { (apiAlbum, apiTracks) ->
            val album = Album(
                id = apiAlbum.id.toString(),
                title = apiAlbum.title,
                artists = apiAlbum.artists.map { artist ->
                    parseArtist(artist)
                },
                cover = buildImageHolder(apiAlbum.cover),
                trackCount = apiAlbum.numberOfTracks,
                duration = apiAlbum.duration,
                releaseDate = run { // Use run block or directly assign if releaseDate expects the Date object itself
                    val date = LocalDate.parse(apiAlbum.releaseDate, DateTimeFormatter.ISO_LOCAL_DATE) // Use ISO_LOCAL_DATE for yyyy-MM-dd
                    Date(
                        day = date.dayOfMonth,
                        month = date.monthValue,
                        year = date.year
                    )
                },
                isExplicit = apiAlbum.explicit == true
            )

            val tracks = apiTracks.map { apiTrack ->
                parseTrack(apiTrack)
            }

            Pair(album, tracks)
        }
    }

    /**
     * Parse artist from HiFi API response
     */
    fun parseArtist(apiArtist: APIArtist): Artist {

        return Artist(
            id = apiArtist.id.toString(),
            name = apiArtist.name,
            cover = apiArtist.picture?.let { buildImageHolder(it) }

        )

    }

    /**
     * Parse playlist from HiFi API response (APIPlaylist)
     */
    fun parsePlaylist(apiPlaylist: APIPlaylist): Playlist {
        return Playlist(
            id = apiPlaylist.uuid,
            title = apiPlaylist.title,
            isEditable = false,
            description = apiPlaylist.description,
            cover = apiPlaylist.squareImage?.let { buildImageHolder(it) }
        )
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

