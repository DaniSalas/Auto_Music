package com.example.auto_music.player

import com.example.auto_music.data.remote.Innertube
import com.example.auto_music.data.remote.model.YouTubeClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import android.util.Log

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<ResolvedStream, Long>>()

    data class ResolvedStream(
        val url: String,
        val userAgent: String
    )

    suspend fun resolveStreamUrl(videoId: String): String? {
        return resolveStream(videoId)?.url
    }

    suspend fun resolveStream(videoId: String): ResolvedStream? {
        // Check cache first
        cachedUrls[videoId]?.let { (stream, expiry) ->
            if (System.currentTimeMillis() < expiry) {
                Log.d(TAG, "Using cached stream for $videoId")
                return stream
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
            if (validateUrl(webRemixUrl, YouTubeClient.WEB_REMIX.userAgent)) {
                Log.i(TAG, "WEB_REMIX URL is valid")
                val stream = ResolvedStream(webRemixUrl, YouTubeClient.WEB_REMIX.userAgent)
                cacheStream(videoId, stream, webRemixResponse?.streamingData?.expiresInSeconds)
                return stream
            } else {
                Log.w(TAG, "WEB_REMIX URL validation failed")
            }
        }

        // Try IOS (Often provides direct URLs and is very reliable)
        Log.i(TAG, "Fallback: Resolving $videoId with IOS")
        val iosResponse = try {
            Innertube.player(videoId, YouTubeClient.IOS)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (IOS)", e)
            null
        }
        val iosUrl = extractUrl(iosResponse)
        if (iosUrl != null) {
            val stream = ResolvedStream(iosUrl, YouTubeClient.IOS.userAgent)
            cacheStream(videoId, stream, iosResponse?.streamingData?.expiresInSeconds)
            return stream
        }

        // Try ANDROID_MUSIC
        Log.i(TAG, "Fallback: Resolving $videoId with ANDROID_MUSIC")
        val androidMusicResponse = try {
            Innertube.player(videoId, YouTubeClient.ANDROID_MUSIC)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (ANDROID_MUSIC)", e)
            null
        }
        val androidMusicUrl = extractUrl(androidMusicResponse)
        if (androidMusicUrl != null) {
            val stream = ResolvedStream(androidMusicUrl, YouTubeClient.ANDROID_MUSIC.userAgent)
            cacheStream(videoId, stream, androidMusicResponse?.streamingData?.expiresInSeconds)
            return stream
        }

        // Try MWEB
        Log.i(TAG, "Fallback: Resolving $videoId with MWEB")
        val mwebResponse = try {
            Innertube.player(videoId, YouTubeClient.MWEB)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (MWEB)", e)
            null
        }
        val mwebUrl = extractUrl(mwebResponse)
        if (mwebUrl != null) {
            val stream = ResolvedStream(mwebUrl, YouTubeClient.MWEB.userAgent)
            cacheStream(videoId, stream, mwebResponse?.streamingData?.expiresInSeconds)
            return stream
        }

        // Try ANDROID
        Log.i(TAG, "Fallback: Resolving $videoId with ANDROID")
        val androidResponse = try {
            Innertube.player(videoId, YouTubeClient.ANDROID)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (ANDROID)", e)
            null
        }
        val androidUrl = extractUrl(androidResponse)
        if (androidUrl != null) {
            val stream = ResolvedStream(androidUrl, YouTubeClient.ANDROID.userAgent)
            cacheStream(videoId, stream, androidResponse?.streamingData?.expiresInSeconds)
            return stream
        }

        // Try ANDROID_VR
        Log.i(TAG, "Fallback: Resolving $videoId with ANDROID_VR")
        val androidVrResponse = try {
            Innertube.player(videoId, YouTubeClient.ANDROID_VR)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (ANDROID_VR)", e)
            null
        }
        val androidVrUrl = extractUrl(androidVrResponse)
        if (androidVrUrl != null) {
            val stream = ResolvedStream(androidVrUrl, YouTubeClient.ANDROID_VR.userAgent)
            cacheStream(videoId, stream, androidVrResponse?.streamingData?.expiresInSeconds)
            return stream
        }

        // Fallback to VISIONOS (Uses IOS platform now)
        Log.i(TAG, "Fallback: Resolving $videoId with VISIONOS")
        val visionResponse = try {
            Innertube.player(videoId, YouTubeClient.VISIONOS)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Innertube.player (VISIONOS)", e)
            null
        }
        val visionUrl = extractUrl(visionResponse)
        if (visionUrl != null) {
            val stream = ResolvedStream(visionUrl, YouTubeClient.VISIONOS.userAgent)
            cacheStream(videoId, stream, visionResponse?.streamingData?.expiresInSeconds)
            return stream
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
            val stream = ResolvedStream(tvUrl, YouTubeClient.TVHTML5.userAgent)
            cacheStream(videoId, stream, tvResponse?.streamingData?.expiresInSeconds)
            return stream
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
            val stream = ResolvedStream(tvEmbedUrl, YouTubeClient.TVHTML5_EMBEDDED.userAgent)
            cacheStream(videoId, stream, tvEmbedResponse?.streamingData?.expiresInSeconds)
            return stream
        }

        Log.e(TAG, "Failed to resolve any playable URL for $videoId")
        return null
    }

    private fun extractUrl(response: com.example.auto_music.data.remote.model.PlayerResponse?): String? {
        if (response == null) {
            Log.e(TAG, "Response is null")
            return null
        }
        if (response.playabilityStatus != null && response.playabilityStatus.status != "OK") {
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
        
        // Find best audio format
        val audioFormats = formats.filter { it.isAudio }
        Log.i(TAG, "Found ${audioFormats.size} audio formats")

        val audioFormat = audioFormats.maxByOrNull { it.bitrate } ?: audioFormats.firstOrNull() ?: formats.firstOrNull()
            
        var url = audioFormat?.url
        
        if (url == null && audioFormat?.signatureCipher != null) {
            Log.i(TAG, "Found signatureCipher for format ${audioFormat.itag}. Attempting to extract URL.")
            url = decodeSignatureCipher(audioFormat.signatureCipher)
        } else if (url == null && audioFormat?.signatureCipher == null && audioFormat?.cipher != null) {
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

    private suspend fun validateUrl(url: String, userAgent: String): Boolean {
        return try {
             val response = Innertube.client.get(url) {
                 header("Range", "bytes=0-1")
                 header("User-Agent", userAgent)
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

    private fun cacheStream(videoId: String, stream: ResolvedStream, expiresInSeconds: Int?) {
        val expiresIn = expiresInSeconds?.toLong() ?: 21600L
        cachedUrls[videoId] = stream to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
    }
}
