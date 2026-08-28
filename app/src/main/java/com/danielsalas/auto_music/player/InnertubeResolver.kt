package com.danielsalas.auto_music.player

import android.util.Log
import com.danielsalas.auto_music.data.remote.Innertube
import com.danielsalas.auto_music.data.remote.model.YouTubeClient
import com.danielsalas.auto_music.data.remote.model.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.*
import java.net.URLDecoder
import io.ktor.client.call.body
import kotlinx.coroutines.withTimeoutOrNull

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<ResolvedStream, Long>>()

    data class ResolvedStream(
        val url: String,
        val userAgent: String,
        val status: String = "OK",
        val diagnosticLog: String = ""
    )

    suspend fun resolveStream(videoId: String): ResolvedStream {
        if (cachedUrls.containsKey(videoId)) {
            val (stream, expiry) = cachedUrls[videoId]!!
            if (System.currentTimeMillis() < expiry) return stream
        }

        val logBuilder = StringBuilder()

        // Strategy 1: TV Identity
        try {
            Log.d(TAG, "Trying TV for $videoId")
            val response = withTimeoutOrNull(8000) { Innertube.player(videoId, YouTubeClient.EMBEDDED) }
            if (response?.playabilityStatus?.status == "OK") {
                val url = extractUrlFromResponse(response)
                if (url != null) {
                    if (verifyStream(url)) {
                        return ResolvedStream(url, YouTubeClient.EMBEDDED.userAgent).also { cache(videoId, it, response) }
                    } else logBuilder.append("TV:403; ")
                } else logBuilder.append("TV:Cipher; ")
            } else logBuilder.append("TV:${response?.playabilityStatus?.status ?: "Timeout"}; ")
        } catch (e: Exception) { logBuilder.append("TV:Err; ") }

        // Strategy 2: Android Music Identity
        try {
            Log.d(TAG, "Trying Music for $videoId")
            val response = withTimeoutOrNull(8000) { Innertube.player(videoId, YouTubeClient.ANDROID_MUSIC) }
            if (response?.playabilityStatus?.status == "OK") {
                val url = extractUrlFromResponse(response)
                if (url != null) {
                    if (verifyStream(url)) {
                        return ResolvedStream(url, YouTubeClient.ANDROID_MUSIC.userAgent).also { cache(videoId, it, response) }
                    } else logBuilder.append("Music:403; ")
                } else logBuilder.append("Music:Cipher; ")
            } else logBuilder.append("Music:${response?.playabilityStatus?.status ?: "Timeout"}; ")
        } catch (e: Exception) { logBuilder.append("Music:Err; ") }

        // Strategy 3: Proxy Pool
        val proxyResult = fetchFromProxyPool(videoId)
        if (proxyResult is ProxyResult.Success) {
            return ResolvedStream(proxyResult.url, "Mozilla/5.0")
        } else if (proxyResult is ProxyResult.Failure) {
            logBuilder.append("Proxy:${proxyResult.message}")
        }

        val finalLog = logBuilder.toString().ifEmpty { "No data returned from any engine" }
        Log.e(TAG, "❌ All strategies failed: $finalLog")
        return ResolvedStream("", "", status = "FAILED", diagnosticLog = finalLog)
    }

    private suspend fun verifyStream(url: String): Boolean {
        return try {
            withTimeoutOrNull(3000) {
                val response = Innertube.client.request(url) {
                    method = HttpMethod.Get
                    header(HttpHeaders.Range, "bytes=0-1")
                    header(HttpHeaders.UserAgent, "Mozilla/5.0")
                    header(HttpHeaders.Accept, "*/*")
                }
                response.status.value < 400
            } ?: false
        } catch (e: Exception) { false }
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

    private suspend fun fetchFromProxyPool(videoId: String): ProxyResult {
        val instances = listOf(
            "https://invidious.projectsegfau.lt",
            "https://yewtu.be",
            "https://invidious.nerdvpn.de",
            "https://invidious.privacyredirect.com",
            "https://inv.thepixora.com"
        ).shuffled()
        
        var errs = ""
        for (instance in instances) {
            try {
                val target = "$instance/latest_version?id=$videoId&itag=140&local=true"
                val response = withTimeoutOrNull(5000) {
                    Innertube.client.get(target) {
                        header(HttpHeaders.Range, "bytes=0-200")
                        header(HttpHeaders.UserAgent, "Mozilla/5.0")
                        header(HttpHeaders.Accept, "*/*")
                    }
                } ?: continue

                if (response.status.value < 400) {
                    val bytes: ByteArray = response.body()
                    val prefix = String(bytes)
                    if (!prefix.contains("<!DOCTYPE") && !prefix.contains("<html") && !prefix.contains("BotGuard")) {
                        return ProxyResult.Success(target)
                    } else errs += "Bot; "
                } else errs += "${response.status.value}; "
            } catch (e: Exception) { errs += "Err; " }
        }
        return ProxyResult.Failure(errs.ifEmpty { "EmptyPool" })
    }
}
