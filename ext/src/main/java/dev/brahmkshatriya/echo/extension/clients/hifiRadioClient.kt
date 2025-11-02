package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track

class hifiRadioClient {
    fun loadTracks(radio: Radio): Feed<Track> {}
    fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {

    }
}