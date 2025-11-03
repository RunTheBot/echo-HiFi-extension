package dev.brahmkshatriya.echo.extension.api.official.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Item(
    val artifactType: String? = null,
    val id: JsonPrimitive? = null,
    val title: String? = null,
    val artists: List<Artist>? = null,
    val duration: Long? = null,
    val cover: String? = null,
    val vibrantColor: String? = null,
    val videoCover: String? = null,
    val copyright: String? = null,
    val numberOfVolumes: Long? = null,
    val numberOfTracks: Long? = null,
    val numberOfVideos: Long? = null,
    val popularity: Long? = null,
    val doublePopularity: Double? = null,
    val version: String? = null,
    val releaseDate: String? = null,
    val type: String? = null,
    val explicit: Boolean? = null,
    val upc: String? = null,
    val audioQuality: String? = null,
    val masterAlbum: MasterAlbum? = null,
    val allowStreaming: Boolean? = null,
    val streamStartDate: String? = null,
    val streamReady: Boolean? = null,
    val payToStream: Boolean? = null,
    val audioModes: List<String>? = null,
    val adSupportedStreamReady: Boolean? = null,
    val mediaMetadata: MediaMetadata? = null,
    val providerName: String? = null,
    val djReady: Boolean? = null,
    val stemReady: Boolean? = null,
    val upload: Boolean? = null,
    val accessType: JsonElement? = null,
    val createdAt: JsonElement? = null,

    @SerialName("userId")
    val userID: JsonElement? = null,

    val premiumStreamingOnly: Boolean? = null,
    val url: String? = null,

    val name: String? = null,
    val picture: String? = null,
    val artistTypes: List<String>? = null,
    val artistRoles: List<ArtistRole>? = null,
    val mixes: Mixes? = null,
    val selectedAlbumCoverFallback: String? = null,
    val handle: String? = null,
    @SerialName("artworkId")
    val artworkID: JsonElement? = null,
    val spotlighted: Boolean? = null,
    val contributionsEnabled: Boolean? = null,
    val cashAppOnboarded: Boolean? = null,

    val uuid: String? = null,
    val numberOfAudioTracks: Long? = null,
    val numberOfVideoTracks: Long? = null,
    val description: String? = null,
    val lastUpdated: String? = null,
    val created: String? = null,
    val image: String? = null,
    val squareImage: String? = null,
    val createdByArtists: List<Artist>? = null,
    val publicPlaylist: Boolean? = null,
    val promotedArtists: List<Artist>? = null,
    val lastItemAddedAt: String? = null,
    val creator: Creator? = null,


    val editable: Boolean? = null,
    val album: Album? = null,
    val trackNumber: Long? = null,
    val volumeNumber: Long? = null,
    val replayGain: Double? = null,
    val isrc: String? = null,
    val peak: Double? = null,
    val prePaywallPresentation: String? = null,
    val firstAvailable: String? = null,
    val linkedStereoIsrc: String? = null,

    @SerialName("audioOnlyTrackId")
    val audioOnlyTrackID: Long? = null,
    @SerialName("adsUrl")
    val adsURL: JsonElement? = null,
    val adsPrePaywallOnly: Boolean? = null,
    @SerialName("providerId")
    val providerID: Long? = null,
    val quality: String? = null,
    @SerialName("imageId")
    val imageID: String? = null,

    val curators: JsonArray? = null,
    val contentBehavior: String? = null,
    val sharingLevel: String? = null,
    val status: String? = null,
    val source: String? = null,
    val trn: String? = null,
    val trackGroup: String? = null,

    val genres: List<Genre>? = null,
    val audioAnalysisAttributes: AudioAnalysisAttributes? = null,

    val icon: String? = null,
    val apiPath: String? = null,
) {
    @Serializable
    data class Artist(
        val id: Long? = null,
        val name: String? = null,
        val handle: String? = null,
        val picture: String? = null,

        @SerialName("userId")
        val userID: JsonElement? = null,

        val type: String? = null,
        val main: Boolean? = null,
    )

    @Serializable
    data class MasterAlbum(
        val id: String? = null,
        val releaseDate: String? = null,
        val categories: List<String>? = null,
    )

    @Serializable
    data class MediaMetadata(
        val tags: List<String>? = null,
    )

    @Serializable
    data class ArtistRole(
        @SerialName("categoryId")
        val categoryID: Long? = null,

        val category: String? = null,
    )

    @Serializable
    data class Mixes(
        @SerialName("ARTIST_MIX")
        val artistMix: String? = null,
        @SerialName("TRACK_MIX")
        val trackMix: String? = null,
    )

    @Serializable
    data class Creator(
        val id: Long? = null,
        val name: String? = null,
        val picture: String? = null,
        val type: String? = null,
    )

    @Serializable
    data class AudioAnalysisAttributes(
        val bpm: String? = null,
    )


    @Serializable
    data class Genre(
        val id: Long? = null,
        val name: String? = null,

        @SerialName("parentId")
        val parentID: Long? = null,
    )

    @Serializable
    data class Album(
        val id: Long? = null,
        val title: String? = null,
        val version: JsonElement? = null,
        val cover: String? = null,
        val vibrantColor: String? = null,
        val videoCover: String? = null,
        val releaseDate: String? = null,
    )
}