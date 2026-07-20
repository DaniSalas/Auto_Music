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

    suspend fun resolveStream(videoId: String): ResolvedStream? {
        cachedUrls[videoId]?.let { (stream, expiry) ->
            if (System.currentTimeMillis() < expiry) return stream
        }

        // Optimized order: TESTSUITE and TVHTML5 are most reliable for direct URLs
        val clients = listOf(
            YouTubeClient.ANDROID_TESTSUITE,
            YouTubeClient.TVHTML5_EMBEDDED,
            YouTubeClient.ANDROID_VR,
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.WEB_REMIX
        )

        for (client in clients) {
            val response = try { Innertube.player(videoId, client) } catch (e: Exception) { null }
            if (response?.playabilityStatus?.status != "OK") continue

            val url = extractUrl(response)
            if (url != null) {
                val stream = ResolvedStream(url, client.userAgent)
                cacheStream(videoId, stream, response.streamingData?.expiresInSeconds)
                return stream
            }
        }
        return null
    }

    private fun extractUrl(response: com.example.auto_music.data.remote.model.PlayerResponse?): String? {
        val streamingData = response?.streamingData ?: return null
        val formats = (streamingData.adaptiveFormats ?: emptyList()) + (streamingData.formats ?: emptyList())
        val audioFormats = formats.filter { it.isAudio }
        
        // Prefer direct URLs to avoid complex signature decryption
        val audioFormat = audioFormats.find { it.url != null } 
            ?: audioFormats.firstOrNull() 
            ?: formats.firstOrNull()
            
        var url = audioFormat?.url
        if (url == null && audioFormat?.signatureCipher != null) {
            url = decodeSignatureCipher(audioFormat.signatureCipher)
        }
        return url
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

    private fun cacheStream(videoId: String, stream: ResolvedStream, expiresInSeconds: Int?) {
        val expiresIn = expiresInSeconds?.toLong() ?: 21600L
        cachedUrls[videoId] = stream to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
    }
}
