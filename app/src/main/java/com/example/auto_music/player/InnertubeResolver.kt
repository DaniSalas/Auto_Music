package com.example.auto_music.player

import com.example.auto_music.data.remote.Innertube
import com.example.auto_music.data.remote.model.YouTubeClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import android.util.Log

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<String, Long>>()

    suspend fun resolveStreamUrl(videoId: String): String? {
        // Check cache first
        cachedUrls[videoId]?.let { (url, expiry) ->
            if (System.currentTimeMillis() < expiry) {
                Log.d(TAG, "Using cached URL for $videoId")
                return url
            }
        }

        // Try WEB_REMIX first (Main client)
        Log.d(TAG, "Resolving $videoId with WEB_REMIX")
        val webRemixResponse = Innertube.player(videoId, YouTubeClient.WEB_REMIX)
        val webRemixUrl = extractUrl(webRemixResponse)
        
        if (webRemixUrl != null && !webRemixUrl.contains("signature=")) {
            // If it's a direct URL (not needing deobfuscation) and works
            if (validateUrl(webRemixUrl)) {
                cacheUrl(videoId, webRemixUrl, webRemixResponse?.streamingData?.expiresInSeconds)
                return webRemixUrl
            }
        }

        // Fallback to VISIONOS (No throttle gate, usually direct URLs)
        Log.d(TAG, "Fallback: Resolving $videoId with VISIONOS")
        val visionResponse = Innertube.player(videoId, YouTubeClient.VISIONOS)
        val visionUrl = extractUrl(visionResponse)
        
        if (visionUrl != null) {
            cacheUrl(videoId, visionUrl, visionResponse?.streamingData?.expiresInSeconds)
            return visionUrl
        }

        // Last ditch effort: TVHTML5 (sometimes bypasses restrictions)
        Log.d(TAG, "Fallback: Resolving $videoId with TVHTML5")
        val tvResponse = Innertube.player(videoId, YouTubeClient.TVHTML5)
        val tvUrl = extractUrl(tvResponse)
        if (tvUrl != null) {
            cacheUrl(videoId, tvUrl, tvResponse?.streamingData?.expiresInSeconds)
            return tvUrl
        }

        // Fallback: TVHTML5_EMBEDDED (Bypasses age restrictions)
        Log.d(TAG, "Fallback: Resolving $videoId with TVHTML5_EMBEDDED")
        val tvEmbedResponse = Innertube.player(videoId, YouTubeClient.TVHTML5_EMBEDDED)
        val tvEmbedUrl = extractUrl(tvEmbedResponse)
        if (tvEmbedUrl != null) {
            cacheUrl(videoId, tvEmbedUrl, tvEmbedResponse?.streamingData?.expiresInSeconds)
            return tvEmbedUrl
        }

        Log.e(TAG, "Failed to resolve any playable URL for $videoId")
        return null
    }

    private fun extractUrl(response: com.example.auto_music.data.remote.model.PlayerResponse?): String? {
        if (response == null) {
            Log.e(TAG, "Response is null")
            return null
        }
        if (response.playabilityStatus.status != "OK") {
            Log.e(TAG, "Playability status not OK: ${response.playabilityStatus.status} - ${response.playabilityStatus.reason}")
            return null
        }
        
        val streamingData = response.streamingData
        if (streamingData == null) {
            Log.e(TAG, "StreamingData is null")
            return null
        }
        
        val formats = (streamingData.adaptiveFormats ?: emptyList()) + (streamingData.formats ?: emptyList())
        if (formats.isEmpty()) {
            Log.e(TAG, "No formats found in response")
            return null
        }
        
        // Find best audio format
        val audioFormat = formats.filter { it.isAudio }
            .maxByOrNull { it.bitrate } ?: formats.firstOrNull { it.isAudio } ?: formats.firstOrNull()
            
        val url = audioFormat?.url
        if (url == null && audioFormat?.signatureCipher != null) {
            Log.w(TAG, "Found signatureCipher but no direct URL for format ${audioFormat.itag}")
        }
        
        return url
    }

    private suspend fun validateUrl(url: String): Boolean {
        return try {
             val response = Innertube.client.get(url) {
                 header("Range", "bytes=0-1")
             }
             val isValid = response.status.value in 200..299 || response.status.value == 206
             if (!isValid) {
                 Log.w(TAG, "URL validation failed with status ${response.status.value}")
             }
             isValid
        } catch (e: Exception) {
            Log.e(TAG, "URL validation failed for $url", e)
            false
        }
    }

    private fun cacheUrl(videoId: String, url: String, expiresInSeconds: Int?) {
        val expiresIn = expiresInSeconds?.toLong() ?: 21600L
        cachedUrls[videoId] = url to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
    }
}
