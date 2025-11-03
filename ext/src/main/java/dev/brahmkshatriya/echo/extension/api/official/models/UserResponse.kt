package dev.brahmkshatriya.echo.extension.api.official.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse (
    val id: Long? = null,
    val username: String? = null,
    val profileName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val countryCode: String? = null,
    val created: String? = null,
    val newsletter: Boolean? = null,
    val acceptedEULA: Boolean? = null,
    val dateOfBirth: String? = null,
    val facebookUid: Long? = null,
    val appleUid: String? = null,

    @SerialName("parentId")
    val parentID: Long? = null,

    val partner: Long? = null,

    @SerialName("tidalId")
    val tidalID: String? = null,

    val earlyAccessProgram: Boolean? = null,
    val yearOfBirth: Long? = null,
    val nostrPublicKey: String? = null,

    @SerialName("artistId")
    val artistID: Long? = null
)