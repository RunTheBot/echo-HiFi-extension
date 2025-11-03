package dev.brahmkshatriya.echo.extension.api.official.models

import dev.brahmkshatriya.echo.extension.api.official.models.Page.Items
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
data class ArtistResponse (
    val type: String? = null,
    val header: Header? = null,
    val item: Page.PageItem? = null,
    val items: List<Items>? = null
) {

    @Serializable
    data class Header(
        val type: String? = null,

        @SerialName("contributionLinkUrl")
        val contributionLinkURL: JsonElement? = null,

        val contributions: JsonElement? = null,
        val playableContent: PlayableContent? = null,
        val biography: Biography? = null,
        val mainLink: JsonElement? = null,
        val followersAmount: Long? = null,
        val followingsAmount: Long? = null,
    )

    @Serializable
    data class Biography(
        val text: String? = null,
        val source: String? = null,
        val moderationStatus: String? = null,
    )

    @Serializable
    data class PlayableContent(
        val play: Boolean? = null,
        val shuffle: Boolean? = null,
        val items: JsonArray? = null,
        val url: String? = null,
    )
}