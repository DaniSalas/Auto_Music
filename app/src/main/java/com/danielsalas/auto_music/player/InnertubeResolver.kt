package com.danielsalas.auto_music.player

import android.util.Log
import com.danielsalas.auto_music.data.remote.Innertube
import com.danielsalas.auto_music.data.remote.model.YouTubeClient
import com.danielsalas.auto_music.data.remote.model.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<ResolvedStream, Long>>()

    data class ResolvedStream(
        val url: String,
        val userAgent: String,
        val status: String = "OK"
    )

    suspend fun resolveStream(videoId: String): ResolvedStream? {
        cachedUrls[videoId]?.let { (stream, expiry) ->
            if (System.currentTimeMillis() < expiry) return stream
        }

        var lastError = "No attempts made"

        // Strategy 1: TV Embedded Bypass
        try {
            Log.d(TAG, "Attempting TV_EMBEDDED for $videoId")
            val response = Innertube.player(videoId, YouTubeClient.EMBEDDED)
            if (response?.playabilityStatus?.status == "OK") {
                val url = extractUrlFromResponse(response)
                if (url != null) {
                    return ResolvedStream(url, YouTubeClient.EMBEDDED.userAgent).also { cache(videoId, it, response) }
                } else {
                    lastError = "TV_EMBEDDED: No URL in response (Likely ciphered)"
                }
            } else {
                lastError = "TV_EMBEDDED: ${response?.playabilityStatus?.reason ?: "Unknown Error"}"
            }
        } catch (e: Exception) { lastError = "TV_EMBEDDED: ${e.message}" }

        // Strategy 2: Official ANDROID_MUSIC
        try {
            Log.d(TAG, "Attempting ANDROID_MUSIC for $videoId")
            val response = Innertube.player(videoId, YouTubeClient.ANDROID_MUSIC)
            if (response?.playabilityStatus?.status == "OK") {
                val url = extractUrlFromResponse(response)
                if (url != null) {
                    return ResolvedStream(url, YouTubeClient.ANDROID_MUSIC.userAgent).also { cache(videoId, it, response) }
                } else {
                    lastError = "ANDROID_MUSIC: No URL (Ciphered)"
                }
            } else {
                lastError = "ANDROID_MUSIC: ${response?.playabilityStatus?.reason ?: "Unavailable"}"
            }
        } catch (e: Exception) { lastError = "ANDROID_MUSIC: ${e.message}" }

        // Strategy 3: Invidious Proxy Pool
        val proxyResult = fetchFromInvidiousProxy(videoId)
        if (proxyResult is ProxyResult.Success) {
            return ResolvedStream(proxyResult.url, "Mozilla/5.0").also { 
                cachedUrls[videoId] = it to (System.currentTimeMillis() + 3600000L) 
            }
        } else if (proxyResult is ProxyResult.Failure) {
            lastError = "Proxy: ${proxyResult.message}"
        }

        Log.e(TAG, "❌ Resolution failed: $lastError")
        return ResolvedStream("", "", status = lastError)
    }

    private fun cache(videoId: String, stream: ResolvedStream, response: PlayerResponse) {
        val expiresIn = response.streamingData?.expiresInSeconds?.toLong() ?: 21600L
        cachedUrls[videoId] = stream to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
    }

    private fun extractUrlFromResponse(response: PlayerResponse): String? {
        val streamingData = response.streamingData ?: return null
        val formats = (streamingData.adaptiveFormats ?: emptyList()) + (streamingData.formats ?: emptyList())
        val bestFormat = formats.filter { it.isAudio }
            .sortedByDescending { if (it.itag == 140) 1000000 else it.bitrate ?: 0 }
            .find { it.url != null } ?: formats.find { it.url != null }
        return bestFormat?.url
    }

    sealed class ProxyResult {
        data class Success(val url: String) : ProxyResult()
        data class Failure(val message: String) : ProxyResult()
    }

    private suspend fun fetchFromInvidiousProxy(videoId: String): ProxyResult {
        val instances = listOf(
            "https://invidious.projectsegfau.lt",
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.privacyredirect.com",
            "https://yewtu.be"
        ).shuffled()
        
        var errorAcc = ""
        for (instance in instances) {
            try {
                val target = "$instance/latest_version?id=$videoId&itag=140&local=true"
                val response = Innertube.client.request(target) {
                    method = HttpMethod.Head
                    header(HttpHeaders.UserAgent, "Mozilla/5.0")
                }
                if (response.status.value < 400) return ProxyResult.Success(target)
                else errorAcc += "${instance.substringAfter("://")}: ${response.status.value}; "
            } catch (e: Exception) { 
                errorAcc += "${instance.substringAfter("://")}: ${e.message}; "
            }
        }
        return ProxyResult.Failure(if (errorAcc.isEmpty()) "All instances down" else errorAcc)
    }
}
