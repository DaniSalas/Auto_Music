package com.danielsalas.auto_music.data.remote

import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import android.util.Log
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import com.danielsalas.auto_music.data.remote.model.PlayerResponse
import com.danielsalas.auto_music.data.remote.model.YouTubeClient
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Protocol

object InnertubeConstants {
    const val YOUTUBE_MUSIC_URL = "https://music.youtube.com"
    const val YOUTUBE_URL = "https://www.youtube.com"
}

object Innertube {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        engine {
            config {
                connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                connectTimeout(20, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(20, TimeUnit.SECONDS)
                protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                retryOnConnectionFailure(true)
            }
        }
    }

    var visitorData: String? = null
    private const val DEFAULT_STS = 20492

    suspend fun fetchVisitorData() {
        try {
            val response = client.get("${InnertubeConstants.YOUTUBE_URL}/?theme=true") {
                userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3")
            }
            val text = response.bodyAsText()
            Regex("ytcfg\\.set\\(\\{.*?\"VISITOR_DATA\":\"(.*?)\"").find(text)?.groupValues?.get(1)?.let {
                visitorData = it
                Log.i("Innertube", "Fetched visitorData from root: $it")
                return
            }
            
            val swResponse = client.get("${InnertubeConstants.YOUTUBE_MUSIC_URL}/sw.js_data")
            if (swResponse.status.value in 200..299) {
                Regex("Cg[a-zA-Z0-9_-]{35,45}").find(swResponse.bodyAsText())?.value?.let {
                    visitorData = it
                    Log.i("Innertube", "Fetched visitorData from sw: $it")
                }
            }
        } catch (e: Exception) { 
            Log.w("Innertube", "fetchVisitorData failed: ${e.message}") 
        }
    }

    suspend fun search(query: String): InnerTubeResponse? {
        return try {
            val clientType = YouTubeClient.WEB_REMIX
            val context = clientType.toContext(visitorData)
            // Enhanced search params for "Songs only" and "More results"
            val response = client.post("${InnertubeConstants.YOUTUBE_MUSIC_URL}/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                header("X-YouTube-Client-Name", clientType.clientId)
                header("X-YouTube-Client-Version", clientType.clientVersion)
                header("X-Goog-Api-Key", clientType.apiKey)
                header("X-Origin", InnertubeConstants.YOUTUBE_MUSIC_URL)
                header("X-Goog-Api-Format-Version", "1")
                header(HttpHeaders.Referrer, "${InnertubeConstants.YOUTUBE_MUSIC_URL}/")
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                userAgent(clientType.userAgent)
                parameter("key", clientType.apiKey)
                setBody(SearchBody(
                    query = query, 
                    context = context,
                    params = "EgWKAQIIAWoKEAkQAxAEEAkQBQ%3D%3D" // Filter for SONGS specifically
                ))
            }
            response.body<InnerTubeResponse>()
        } catch (e: Exception) { 
            Log.e("Innertube", "Search error: ${e.message}")
            null 
        }
    }

    suspend fun player(videoId: String, clientType: YouTubeClient): PlayerResponse? {
        return try {
            val context = clientType.toContext(visitorData)
            val body = PlayerBody(
                context = context,
                videoId = videoId,
                playbackContext = if (clientType.useSignatureTimestamp) {
                    PlayerBody.PlaybackContext(
                        PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp = DEFAULT_STS)
                    )
                } else null
            )
            
            val baseUrl = if (clientType.isMusic) InnertubeConstants.YOUTUBE_MUSIC_URL else InnertubeConstants.YOUTUBE_URL
            val response = client.post("${baseUrl}/youtubei/v1/player") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Format-Version", "1")
                header("X-YouTube-Client-Name", clientType.clientId)
                header("X-YouTube-Client-Version", clientType.clientVersion)
                header("X-Goog-Api-Key", clientType.apiKey)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                
                if (clientType.isMusic) {
                    header("X-Origin", InnertubeConstants.YOUTUBE_MUSIC_URL)
                    header(HttpHeaders.Referrer, "${InnertubeConstants.YOUTUBE_MUSIC_URL}/")
                } else if (clientType.isEmbedded) {
                    header("Referer", "https://www.youtube.com/embed/$videoId")
                } else {
                    header("Referer", "https://www.youtube.com/watch?v=$videoId")
                }
                
                userAgent(clientType.userAgent)
                parameter("key", clientType.apiKey)
                setBody(body)
            }
            if (response.status.value !in 200..299) return null
            response.body<PlayerResponse>()
        } catch (e: Exception) { null }
    }
}
