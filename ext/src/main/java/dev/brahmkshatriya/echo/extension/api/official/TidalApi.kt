package dev.brahmkshatriya.echo.extension.api.official

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.extension.api.official.models.ArtistResponse
import dev.brahmkshatriya.echo.extension.api.official.models.Item
import dev.brahmkshatriya.echo.extension.api.official.models.Page
import dev.brahmkshatriya.echo.extension.api.official.models.PagesResponse
import dev.brahmkshatriya.echo.extension.api.official.models.SearchResponse
import dev.brahmkshatriya.echo.extension.api.official.models.TokenResponse
import dev.brahmkshatriya.echo.extension.api.official.models.UserResponse
import dev.brahmkshatriya.echo.extension.logMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

// Completely stolen from: https://github.com/brahmkshatriya/echo-tidal-extension/

class TidalApi {
    companion object {
        const val DEVICE_TYPE = "BROWSER"
        const val PLATFORM = "WEB"
        val JSON = Json { ignoreUnknownKeys = true }
    }

    var locale = "en_US"
    var countryCode = "CA"

    val version = "2025.10.29"
    val clientId = "txNoH4kkV41MfH25"
    val clientSecret = "dQjy0MinCEvxi1O4UmxvxWnDjt4cgHBPw8ll6nYBk98="

    val client = OkHttpClient()

    fun request(path: String, params: Map<String, String> = mapOf()): Request.Builder {
        val url = HttpUrl.Builder()
            .scheme("https").host("tidal.com")
            .addPathSegments(path)
            .addQueryParameter("deviceType", DEVICE_TYPE)
            .addQueryParameter("platform", PLATFORM)
            .addQueryParameter("locale", locale)
            .addQueryParameter("countryCode", countryCode)

        params.forEach { (k, v) ->
            url.addQueryParameter(k, v)
        }

        val request = Request.Builder()
            .url(url.build())
            .header("x-tidal-client-version", version)
            .header("x-tidal-token", clientId)

        accessToken?.let { request.header("Authorization", "Bearer $it") }
        return request
    }

    suspend fun call(request: Request): String {
        val response = client.newCall(request).await()
        return if (response.isSuccessful) response.body.string()
        else throw IllegalStateException("Call Failed: ${response.code} ${response.body.string()}")
    }

    var refreshToken: String? = null
    var accessToken: String? = null
    var expiresIn: Long? = null

    fun clear() {
        refreshToken = null
        accessToken = null
        expiresIn = null
    }

    val mutex = Mutex()

    suspend fun accessToken() = mutex.withLock {
        if (accessToken == null || isTokenExpired())
            accessToken = createAccessToken(refreshToken)
        accessToken!!
    }

    fun isTokenExpired(): Boolean {
        return expiresIn == null || System.currentTimeMillis() >= expiresIn!!
    }

    suspend fun createAccessToken(refreshToken: String?): String {
        val formBody = if (refreshToken != null) FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", "49YxDN9a2aFV6RTG")
            .add("scope", "r_usr w_usr")
        else FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)

        val res = call(
            Request.Builder()
                .url("https://auth.tidal.com/v1/oauth2/token")
                .post(formBody.build())
                .build()
        )
        val tokenResponse = JSON.decodeFromString<TokenResponse>(res)
        expiresIn = System.currentTimeMillis() + (tokenResponse.expiresIn!! * 1000)
        return tokenResponse.accessToken!!
    }

    suspend fun authReq(path: String, params: Map<String, String> = mapOf()) = request(path, params)
        .header("Authorization", "Bearer ${accessToken()}")

    suspend fun home(cursor: String? = null): Page {

        val req = if (refreshToken != null) authReq(
            "v2/home/feed/static",
            if (cursor != null) mapOf("cursor" to cursor) else mapOf()
        )
        else {
            logMessage("no token")
            throw Exception("no token")
        }/*request(
            "v2/home/feed/static",
            if (cursor != null) mapOf("cursor" to cursor) else mapOf()
        )*/
        val res = call(req.build())
        logMessage("Tidal Home Response: $res")
        return JSON.decodeFromString(res)
    }

    suspend fun search(
        query: String,
        types: String = "ARTISTS,ALBUMS,TRACKS,VIDEOS,PLAYLISTS,UPLOADS",
        offset: String = "0",
    ): SearchResponse {
        if (refreshToken == null) throw IllegalStateException("Refresh Token is required")
        val res = call(
            authReq(
                "v2/search", mapOf(
                    "query" to query,
                    "includeContributors" to "true",
                    "includeDidYouMean" to "false",
                    "includeUserPlaylists" to "true",
                    "offset" to offset,
                    "limit" to "50",
                    "supportsUserData" to "true",
                    "types" to types,
                )
            ).build()
        )
        return JSON.decodeFromString(res)
    }

    suspend fun track(id: String): Item {
        val res = call(authReq("v1/tracks/$id").build())
        return JSON.decodeFromString(res)
    }

    suspend fun users(id: String): UserResponse {
        val res = call(authReq("v1/users/$id").build())
        return JSON.decodeFromString(res)
    }

    suspend fun artist(id: String): ArtistResponse {
        val res = call(authReq("v2/artist/$id").build())
        return JSON.decodeFromString(res)
    }

    suspend fun pages(id: String): PagesResponse {
        val res = call(authReq("v1/pages/$id").build())
        return JSON.decodeFromString(res)
    }
}

