package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.extension.api.HiFiAPI
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.extension.AtmosMatcher
import dev.brahmkshatriya.echo.extension.AudioQuality
import dev.brahmkshatriya.echo.extension.TidalExtension
import dev.brahmkshatriya.echo.extension.logMessage
import kotlinx.serialization.json.Json


class HiFiTrackClient ( private val hiFiAPI: HiFiAPI )   {
    suspend fun loadStreamableMedia(streamable: Streamable): Streamable.Media {
        val quality = streamable.extras["QUALITY"]?.let { Json.decodeFromString<AudioQuality>(it) }
            ?: throw IllegalStateException("QUALITIES_AVAILABLE not found in track extras")
        val trackId = streamable.id.removePrefix(placeholderPrefix).substringBefore(":").toLong()
        logMessage("Loading streamable media for trackId: $trackId with quality: '$quality'")
        if (quality != AudioQuality.HIRES_LOSSLESS) {
//            logMessage("Loading streamable media Low/High/Lossless for trackId: $trackId with quality: $quality")
            val trackJson = hiFiAPI.getTrack(trackId, quality)

            val sourceURL = try {
                // Log the entire response to debug
                logMessage("Full Response: $trackJson")

                // Try to get OriginalTrackUrl from the array
                val url = trackJson.originalTrackUrl

                url ?: throw IllegalStateException("OriginalTrackUrl not found in any element. Response: $trackJson")
            } catch (e : Exception){
                throw Exception("Failed to extract source URL: ${e.message} sourceJSON: $trackJson trackId: $trackId quality: $quality")
            }
    //        val dashUrl = hiFiAPI.getDashStreamUrl(trackId)


            return try {
                val audioSource = Streamable.Source.Http(
                    request = NetworkRequest(url = sourceURL),
                    type = Streamable.SourceType.Progressive,
                    quality = quality.ordinal,
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
        } else {
//            logMessage("Loading streamable media Hi-Res Lossless for trackId: $trackId with quality: $quality")
            val dashManifestResult = hiFiAPI.getDashURL(trackId)

//            logMessage("Loading streamable media Hi-Res: $dashManifestResult")

            val dashSource = Streamable.Source.Http(
                request = NetworkRequest(url = dashManifestResult),
                type = Streamable.SourceType.DASH,
                quality = quality.ordinal,
                title = "DASH - $quality"
            )

            return Streamable.Media.Server(
                sources = listOf(dashSource),
                merged = false
            )
        }

    }


    suspend fun loadTrack(track: Track): Track {

        val qualitiesAvailable = track.extras["QUALITIES_AVAILABLE"]?.let {
            Json.decodeFromString<List<AudioQuality>>(
                it
            )
        } ?: throw IllegalStateException("QUALITIES_AVAILABLE not found in track extras")

        logMessage("Loading track with ID: ${track.id} and title: ${track.title} with available qualities: ${track.extras["QUALITIES_AVAILABLE"]}")
//        logMessage("Track Json: ${track.extras["API_TRACK_JSON"]}")

//        val streamables = qualityOptions.map { quality ->
//
//            val qualityValue = when (quality) {
//                "HI_RES_LOSSLESS" -> 9
//                "LOSSLESS" -> 6
//                "HIGH" -> 3
//                "LOW" -> 0
//                else -> 0
//            }
//
//            val qualityTitle = when (quality) {
//                "HI_RES_LOSSLESS" -> "Hi-Res Lossless"
//                "LOSSLESS" -> "Lossless"
//                "HIGH" -> "High"
//                "LOW" -> "Low"
//                else -> "UNKNOWN"
//            }
//
//            Streamable.server(
//                id = "$placeholderPrefix${track.id}:$quality",
//                quality = qualityValue,
//                title = qualityTitle,
//                extras = mapOf(
//                    "QUALITY" to quality
//                )
//            )
//        }

        val streamables = qualitiesAvailable
            .reversed()
            .map { quality ->
            Streamable.server(
                id = "$placeholderPrefix${track.id}:$quality",
                quality = quality.ordinal,
                title = quality.displayName,
                extras = mapOf(
                    "QUALITY" to Json.encodeToString(quality)
                )
            )
        }.toMutableList()

        // Check if the track has an atmos version available (only if Atmos matching is enabled)
        if (TidalExtension.isAtmosMatchingEnabled()) {
            val artistIds = track.artists.map { it.id }
            val durationSeconds = track.duration?.div(1000)!! // Convert from milliseconds to seconds

            AtmosMatcher.findAtmosMatch(track.title, artistIds, durationSeconds)?.let { atmosQualityID ->
                logMessage("Adding Atmos quality for track: '${track.title}' with Atmos Quality ID: $atmosQualityID")
                streamables.add(
                    0,
                    Streamable.server(
                        id = "$placeholderPrefix${atmosQualityID}:${AudioQuality.DOLBY_ATMOS}",
                        quality = AudioQuality.DOLBY_ATMOS.ordinal,
                        title = AudioQuality.DOLBY_ATMOS.displayName,
                        extras = mapOf(
                            "QUALITY" to Json.encodeToString(AudioQuality.DOLBY_ATMOS)
                        )
                    )
                )
            }
        }

        return track.copy(
            streamables = streamables
        )
    }
    private val placeholderPrefix = "hifi:"
}