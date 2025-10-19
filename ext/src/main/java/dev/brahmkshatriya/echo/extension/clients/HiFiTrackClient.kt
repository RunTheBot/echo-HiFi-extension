package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.extension.HiFiAPI
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient


class HiFiTrackClient ( private val hiFiAPI: HiFiAPI )   {
    private val client by lazy { OkHttpClient() }

    private val qualityOptions = listOf("HI_RES_LOSSLESS", "LOSSLESS", "HIGH", "LOW")

    private fun getQualityValue(quality: String): Int {
        return when (quality) {
            "HI_RES_LOSSLESS" -> 9
            "LOSSLESS" -> 6
            "HIGH" -> 3
            "LOW" -> 0
            else -> 0
        }
    }

    suspend fun loadStreamableMedia(streamable: Streamable): Streamable.Media {
        val quality = streamable.extras["QUALITY"] ?: "LOW"
        val trackId = streamable.id.removePrefix(placeholderPrefix).substringBefore(":").toLong()
        val trackJson = hiFiAPI.getTrack(trackId, quality)

        val sourceURL = try {
            trackJson[0].jsonObject["OriginalTrackUrl"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("OriginalTrackUrl not found")
        } catch (e : Exception){
            throw Exception("Failed to extract source URL: ${e.message} sourceJSON: $trackJson trackId: $trackId quality: $quality")
        }
//        val dashUrl = hiFiAPI.getDashStreamUrl(trackId)


        return try {
            val audioSource = Streamable.Source.Http(
                request = NetworkRequest(url = sourceURL),
                type = Streamable.SourceType.Progressive,
                quality = getQualityValue(quality),
                title = "Audio - $quality"
            )

            Streamable.Media.Server(
                sources = listOf(audioSource),
                merged = false
            )
        } catch (e: Exception){
            throw Exception("Failed to parse streamable media: ${e.message} source: $sourceURL " +
                    "trackId: $trackId quality: $quality")
        }

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