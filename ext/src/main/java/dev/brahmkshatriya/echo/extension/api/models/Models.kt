package dev.brahmkshatriya.echo.extension.api.models

import dev.brahmkshatriya.echo.extension.AudioQuality
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject

/**
 * Dash manifest result
 */
data class DashManifestResult(
    val kind: String, // "dash" or "flac"
    val manifest: String? = null,
    val manifestText: String? = null,
    val urls: List<String> = emptyList(),
    val contentType: String? = null
)

/**
 * Generic search response wrapper
 * @param T The type of items in the response
 */
data class SearchResponse<T>(
    val limit: Int,
    val offset: Int,
    val totalNumberOfItems: Int,
    val items: List<T>
)

interface TrackLookup {
    val apiTrack: APITrack
    val info: TrackInfo
    val originalTrackUrl: String?
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class APITrack(
    val id: Long,
    val title: String,
    val duration: Long,
    val replayGain: Double? = null,
    val peak: Double? = null,
    val allowStreaming: Boolean = true,
    val streamReady: Boolean = true,
    val payToStream: Boolean = false,
    val adSupportedStreamReady: Boolean = false,
    val djReady: Boolean = false,
    val stemReady: Boolean = false,
    val streamStartDate: String? = null,
    val premiumStreamingOnly: Boolean = false,
    val trackNumber: Long = 0,
    val volumeNumber: Long = 0,
    val version: String? = null,
    val popularity: Long = 0,
    val copyright: String? = null,
    val bpm: Int? = null,
    val url: String = "",
    val isrc: String? = null,
    val editable: Boolean = false,
    val explicit: Boolean = false,
    val audioQuality: AudioQuality,
    val audioModes: List<String> = emptyList(),
    val upload: Boolean = false,
    val accessType: String? = null,
    val spotlighted: Boolean = false,
    val artist: APIArtist? = null,
    val artists: List<APIArtist>,
    val album: APIAlbum? = null,
    val mixes: Map<String, String>? = null,
    val mediaMetadata: MediaMetadata? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class MediaMetadata(
    val tags: List<AudioQuality>
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class APIArtist(
    val id: Long,
    val name: String,
    val handle: String? = null,
    val type: String? = null,
    val picture: String? = null,
    val url: String? = null,
    val popularity: Long? = null,
    val artistTypes: List<String>? = null,
    val artistRoles: List<ArtistRole>? = null,
    val mixes: Map<String, String>? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class ArtistRole(
    val category: String,
    val categoryId: Long
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class APIAlbum(
    val id: Long,
    val title: String,
    val cover: String,
    val videoCover: String? = null,
    val releaseDate: String? = null,
    val duration: Long? = null,
    val numberOfTracks: Long? = null,
    val numberOfVideos: Long? = null,
    val numberOfVolumes: Long? = null,
    val explicit: Boolean? = null,
    val popularity: Long? = null,
    val type: String? = null,
    val upc: String? = null,
    val copyright: String? = null,
    val artist: APIArtist? = null,
    val artists: List<APIArtist> = emptyList(),
    val audioQuality: AudioQuality? = null,
    val audioModes: List<String>? = null,
    val url: String? = null,
    val vibrantColor: String? = null,
    val streamReady: Boolean? = null,
    val allowStreaming: Boolean? = null,
    val mediaMetadata: MediaMetadata? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class APIPlaylist(
    val uuid: String,
    val title: String,
    val description: String? = null,
    val numberOfTracks: Long? = null,
    val numberOfVideos: Long? = null,
    val creator: JsonObject? = null,
    val duration: Long? = null,
    val created: String? = null,
    val lastUpdated: String? = null,
    val type: String? = null,
    val publicPlaylist: Boolean = false,
    val url: String? = null,
    val image: String? = null,
    val popularity: Long? = null,
    val squareImage: String? = null,
    val customImageUrl: String? = null,
    val promotedArtists: List<APIArtist> = emptyList(),
    val lastItemAddedAt: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class TrackInfo (
    val trackId: Long,
    val audioQuality: AudioQuality,
    val audioMode: String,
    val manifest: String,
    val manifestMimeType: String,
    val assetPresentation: String,
    val albumReplayGain: Double? = null,
    val albumPeakAmplitude: Double? = null,
    val trackReplayGain: Double? = null,
    val trackPeakAmplitude: Double? = null,
    val bitDepth: Long? = null,
    val sampleRate: Long? = null,
)

