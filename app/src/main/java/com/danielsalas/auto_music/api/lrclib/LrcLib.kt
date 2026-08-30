package com.danielsalas.auto_music.api.lrclib

import com.danielsalas.auto_music.api.lrclib.models.Track
import com.danielsalas.auto_music.api.lrclib.models.bestMatchingFor
import com.danielsalas.auto_music.data.remote.Innertube
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.net.URLEncoder

object LrcLib {
    private val client = Innertube.client
    private const val BASE_URL = "https://lrclib.net"

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int
    ): String? = runCatching {
        val response = client.get("$BASE_URL/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
        }
        if (response.status.value == 200) {
            val tracks = response.body<List<Track>>()
            tracks.bestMatchingFor(duration)?.let { it.syncedLyrics ?: it.plainLyrics }
        } else null
    }.getOrNull()
}
