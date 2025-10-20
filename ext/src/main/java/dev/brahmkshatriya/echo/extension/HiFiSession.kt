package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings

class HiFiSession(
    var settings: Settings? = null,
    ) {

    companion object {
        @Volatile
        private var instance: HiFiSession? = null

        fun getInstance(): HiFiSession {
            return instance ?: synchronized(this) {
                instance ?: HiFiSession().also { instance = it }
            }
        }
    }
}