package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.extension.HiFiClient
import dev.brahmkshatriya.echo.extension.HiFiMapper
import dev.brahmkshatriya.echo.extension.TidalExtension
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * HiFi Search Client
 * Handles all search-related operations for the HiFi/Tidal extension
 */
class HiFiSearchClient(
    private val tidalExtension: TidalExtension,
    private val hifiClient: HiFiClient
) {

    @Volatile
    private var cachedSearchResults: Pair<String, List<Shelf>>? = null

    /**
     * Get quick search suggestions (history + trending)
     */
    suspend fun quickSearch(query: String): List<QuickSearchItem.Query> {
        return if (query.isBlank()) {
            // Return trending/popular searches
            listOf(
                QuickSearchItem.Query("Popular", false),
                QuickSearchItem.Query("New Releases", false),
                QuickSearchItem.Query("Charts", false)
            )
        } else {
            // Return query suggestions based on partial matches
            try {
                val response = hifiClient.searchAll(query, limit = 10)
                if (response != null) {
                    // Extract unique queries from results - all items are in "items" array
                    val suggestions = mutableSetOf<String>()
                    
                    // Add the original query
                    suggestions.add(query)
                    
                    // Try to extract suggestions from response items (tracks and artists)
                    response["items"]?.jsonArray?.take(3)?.forEach { item ->
                        val itemObj = item.jsonObject
                        // Try track title first, then artist name
                        itemObj["title"]?.jsonPrimitive?.content?.let {
                            suggestions.add(it)
                        } ?: itemObj["name"]?.jsonPrimitive?.content?.let {
                            suggestions.add(it)
                        }
                    }
                    
                    suggestions.map { QuickSearchItem.Query(it, false) }
                } else {
                    listOf(QuickSearchItem.Query(query, false))
                }
            } catch (e: Exception) {
                println("Error in quickSearch: ${e.message}")
                listOf(QuickSearchItem.Query(query, false))
            }
        }
    }

    /**
     * Load search feed with tabs for different result types
     */
    suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        return if (query.isBlank()) {
            // Return empty feed for blank query
            Feed(emptyList()) { emptyList<Shelf>().toFeedData() }
        } else {
            // Load tabs and return Feed with tab-based filtering
            val tabs = loadSearchTabs(query)
            Feed(tabs) { tab ->
                performSearchByTab(query, tab)
            }
        }
    }

    /**
     * Perform search and return results filtered by tab
     */
    private suspend fun performSearchByTab(query: String, tab: Tab?): Feed.Data<Shelf> {
        try {
            // Return from cache if available
            if (tab?.id == "All") {
                cachedSearchResults?.takeIf { it.first == query }?.second?.let {
                    return it.toFeedData()
                }
            }

            // Not in cache for this tab, fetch and filter
            val response = hifiClient.searchAll(query, limit = 50)
            if (response != null) {
                // Build and cache all shelves on first access
                val allShelves = buildSearchShelves(response)
                cachedSearchResults = query to allShelves
                
                // Filter based on current tab
                val filteredShelves = when (tab?.id) {
                    "All", null -> allShelves
                    "Tracks" -> allShelves.filter { it is Shelf.Lists.Tracks }
                    "Artists" -> allShelves.filter { it is Shelf.Lists.Items && it.title == "Artists" }
                    "Albums" -> allShelves.filter { it is Shelf.Lists.Items && it.title == "Albums" }
                    "Playlists" -> allShelves.filter { it is Shelf.Lists.Items && it.title == "Playlists" }
                    else -> allShelves
                }
                
                return filteredShelves.toFeedData()
            }
            return emptyList<Shelf>().toFeedData()
        } catch (e: Exception) {
            println("Error performing search: ${e.message}")
            return emptyList<Shelf>().toFeedData()
        }
    }

    /**
     * Build shelves from search response grouped by type
     */
    private fun buildSearchShelves(response: kotlinx.serialization.json.JsonObject): List<Shelf> {
        val shelves = mutableListOf<Shelf>()

        try {
            // Parse and group results by type from the items array
            val tracks = mutableListOf<dev.brahmkshatriya.echo.common.models.Track>()
            val artists = mutableListOf<dev.brahmkshatriya.echo.common.models.Artist>()
            val albums = mutableListOf<dev.brahmkshatriya.echo.common.models.Album>()
            val playlists = mutableListOf<dev.brahmkshatriya.echo.common.models.Playlist>()

            // All items are mixed in the "items" array - need to discriminate by structure
            response["items"]?.jsonArray?.forEach { item ->
                val itemObj = item.jsonObject
                
                // Determine item type based on its structure
                when {
                    // Track: has duration, artists array, and album
                    itemObj["duration"] != null && itemObj["artists"] != null && itemObj["album"] != null -> {
                        HiFiMapper.parseTrack(itemObj)?.let { tracks.add(it) }
                    }
                    // Artist: has no duration/album, but has name and picture
                    itemObj["duration"] == null && itemObj["album"] == null && itemObj["name"] != null -> {
                        HiFiMapper.parseArtist(itemObj)?.let { artists.add(it) }
                    }
                    // Album: has title, cover, artists but no duration
                    itemObj["duration"] == null && itemObj["cover"] != null && itemObj["artists"] != null -> {
                        HiFiMapper.parseAlbum(itemObj)?.let { albums.add(it) }
                    }
                    // Playlist: has numberOfTracks or other playlist-specific fields
                    itemObj["numberOfTracks"] != null || (itemObj["type"]?.jsonPrimitive?.content == "PLAYLIST") -> {
                        HiFiMapper.parsePlaylist(itemObj)?.let { playlists.add(it) }
                    }
                    // Default: try track first, then others
                    else -> {
                        HiFiMapper.parseTrack(itemObj)?.let { tracks.add(it) }
                            ?: HiFiMapper.parseArtist(itemObj)?.let { artists.add(it) }
                            ?: HiFiMapper.parseAlbum(itemObj)?.let { albums.add(it) }
                            ?: HiFiMapper.parsePlaylist(itemObj)?.let { playlists.add(it) }
                    }
                }
            }

            // Build shelves for all types (filtering happens per-tab later)
            if (tracks.isNotEmpty()) {
                shelves.add(
                    Shelf.Lists.Tracks(
                        id = "search_tracks",
                        title = "Tracks",
                        list = tracks
                    )
                )
            }
            if (artists.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                shelves.add(
                    Shelf.Lists.Items(
                        id = "search_artists",
                        title = "Artists",
                        list = artists as List<dev.brahmkshatriya.echo.common.models.EchoMediaItem>
                    )
                )
            }
            if (albums.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                shelves.add(
                    Shelf.Lists.Items(
                        id = "search_albums",
                        title = "Albums",
                        list = albums as List<dev.brahmkshatriya.echo.common.models.EchoMediaItem>
                    )
                )
            }
            if (playlists.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                shelves.add(
                    Shelf.Lists.Items(
                        id = "search_playlists",
                        title = "Playlists",
                        list = playlists as List<dev.brahmkshatriya.echo.common.models.EchoMediaItem>
                    )
                )
            }
        } catch (e: Exception) {
            println("Error building search shelves: ${e.message}")
        }

        return shelves
    }

    /**
     * Get available search tabs - also fetches and caches initial results
     */
    private suspend fun loadSearchTabs(query: String): List<Tab> {
        try {
            // Do the initial fetch here to populate cache
            val response = hifiClient.searchAll(query, limit = 50)
            if (response != null) {
                // Build all shelves and cache them immediately
                val allShelves = buildSearchShelves(response)
                cachedSearchResults = query to allShelves
                
                // Generate tabs based on what result types are available
                val tabs = mutableListOf(Tab("All", "All"))
                
                if (allShelves.any { it is Shelf.Lists.Tracks }) {
                    tabs.add(Tab("Tracks", "Tracks"))
                }
                if (allShelves.any { it is Shelf.Lists.Items && it.title == "Artists" }) {
                    tabs.add(Tab("Artists", "Artists"))
                }
                if (allShelves.any { it is Shelf.Lists.Items && it.title == "Albums" }) {
                    tabs.add(Tab("Albums", "Albums"))
                }
                if (allShelves.any { it is Shelf.Lists.Items && it.title == "Playlists" }) {
                    tabs.add(Tab("Playlists", "Playlists"))
                }
                
                return tabs
            }
        } catch (e: Exception) {
            println("Error loading search tabs: ${e.message}")
        }
        return listOf(Tab("All", "All"))
    }

    /**
     * Delete quick search item (for history management)
     */
    suspend fun deleteQuickSearch(item: QuickSearchItem) {
        try {
            // For HiFi API, we don't have history to delete
            // This is a no-op but could be implemented if API supports it
            println("Delete quick search: ${item.title}")
        } catch (e: Exception) {
            println("Error deleting quick search: ${e.message}")
        }
    }
}
