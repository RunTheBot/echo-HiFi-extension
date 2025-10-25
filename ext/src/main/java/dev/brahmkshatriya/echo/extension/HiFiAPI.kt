package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.api.HiFiAPI as NewHiFiAPI
import dev.brahmkshatriya.echo.extension.api.handlers.DASH_MANIFEST_UNAVAILABLE_CODE
import dev.brahmkshatriya.echo.extension.api.models.DashManifestResult
import dev.brahmkshatriya.echo.extension.api.models.SearchResponse
import dev.brahmkshatriya.echo.extension.api.models.APITrack
import dev.brahmkshatriya.echo.extension.api.models.APIArtist
import dev.brahmkshatriya.echo.extension.api.models.APIAlbum
import dev.brahmkshatriya.echo.extension.api.models.APIPlaylist
import dev.brahmkshatriya.echo.extension.api.models.TrackLookup
import okhttp3.OkHttpClient

const val RATE_LIMIT_ERROR_MESSAGE = "Rate limited"
const val DASH_MANIFEST_UNAVAILABLE_CODE = "DASH_UNAVAILABLE"

/**
 * Backward Compatibility Wrapper for HiFiAPI
 *
 * This class maintains backward compatibility by delegating to the new refactored API.
 * The actual implementation has been reorganized into:
 * - dev.brahmkshatriya.echo.extension.api.HiFiAPI (main orchestration)
 * - dev.brahmkshatriya.echo.extension.api.handlers.* (specific handlers)
 * - dev.brahmkshatriya.echo.extension.api.models.* (data models)
 * - dev.brahmkshatriya.echo.extension.api.utils.* (utility functions)
 *
 * @deprecated Use dev.brahmkshatriya.echo.extension.api.HiFiAPI directly for new code
 */
class HiFiAPI(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val newApi = NewHiFiAPI(httpClient)

    constructor() : this(OkHttpClient())

    // ===== Search Operations =====
    suspend fun searchTracks(query: String, limit: Long?): SearchResponse<APITrack> {
        return newApi.searchTracks(query, limit)
    }

    suspend fun searchArtists(query: String): SearchResponse<APIArtist> {
        return newApi.searchArtists(query)
    }

    suspend fun searchAlbums(query: String): SearchResponse<APIAlbum> {
        return newApi.searchAlbums(query)
    }

    suspend fun searchPlaylists(query: String): SearchResponse<APIPlaylist> {
        return newApi.searchPlaylists(query)
    }

    // ===== Track Operations =====
    suspend fun getTrack(id: Long, quality: AudioQuality = AudioQuality.LOW): TrackLookup {
        return newApi.getTrack(id, quality)
    }

    suspend fun getDashURL(trackId: Long, quality: AudioQuality = AudioQuality.HIRES_LOSSLESS): String {
        return newApi.getDashURL(trackId, quality)
    }

    // ===== Album Operations =====
    suspend fun getAlbum(id: Long): Pair<APIAlbum, List<APITrack>> {
        return newApi.getAlbum(id)
    }

    // ===== Artist Operations =====
    suspend fun getArtist(id: Long): Triple<APIArtist, List<APIAlbum>, List<APITrack>> {
        return newApi.getArtist(id)
    }

    // ===== Playlist Operations =====
    suspend fun getPlaylist(uuid: String): Pair<APIPlaylist, List<APITrack>> {
        return newApi.getPlaylist(uuid)
    }

    // ===== Stream Operations =====
    suspend fun getStreamUrl(trackId: Long, quality: AudioQuality): String {
        return newApi.getStreamUrl(trackId, quality)
    }

    suspend fun getDashManifest(
        trackId: Long,
        quality: AudioQuality = AudioQuality.HIRES_LOSSLESS
    ): DashManifestResult {
        return newApi.getDashManifest(trackId, quality)
    }
}

// Legacy type aliases and data classes for backward compatibility
@Deprecated("Use dev.brahmkshatriya.echo.extension.api.models.SearchResponse")
typealias SearchResponseCompat<T> = SearchResponse<T>

@Deprecated("Use dev.brahmkshatriya.echo.extension.api.models.DashManifestResult")
typealias DashManifestResultCompat = DashManifestResult

@Deprecated("Use dev.brahmkshatriya.echo.extension.api.models.APITrack")
typealias APITrackCompat = APITrack

@Deprecated("Use dev.brahmkshatriya.echo.extension.api.models.APIArtist")
typealias APIArtistCompat = APIArtist

@Deprecated("Use dev.brahmkshatriya.echo.extension.api.models.APIAlbum")
typealias APIAlbumCompat = APIAlbum

@Deprecated("Use dev.brahmkshatriya.echo.extension.api.models.APIPlaylist")
typealias APIPlaylistCompat = APIPlaylist

@Deprecated("Use dev.brahmkshatriya.echo.extension.api.models.TrackLookup")
typealias TrackLookupCompat = TrackLookup
