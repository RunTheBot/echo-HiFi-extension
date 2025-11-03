package dev.brahmkshatriya.echo.extension.api.official.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PagesResponse (
    val selfLink: String? = null,
    val id: String? = null,
    val title: String? = null,
    val rows: List<Row>? = null
) {

    @Serializable
    data class Row(
        val modules: List<Module>? = null,
    )

    @Serializable
    data class Module(
        val id: String? = null,
        val type: String? = null,
        val width: Long? = null,
        val title: String? = null,
        val description: String? = null,
        val items: List<ModuleItem>? = null,
        val preTitle: String? = null,
        val showMore: ShowMore? = null,
        val pagedList: PagedList? = null,
        val lines: Long? = null,
        val supportsPaging: Boolean? = null,
        val quickPlay: Boolean? = null,
        val playlistStyle: String? = null,
        val layout: JsonElement? = null,
        val listFormat: JsonElement? = null,
        val header: JsonElement? = null,
        val showTableHeaders: Boolean? = null
    )

    @Serializable
    data class ModuleItem(
        val header: String? = null,
        val shortHeader: String? = null,
        val shortSubHeader: String? = null,

        @SerialName("imageId")
        val imageID: String? = null,

        val type: String? = null,

        @SerialName("artifactId")
        val artifactID: String? = null,

        val text: String? = null,
        val featured: Boolean? = null,
    )

    @Serializable
    data class PagedList(
        @SerialName("dataApiPath")
        val dataAPIPath: String? = null,

        val limit: Long? = null,
        val offset: Long? = null,
        val totalNumberOfItems: Long? = null,
        val items: List<Item>? = null,
    )

    @Serializable
    data class ShowMore(
        val title: String? = null,
        val apiPath: String? = null,
    )
}