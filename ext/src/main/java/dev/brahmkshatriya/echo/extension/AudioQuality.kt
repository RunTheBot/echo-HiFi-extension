package dev.brahmkshatriya.echo.extension


enum class AudioQuality(val displayName: String, val isExclusive: Boolean = false) {

    LOW("Low"),
    HIGH("High"),
    LOSSLESS("Lossless"),
    HIRES_LOSSLESS("Hi-Res Lossless"),
    DOLBY_ATMOS("Dolby Atmos", true);

    companion object {
        fun fromString(quality: String?): AudioQuality {
            return entries.find { it.name == quality?.uppercase() } ?: LOW
        }

        fun getHighestFromList(qualities: List<AudioQuality>): AudioQuality {
            return qualities
                .maxByOrNull { it.ordinal } ?: LOW
        }

        fun getAllBelow(quality: AudioQuality): List<AudioQuality> {
            if (quality.isExclusive) return listOf(quality)
            return entries.filter { it.ordinal <= quality.ordinal }
        }

        fun getAllBelow(qualities: List<AudioQuality>): List<AudioQuality> {
            return getAllBelow(getHighestFromList(qualities))
        }
    }

    override fun toString(): String {
        // Honestly dumbass fucking workaround who tf made this API. ts is not using a enum internally
        return if (this == AudioQuality.HIRES_LOSSLESS) "HI_RES_LOSSLESS" else this.name
    }
}
