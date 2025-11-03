package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.api.HiFiAPI.models.APITrack

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

    // Track which Atmos tracks have been matched with normal versions
    private val matchedAtmosIds = mutableSetOf<String>()

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
            matchedAtmosIds.add(atmosId)
            logMessage("Found Atmos match for '$trackTitle' | Artists: $sortedArtistIds | Duration: ${durationSeconds}s -> ID: $atmosId")
        }
        return atmosId
    }

    /**
     * Get all unmatched Atmos track IDs (those without corresponding normal versions)
     */
    fun getUnmatchedAtmosIds(): List<String> {
        val unmatched = atmosTracksByKey.values.filter { it !in matchedAtmosIds }
        unmatched.forEach { id ->
            logMessage("Atmos track without normal version found - adding as separate track: ID $id")
        }
        return unmatched
    }

    /**
     * Check if an Atmos track ID was matched with a normal version
     */
    fun isAtmosMatched(atmosId: String): Boolean {
        return atmosId in matchedAtmosIds
    }

    /**
     * Clear all registered Atmos tracks
     */
    fun clear() {
        logMessage("Clearing Atmos track registry (${atmosTracksByKey.size} tracks removed, ${matchedAtmosIds.size} were matched)")
        atmosTracksByKey.clear()
        matchedAtmosIds.clear()
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

