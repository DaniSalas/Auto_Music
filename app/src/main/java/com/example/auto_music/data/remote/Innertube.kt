package com.example.auto_music.data.remote

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

import com.example.auto_music.data.remote.model.PlayerResponse
import com.example.auto_music.data.remote.model.YouTubeClient

object InnertubeConstants {
    const val YOUTUBE_MUSIC_URL = "https://music.youtube.com"
    const val CHROME_WINDOWS_VISITOR_DATA = "Cgtfa01kaENlQ0p4Zyj938LFBjIKCgJVUxIEGgAgLw%3D%3D"
    const val CHROME_WINDOWS_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3"
    const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    const val CLIENT_NAME = "WEB_REMIX"
    const val X_CLIENT_NAME = "67"
    const val CLIENT_VERSION = "1.20260213.01.00"
    const val MUSIC_ITEM_RENDERER_MASK = "musicResponsiveListItemRenderer(flexColumns,fixedColumns,thumbnail,navigationEndpoint,badges)"
    const val SEARCH_MASK = "contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents.musicShelfRenderer(continuations,contents.$MUSIC_ITEM_RENDERER_MASK)"
}

object Innertube {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    var visitorData: String = InnertubeConstants.CHROME_WINDOWS_VISITOR_DATA

    suspend fun fetchVisitorData() {
        try {
            val response = client.get("https://music.youtube.com/sw.js_data")
            val text = response.bodyAsText()
            
            // Try robust parsing similar to kreate_imp
            val jsonText = text.substringAfter(")]}'", "").trim()
            if (jsonText.isNotEmpty()) {
                val jsonElement = json.parseToJsonElement(jsonText)
                val visitorId = jsonElement.jsonArray.getOrNull(0)
                    ?.jsonArray?.getOrNull(2)
                    ?.jsonArray?.firstOrNull { 
                        it.jsonPrimitive.contentOrNull?.startsWith("Cg") == true 
                    }?.jsonPrimitive?.contentOrNull
                
                if (visitorId != null) {
                    visitorData = visitorId
                    Log.d("Innertube", "Fetched visitorData (robust): $visitorId")
                    return
                }
            }
            
            // Fallback to regex if robust parsing fails
            val match = Regex("Cg[a-zA-Z0-9_-]{38}").find(text)
            match?.value?.let {
                visitorData = it
                Log.d("Innertube", "Fetched visitorData (regex): $it")
            }
        } catch (e: Exception) {
            Log.e("Innertube", "Failed to fetch visitorData: ${e.message}")
        }
    }

    suspend fun search(query: String): InnerTubeResponse? {
        val currentVisitorData = visitorData
        return try {
            val context = InnerTubeContext(
                client = InnerTubeClient(
                    clientName = InnertubeConstants.CLIENT_NAME,
                    clientVersion = InnertubeConstants.CLIENT_VERSION,
                    platform = "DESKTOP",
                    hl = "en",
                    gl = "US",
                    visitorData = currentVisitorData,
                    userAgent = InnertubeConstants.CHROME_WINDOWS_USER_AGENT,
                    referer = "${InnertubeConstants.YOUTUBE_MUSIC_URL}/"
                )
            )
            val body = SearchBody(query = query, context = context)
            
            val response = client.post("${InnertubeConstants.YOUTUBE_MUSIC_URL}/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Format-Version", "1")
                header("X-YouTube-Client-Name", InnertubeConstants.X_CLIENT_NAME)
                header("X-YouTube-Client-Version", InnertubeConstants.CLIENT_VERSION)
                header("X-Goog-Api-Key", InnertubeConstants.API_KEY)
                header("X-Origin", InnertubeConstants.YOUTUBE_MUSIC_URL)
                header("X-Youtube-Bootstrap-Logged-In", "false")
                header(HttpHeaders.Referrer, "${InnertubeConstants.YOUTUBE_MUSIC_URL}/")
                header("X-Goog-Visitor-Id", currentVisitorData)
                userAgent(InnertubeConstants.CHROME_WINDOWS_USER_AGENT)
                parameter("prettyPrint", "false")
                parameter("key", InnertubeConstants.API_KEY)
                setBody(body)
            }
            val responseText = response.bodyAsText()
            // Split log message to avoid truncation
            responseText.chunked(3000).forEach { Log.d("Innertube", "Response chunk: $it") }
            json.decodeFromString<InnerTubeResponse>(responseText)
        } catch (e: Exception) {
            Log.e("Innertube", "Search failed: ${e.message}", e)
            null
        }
    }

    suspend fun player(
        videoId: String,
        clientType: YouTubeClient = YouTubeClient.WEB_REMIX,
        signatureTimestamp: Int? = 20340
    ): PlayerResponse? {
        return try {
            val context = clientType.toContext(visitorData).let {
                if (clientType.isEmbedded) {
                    it.copy(
                        thirdParty = InnerTubeContext.ThirdParty(
                            embedUrl = "https://www.youtube.com/watch?v=${videoId}"
                        )
                    )
                } else it
            }
            
            val body = PlayerBody(
                context = context,
                videoId = videoId,
                playbackContext = if (clientType.useSignatureTimestamp) {
                    PlayerBody.PlaybackContext(
                        PlayerBody.PlaybackContext.ContentPlaybackContext(
                            signatureTimestamp
                        )
                    )
                } else null
            )
            
            val baseUrl = if (clientType.isMusic) InnertubeConstants.YOUTUBE_MUSIC_URL else "https://www.youtube.com"
            
            val response = client.post("${baseUrl}/youtubei/v1/player") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Format-Version", "1")
                header("X-YouTube-Client-Name", clientType.clientId)
                header("X-YouTube-Client-Version", clientType.clientVersion)
                header("X-Goog-Api-Key", clientType.apiKey)
                
                if (clientType.isMusic) {
                    header("X-Origin", InnertubeConstants.YOUTUBE_MUSIC_URL)
                    header(HttpHeaders.Referrer, "${InnertubeConstants.YOUTUBE_MUSIC_URL}/")
                }

                header("X-Goog-Visitor-Id", visitorData)
                userAgent(clientType.userAgent)
                parameter("prettyPrint", "false")
                parameter("key", clientType.apiKey)
                setBody(body)
            }
            val responseText = response.bodyAsText()
            
            // Log raw response for debugging playback issues
            Log.d("Innertube", "Player response for $videoId with ${clientType.clientName}")
            responseText.chunked(3000).forEach { Log.d("Innertube", "Player chunk: $it") }

            json.decodeFromString<PlayerResponse>(responseText)
        } catch (e: Exception) {
            Log.e("Innertube", "Player request failed: ${e.message}", e)
            null
        }
    }
}
