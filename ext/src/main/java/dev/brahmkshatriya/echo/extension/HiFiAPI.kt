package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.*
import kotlin.math.pow

const val RATE_LIMIT_ERROR_MESSAGE = "Rate limited"
const val DASH_MANIFEST_UNAVAILABLE_CODE = "DASH_UNAVAILABLE"

typealias AudioQuality = String


/**
 * Lossless API Client
 * Provides access to Tidal via the HiFi API (https://github.com/sachinsenal0x64/hifi)
 * Ported from TypeScript to Kotlin
 */
class HiFiAPI(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val metadataQueueMutex = Mutex()

    constructor() : this(OkHttpClient())
    


    private suspend fun buildUrl(path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        // Log if base URL is missing
        val baseUrl = HiFiSession.getInstance().settings?.getString("api_endpoint")

        if (baseUrl == null) {
            logMessage("Warning: API endpoint is not configured in settings.")
            throw Exception("API endpoint is not configured.")
        }

        return "$baseUrl$normalizedPath"
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

    private fun parseTrackLookup(data: JsonArray): TrackLookup {
        var apiTrack: APITrack? = null
        var info: TrackInfo? = null
        var originalTrackUrl: String? = null

        for (entry in data) {
            entry as JsonObject
            if (entry.containsKey("album") && entry.containsKey("artist") && entry.containsKey("duration")) {
                apiTrack = Json.decodeFromJsonElement<APITrack>(entry)
                continue
            }
            if (entry.containsKey("manifest")) {
                info = Json.decodeFromJsonElement<TrackInfo>(entry)
                continue
            }
            if (originalTrackUrl == null && entry.containsKey("OriginalTrackUrl")) {
                val candidate = entry["OriginalTrackUrl"]?.jsonPrimitive?.content
                if (candidate != null) {
                    originalTrackUrl = candidate
                }
            }
        }

        if (apiTrack == null || info == null) {
            throw Exception("Malformed track response")
        }

        return object : TrackLookup {
            override val apiTrack: APITrack = apiTrack
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
    suspend fun searchTracks(query: String, limit: Long?): SearchResponse<APITrack> {
        val response =
            fetch(buildUrl("/search/?s=${URLEncoder.encode(query, "UTF-8")}${limit?.let { "&li=$it" } ?: ""}"))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search tracks: ${response.code}")
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
    suspend fun searchArtists(query: String, ): SearchResponse<APIArtist> {
        val response = fetch(buildUrl("/search/?a=${URLEncoder.encode(query, "UTF-8")}"))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to search artists")
        val data = response.body.string()
        val json = (Json.parseToJsonElement(data) as JsonArray)[0] as JsonObject
        val normalized = normalizeSearchResponse<JsonObject>(json, "artists")
        val deserializedItems = normalized.items.map { item ->
            try {
                prepareArtist(Json.decodeFromJsonElement<APIArtist>(item as JsonElement))
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
    suspend fun searchAlbums(query: String): SearchResponse<APIAlbum> {
        val response = fetch(buildUrl("/search/?al=${URLEncoder.encode(query, "UTF-8")}"))
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
    suspend fun searchPlaylists(query: String): SearchResponse<APIPlaylist> {
        val response = fetch(buildUrl("/search/?p=${URLEncoder.encode(query, "UTF-8")}"))
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
        val url = buildUrl("/track/?id=$id&quality=$quality")
        var lastError: Exception? = null

        for (attempt in 1..3) {
            val response = fetch(url)
            ensureNotRateLimited(response)
            if (response.isSuccessful) {
                val data = response.body.string()
                val json = Json.parseToJsonElement(data) as JsonArray
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

            delay(2.0.pow(attempt.toDouble()).toLong() * 100L)
        }

        throw lastError ?: Exception("Failed to get track")
    }

    suspend fun getDashURL(trackId: Long, quality: AudioQuality = "HI_RES_LOSSLESS"): String{

        val url = buildUrl("/dash/?id=$trackId&quality=$quality")
        return url

    }

    suspend fun getDashManifest(
        trackId: Long,
        quality: AudioQuality = "HI_RES_LOSSLESS"
    ): DashManifestResult {
        val url = buildUrl("/dash/?id=$trackId&quality=$quality")
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
        val response = fetch(buildUrl("/album/?id=$id"))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to get album: ${response.code}")
        val data = response.body.string()
        val json = Json.parseToJsonElement(data)

        val entries = json as? JsonArray ?: listOf(json)

        var albumEntry: APIAlbum? = null
        var trackCollection: JsonObject? = null

        for (entry in entries) {
            if (entry !is JsonObject) continue

            if (entry.containsKey("title") && entry.containsKey("id") && entry.containsKey("cover")) {
                albumEntry = prepareAlbum(Json.decodeFromJsonElement<APIAlbum>(entry))
                continue
            }

            if (trackCollection == null && entry.containsKey("items") && entry["items"] is JsonArray) {
                trackCollection = entry
            }
        }

        if (albumEntry == null) {
            throw Exception("Album not found")
        }

        val tracks: MutableList<APITrack> = mutableListOf()
        val items = trackCollection?.get("items")?.jsonArray
        logMessage("Album track items: $items")
        if (items != null) {
            for (rawItem in items) {
                if (rawItem !is JsonObject) {
                    logMessage("Skipping non-object track item: $rawItem")
                    continue
                }

                var trackCandidate: APITrack? = null
                if (rawItem.containsKey("item") && rawItem["item"] is JsonObject) {
                    trackCandidate = rawItem["item"]?.let { Json.decodeFromJsonElement<APITrack>(it) }
                } else {
                    trackCandidate = Json.decodeFromJsonElement<APITrack>(rawItem)
                }

                val candidateWithAlbum = if (trackCandidate?.album == null) {
                    trackCandidate?.copy(album = albumEntry)
                } else {
                    trackCandidate
                }

                if (candidateWithAlbum != null) {
                    tracks.add(prepareTrack(candidateWithAlbum))
                }
            }
        }

        return Pair(albumEntry, tracks)
    }

    /**
     * Get artist details with albums and top tracks
     */
    suspend fun getArtist(id: Long): Triple<APIArtist, List<APIAlbum>, List<APITrack>> {
        val response = fetch(buildUrl("/artist/?f=$id"))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to get artist: ${response.code}")
        val data = response.body.string()
        val json = Json.parseToJsonElement(data)
        val entries = json as? JsonArray ?: listOf(json)

        val visited = mutableSetOf<Any>()
        val albumMap = mutableMapOf<Long, APIAlbum>()
        val trackMap = mutableMapOf<Long, APITrack>()
        var artist: APIArtist? = null

        fun isTrackLike(value: Any?): Boolean {
            if (value == null || value !is JsonObject) return false
            return (
                value.containsKey("id") && value["id"] is JsonPrimitive &&
                value.containsKey("title") && value["title"] is JsonPrimitive &&
                value.containsKey("duration") && value["duration"] is JsonPrimitive &&
                value.containsKey("trackNumber") &&
                value.containsKey("album") && value["album"] != null
            )
        }

        fun isAlbumLike(value: Any?): Boolean {
            if (value == null || value !is JsonObject) return false
            return (
                value.containsKey("id") && value["id"] is JsonPrimitive &&
                value.containsKey("title") && value["title"] is JsonPrimitive &&
                value.containsKey("cover")
            )
        }

        fun isArtistLike(value: Any?): Boolean {
            if (value == null || value !is JsonObject) return false
            return (
                value.containsKey("id") && value["id"] is JsonPrimitive &&
                value.containsKey("name") && value["name"] is JsonPrimitive &&
                value.containsKey("type") && value["type"] is JsonPrimitive &&
                (value.containsKey("artistRoles") || value.containsKey("artistTypes") || value.containsKey("url"))
            )
        }

        fun recordArtist(candidate: APIArtist?) {
            if (candidate == null) return
            val normalized = prepareArtist(candidate)
            if (artist == null || artist!!.id == normalized.id) {
                artist = normalized
            }
        }

        fun addAlbum(candidate: APIAlbum?) {
            if (candidate == null || candidate.id <= 0) return
            val normalized = prepareAlbum(candidate)
            albumMap[normalized.id] = normalized
            recordArtist(normalized.artist ?: normalized.artists.firstOrNull())
        }

        fun addTrack(candidate: APITrack?) {
            if (candidate == null || candidate.id <= 0) return
            val normalized = prepareTrack(candidate)
            if (normalized.album == null) {
                return
            }
            addAlbum(normalized.album)
            val knownAlbum = albumMap[normalized.album!!.id]
            if (knownAlbum != null) {
                val enrichedTrack = normalized.copy(album = knownAlbum)
                trackMap[enrichedTrack.id] = enrichedTrack
            } else {
                trackMap[normalized.id] = normalized
            }
            recordArtist(normalized.artist)
        }

        lateinit var scanValue: (Any?) -> Unit
        lateinit var parseModuleItems: (Any?) -> Unit

        scanValue = fun(value: Any?) {
            if (value == null) return

            if (value is JsonArray) {
                val trackCandidates = value.filter { isTrackLike(it) }
                if (trackCandidates.isNotEmpty()) {
                    for (candidate in trackCandidates) {
                        try {
                            val track = Json.decodeFromJsonElement<APITrack>(candidate)
                            addTrack(track)
                        } catch (e: Exception) {
                            // Skip malformed tracks
                        }
                    }
                    return
                }
                for (entry in value) {
                    scanValue(entry)
                }
                return
            }

            if (value !is JsonObject) {
                return
            }

            if (visited.contains(value)) {
                return
            }
            visited.add(value)

            if (isArtistLike(value)) {
                try {
                    val candidateArtist = Json.decodeFromJsonElement<APIArtist>(value)
                    recordArtist(candidateArtist)
                } catch (e: Exception) {
                    // Skip malformed artists
                }
            }

            if (value.containsKey("modules") && value["modules"] is JsonArray) {
                for (moduleEntry in value["modules"]!!.jsonArray) {
                    scanValue(moduleEntry)
                }
            }

            if (value.containsKey("pagedList") && value["pagedList"] is JsonObject) {
                val pagedList = value["pagedList"]!!.jsonObject
                if (pagedList.containsKey("items")) {
                    parseModuleItems(pagedList["items"])
                }
            }

            if (value.containsKey("items") && value["items"] is JsonArray) {
                parseModuleItems(value["items"])
            }

            if (value.containsKey("rows") && value["rows"] is JsonArray) {
                parseModuleItems(value["rows"])
            }

            if (value.containsKey("listItems") && value["listItems"] is JsonArray) {
                parseModuleItems(value["listItems"])
            }

            for (nested in value.values) {
                scanValue(nested)
            }
        }

        parseModuleItems = fun(items: Any?) {
            if (items == null || items !is JsonArray) return
            for (entry in items) {
                if (entry == null || entry !is JsonObject) continue

                val candidate = if (entry.containsKey("item")) entry["item"] else entry
                if (candidate != null && isAlbumLike(candidate)) {
                    try {
                        val album = Json.decodeFromJsonElement<APIAlbum>(candidate)
                        addAlbum(album)
                        val normalizedAlbum = albumMap[album.id]
                        recordArtist(normalizedAlbum?.artist ?: normalizedAlbum?.artists?.firstOrNull())
                    } catch (e: Exception) {
                        // Skip malformed albums
                    }
                    continue
                }
                if (candidate != null && isTrackLike(candidate)) {
                    try {
                        val track = Json.decodeFromJsonElement<APITrack>(candidate)
                        addTrack(track)
                    } catch (e: Exception) {
                        // Skip malformed tracks
                    }
                    continue
                }

                scanValue(candidate)
            }
        }

        for (entry in entries) {
            scanValue(entry)
        }

        if (artist == null) {
            val trackPrimaryArtist = trackMap.values
                .map { it.artist ?: it.artists.firstOrNull() }
                .firstOrNull()
            val albumPrimaryArtist = albumMap.values
                .map { it.artist ?: it.artists.firstOrNull() }
                .firstOrNull()
            recordArtist(trackPrimaryArtist ?: albumPrimaryArtist)
        }

        if (artist == null) {
            try {
                val fallbackResponse = fetch(buildUrl("/artist/?id=$id"))
                ensureNotRateLimited(fallbackResponse)
                if (fallbackResponse.isSuccessful) {
                    val fallbackData = fallbackResponse.body.string()
                    val fallbackJson = Json.parseToJsonElement(fallbackData)
                    val baseArtist = fallbackJson as? JsonObject ?: (fallbackJson as? JsonArray)?.firstOrNull()
                    if (baseArtist is JsonObject) {
                        recordArtist(Json.decodeFromJsonElement<APIArtist>(baseArtist))
                    }
                }
            } catch (fallbackError: Exception) {
                logMessage("Failed to fetch base artist details: $fallbackError")
            }
        }

        if (artist == null) {
            throw Exception("Artist not found")
        }

        val albums = albumMap.values.map { album ->
            if (album.artist == null) {
                album.copy(artist = artist)
            } else {
                album
            }
        }

        val albumById = albumMap.mapKeys { it.key }

        val tracks = trackMap.values.map { track ->
            val enrichedArtist = track.artist ?: artist
            val album = track.album
            val enrichedAlbum = album?.let {
                albumById[it.id] ?: (artist?.let { a -> if (it.artist == null) it.copy(artist = a) else it })
            } ?: album
            track.copy(
                artist = enrichedArtist,
                album = enrichedAlbum
            )
        }

        fun parseDate(value: String?): Long {
            if (value == null) return Long.MIN_VALUE
            return try {
                java.text.SimpleDateFormat("yyyy-MM-dd").parse(value)?.time ?: Long.MIN_VALUE
            } catch (e: Exception) {
                Long.MIN_VALUE
            }
        }

        val sortedAlbums = albums.sortedWith(compareBy<APIAlbum> { album ->
            val timeA = parseDate(album.releaseDate)
            if (timeA == Long.MIN_VALUE) 1 else 0
        }.thenBy { album ->
            val timeA = parseDate(album.releaseDate)
            -timeA
        }.thenBy { album ->
            -(album.popularity ?: 0)
        })

        val sortedTracks = tracks
            .sortedByDescending { it.popularity }
            .take(100)

        return Triple(
            artist,
            sortedAlbums,
            sortedTracks
        )
    }

    /**
     * Get playlist details
     */
    suspend fun getPlaylist(uuid: String): Pair<APIPlaylist, List<APITrack>> {
        val response = fetch(buildUrl("/playlist/?id=$uuid"))
        ensureNotRateLimited(response)
        if (!response.isSuccessful) throw Exception("Failed to get playlist: ${response.code}")
        val data = response.body.string()
        val json = Json.parseToJsonElement(data)

        val entries = json as? JsonArray ?: listOf(json)

        var playlistEntry: APIPlaylist? = null
        var trackCollection: JsonObject? = null

        for (entry in entries) {
            if (entry !is JsonObject) continue

            if (entry.containsKey("uuid") && entry.containsKey("title")) {
                playlistEntry = Json.decodeFromJsonElement<APIPlaylist>(entry)
                continue
            }

            if (trackCollection == null && entry.containsKey("items") && entry["items"] is JsonArray) {
                trackCollection = entry
            }
        }

        if (playlistEntry == null) {
            throw Exception("Playlist not found")
        }

        val tracks: MutableList<APITrack> = mutableListOf()
        val items = trackCollection?.get("items")?.jsonArray
        logMessage("Playlist track items: $items")
        if (items != null) {
            for (rawItem in items) {
                if (rawItem !is JsonObject) {
                    logMessage("Skipping non-object track item: $rawItem")
                    continue
                }

                var trackCandidate: APITrack? = null
                if (rawItem.containsKey("item") && rawItem["item"] is JsonObject) {
                    trackCandidate = rawItem["item"]?.let { Json.decodeFromJsonElement<APITrack>(it) }
                } else {
                    trackCandidate = Json.decodeFromJsonElement<APITrack>(rawItem)
                }

                if (trackCandidate != null) {
                    tracks.add(prepareTrack(trackCandidate))
                }
            }
        }

        return Pair(playlistEntry, tracks)
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class TrackInfo (
    val trackId: Long,
    val audioQuality: String,
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


