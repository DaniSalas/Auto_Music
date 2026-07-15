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

        // List of clients to try in order of reliability for streaming without complex JS transforms
        val clients = listOf(
            YouTubeClient.VISIONOS,
            YouTubeClient.ANDROID_VR,
            YouTubeClient.TVHTML5_EMBEDDED,
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.IOS,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.MWEB
        )

        for (client in clients) {
            Log.i(TAG, "Resolving $videoId with ${client.clientName}")
            val response = try {
                Innertube.player(videoId, client)
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Innertube.player (${client.clientName})", e)
                null
            }

            val status = response?.playabilityStatus?.status
            if (status != "OK") {
                Log.w(TAG, "Status $status for client ${client.clientName}: ${response?.playabilityStatus?.reason}")
                continue
            }

            val url = extractUrl(response)
            if (url != null) {
                // Testing URL validity with Ktor using the client's User-Agent
                if (validateUrl(url, client.userAgent)) {
                    Log.i(TAG, "Successfully resolved $videoId with ${client.clientName}")
                    val stream = ResolvedStream(url, client.userAgent)
                    cacheStream(videoId, stream, response?.streamingData?.expiresInSeconds)
                    return stream
                } else {
                    Log.w(TAG, "Validation failed for ${client.clientName} URL (might require n-transform or different headers)")
                }
            }
        }

        Log.e(TAG, "Failed to resolve any playable URL for $videoId")
        return null
    }

    private fun extractUrl(response: com.example.auto_music.data.remote.model.PlayerResponse?): String? {
        if (response == null) return null
        
        val streamingData = response.streamingData ?: return null
        val formats = (streamingData.adaptiveFormats ?: emptyList()) + (streamingData.formats ?: emptyList())
        if (formats.isEmpty()) return null

        // Filter for audio formats
        val audioFormats = formats.filter { it.isAudio }
        
        // Strategy: 
        // 1. Prefer formats with a direct 'url' (no signatureCipher)
        // 2. Prefer opus (itag 251, 250, 249) then mp4a (itag 140, 139)
        val preferredItags = listOf(251, 140, 250, 139, 249)
        
        // Try to find a preferred itag with a direct URL first
        var audioFormat = preferredItags.firstNotNullOfOrNull { itag ->
            audioFormats.find { it.itag == itag && it.url != null }
        }
        
        // Fallback to any itag with a direct URL
        if (audioFormat == null) {
            audioFormat = audioFormats.filter { it.url != null }.maxByOrNull { it.bitrate ?: 0 }
        }
        
        // If still null, we have to try signatureCipher (which might fail without JS transform)
        if (audioFormat == null) {
            audioFormat = preferredItags.firstNotNullOfOrNull { itag ->
                audioFormats.find { it.itag == itag }
            } ?: audioFormats.maxByOrNull { it.bitrate ?: 0 } ?: formats.firstOrNull()
        }
            
        var url = audioFormat?.url
        
        if (url == null && audioFormat?.signatureCipher != null) {
            Log.d(TAG, "Format ${audioFormat.itag} has signatureCipher, attempting basic decode")
            url = decodeSignatureCipher(audioFormat.signatureCipher)
        } else if (url == null && audioFormat?.cipher != null) {
            Log.d(TAG, "Format ${audioFormat.itag} has cipher, attempting basic decode")
            url = decodeSignatureCipher(audioFormat.cipher)
        }
        
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
                // NOTE: This basic decode often fails for modern YouTube because 's' requires 
                // a JavaScript-based transformation.
                val connector = if (baseUrl.contains("?")) "&" else "?"
                "$baseUrl$connector$sp=$signature"
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
                 // Web clients often need Referer/Origin
                 if (userAgent.contains("Mozilla") && !userAgent.contains("Android") && !userAgent.contains("iPhone")) {
                    header("Referer", "https://music.youtube.com/")
                    header("Origin", "https://music.youtube.com")
                 }
             }
             val isValid = response.status.value in 200..299 || response.status.value == 206
             if (!isValid) {
                 Log.w(TAG, "URL validation failed with status ${response.status.value}")
             }
             isValid
        } catch (e: Exception) {
            Log.e(TAG, "URL validation exception: ${e.message}")
            false
        }
    }

    private fun cacheStream(videoId: String, stream: ResolvedStream, expiresInSeconds: Int?) {
        val expiresIn = expiresInSeconds?.toLong() ?: 21600L
        cachedUrls[videoId] = stream to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
    }
}
