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
        val diagnosticLog: String = "",
        val mimeType: String = "audio/mp4"
    )

    suspend fun resolveStream(videoId: String): ResolvedStream {
        if (cachedUrls.containsKey(videoId)) {
            val (stream, expiry) = cachedUrls[videoId]!!
            if (System.currentTimeMillis() < expiry) return stream
        }

        val logBuilder = StringBuilder()

        // LEVEL 1: High-Performance Conversion API (The "Master Key")
        // These APIs handle bot detection server-side.
        try {
            Log.d(TAG, "Attempting Level 1: Conversion API for $videoId")
            val convUrl = fetchFromConversionApi(videoId)
            if (convUrl != null) {
                Log.i(TAG, "✅ Level 1 Success!")
                return ResolvedStream(convUrl, "Mozilla/5.0", status = "Source: Conversion API")
            } else logBuilder.append("Conv:Fail; ")
        } catch (e: Exception) { logBuilder.append("Conv:Err; ") }

        // LEVEL 2: SoundCloud Fallback (Bypass YouTube entirely)
        // If it's a popular song like U2, it's definitely on SoundCloud.
        try {
            Log.d(TAG, "Attempting Level 2: SoundCloud Fallback for $videoId")
            // We search for the same ID or title on SC (simplified here)
            val scUrl = fetchFromSoundCloudFallback(videoId)
            if (scUrl != null) {
                Log.i(TAG, "✅ Level 2 Success!")
                return ResolvedStream(scUrl, "Mozilla/5.0", status = "Source: SoundCloud")
            } else logBuilder.append("SC:NoMatch; ")
        } catch (e: Exception) { logBuilder.append("SC:Err; ") }

        // LEVEL 3: Verified Invidious Tunnels (itag 251/140)
        val proxyResult = fetchFromProxyPool(videoId)
        if (proxyResult is ProxyResult.Success) {
            return ResolvedStream(proxyResult.url, "Mozilla/5.0", status = "Source: Proxy Tunnel")
        } else if (proxyResult is ProxyResult.Failure) {
            logBuilder.append("Proxy:${proxyResult.message}")
        }

        val finalLog = logBuilder.toString().ifEmpty { "All engines failed" }
        Log.e(TAG, "❌ ALL STRATEGIES FAILED: $finalLog")
        return ResolvedStream("", "", status = "FAILED", diagnosticLog = finalLog)
    }

    private suspend fun fetchFromConversionApi(videoId: String): String? {
        val engines = listOf(
            "https://yt-api.savetube.me/video/info/$videoId",
            "https://api.cobalt.tools/api/json" // V10
        )
        
        for (engine in engines) {
            try {
                if (engine.contains("savetube")) {
                    val res = Innertube.client.get(engine)
                    if (res.status.value == 200) {
                        val json = Innertube.json.parseToJsonElement(res.bodyAsText()).jsonObject
                        val stream = json["url"]?.jsonPrimitive?.content
                        if (!stream.isNullOrBlank()) return stream
                    }
                } else if (engine.contains("cobalt")) {
                    val res = Innertube.client.post(engine) {
                        header(HttpHeaders.ContentType, "application/json")
                        setBody(buildJsonObject { 
                            put("url", "https://www.youtube.com/watch?v=$videoId")
                            put("downloadMode", "audio")
                        })
                    }
                    if (res.status.value == 200) {
                        val json = Innertube.json.parseToJsonElement(res.bodyAsText()).jsonObject
                        return json["url"]?.jsonPrimitive?.content
                    }
                }
            } catch (e: Exception) { }
        }
        return null
    }

    private suspend fun fetchFromSoundCloudFallback(videoId: String): String? {
        // As a fallback, we can use a piped instance to search specifically for SoundCloud streams
        // or a public SC-to-MP3 API. For now, we try another Piped node that is known for non-YT sources.
        return null // Placeholder for SC logic
    }

    private suspend fun fetchFromProxyPool(videoId: String): ProxyResult {
        val instances = listOf(
            "https://invidious.jing.rocks",
            "https://inv.nadeko.net",
            "https://yewtu.be",
            "https://invidious.nerdvpn.de"
        ).shuffled()
        
        var errs = ""
        for (instance in instances) {
            try {
                val target = "$instance/latest_version?id=$videoId&itag=140&local=true"
                val response = withTimeoutOrNull(6000) {
                    Innertube.client.get(target) {
                        header(HttpHeaders.Range, "bytes=0-100")
                        header(HttpHeaders.UserAgent, "Mozilla/5.0")
                    }
                } ?: continue

                if (response.status.value < 400) {
                    val bytes: ByteArray = response.body()
                    val head = String(bytes)
                    if (!head.contains("<!DOCTYPE") && !head.contains("<html")) {
                        return ProxyResult.Success(target)
                    } else errs += "Bot; "
                } else errs += "${response.status.value}; "
            } catch (e: Exception) { errs += "Err; " }
        }
        return ProxyResult.Failure(errs.take(30))
    }

    private fun cache(videoId: String, stream: ResolvedStream, response: PlayerResponse) {
        val expiresIn = response.streamingData?.expiresInSeconds?.toLong() ?: 21600L
        cachedUrls[videoId] = stream to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
    }

    private fun extractUrlFromResponse(response: PlayerResponse): String? {
        val streamingData = response.streamingData ?: return null
        if (!streamingData.hlsManifestUrl.isNullOrBlank()) return streamingData.hlsManifestUrl
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
}
