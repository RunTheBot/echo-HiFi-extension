package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.clients.HiFiSearchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.brahmkshatriya.echo.common.models.Streamable.SourceType
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia

import okhttp3.OkHttpClient
import kotlin.collections.listOf

/**
 * Tidal HiFi Extension for Echo
 * Provides access to Tidal music streaming via the HiFi API
 * https://github.com/sachinsenal0x64/hifi
 */
class TidalExtension :
    ExtensionClient,
    TrackClient,
    AlbumClient,
    ArtistClient,
    PlaylistClient,
    HomeFeedClient,
    QuickSearchClient {

    private lateinit var settings: Settings
    private val hifiClient = HiFiClient()
    private val httpClient = OkHttpClient()
    private lateinit var searchClient: HiFiSearchClient

    override suspend fun getSettingItems(): List<Setting> {
        return emptyList()
    }

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    override suspend fun onInitialize() {
        // Initialize search client
        searchClient = HiFiSearchClient(this, hifiClient)
        
        // Initialize extension - verify API connectivity if needed
        val testResponse = hifiClient.searchTracks("test", limit = 1)
        if (testResponse == null) {
            println("Warning: Could not reach HiFi API during initialization")
        }
    }

    // ==================== TrackClient ====================

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        // If track already has streamables, return it as is
        if (track.streamables.isNotEmpty()) {
            return track
        }
        
        // Otherwise fetch from HiFi API
        val trackData = hifiClient.getTrack(track.id.toLongOrNull() ?: 0)?.let {
            HiFiMapper.parseTrack(it)
        }
        return trackData ?: track
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        try {
            // Get the track ID from streamable extras or ID
            val trackId = streamable.extras["trackId"]?.toLongOrNull() 
                ?: streamable.id.substringBefore("-").toLongOrNull()
                ?: throw Exception("Track ID not found in streamable: ${streamable.id}")
            
            // Determine quality level
            val quality = streamable.extras["quality"] ?: "LOSSLESS"
            
            println("HiFi: Loading stream for track $trackId with quality $quality")
            
            // Fetch the stream info from HiFi API
            val streamJson = hifiClient.getStream(trackId, quality)
                ?: throw Exception("Could not fetch stream from HiFi API for track: $trackId with quality: $quality")
            
            // Extract the stream URL from the JSON response
            // Expected format: {"mimeType":"audio/flac","codecs":"flac","encryptionType":"NONE","urls":["https://..."]}
            val urls = streamJson["urls"]?.jsonArray
            if (urls == null || urls.isEmpty()) {
                throw Exception("No stream URLs found in HiFi response for track: $trackId")
            }
            
            val streamUrl = urls.firstOrNull()?.jsonPrimitive?.content
                ?: throw Exception("Could not extract stream URL from HiFi response")
            
            println("HiFi: Got stream URL, length: ${streamUrl.length}")
            
            // Return as progressive media (direct stream URL)
            return streamUrl.toServerMedia(
                headers = mapOf(),
                type = Streamable.SourceType.Progressive,
                isVideo = false
            )
        } catch (e: Exception) {
            println("HiFi: Error in loadStreamableMedia: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? {
        // Track details/related content feed
        return null
    }

    // ==================== AlbumClient ====================

    override suspend fun loadAlbum(album: Album): Album {
        val albumData = hifiClient.getAlbum(album.id.toLongOrNull() ?: 0)?.let {
            HiFiMapper.parseAlbum(it)
        }
        return albumData ?: album
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? {
        val response = hifiClient.getAlbum(album.id.toLongOrNull() ?: 0) ?: return null
        val tracks = response["items"]?.jsonArray?.mapNotNull { item ->
            HiFiMapper.parseTrack(item.jsonObject)
        } ?: emptyList()

        return if (tracks.isNotEmpty()) {
            Feed(
                tabs = emptyList(),
                getPagedData = { Feed.Data(PagedData.Single { tracks }) }
            )
        } else {
            null
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? {
        return null
    }

    // ==================== ArtistClient ====================

    override suspend fun loadArtist(artist: Artist): Artist {
        val artistData = hifiClient.getArtist(artist.id.toLongOrNull() ?: 0)?.let {
            HiFiMapper.parseArtist(it)
        }
        return artistData ?: artist
    }

    override suspend fun loadFeed(artist: Artist): Feed<Shelf> {
        // Return empty feed for now
        return Feed(
            tabs = emptyList(),
            getPagedData = { Feed.Data(PagedData.Single { emptyList<Shelf>() }) }
        )
    }

    // ==================== PlaylistClient ====================

    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val playlistData = hifiClient.getPlaylist(playlist.id)?.let { response ->
            HiFiMapper.parsePlaylist(response)
        }
        return playlistData ?: playlist
    }

    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        val response = hifiClient.getPlaylist(playlist.id)
        val tracks = response?.get("items")?.jsonArray?.mapNotNull { item ->
            val trackObj = item.jsonObject["item"]?.jsonObject ?: return@mapNotNull null
            HiFiMapper.parseTrack(trackObj)
        } ?: emptyList()

        return Feed(
            tabs = emptyList(),
            getPagedData = { Feed.Data(PagedData.Single { tracks }) }
        )
    }

    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? {
        return null
    }

    // ==================== HomeFeedClient ====================

    override suspend fun loadHomeFeed(): Feed<Shelf> {
        // Return empty feed for now
        return Feed(
            tabs = emptyList(),
            getPagedData = { Feed.Data(PagedData.Single { emptyList<Shelf>() }) }
        )
    }

    // ==================== QuickSearchClient ====================

    /**
     * Quick search - returns list of search suggestions
     */
    override suspend fun quickSearch(query: String): List<QuickSearchItem.Query> {
        return searchClient.quickSearch(query)
    }

    /**
     * Load search feed for a given query
     */
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        return searchClient.loadSearchFeed(query)
    }

    /**
     * Delete quick search item (for history management)
     */
    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        searchClient.deleteQuickSearch(item)
    }
}