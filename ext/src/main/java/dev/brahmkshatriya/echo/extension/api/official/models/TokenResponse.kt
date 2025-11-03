package dev.brahmkshatriya.echo.extension.api.official.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    val scope: String? = null,
    val user: User? = null,
    val clientName: String? = null,

    @SerialName("token_type")
    val tokenType: String? = null,

    @SerialName("access_token")
    val accessToken: String? = null,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    @SerialName("expires_in")
    val expiresIn: Long? = null,

    @SerialName("user_id")
    val userID: Long? = null,
) {
    @Serializable
    data class User(
        val email: String? = null,
        val username: String? = null,
    )
}