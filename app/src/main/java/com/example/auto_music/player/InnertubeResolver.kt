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
        Log.i(TAG, "Resolving $videoId with WEB_REMIX")
        val webRemixResponse = try {
            Innertube.player(videoId, YouTubeClient.WEB_REMIX)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (WEB_REMIX)", e)
            null
        }
        val webRemixUrl = extractUrl(webRemixResponse)
        
        if (webRemixUrl != null) {
            Log.d(TAG, "Testing WEB_REMIX URL...")
            if (validateUrl(webRemixUrl)) {
                Log.i(TAG, "WEB_REMIX URL is valid")
                cacheUrl(videoId, webRemixUrl, webRemixResponse?.streamingData?.expiresInSeconds)
                return webRemixUrl
            } else {
                Log.w(TAG, "WEB_REMIX URL validation failed")
            }
        }

        // Fallback to VISIONOS (No throttle gate, usually direct URLs)
        Log.i(TAG, "Fallback: Resolving $videoId with VISIONOS")
        val visionResponse = try {
            Innertube.player(videoId, YouTubeClient.VISIONOS)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (VISIONOS)", e)
            null
        }
        val visionUrl = extractUrl(visionResponse)
        
        if (visionUrl != null) {
            cacheUrl(videoId, visionUrl, visionResponse?.streamingData?.expiresInSeconds)
            return visionUrl
        }

        // Last ditch effort: TVHTML5 (sometimes bypasses restrictions)
        Log.i(TAG, "Fallback: Resolving $videoId with TVHTML5")
        val tvResponse = try {
            Innertube.player(videoId, YouTubeClient.TVHTML5)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (TVHTML5)", e)
            null
        }
        val tvUrl = extractUrl(tvResponse)
        if (tvUrl != null) {
            cacheUrl(videoId, tvUrl, tvResponse?.streamingData?.expiresInSeconds)
            return tvUrl
        }

        // Fallback: TVHTML5_EMBEDDED (Bypasses age restrictions)
        Log.i(TAG, "Fallback: Resolving $videoId with TVHTML5_EMBEDDED")
        val tvEmbedResponse = try {
            Innertube.player(videoId, YouTubeClient.TVHTML5_EMBEDDED)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (TVHTML5_EMBEDDED)", e)
            null
        }
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

        Log.i(TAG, "Found ${formats.size} formats. Filtering for audio...")
        
        // Log all formats for debugging
        formats.forEach { format ->
            Log.d(TAG, "Format: itag=${format.itag}, mimeType=${format.mimeType}, hasUrl=${format.url != null}, hasCipher=${format.signatureCipher != null}")
        }
        
        // Find best audio format
        val audioFormats = formats.filter { it.isAudio }
        Log.i(TAG, "Found ${audioFormats.size} audio formats")

        val audioFormat = audioFormats.maxByOrNull { it.bitrate } ?: audioFormats.firstOrNull() ?: formats.firstOrNull()
            
        var url = audioFormat?.url
        
        if (url == null && audioFormat?.signatureCipher != null) {
            Log.i(TAG, "Found signatureCipher for format ${audioFormat.itag}. Attempting to extract URL.")
            url = decodeSignatureCipher(audioFormat.signatureCipher)
        } else if (url == null && audioFormat?.cipher != null) {
            Log.i(TAG, "Found cipher for format ${audioFormat.itag}. Attempting to extract URL.")
            url = decodeSignatureCipher(audioFormat.cipher)
        }
        
        Log.i(TAG, "Final extracted URL for itag ${audioFormat?.itag}: ${url?.take(100)}...")
        return url
    }

    private fun decodeSignatureCipher(cipher: String): String? {
        return try {
            val params = cipher.split("&").associate { 
                val parts = it.split("=")
                if (parts.size >= 2) {
                    java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
                } else {
                    "" to ""
                }
            }
            val baseUrl = params["url"]
            val signature = params["s"]
            val sp = params["sp"] ?: "sig"
            
            if (baseUrl != null && signature != null) {
                // For now, we just append the signature. 
                // Full deobfuscation might be needed if this fails.
                if (baseUrl.contains("?")) {
                    "$baseUrl&$sp=$signature"
                } else {
                    "$baseUrl?$sp=$signature"
                }
            } else {
                baseUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode signature cipher", e)
            null
        }
    }

    private suspend fun validateUrl(url: String): Boolean {
        return try {
             val response = Innertube.client.get(url) {
                 header("Range", "bytes=0-1")
                 header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                 header("Referer", "https://music.youtube.com/")
                 header("Origin", "https://music.youtube.com")
             }
             val isValid = response.status.value in 200..299 || response.status.value == 206
             if (!isValid) {
                 Log.w(TAG, "URL validation failed with status ${response.status.value} for URL: ${url.take(100)}...")
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
