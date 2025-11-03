package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
package dev.brahmkshatriya.echo.extension.models
@Serializable
data class TokenResponse(
    @SerialName("userId")
    val userID: Long? = null,
    @SerialName("user")
    val user: UserInfo? = null,
    @SerialName("refreshToken")
    val refreshToken: String? = null,
    @SerialName("accessToken")
    val accessToken: String? = null,
    @SerialName("expiresIn")
    val expiresIn: Long? = null,
    @SerialName("tokenType")
    val tokenType: String? = null
)

@Serializable
data class UserInfo(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null,
    @SerialName("countryCode")
    val countryCode: String? = null
)

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
package dev.brahmkshatriya.echo.extension.models
@Serializable
data class TokenResponse(
    @SerialName("userId")
    val userID: Long? = null,
    @SerialName("user")
    val user: UserInfo? = null,
    @SerialName("refreshToken")
    val refreshToken: String? = null,
    @SerialName("accessToken")
    val accessToken: String? = null,
    @SerialName("expiresIn")
    val expiresIn: Long? = null,
    @SerialName("tokenType")
    val tokenType: String? = null
)

@Serializable
data class UserInfo(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null,
    @SerialName("countryCode")
    val countryCode: String? = null
)

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("userId")
    val userID: Long? = null,
    @SerialName("user")
    val user: UserInfo? = null,
    @SerialName("refreshToken")
    val refreshToken: String? = null,
    @SerialName("accessToken")
    val accessToken: String? = null,
    @SerialName("expiresIn")
    val expiresIn: Long? = null,
    @SerialName("tokenType")
    val tokenType: String? = null
)

@Serializable
data class UserInfo(
    @SerialName("id")
    val id: Long? = null,
    @SerialName("username")
    val username: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("firstName")
    val firstName: String? = null,
    @SerialName("lastName")
    val lastName: String? = null,
    @SerialName("countryCode")
    val countryCode: String? = null
)
