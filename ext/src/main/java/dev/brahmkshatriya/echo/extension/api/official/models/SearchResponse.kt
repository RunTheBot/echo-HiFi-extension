package dev.brahmkshatriya.echo.extension.api.official.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SearchResponse (
    val tracks: ItemPage? = null,
    val uploads: ItemPage? = null,
    val albums: ItemPage? = null,
    val playlists: ItemPage? = null,
    val videos: ItemPage? = null,
    val artists: ItemPage? = null,
    val contentTypeFilters: List<String>? = null,
    val topHits: List<TopHit>? = null,

    @SerialName("queryId")
    val queryID: String? = null
) {

    @Serializable
    data class ItemPage(
        val items: List<Item>? = null,
        val totalNumberOfItems: Long? = null,
        val cacheable: Boolean? = null,
        val nextPage: JsonElement? = null,
    )

    @Serializable
    data class TopHit(
        val value: Item? = null,
        val type: String? = null,
    )

}
