package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.extension.AudioQuality
import dev.brahmkshatriya.echo.extension.api.handlers.AlbumHandler
import dev.brahmkshatriya.echo.extension.api.handlers.ArtistHandler
import dev.brahmkshatriya.echo.extension.api.handlers.PlaylistHandler
import dev.brahmkshatriya.echo.extension.api.handlers.SearchHandler
import dev.brahmkshatriya.echo.extension.api.handlers.StreamHandler
import dev.brahmkshatriya.echo.extension.api.handlers.TrackHandler
import dev.brahmkshatriya.echo.extension.api.models.APIAlbum
import dev.brahmkshatriya.echo.extension.api.models.APIArtist
import dev.brahmkshatriya.echo.extension.api.models.APIPlaylist
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.models.DashManifestResult
import dev.brahmkshatriya.echo.extension.api.models.SearchResponse
import dev.brahmkshatriya.echo.extension.api.models.TrackLookup
import dev.brahmkshatriya.echo.extension.api.utils.HttpUtils
import okhttp3.OkHttpClient

/**
 * Lossless API Client
 * Provides access to Tidal via the HiFi API (https://github.com/sachinsenal0x64/hifi)
 *
 * This is a refactored version organized into focused handler classes for better maintainability:
 * - SearchHandler: Search operations for tracks, artists, albums, playlists
 * - TrackHandler: Track information retrieval
 * - AlbumHandler: Album details with tracks
 * - ArtistHandler: Artist details with albums and top tracks
 * - PlaylistHandler: Playlist details with tracks
 * - StreamHandler: Stream URL and DASH manifest resolution
 * - HttpUtils: HTTP communication utilities
 *
 * Ported from TypeScript to Kotlin
 */
class HiFiAPI(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val httpUtils = HttpUtils(httpClient)
    private val trackHandler = TrackHandler(httpUtils)
    private val streamHandler = StreamHandler(httpUtils, trackHandler)
    private val searchHandler = SearchHandler(httpUtils)
    private val albumHandler = AlbumHandler(httpUtils)
    private val playlistHandler = PlaylistHandler(httpUtils)
    private val artistHandler = ArtistHandler(httpUtils)

    constructor() : this(OkHttpClient())

    // ===== Search Operations =====

    /**
     * Search for tracks
     */
    suspend fun searchTracks(query: String, limit: Long?): SearchResponse<APITrack> {
        return searchHandler.searchTracks(query, limit)
    }

    /**
     * Search for artists
     */
    suspend fun searchArtists(query: String): SearchResponse<APIArtist> {
        return searchHandler.searchArtists(query)
    }

    /**
     * Search for albums
     */
    suspend fun searchAlbums(query: String): SearchResponse<APIAlbum> {
        return searchHandler.searchAlbums(query)
    }

    /**
     * Search for playlists
     */
    suspend fun searchPlaylists(query: String): SearchResponse<APIPlaylist> {
        return searchHandler.searchPlaylists(query)
    }

    // ===== Track Operations =====

    /**
     * Get track info and stream URL (with retries for quality fallback)
     */
    suspend fun getTrack(id: Long, quality: AudioQuality = AudioQuality.LOW): TrackLookup {
        return trackHandler.getTrack(id, quality)
    }

    /**
     * Get DASH URL for a track
     */
    suspend fun getDashURL(trackId: Long, quality: AudioQuality = AudioQuality.HIRES_LOSSLESS): String {
        return trackHandler.getDashURL(trackId, quality)
    }

    // ===== Album Operations =====

    /**
     * Get album details with track listing
     */
    suspend fun getAlbum(id: Long): Pair<APIAlbum, List<APITrack>> {
        return albumHandler.getAlbum(id)
    }

    // ===== Artist Operations =====

    /**
     * Get artist details with albums and top tracks
     */
    suspend fun getArtist(id: Long): Triple<APIArtist, List<APIAlbum>, List<APITrack>> {
        return artistHandler.getArtist(id)
    }

    // ===== Playlist Operations =====

    /**
     * Get playlist details
     */
    suspend fun getPlaylist(uuid: String): Pair<APIPlaylist, List<APITrack>> {
        return playlistHandler.getPlaylist(uuid)
    }

    // ===== Stream Operations =====

    /**
     * Get stream URL for a track
     */
    suspend fun getStreamUrl(trackId: Long, quality: AudioQuality): String {
        return streamHandler.getStreamUrl(trackId, quality)
    }

    /**
     * Get DASH manifest for a track
     */
    suspend fun getDashManifest(
        trackId: Long,
        quality: AudioQuality = AudioQuality.HIRES_LOSSLESS
    ): DashManifestResult {
        return streamHandler.getDashManifest(trackId, quality)
    }
}

