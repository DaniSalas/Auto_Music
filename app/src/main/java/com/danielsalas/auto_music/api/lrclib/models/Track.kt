package com.danielsalas.auto_music.api.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

fun List<Track>.bestMatchingFor(duration: Int): Track? {
    if (isEmpty()) return null
    if (duration == -1) return firstOrNull { it.syncedLyrics != null } ?: firstOrNull()
    return filter { it.syncedLyrics != null || it.plainLyrics != null }
        .minByOrNull { abs(it.duration.toInt() - duration) }
        ?.takeIf { abs(it.duration.toInt() - duration) <= 5 }
}
