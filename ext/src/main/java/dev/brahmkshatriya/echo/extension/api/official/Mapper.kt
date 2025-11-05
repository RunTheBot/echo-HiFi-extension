package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.api.official.models.ImageSize
import dev.brahmkshatriya.echo.extension.api.official.models.Item
import dev.brahmkshatriya.echo.extension.api.official.models.Page
import dev.brahmkshatriya.echo.extension.api.official.models.SearchResponse
import java.text.SimpleDateFormat
import java.util.Locale

fun SearchResponse.toFeedData(size: ImageSize): Feed.Data<Shelf> {
    return listOfNotNull(
        topHits?.firstOrNull()?.let {
            it.value!!.toMediaItem(it.type, size).toShelf()
        },
        tracks?.items?.toShelf("TRACKS", size),
        uploads?.items?.toShelf("UPLOADS", size),
        albums?.items?.toShelf("ALBUMS", size),
        artists?.items?.toShelf("ARTISTS", size),
        playlists?.items?.toShelf("PLAYLISTS", size),
        videos?.items?.toShelf("VIDEOS", size),
    ).toFeedData()
}

fun List<Item>?.toShelf(type: String?, size: ImageSize) = if (isNullOrEmpty()) null
else Shelf.Lists.Items(
    id = type!!,
    title = type.lowercase().replaceFirstChar { it.uppercase() },
    list = map { it.toMediaItem(type, size) }
)

fun SearchResponse.page(type: String?) = when (type) {
    "TRACKS" -> tracks
    "UPLOADS" -> uploads
    "ALBUMS" -> albums
    "ARTISTS" -> artists
    "PLAYLISTS" -> playlists
    "VIDEOS" -> videos
    else -> throw Exception("Unknown page type: $type")
}

fun List<Item>?.toShelves(type: String?, size: ImageSize) = this!!.map {
    it.toMediaItem(type, size).toShelf()
}

fun Item.toMediaItem(type: String?, size: ImageSize) = when (type) {
    "TRACKS", "UPLOADS", "VIDEOS" -> toTrack(size, type == "VIDEOS")
    "ALBUMS" -> toAlbum(size)
    "ARTISTS" -> toArtist(size)
    "PLAYLISTS" -> toPlaylist(size)
    else -> throw Exception("Unknown media item type: $type")
}

fun Item.toTrack(size: ImageSize, isVideo: Boolean) = Track(
    id = id!!.content,
    title = title!!,
    cover = (image ?: album?.cover)?.toImage(size, isVideo),
    album = album?.let {
        Album(
            id = it.id!!.toString(),
            title = it.title!!,
            cover = it.cover?.toImage(size),
            releaseDate = it.releaseDate?.toDate(),
            label = providerName
        )
    },
    artists = artists.orEmpty().map {
        Artist(
            id = it.id!!.toString(),
            name = it.name!!,
            cover = it.picture?.toImage(size),
        )
    },
    duration = duration?.let { it * 1000 },
    albumOrderNumber = trackNumber,
    albumDiscNumber = volumeNumber,
    isrc = isrc,
    description = copyright,
    genres = genres.orEmpty().map { it.name!! },
    releaseDate = firstAvailable?.toSDate(),
    extras = mapOf(
        "trackMix" to (mixes?.trackMix ?: ""),
        "isVideo" to isVideo.toString()
    )
)

fun Item.toPlaylist(size: ImageSize) = Playlist(
    id = uuid!!,
    title = title!!,
    isEditable = false,
    isPrivate = when (sharingLevel) {
        "PRIVATE" -> true
        else -> false
    },
    cover = squareImage?.toImage(size),
    description = description,
    trackCount = numberOfTracks,
    duration = duration,
    creationDate = created?.toSSDate()
)

fun Item.toAlbum(size: ImageSize) = Album(
    id = id!!.content,
    title = title!!,
    type = null,
    cover = cover?.toImage(size),
    artists = artists.orEmpty().map {
        Artist(
            id = it.id!!.toString(),
            name = it.name!!,
            cover = it.picture?.toImage(size),
        )
    },
    trackCount = numberOfTracks,
    duration = duration,
    releaseDate = releaseDate?.toDate(),
    description = copyright,
    label = providerName,
    isExplicit = explicit == true,
)

fun Item.toArtist(size: ImageSize) = Artist(
    id = id!!.content,
    name = name!!,
    cover = picture?.toImage(size),
    extras = mapOf(
        "artistMix" to (mixes?.artistMix ?: "")
    )
)

fun Page.toShelves(size: ImageSize) = items!!.mapNotNull { it.toShelves(size) }

fun Page.Items.toShelves(size: ImageSize): Shelf? {
    return when (type) {
        "HORIZONTAL_LIST" -> Shelf.Lists.Items(
            id = moduleID!!,
            title = title!!,
            subtitle = subtitle,
            list = items!!.map { it.toMediaItem(size) }
        )

        "TRACK_LIST" -> Shelf.Lists.Tracks(
            id = moduleID!!,
            title = title!!,
            subtitle = subtitle,
            list = items!!.map { it.data!!.toTrack(size, false) }
        )

        "SHORTCUT_LIST" -> null
        else -> throw Exception("Unknown shelf type: $type")
    }
}

fun Page.PageItem.toMediaItem(size: ImageSize): EchoMediaItem {
    val data = data!!
    return when (type) {
        "PLAYLIST" -> data.toPlaylist(size)
        "ALBUM" -> data.toAlbum(size)
        "TRACK" -> data.toTrack(size, false)
        else -> throw Exception("Unknown media item type: $type")
    }
}

fun String.toDate() = run {
    val year = this.substring(0, 4).toInt()
    val month = this.substring(5, 7).toInt()
    val day = this.substring(8, 10).toInt()
    Date(year, month, day)
}

val sFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US)
fun String.toSDate() = sFormatter.parse(this)?.time?.let { Date(it) }

val ssFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
fun String.toSSDate() = ssFormatter.parse(this)?.time?.let { Date(it) }
fun String.toImage(size: ImageSize, isVideo: Boolean = false) =
    "https://resources.tidal.com/images/${
        replace('-', '/')
    }/${size.px}x${if (isVideo) size.px * 0.5625 else size.px}.jpg".toImageHolder(crop = false)

fun String.toVideo(size: ImageSize) = "https://resources.tidal.com/videos/${
    replace('-', '/')
}/${size.px}x${size.px}.mp4".toGetRequest()