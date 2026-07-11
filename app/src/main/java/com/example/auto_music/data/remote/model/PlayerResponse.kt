package com.example.auto_music.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playerConfig: PlayerConfig? = null
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String,
        val reason: String? = null
    )

    @Serializable
    data class StreamingData(
        val expiresInSeconds: String? = null,
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format>? = null
    ) {
        @Serializable
        data class Format(
            val itag: Int,
            val url: String? = null,
            val mimeType: String,
            val bitrate: Int,
            val width: Int? = null,
            val height: Int? = null,
            val contentLength: String? = null,
            val quality: String? = null,
            val audioQuality: String? = null,
            val signatureCipher: String? = null,
            val cipher: String? = null
        ) {
            val isAudio: Boolean get() = mimeType.startsWith("audio")
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String,
        val title: String? = null,
        val author: String? = null,
        val lengthSeconds: String? = null,
        val thumbnail: Thumbnails? = null
    ) {
        @Serializable
        data class Thumbnails(
            val thumbnails: List<Thumbnail>
        ) {
            @Serializable
            data class Thumbnail(
                val url: String,
                val width: Int,
                val height: Int
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
