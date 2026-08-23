package com.danielsalas.auto_music.player

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<ResolvedStream, Long>>()

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            config {
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }

    data class ResolvedStream(
        val url: String,
        val userAgent: String
    )

    suspend fun resolveStream(videoId: String): ResolvedStream? {
        cachedUrls[videoId]?.let { (stream, expiry) ->
            if (System.currentTimeMillis() < expiry) return stream
        }

        // 1. Direct Web Scraping of YouTube Watch Page (ytInitialPlayerResponse JSON extraction)
        try {
            Log.d(TAG, "Attempting direct YouTube watch page extraction for $videoId")
            val watchRes = httpClient.get("https://www.youtube.com/watch?v=$videoId") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3")
                header("Accept-Language", "en-US,en;q=0.9")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }
            if (watchRes.status.value in 200..299) {
                val html = watchRes.bodyAsText()
                val marker = "ytInitialPlayerResponse = "
                val idx = html.indexOf(marker)
                if (idx != -1) {
                    val endIdx = html.indexOf(";</script>", idx)
                    if (endIdx != -1) {
                        val playerJsonStr = html.substring(idx + marker.length, endIdx)
                        val playerJson = Json.parseToJsonElement(playerJsonStr) as? JsonObject
                        val streamingData = playerJson?.get("streamingData") as? JsonObject
                        
                        val formats = (streamingData?.get("adaptiveFormats") as? JsonArray) 
                            ?: (streamingData?.get("formats") as? JsonArray)
                        
                        if (formats != null) {
                            for (formatEl in formats) {
                                val fmt = formatEl.jsonObject
                                val type = fmt["type"]?.jsonPrimitive?.content ?: ""
                                val itag = fmt["itag"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                val url = fmt["url"]?.jsonPrimitive?.content
                                
                                if (!url.isNullOrBlank() && (itag == 140 || type.contains("audio"))) {
                                    val resolved = ResolvedStream(url, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                    cachedUrls[videoId] = resolved to (System.currentTimeMillis() + 21600000L)
                                    Log.i(TAG, "✅ SUCCESS: Resolved $videoId via direct web scraping (itag $itag)")
                                    return resolved
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct web scraping failed for $videoId: ${e.message}")
        }

        // 2. Secondary Strategy: Innertube Player POST Endpoint with Web Remix client
        try {
            Log.d(TAG, "Attempting Innertube Player API for $videoId")
            val playerRes = httpClient.post("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w") {
                header("Content-Type", "application/json")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3")
                header("X-Forwarded-For", "190.12.34.56")
                setBody("{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20260213.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"$videoId\"}")
            }
            if (playerRes.status.value in 200..299) {
                val playerJson = Json.parseToJsonElement(playerRes.bodyAsText()) as? JsonObject
                val streamingData = playerJson?.get("streamingData") as? JsonObject
                val formats = (streamingData?.get("adaptiveFormats") as? JsonArray) 
                    ?: (streamingData?.get("formats") as? JsonArray)
                if (formats != null) {
                    for (formatEl in formats) {
                        val fmt = formatEl.jsonObject
                        val type = fmt["type"]?.jsonPrimitive?.content ?: ""
                        val itag = fmt["itag"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val url = fmt["url"]?.jsonPrimitive?.content
                        
                        if (!url.isNullOrBlank() && (itag == 140 || type.contains("audio"))) {
                            val resolved = ResolvedStream(url, "Mozilla/5.0")
                            cachedUrls[videoId] = resolved to (System.currentTimeMillis() + 21600000L)
                            Log.i(TAG, "✅ SUCCESS: Resolved $videoId via Innertube Player API")
                            return resolved
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Innertube Player API failed for $videoId: ${e.message}")
        }

        Log.e(TAG, "❌ All verification and resolution strategies failed for $videoId")
        return null
    }
}
