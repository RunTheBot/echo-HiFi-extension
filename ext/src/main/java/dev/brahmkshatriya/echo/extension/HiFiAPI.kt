package dev.brahmkshatriya.echo.extension

import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.*

const val API_BASE = "https://tidal.401658.xyz"
const val RATE_LIMIT_ERROR_MESSAGE = "Rate limited"
const val DASH_MANIFEST_UNAVAILABLE_CODE = "DASH_UNAVAILABLE"

typealias RegionOption = String
typealias AudioQuality = String


/**
 * Lossless API Client
 * Provides access to Tidal via the HiFi API (https://github.com/sachinsenal0x64/hifi)
 * Ported from TypeScript to Kotlin
 */
class HiFiAPI(
    var baseUrl: String = API_BASE,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val metadataQueueMutex = Mutex()

    constructor(baseUrl: String = API_BASE) : this(baseUrl, OkHttpClient())

    private fun resolveRegionalBase(region: RegionOption = "auto"): String {
        return try {
            val target = selectApiTargetForRegion(region)
            target?.baseUrl ?: baseUrl
        } catch (error: Exception) {
            println("HiFiTrackClient - Falling back to default API base URL for region selection $error")
            baseUrl
        }
    }

    private fun buildRegionalUrl(path: String, region: RegionOption = "auto"): String {
        val base = resolveRegionalBase(region).replace(Regex("/+$"), "")
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "$base$normalizedPath"
    }

    private fun <T> normalizeSearchResponse(
        data: JsonObject,
        key: String
    ): SearchResponse<T> {
        val section = findSearchSection<T>(data, key, mutableSetOf())
        return buildSearchResponse<T>(section)
    }



    private fun <T> buildSearchResponse(
        section: JsonObject?
    ): SearchResponse<T> {
        val items = section?.get("items")?.jsonArray ?: JsonArray(emptyList())
        val list = items.map { it as T }
        val limit = section?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: list.size
        val offset = section?.get("offset")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val total = section?.get("totalNumberOfItems")?.jsonPrimitive?.content?.toIntOrNull() ?: list.size

        return SearchResponse(
            items = list,
            limit = limit,
            offset = offset,
            totalNumberOfItems = total
        )
    }

    private fun <T> deserializeSearchItems(
        items: JsonArray,
        deserializer: (JsonElement) -> T?
    ): List<T> {
        return items.mapNotNull { deserializer(it) }
    }

    private fun <T> findSearchSection(
        source: Any?,
        key: String,
        visited: MutableSet<Any>
    ): JsonObject? {
        if (source == null) return null

        if (source is JsonArray) {
            for (entry in source) {
                val found = findSearchSection<T>(entry, key, visited)
                if (found != null) return found
            }
            return null
        }

        if (source !is JsonObject) return null

        if (visited.contains(source)) return null
        visited.add(source)

        if (source.containsKey("items") && source["items"] is JsonArray) {
            logMessage("Found items array while searching for key '$key'")
            return source
        }

        if (source.containsKey(key)) {
            val nested = source[key]
            val fromKey = findSearchSection<T>(nested, key, visited)
            if (fromKey != null) return fromKey
        }

        for (value in source.values) {
            val found = findSearchSection<T>(value, key, visited)
            if (found != null) return found
        }

        return null
    }

    private fun prepareTrack(APITrack: APITrack): APITrack {
        var normalized = APITrack
        // If artist is null but artists list exists, use first artist
        if (normalized.artist == null && normalized.artists.isNotEmpty()) {
            normalized = normalized.copy(artist = normalized.artists[0])
        }
        // and the opposite: if artists list is empty but artist exists, use artist as sole entry
        if (normalized.artists.isEmpty() && normalized.artist != null) {
            normalized = normalized.copy(artists = listOf(normalized.artist))
        }
        return normalized
    }

    private fun prepareAlbum(album: APIAlbum): APIAlbum {
        var normalized = album
        // If artist is null but artists list exists, use first artist
        if (normalized.artist == null && normalized.artists?.isNotEmpty() == true) {
            normalized = normalized.copy(artist = normalized.artists[0])
        }
        return normalized
    }

    private fun prepareArtist(artist: APIArtist): APIArtist {
        var normalized = artist
        // If type is null but artistTypes list exists, use first type
        if (normalized.type == null && normalized.artistTypes?.isNotEmpty() == true) {
            normalized = normalized.copy(type = normalized.artistTypes[0])
        }
        return normalized
    }

    private fun ensureNotRateLimited(response: okhttp3.Response) {
        if (response.code == 429) {
            throw Exception(RATE_LIMIT_ERROR_MESSAGE)
        }
    }

    private suspend fun delay(ms: Long) {
        delay(ms)
    }

    private fun parseTrackLookup(data: JsonObject): TrackLookup {
        val entries = listOf(data)
        val APITrack: APITrack? = null
        val info: TrackInfo? = null
        var originalTrackUrl: String? = null

        for (entry in entries) {
            if (entry.containsKey("album") && entry.containsKey("artist") && entry.containsKey("duration")) {
                // track = entry as Track
                continue
            }
            if (entry.containsKey("manifest")) {
                // info = entry as TrackInfo
                continue
            }
            if (originalTrackUrl == null && entry.containsKey("OriginalTrackUrl")) {
                val candidate = entry["OriginalTrackUrl"]?.jsonPrimitive?.content
                if (candidate != null) {
                    originalTrackUrl = candidate
                }
            }
        }

        if (APITrack == null || info == null) {
            throw Exception("Malformed track response")
        }

        return object : TrackLookup {
            override val apiTrack: APITrack = APITrack
            override val info: TrackInfo = info
            override val originalTrackUrl: String? = originalTrackUrl
        }
    }

    private fun extractStreamUrlFromManifest(manifest: String): String? {
        return try {
            val decoded = String(Base64.getDecoder().decode(manifest))
            try {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(decoded) as? JsonObject
                val urls = json?.get("urls")?.jsonArray
                if (urls != null && urls.isNotEmpty()) {
                    urls[0].jsonPrimitive.content
                } else null
            } catch (jsonError: Exception) {
                logMessage("Manifest JSON parse failed, falling back to pattern match $jsonError")
                val regex = Regex("""https?://[\w\-.~:?#@!$&'()*+,;=%/]+""")
                regex.find(decoded)?.value
            }
        } catch (error: Exception) {
            logMessage("Failed to decode manifest $error")
            null
        }
    }

    private fun isDashManifestPayload(payload: String, contentType: String?): Boolean {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return false
        if (contentType != null && contentType.lowercase().contains("xml")) {
            return trimmed.startsWith("<")
        }
        return Regex("^<\\?xml", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("^<MPD[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                Regex("^<\\w+", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
    }

    private fun <T> parseJsonSafely(payload: String): T? {
        return try {
            kotlinx.serialization.json.Json.parseToJsonElement(payload) as? T
        } catch (error: Exception) {
            logMessage("Failed to parse JSON payload from DASH response $error")
            null
        }
    }

    private fun createDashUnavailableError(message: String): Exception {
        return Exception(message)
    }

    private fun isXmlContentType(contentType: String?): Boolean {
        if (contentType == null) return false
        return Regex("(application|text)/(?:.+\\+)?xml", RegexOption.IGNORE_CASE).containsMatchIn(contentType) ||
                Regex("dash\\+xml|mpd", RegexOption.IGNORE_CASE).containsMatchIn(contentType)
    }

    private fun isJsonContentType(contentType: String?): Boolean {
        if (contentType == null) return false
        return Regex("json", RegexOption.IGNORE_CASE).containsMatchIn(contentType) ||
                Regex("application/vnd\\.tidal\\.bts", RegexOption.IGNORE_CASE).containsMatchIn(contentType)
    }

    private fun extractUrlsFromDashJsonPayload(payload: Any?): List<String> {
        if (payload !is JsonObject) return emptyList()
        val candidate = payload["urls"]
        if (candidate !is JsonArray) return emptyList()
        return candidate.mapNotNull { entry ->
            entry.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun isHiResQuality(quality: AudioQuality): Boolean {
        return quality.uppercase() == "HI_RES_LOSSLESS"
    }

    private suspend fun resolveHiResStreamFromDash(trackId: Long): String {
        val manifest = getDashManifest(trackId, "HI_RES_LOSSLESS")
        if (manifest.kind == "flac") {
            val url = manifest.urls.firstOrNull { it.isNotEmpty() }
            if (url != null) {
                return url
            }
            throw Exception("DASH manifest did not include any FLAC URLs.")
        }
        throw Exception("Hi-res DASH manifest does not expose a direct FLAC URL.")
    }

    private suspend fun fetch(url: String, options: okhttp3.Request.Builder.() -> Unit = {}): okhttp3.Response {
        val requestBuilder = Request.Builder().url(url)
        requestBuilder.options()
        val request = requestBuilder.build()
        return httpClient.newCall(request).execute()
    }

    /**
     * Search for tracks
     */
    suspend fun searchTracks(query: String, limit: Long?, region: RegionOption = "auto"): SearchResponse<APITrack> {
        val response =
            fetch(buildRegionalUrl("/search/?s=${URLEncoder.encode(query, "UTF-8")}${limit?.let { "&li=$it" } ?: ""}",
                region))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search tracks")
        val data = response.body.string()
        val json = Json.parseToJsonElement(data) as JsonObject
        val normalized = normalizeSearchResponse<JsonObject>(json, "tracks")
        val deserializedItems = normalized.items.map { item ->
            try {
                prepareTrack(Json.decodeFromJsonElement<APITrack>(item))
            } catch (e: Exception) {
                throw e
            }
        }
        logMessage("deserializedItems: $deserializedItems")
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    /**
     * Search for artists
     */
    suspend fun searchArtists(query: String, region: RegionOption = "auto"): SearchResponse<APIArtist> {
        val response = fetch(buildRegionalUrl("/search/?a=${URLEncoder.encode(query, "UTF-8")}", region))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search artists")
        val data = response.body.string()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(data) as JsonObject
        val normalized = normalizeSearchResponse<JsonObject>(json, "artists")
        val deserializedItems = normalized.items.map { item ->
            try {
                prepareArtist(kotlinx.serialization.json.Json.decodeFromJsonElement<APIArtist>(item as JsonElement))
            } catch (e: Exception) {
                throw e
            }
        }
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    /**
     * Search for albums
     */
    suspend fun searchAlbums(query: String, region: RegionOption = "auto"): SearchResponse<APIAlbum> {
        val response = fetch(buildRegionalUrl("/search/?al=${URLEncoder.encode(query, "UTF-8")}", region))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search albums")
        val data = response.body.string()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(data) as JsonObject
        val normalized = normalizeSearchResponse<JsonObject>(json, "albums")
        val deserializedItems = normalized.items.map { item ->
            try {
                prepareAlbum(Json.decodeFromJsonElement<APIAlbum>(item))
            } catch (e: Exception) {
                throw e
            }
        }
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    /**
     * Search for playlists
     */
    suspend fun searchPlaylists(query: String, region: RegionOption = "auto"): SearchResponse<APIPlaylist> {
        val response = fetch(buildRegionalUrl("/search/?p=${URLEncoder.encode(query, "UTF-8")}", region))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search playlists")
        val data = response.body.string()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(data) as JsonObject
        val normalized = normalizeSearchResponse<JsonObject>(json, "playlists")
        val deserializedItems = normalized.items.map { item ->
            try {
                Json.decodeFromJsonElement<APIPlaylist>(item)
            } catch (e: Exception) {
                throw e
            }
        }
        return SearchResponse(
            items = deserializedItems,
            limit = normalized.limit,
            offset = normalized.offset,
            totalNumberOfItems = normalized.totalNumberOfItems
        )
    }

    /**
     * Get track info and stream URL (with retries for quality fallback)
     */
    suspend fun getTrack(id: Long, quality: AudioQuality = "LOSSLESS"): TrackLookup {
        val url = "$baseUrl/track/?id=$id&quality=$quality"
        var lastError: Exception? = null

        for (attempt in 1..3) {
            val response = fetch(url)
            ensureNotRateLimited(response)
            if (response.isSuccessful) {
                val data = response.body.string()
                val json = Json.parseToJsonElement(data) as JsonObject
                return parseTrackLookup(json)
            }

            var detail: String? = null
            var userMessage: String? = null
            var subStatus: Int? = null
            try {
                val errorData = response.body?.string() ?: ""
                val errorJson = Json.parseToJsonElement(errorData) as? JsonObject
                detail = errorJson?.get("detail")?.jsonPrimitive?.content
                userMessage = errorJson?.get("userMessage")?.jsonPrimitive?.content
                if (detail == null) detail = userMessage
                subStatus = errorJson?.get("subStatus")?.jsonPrimitive?.content?.toIntOrNull()
            } catch (e: Exception) {
                // Ignore JSON parse errors
            }

            val isTokenRetry = response.code == 401 && subStatus == 11002
            val message = detail ?: "Failed to get track (status ${response.code})"
            lastError = Exception(if (isTokenRetry) (userMessage ?: message) else message)
            val shouldRetry = isTokenRetry ||
                    (detail != null && Regex("quality not found", RegexOption.IGNORE_CASE).containsMatchIn(detail)) ||
                    response.code >= 500

            if (attempt == 3 || !shouldRetry) {
                throw lastError
            }

            delay(200L * attempt)
        }

        throw lastError ?: Exception("Failed to get track")
    }

    suspend fun getDashManifest(
        trackId: Long,
        quality: AudioQuality = "HI_RES_LOSSLESS"
    ): DashManifestResult {
        val url = "$baseUrl/dash/?id=$trackId&quality=$quality"
        var lastError: Exception? = null

        for (attempt in 1..3) {
            val response = fetch(url)
            ensureNotRateLimited(response)
            val contentType = response.header("content-type")

            if (response.isSuccessful) {
                val payload = response.body.string()

                if (isXmlContentType(contentType) || isDashManifestPayload(payload, contentType)) {
                    return DashManifestResult(
                        kind = "dash",
                        manifest = payload,
                        contentType = contentType
                    )
                }

                if (isJsonContentType(contentType) || payload.trim().startsWith("{")) {
                    val parsed = parseJsonSafely<JsonObject>(payload)
                    if (parsed != null &&
                        parsed["detail"]?.jsonPrimitive?.content?.lowercase() == "not found"
                    ) {
                        lastError = createDashUnavailableError("Dash manifest not found for track")
                    } else {
                        val urls = extractUrlsFromDashJsonPayload(parsed)
                        return DashManifestResult(
                            kind = "flac",
                            manifestText = payload,
                            urls = urls,
                            contentType = contentType
                        )
                    }
                } else {
                    if (isDashManifestPayload(payload, contentType)) {
                        return DashManifestResult(
                            kind = "dash",
                            manifest = payload,
                            contentType = contentType
                        )
                    }
                    val parsed = parseJsonSafely<Any>(payload)
                    val urls = extractUrlsFromDashJsonPayload(parsed)
                    if (urls.isNotEmpty()) {
                        return DashManifestResult(
                            kind = "flac",
                            manifestText = payload,
                            urls = urls,
                            contentType = contentType
                        )
                    }
                    lastError = createDashUnavailableError("Received unexpected payload from dash endpoint.")
                }
            } else {
                if (response.code == 404) {
                    var detail: String? = null
                    try {
                        val errorPayload = response.body.string()
                        val errorJson = Json.parseToJsonElement(errorPayload) as? JsonObject
                        detail = errorJson?.get("detail")?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        // ignore
                    }
                    lastError = if (detail?.lowercase() == "not found") {
                        createDashUnavailableError("Dash manifest not found for track")
                    } else {
                        Exception("Failed to load dash manifest (status ${response.code})")
                    }
                } else {
                    lastError = Exception("Failed to load dash manifest (status ${response.code})")
                }
            }

            if (attempt < 3) {
                delay(200L * attempt)
            }
        }

        throw lastError ?: createDashUnavailableError("Unable to load dash manifest for track")
    }

    /**
     * Get album details with track listing
     */
    suspend fun getAlbum(id: Long): Pair<APIAlbum, List<APITrack>> {
        val response = fetch("$baseUrl/album/?id=$id")
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to get album")
        val data = response.body.string()
        val json = Json.parseToJsonElement(data)

        val entries = json as? JsonArray ?: listOf(json)

        val albumEntry: APIAlbum? = null
        var trackCollection: JsonObject? = null

        for (entry in entries) {
            if (entry !is JsonObject) continue

            if (entry.containsKey("title") && entry.containsKey("id") && entry.containsKey("cover")) {
                // albumEntry = prepareAlbum(entry as Album)
                continue
            }

            if (trackCollection == null && entry.containsKey("items") && entry["items"] is JsonArray) {
                trackCollection = entry
            }
        }

        if (albumEntry == null) {
            throw Exception("Album not found")
        }

        val APITracks: MutableList<APITrack> = mutableListOf()
        val items = trackCollection?.get("items")?.jsonArray
        if (items != null) {
            for (rawItem in items) {
                if (rawItem !is JsonObject) continue

                var APITrackCandidate: APITrack? = null
                if (rawItem.containsKey("item") && rawItem["item"] is JsonObject) {
                    APITrackCandidate = rawItem["item"] as APITrack
                } else {
                    APITrackCandidate = rawItem as APITrack
                }

                val candidateWithAlbum = if (APITrackCandidate.album == null) {
                    // trackCandidate.copy(album = albumEntry)
                    APITrackCandidate
                } else {
                    APITrackCandidate
                }
            }
        }

        return Pair(albumEntry, APITracks)
    }

    /**
     * Get stream URL for a track
     */
    suspend fun getStreamUrl(trackId: Long, quality: AudioQuality = "LOSSLESS"): String {
        var currentQuality = quality
        if (isHiResQuality(currentQuality)) {
            try {
                return resolveHiResStreamFromDash(trackId)
            } catch (error: Exception) {
                logMessage("Failed to resolve hi-res stream via DASH manifest $error")
                currentQuality = "LOSSLESS"
            }
        }

        var lastError: Exception? = null

        for (attempt in 1..3) {
            try {
                val lookup = getTrack(trackId, currentQuality)
                if (lookup.originalTrackUrl != null) {
                    return lookup.originalTrackUrl!!
                }

                val manifestUrl = extractStreamUrlFromManifest(lookup.info.manifest)
                if (manifestUrl != null) {
                    return manifestUrl
                }

                lastError = Exception("Unable to resolve stream URL for track")
            } catch (error: Exception) {
                lastError = error
            }

            if (attempt < 3) {
                delay(200L * attempt)
            }
        }

        throw lastError ?: Exception("Unable to resolve stream URL for track")
    }
}
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
    val replayGain: Float? = null,
    val peak: Float? = null,
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
    val audioQuality: String = "LOSSLESS",
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
    val tags: List<String>
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
    val audioQuality: String? = null,
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

interface TrackInfo {
    val trackId: Long
    val audioQuality: String
    val audioMode: String
    val manifest: String
    val manifestMimeType: String
    val assetPresentation: String
    val albumReplayGain: Long?
    val albumPeakAmplitude: Long?
    val trackReplayGain: Long?
    val trackPeakAmplitude: Long?
    val bitDepth: Long?
    val sampleRate: Long?
}

/**
 * Data class for API regional target
 */
data class ApiTarget(
    val baseUrl: String,
    val region: String
)

/**
 * Helper function to select API target for region
 */
fun selectApiTargetForRegion(region: RegionOption): ApiTarget? {
    return when (region.lowercase()) {
        "eu" -> ApiTarget("https://tidal.401658.xyz", "eu")
        "us" -> ApiTarget("https://tidal.401658.xyz", "us")
        "auto", "default" -> ApiTarget(API_BASE, "default")
        else -> null
    }
}

