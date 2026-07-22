package com.example.auto_music.player

import com.example.auto_music.data.remote.Innertube
import com.example.auto_music.data.remote.model.YouTubeClient
import android.util.Log

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<ResolvedStream, Long>>()

    data class ResolvedStream(
        val url: String,
        val userAgent: String
    )

    suspend fun resolveStream(videoId: String): ResolvedStream? {
        cachedUrls[videoId]?.let { (stream, expiry) ->
            if (System.currentTimeMillis() < expiry) return stream
        }

        // Ordered list of clients to try for direct audio URLs
        val clients = listOf(
            YouTubeClient.ANDROID_TESTSUITE,
            YouTubeClient.TVHTML5_EMBEDDED,
            YouTubeClient.ANDROID_VR,
            YouTubeClient.IOS
        )

        for (client in clients) {
            Log.d(TAG, "Resolving $videoId using ${client.clientName}...")
            val response = try { 
                Innertube.player(videoId, client) 
            } catch (e: Exception) { 
                null 
            }

            if (response?.playabilityStatus?.status == "OK") {
                val url = extractUrl(response)
                if (url != null) {
                    val stream = ResolvedStream(url, client.userAgent)
                    val expiresIn = response.streamingData?.expiresInSeconds?.toLong() ?: 21600L
                    cachedUrls[videoId] = stream to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
                    Log.i(TAG, "✅ SUCCESS: $videoId resolved with ${client.clientName}")
                    return stream
                }
            } else {
                Log.w(TAG, "❌ FAILED: ${client.clientName} for $videoId. Status: ${response?.playabilityStatus?.status}")
            }
        }
        return null
    }

    private fun extractUrl(response: com.example.auto_music.data.remote.model.PlayerResponse?): String? {
        val streamingData = response?.streamingData ?: return null
        val formats = (streamingData.adaptiveFormats ?: emptyList()) + (streamingData.formats ?: emptyList())
        
        val audioFormats = formats.filter { it.isAudio }
        
        // 1. First priority: itag 140 (M4A) with direct URL
        val m4aDirect = audioFormats.find { it.itag == 140 && it.url != null }
        if (m4aDirect?.url != null) return m4aDirect.url

        // 2. Second priority: Any audio format with direct URL
        val anyAudioDirect = audioFormats.find { it.url != null }
        if (anyAudioDirect?.url != null) return anyAudioDirect.url

        // 3. Third priority: Any direct URL
        val anyDirect = formats.find { it.url != null }
        if (anyDirect?.url != null) return anyDirect.url

        // 4. Fallback: Extract from signatureCipher (no deobfuscation yet)
        val cipherFormat = audioFormats.find { it.signatureCipher != null } ?: formats.find { it.signatureCipher != null }
        if (cipherFormat?.signatureCipher != null) {
            return decodeSignatureCipher(cipherFormat.signatureCipher)
        }
        
        return null
    }

    private fun decodeSignatureCipher(cipher: String): String? {
        return try {
            val params = cipher.split("&").associate { 
                val parts = it.split("=")
                if (parts.size >= 2) java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
                else "" to ""
            }
            val baseUrl = params["url"] ?: return null
            val signature = params["s"] ?: return baseUrl
            val sp = params["sp"] ?: "sig"
            val connector = if (baseUrl.contains("?")) "&" else "?"
            "$baseUrl$connector$sp=$signature"
        } catch (e: Exception) { null }
    }
}
