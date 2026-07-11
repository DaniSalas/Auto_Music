package com.example.auto_music.player

import com.example.auto_music.data.remote.Innertube
import android.util.Log

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<String, Long>>()

    suspend fun resolveStreamUrl(videoId: String): String? {
        // Check cache first (memory cache for the session)
        cachedUrls[videoId]?.let { (url, expiry) ->
            if (System.currentTimeMillis() < expiry) {
                Log.d(TAG, "Using cached URL for $videoId")
                return url
            }
        }

        Log.d(TAG, "Resolving stream URL for $videoId from Innertube")
        val response = Innertube.player(videoId) ?: return null
        
        if (response.playabilityStatus.status != "OK") {
            Log.e(TAG, "Playability status not OK: ${response.playabilityStatus.reason}")
            return null
        }

        val streamingData = response.streamingData ?: return null
        
        // Prefer adaptive formats (audio-only)
        // itag 140 is usually 128kbps m4a, 251 is opus
        val adaptiveFormats = streamingData.adaptiveFormats ?: emptyList()
        val audioFormat = adaptiveFormats.filter { it.isAudio }
            .maxByOrNull { it.bitrate } ?: streamingData.formats?.firstOrNull()

        if (audioFormat == null) {
            Log.e(TAG, "No suitable audio format found")
            return null
        }

        val url = audioFormat.url
        if (url != null) {
            // Cache for the duration specified by YouTube (usually 6 hours)
            val expiresIn = streamingData.expiresInSeconds?.toLongOrNull() ?: 21600L
            cachedUrls[videoId] = url to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
            return url
        }

        // If it's a signatureCipher, we'd need deobfuscation logic.
        // YouTube Music usually provides direct URLs for WEB_REMIX if poToken/visitorData is right.
        Log.w(TAG, "Stream URL is null for $videoId (likely signatureCipher needed)")
        return null
    }
}
