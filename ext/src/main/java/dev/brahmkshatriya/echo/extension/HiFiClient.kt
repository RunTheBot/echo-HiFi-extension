package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * HiFi API Client
 * Provides access to Tidal via the HiFi API (https://github.com/sachinsenal0x64/hifi)
 */
class HiFiClient(
    private val apiUrl: String = "https://tidal.401658.xyz",
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    /**
     * Search for tracks
     * @param query Search query
     * @param limit Maximum number of results
     * @param offset Pagination offset
     */
    suspend fun searchTracks(query: String, limit: Int = 25, offset: Int = 0): JsonObject? {
        return get("/search/?s=${URLEncoder.encode(query, "UTF-8")}&li=$limit&o=$offset")
    }

    /**
     * Search for artists
     * @param query Artist name
     * @param limit Maximum number of results
     * @param offset Pagination offset
     */
    suspend fun searchArtists(query: String, limit: Int = 25, offset: Int = 0): JsonObject? {
        return get("/search/?a=${URLEncoder.encode(query, "UTF-8")}&li=$limit&o=$offset")
    }

    /**
     * Search for albums
     * @param query Album name
     * @param limit Maximum number of results
     * @param offset Pagination offset
     */
    suspend fun searchAlbums(query: String, limit: Int = 25, offset: Int = 0): JsonObject? {
        return get("/search/?al=${URLEncoder.encode(query, "UTF-8")}&li=$limit&o=$offset")
    }

    /**
     * Get track playback info
     * @param trackId Track ID
     * @param quality Audio quality (LOW, HIGH, LOSSLESS, HI_RES, HI_RES_LOSSLESS)
     */
    suspend fun getTrack(trackId: Long, quality: String = "LOSSLESS"): JsonObject? {
        return get("/track/?id=$trackId&quality=$quality")
    }

    /**
     * Get HiRes DASH stream
     * @param trackId Track ID
     * @param quality Quality level (HI_RES_LOSSLESS, DOLBY_ATMOS, SONY_360RA, MQA)
     */
    suspend fun getDashStream(trackId: Long, quality: String = "HI_RES_LOSSLESS"): String? {
        return getRaw("/dash/?id=$trackId&quality=$quality")
    }

    /**
     * Get album information
     * @param albumId Album ID
     */
    suspend fun getAlbum(albumId: Long): JsonObject? {
        return get("/album/?id=$albumId")
    }

    /**
     * Get playlist information
     * @param playlistId Playlist UUID
     */
    suspend fun getPlaylist(playlistId: String): JsonObject? {
        return get("/playlist/?id=$playlistId")
    }

    /**
     * Get artist information
     * @param artistId Artist ID
     */
    suspend fun getArtist(artistId: Long): JsonObject? {
        return get("/artist/?id=$artistId")
    }

    /**
     * Get artist with full discography
     * @param artistId Artist ID
     */
    suspend fun getArtistFull(artistId: Long): JsonObject? {
        return get("/artist/?f=$artistId")
    }

    /**
     * Get track lyrics
     * @param trackId Track ID
     */
    suspend fun getLyrics(trackId: Long): JsonObject? {
        return get("/lyrics/?id=$trackId")
    }

    /**
     * Get cover art
     * @param trackId Track ID
     */
    suspend fun getCover(trackId: Long? = null, query: String? = null): JsonObject? {
        return when {
            trackId != null -> get("/cover/?id=$trackId")
            query != null -> get("/cover/?q=${URLEncoder.encode(query, "UTF-8")}")
            else -> null
        }
    }

    /**
     * Get home feed
     * @param country Country code (US, AU, etc.)
     */
    suspend fun getHomeFeed(country: String = "US"): JsonObject? {
        return get("/home/?country=$country")
    }

    /**
     * Get mix content
     * @param mixId Mix ID
     * @param country Country code
     */
    suspend fun getMix(mixId: String, country: String = "US"): JsonObject? {
        return get("/mix/?id=$mixId&country=$country")
    }

    /**
     * Search and get immediate playback
     * @param query Song search query
     * @param quality Audio quality
     */
    suspend fun searchAndPlay(query: String, quality: String = "LOSSLESS"): JsonObject? {
        return get("/song/?q=${URLEncoder.encode(query, "UTF-8")}&quality=$quality")
    }

    /**
     * Search for all types (tracks, artists, albums) with pagination
     * @param query Search query
     * @param limit Maximum number of results per type
     * @param offset Pagination offset
     */
    suspend fun searchAll(query: String, limit: Int = 25, offset: Int = 0): JsonObject? {
        return get("/search/?q=${URLEncoder.encode(query, "UTF-8")}&li=$limit&o=$offset")
    }

    /**
     * Make a GET request and parse JSON response
     */
    private suspend fun get(path: String): JsonObject? {
        return try {
            val url = apiUrl + path
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()

            if (!response.isSuccessful) {
                println("HiFi API Error: ${response.code} - $path")
                return null
            }

            val body = response.body?.string() ?: return null
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            println("HiFi API Exception: ${e.message}")
            null
        }
    }

    /**
     * Make a GET request and return raw response
     */
    private suspend fun getRaw(path: String): String? {
        return try {
            val url = apiUrl + path
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).await()

            if (!response.isSuccessful) {
                println("HiFi API Error: ${response.code} - $path")
                return null
            }

            response.body?.string()
        } catch (e: Exception) {
            println("HiFi API Exception: ${e.message}")
            null
        }
    }
}
