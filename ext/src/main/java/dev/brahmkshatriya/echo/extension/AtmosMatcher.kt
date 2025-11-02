package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.api.models.APITrack

/**
 * Atmos matching system
 * Matches Atmos versions of tracks with their standard versions by:
 * - Exact title match
 * - Artist IDs (all artists must match)
 * - Duration (in seconds, already rounded from server)
 */
object AtmosMatcher {

    /**
     * Data class to hold Atmos track information
     */
    data class AtmosTrackKey(
        val title: String,
        val artistIds: List<String>, // Sorted for consistent comparison
        val durationSeconds: Long
    )

    // Store Atmos track IDs by their matching key
    private val atmosTracksByKey = mutableMapOf<AtmosTrackKey, String>()

    /**
     * Register an Atmos track for later matching
     * Stores the track with title, artist IDs, and duration as the key
     */
    fun registerAtmosTrack(apiTrack: APITrack) {
        val artistIds = apiTrack.artists.map { it.id.toString() }.sorted()
        val durationSeconds = apiTrack.duration // Already in seconds from server

        val key = AtmosTrackKey(
            title = apiTrack.title,
            artistIds = artistIds,
            durationSeconds = durationSeconds.toLong()
        )

        atmosTracksByKey[key] = apiTrack.id.toString()
        logMessage("Registered Atmos track: '${apiTrack.title}' | Artists: $artistIds | Duration: ${durationSeconds}s (ID: ${apiTrack.id})")
    }

    /**
     * Find matching Atmos track ID for a normal track
     * Matches by exact title, all artist IDs, and duration
     */
    fun findAtmosMatch(trackTitle: String, artistIds: List<String>, durationSeconds: Long): String? {
        val sortedArtistIds = artistIds.sorted()

        val key = AtmosTrackKey(
            title = trackTitle,
            artistIds = sortedArtistIds,
            durationSeconds = durationSeconds
        )

        val atmosId = atmosTracksByKey[key]
        if (atmosId != null) {
            logMessage("Found Atmos match for '$trackTitle' | Artists: $sortedArtistIds | Duration: ${durationSeconds}s -> ID: $atmosId")
        }
        return atmosId
    }

    /**
     * Clear all registered Atmos tracks
     */
    fun clear() {
        logMessage("Clearing Atmos track registry (${atmosTracksByKey.size} tracks removed)")
        atmosTracksByKey.clear()
    }

    /**
     * Get statistics about registered Atmos tracks
     */
    fun getStats(): String {
        return "Atmos tracks registered: ${atmosTracksByKey.size}"
    }

    /**
     * Get all registered Atmos track IDs
     */
    fun getAllAtmosTrackIds(): List<String> {
        return atmosTracksByKey.values.toList()
    }
}

