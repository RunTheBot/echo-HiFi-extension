package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.api.HiFiAPI.HiFiAPI
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.HiFiMapper.buildImageHolder
import dev.brahmkshatriya.echo.extension.HiFiMapper.parseArtist
import dev.brahmkshatriya.echo.extension.HiFiMapper.parsePlaylist
import dev.brahmkshatriya.echo.extension.api.HiFiAPI.models.APIAlbum
import dev.brahmkshatriya.echo.extension.clients.HiFiSearchClient
import dev.brahmkshatriya.echo.extension.clients.HiFiTrackClient
import dev.brahmkshatriya.echo.extension.api.HiFiAPI.models.APIArtist
import dev.brahmkshatriya.echo.extension.api.HiFiAPI.models.APIPlaylist
import dev.brahmkshatriya.echo.extension.api.HiFiAPI.models.APITrack
import dev.brahmkshatriya.echo.extension.api.official.TidalApi
import dev.brahmkshatriya.echo.extension.api.official.TidalApi.Companion.JSON
import dev.brahmkshatriya.echo.extension.api.official.models.ImageSize
import dev.brahmkshatriya.echo.extension.api.official.models.TokenResponse
import dev.brahmkshatriya.echo.extension.clients.hifiRadioClient
import dev.brahmkshatriya.echo.common.helpers.Page


import okhttp3.OkHttpClient

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
    QuickSearchClient,
    RadioClient,
    LoginClient.WebView {

    private val session by lazy { HiFiSession.getInstance() }

    val officialAPI by lazy { TidalApi() }
    private lateinit var hiFiAPI: HiFiAPI
    private val httpClient = OkHttpClient()
    private lateinit var searchClient: HiFiSearchClient
    private var user: User? = null

    companion object {
        private const val API_ENDPOINT_KEY = "api_endpoint"
        private const val DEFAULT_ENDPOINT = "https://tidal.401658.xyz"
        private const val COUNTRY_CODE_KEY = "country_code"
        private const val DEFAULT_COUNTRY_CODE = "US"
        private const val ATMOS_MATCHING_KEY = "atmos_matching_enabled"
        private const val ATMOS_MATCHING_DEFAULT = false

        fun isAtmosMatchingEnabled(): Boolean {
            val value = HiFiSession.getInstance().settings?.getBoolean(ATMOS_MATCHING_KEY) ?: ATMOS_MATCHING_DEFAULT
            return value
        }
    }

    override suspend fun getSettingItems(): List<Setting> {
        return listOf(
            SettingTextInput(
                title = "API Endpoint",
                key = API_ENDPOINT_KEY,
                summary = "Select or enter the Tidal HiFi API endpoint URL",
                defaultValue = DEFAULT_ENDPOINT,
            ),
            SettingTextInput(
                title = "Country Code",
                key = COUNTRY_CODE_KEY,
                summary = "Enter ISO two-letter country code (e.g., US, GB, DE)",
                defaultValue = DEFAULT_COUNTRY_CODE,
            ),
            SettingSwitch(
                title = "Atmos Matching",
                key = ATMOS_MATCHING_KEY,
                summary = "Match Atmos tracks with normal versions to provide Atmos as a quality option",
                defaultValue = ATMOS_MATCHING_DEFAULT,
            )
        )
    }

    override fun setSettings(settings: Settings) {
        session.settings = settings
        logMessage("Atmos matching feature: ${if (isAtmosMatchingEnabled()) "ENABLED" else "DISABLED"}")
    }

    override suspend fun onExtensionSelected() {
    }


    override suspend fun onInitialize() {
        // Initialize search client
        hiFiAPI = HiFiAPI()
        searchClient = HiFiSearchClient(this, hiFiAPI)

        logMessage("Tidal HiFi Extension initialized")

        // Initialize extension - verify API connectivity if needed
//        hiFiAPI.searchTracks("test", limit = 1)
    }

    // ==================== TrackClient ====================

    private val hiFiTrackClient by lazy { HiFiTrackClient(hiFiAPI) }

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return hiFiTrackClient.loadTrack(track)
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        // For HiFi, we would need to fetch the actual stream URL
        // For now, return empty to indicate not supported
//        throw Exception("Streamable media loading not yet implemented")
        return hiFiTrackClient.loadStreamableMedia(streamable)
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? {
        // Track details/related content feed
        return null
    }

    // ==================== AlbumClient ====================

    private val albumCache = mutableMapOf<String, List<Track>>()

    override suspend fun loadAlbum(album: Album): Album {
        val albumData = HiFiMapper.parseAlbum(
            hiFiAPI.getAlbum(album.id.toLongOrNull() ?: 0)
        )
        albumCache[album.id] = albumData.second
        return albumData.first
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? {
        val tracks = albumCache[album.id] ?: return null

        return Feed(
            tabs = emptyList(),
            getPagedData = { Feed.Data(PagedData.Single { tracks }) }
        )
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? {
        return null
    }

    // ==================== ArtistClient ====================

    private val artistCache = mutableMapOf<String, Triple<APIArtist, List<APIAlbum>, List<APITrack>>>()


    // TODO: Implement artist loading
    override suspend fun loadArtist(artist: Artist): Artist {
        val artistData = hiFiAPI.getArtist(artist.id.toLongOrNull() ?: 0)
        artistCache[artist.id] = artistData
        return parseArtist(artistData.first)
    }

    override suspend fun loadFeed(artist: Artist): Feed<Shelf> {
        // Get cached artist data or return empty if not available
        val artistData = artistCache[artist.id]
            ?: return Feed(
                tabs = emptyList(),
                getPagedData = { Feed.Data(PagedData.Single { emptyList<Shelf>() }) }
            )

        val (apiArtist, albums, tracks) = artistData
        val shelves = mutableListOf<Shelf>()

        // Add top tracks shelf if available (limited to top 10)
        if (tracks.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Tracks(
                    id = "artist_top_tracks",
                    title = "Top Tracks",
                    list = tracks.take(12).map { HiFiMapper.parseTrack(it) }
                )
            )
        }

        // Add albums shelf if available
        if (albums.isNotEmpty()) {
            shelves.add(
                Shelf.Lists.Items(
                    id = "artist_albums",
                    title = "Albums",
                    list = albums.map { album ->
                        HiFiMapper.parseAlbum(album to emptyList()).first
                    } as List<dev.brahmkshatriya.echo.common.models.EchoMediaItem>
                )
            )
        }

        return Feed(
            tabs = emptyList(),
            getPagedData = { Feed.Data(PagedData.Single { shelves }) }
        )
    }

    // ==================== PlaylistClient ====================

    var playlistCache = mutableMapOf<String, Pair<APIPlaylist, List<APITrack>>>()

    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val playlistData = hiFiAPI.getPlaylist(playlist.id)
        playlistCache[playlist.id] = playlistData
        return parsePlaylist(playlistData.first)
    }

    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        val tracks = playlistCache[playlist.id]?.second ?: return Feed(
            tabs = emptyList(),
            getPagedData = { Feed.Data(PagedData.Single { emptyList<Track>() }) }
        )

        return Feed(
            tabs = emptyList(),
            getPagedData = { Feed.Data(PagedData.Single { tracks.map { HiFiMapper.parseTrack(it) } }) }
        )
    }

    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? {
        return null
    }

    // ==================== HomeFeedClient ====================

    override suspend fun loadHomeFeed() = PagedData.Continuous {
        officialAPI.home().run { Page(toShelves(ImageSize.MEDIUM), page?.cursor) }
    }.toFeed()
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

    // ==================== RadioClient ====================

    private val hifiRadioClient by lazy { hifiRadioClient() }

    override suspend fun loadTracks(radio: Radio): Feed<Track> = hifiRadioClient.loadTracks(radio)

    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio = hifiRadioClient.radio(item, context)

    override suspend fun loadRadio(radio: Radio): Radio  = radio

    // ==================== LoginClient.WebView ====================

    override val webViewRequest = object : WebViewRequest.Evaluate<List<User>> {
        override val javascriptToEvaluateOnPageStart = """
            function() {
                'use strict';
                const origFetch = window.fetch;
                window.fetch = async (...args) => {
                    const [resource] = args;
                    const url = typeof resource === 'string' ? resource : resource.url;
                    const res = await origFetch(...args);
                    if (url.includes('oauth2/token')) window.TOKEN = await res.clone().text();
                    return res;
                };
            }
        """.trimIndent()
        override val javascriptToEvaluate = "function() { return window.TOKEN; }"
        override suspend fun onStop(url: NetworkRequest, data: String?): List<User>? {
            val json = JSON.decodeFromString<TokenResponse>(data ?: "")
            val api = TidalApi()
            api.refreshToken = json.refreshToken
            val artistId = api.users(json.userID!!.toString()).artistID!!.toString()
            val artistItem = api.artist(artistId).item!!.data!!
            val user = User(
                id = json.userID.toString(),
                name = artistItem.name ?: "Tidal User",
                subtitle = artistItem.handle?.let { "@$it" } ?: json.user?.email,
                cover = artistItem.picture?.let { buildImageHolder(it) },
                extras = mapOf("refreshToken" to (json.refreshToken!!))
            )
            return listOf(user)
        }

        override val initialUrl = "https://tidal.com/".toGetRequest()
        override val stopUrlRegex = Regex(".*oauth2/me.*")
    }

    override fun setLoginUser(user: User?) {
        this.user = user
        officialAPI.clear()
        officialAPI.refreshToken = user?.extras?.get("refreshToken")
    }

    override suspend fun getCurrentUser() = user?.copy(extras = mapOf())

}