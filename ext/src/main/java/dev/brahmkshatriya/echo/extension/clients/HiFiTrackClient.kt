package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Streamable.Source.Companion.toSource
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.HiFiAPI
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject


class HiFiTrackClient ( private val hiFiAPI: HiFiAPI )   {
    private val qualityOptions = listOf("HI_RES_LOSSLESS", "LOSSLESS", "HIGH", "LOW")

    suspend fun loadStreamableMedia(streamable: Streamable): Streamable.Media {
        val quality = streamable.extras["QUALITY"] ?: "LOW"
        val trackId = streamable.id.removePrefix(placeholderPrefix).substringBefore(":").toLong()
        val trackJson = hiFiAPI.getTrack(trackId, quality) ?: throw Exception("Failed to load track from HiFi API")
        val sourceURL = trackJson.jsonArray[1].jsonObject["originalTrack"].toString()

        return sourceURL.toSource().toMedia()

    }


    suspend fun loadTrack(track: Track): Track {
        val streamables = qualityOptions.map { quality ->

            val qualityValue = when (quality) {
                "HI_RES_LOSSLESS" -> 9
                "LOSSLESS" -> 6
                "HIGH" -> 3
                "LOW" -> 0
                else -> 0
            }

            val qualityTitle = when (quality) {
                "HI_RES_LOSSLESS" -> "Hi-Res Lossless"
                "LOSSLESS" -> "Lossless"
                "HIGH" -> "High"
                "LOW" -> "Low"
                else -> "UNKNOWN"
            }

            Streamable.server(
                id = "$placeholderPrefix${track.id}:$quality",
                quality = qualityValue,
                title = qualityTitle,
                extras = mapOf(
                    "QUALITY" to quality
                )
            )
        }
        return track.copy(
            streamables = streamables
        )
    }
    private val placeholderPrefix = "hifi:"
}