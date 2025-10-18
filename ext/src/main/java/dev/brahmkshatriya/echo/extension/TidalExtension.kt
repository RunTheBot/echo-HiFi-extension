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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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

    override suspend fun getSettingItems(): List<Setting> {
        return emptyList()
    }

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    override suspend fun onInitialize() {
        // Initialize extension - verify API connectivity if needed
        val testResponse = hifiClient.searchTracks("test", limit = 1)
        if (testResponse == null) {
            println("Warning: Could not reach HiFi API during initialization")
        }
    }

    // ==================== TrackClient ====================

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        val trackData = hifiClient.getTrack(track.id.toLongOrNull() ?: 0)?.let {
            HiFiMapper.parseTrack(it)
        }
        return trackData ?: track
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        // For HiFi, we would need to fetch the actual stream URL
        // For now, return empty to indicate not supported
        throw Exception("Streamable media loading not yet implemented")
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
            Feed.Data(PagedData.Single { tracks }) as Feed<Track>
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
     * Quick search - returns list of recent/suggested queries
     */
    override suspend fun quickSearch(query: String): List<QuickSearchItem.Query> {
        if (query.isBlank()) {
            return emptyList()
        }

        return try {
            // For now, just return the query itself as a suggestion
            // In a real implementation, you might fetch from a history/suggestions API
            listOf(
                QuickSearchItem.Query(query = query, searched = false)
            )
        } catch (e: Exception) {
            println("Error in quickSearch: ${e.message}")
            emptyList()
        }
    }

    /**
     * Load search feed for a given query
     */
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        return Feed(
            tabs = emptyList(),
            getPagedData = { _: Tab? ->
                val searchResults = performSearchAsShelf(query)
                Feed.Data(PagedData.Single { searchResults })
            }
        )
    }

    /**
     * Delete quick search item (e.g., from history)
     */
    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        // For HiFi API, we don't have a way to delete search history
        // This could be implemented if the API supported it
        try {
            println("Delete quick search: ${item.title}")
            // Placeholder for future implementation
        } catch (e: Exception) {
            println("Error deleting quick search: ${e.message}")
        }
    }

    /**
     * Perform search and convert results to Shelf objects
     */
    private suspend fun performSearchAsShelf(query: String): List<Shelf> {
        if (query.isBlank()) {
            return emptyList()
        }

        return try {
            val response = hifiClient.searchAll(query, limit = 50)
            if (response != null) {
                val searchResults = HiFiMapper.parseSearchResults(response)
                
                // Convert QuickSearchItem results to Shelf objects for display
                val shelves = mutableListOf<Shelf>()
                
                // Group results by type
                val tracks = searchResults.filterIsInstance<QuickSearchItem.Media>()
                    .mapNotNull { if (it.media is Track) it.media else null }
                val artists = searchResults.filterIsInstance<QuickSearchItem.Media>()
                    .mapNotNull { if (it.media is Artist) it.media else null }
                val albums = searchResults.filterIsInstance<QuickSearchItem.Media>()
                    .mapNotNull { if (it.media is Album) it.media else null }
                val playlists = searchResults.filterIsInstance<QuickSearchItem.Media>()
                    .mapNotNull { if (it.media is Playlist) it.media else null }
                
                // Create shelves for each type
                if (tracks.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    shelves.add(
                        Shelf.Lists.Tracks(
                            id = "search_tracks",
                            title = "Tracks",
                            list = tracks as List<Track>
                        )
                    )
                }
                
                if (artists.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "search_artists",
                            title = "Artists",
                            list = artists
                        )
                    )
                }
                
                if (albums.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "search_albums",
                            title = "Albums",
                            list = albums
                        )
                    )
                }
                
                if (playlists.isNotEmpty()) {
                    shelves.add(
                        Shelf.Lists.Items(
                            id = "search_playlists",
                            title = "Playlists",
                            list = playlists
                        )
                    )
                }
                
                shelves
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error performing search: ${e.message}")
            emptyList()
        }
    }
}