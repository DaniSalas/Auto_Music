package com.danielsalas.auto_music.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playerConfig: PlayerConfig? = null
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null
    )

    @Serializable
    data class StreamingData(
        val expiresInSeconds: Int? = null,
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format>? = null
    ) {
        @Serializable
        data class Format(
            val itag: Int? = null,
            val url: String? = null,
            val mimeType: String? = null,
            val bitrate: Int? = null,
            val width: Int? = null,
            val height: Int? = null,
            val contentLength: String? = null,
            val quality: String? = null,
            val audioQuality: String? = null,
            val signatureCipher: String? = null,
            val cipher: String? = null
        ) {
            val isAudio: Boolean get() = width == null
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val author: String? = null,
        val lengthSeconds: String? = null,
        val thumbnail: Thumbnails? = null
    ) {
        @Serializable
        data class Thumbnails(
            val thumbnails: List<Thumbnail>? = null
        ) {
            @Serializable
            data class Thumbnail(
                val url: String? = null,
                val width: Int? = null,
                val height: Int? = null
            )
        }
    }

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig? = null
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double? = null,
            val perceptualLoudnessDb: Double? = null
        )
    }
}
