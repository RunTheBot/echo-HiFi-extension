package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.extension.HiFiAPI
import dev.brahmkshatriya.echo.extension.HiFiMapper
import dev.brahmkshatriya.echo.extension.TidalExtension
import dev.brahmkshatriya.echo.extension.logMessage
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * HiFi Search Client
 * Handles all search-related operations for the HiFi/Tidal extension
 */
class HiFiSearchClient(
    private val tidalExtension: TidalExtension,
    private val hifiClient: HiFiAPI
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
                val tracksResponse = hifiClient.searchTracks(query, limit = 10)
                if (tracksResponse.items.isNotEmpty()) {
                    // Extract unique queries from results
                    val suggestions = mutableSetOf<String>()
                    
                    // Add the original query
                    suggestions.add(query)
                    
                    // Add track titles and artist names from results
                    tracksResponse.items.take(3).forEach { track ->
                        suggestions.add(track.title)
                        track.artists.firstOrNull()?.let { suggestions.add(it.name) }
                    }
                    
                    suggestions.map { QuickSearchItem.Query(it, false) }
                } else {
                    listOf(QuickSearchItem.Query(query, false))
                }
            } catch (e: Exception) {
                dev.brahmkshatriya.echo.extension.logMessage("Error in quickSearch: ${e.message}")
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
            val allShelves = performCombinedSearch(query)
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
        } catch (e: Exception) {
            logMessage("Error performing search: ${e.message}")
            throw error("Error performing search: ${e.message}")
        }
    }

    /**
     * Perform combined search across all types
     */
    private suspend fun performCombinedSearch(query: String): List<Shelf> {
        val shelves = mutableListOf<Shelf>()
        
        // Search for tracks
        try {
            val tracksResponse = hifiClient.searchTracks(query, limit = 50)
            if (tracksResponse.items.isNotEmpty()) {
                shelves.add(
                    Shelf.Lists.Tracks(
                        id = "search_tracks",
                        title = "Tracks",
                        list = tracksResponse.items.map { HiFiMapper.parseTrack(it) }
                    )
                )
            }
        } catch (e: Exception) {
            logMessage("Error searching tracks: ${e.message}")
        }

        // TODO: Enable artist search when debugged
        
//        // Search for artists
//        try {
//            val artistsResponse = hifiClient.searchArtists(query)
//            if (artistsResponse.items.isNotEmpty()) {
//                @Suppress("UNCHECKED_CAST")
//                shelves.add(
//                    Shelf.Lists.Items(
//                        id = "search_artists",
//                        title = "Artists",
//                        list = artistsResponse.items.map { HiFiMapper.parseArtist(it) } as List<dev.brahmkshatriya.echo.common.models.EchoMediaItem>
//                    )
//                )
//            }
//        } catch (e: Exception) {
//            logMessage("Error searching artists: ${e.message}")
//        }

        // Search for albums
        try {
            val albumsResponse = hifiClient.searchAlbums(query)
            if (albumsResponse.items.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                shelves.add(
                    Shelf.Lists.Items(
                        id = "search_albums",
                        title = "Albums",
                        list = albumsResponse.items.map { album ->
                            HiFiMapper.parseAlbum(album to emptyList()).first
                        } as List<dev.brahmkshatriya.echo.common.models.EchoMediaItem>
                    )
                )
            }
        } catch (e: Exception) {
            logMessage("Error searching albums: ${e.message}")
        }

        // Search for playlists
        try {
            val playlistsResponse = hifiClient.searchPlaylists(query)
            if (playlistsResponse.items.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                shelves.add(
                    Shelf.Lists.Items(
                        id = "search_playlists",
                        title = "Playlists",
                        list = playlistsResponse.items.map { HiFiMapper.parsePlaylist(it) } as List<dev.brahmkshatriya.echo.common.models.EchoMediaItem>
                    )
                )
            }
        } catch (e: Exception) {
            logMessage("Error searching playlists: ${e.message}")
        }
        
        return shelves
    }

    /**
     * Get available search tabs - also fetches and caches initial results
     */
    private suspend fun loadSearchTabs(query: String): List<Tab> {
        try {
            // Do the initial fetch here to populate cache
            val allShelves = performCombinedSearch(query)
            cachedSearchResults = query to allShelves
            
            // Generate tabs based on what result types are available
            val tabs = mutableListOf(Tab("All", "All"))
            
            if (allShelves.any { shelf -> shelf is Shelf.Lists.Tracks }) {
                tabs.add(Tab("Tracks", "Tracks"))
            }
            if (allShelves.any { shelf -> shelf is Shelf.Lists.Items && shelf.title == "Artists" }) {
                tabs.add(Tab("Artists", "Artists"))
            }
            if (allShelves.any { shelf -> shelf is Shelf.Lists.Items && shelf.title == "Albums" }) {
                tabs.add(Tab("Albums", "Albums"))
            }
            if (allShelves.any { shelf -> shelf is Shelf.Lists.Items && shelf.title == "Playlists" }) {
                tabs.add(Tab("Playlists", "Playlists"))
            }
            
            return tabs
        } catch (e: Exception) {
            logMessage("Error loading search tabs: ${e.message}")
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
            logMessage("Delete quick search: ${item.title}")
        } catch (e: Exception) {
            logMessage("Error deleting quick search: ${e.message}")
        }
    }
}
