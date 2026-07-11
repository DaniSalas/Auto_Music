package com.example.auto_music.data.remote

import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
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

object InnertubeConstants {
    const val YOUTUBE_MUSIC_URL = "https://music.youtube.com"
    const val CHROME_WINDOWS_VISITOR_DATA = "Cgtfa01kaENlQ0p4Zyj938LFBjIKCgJVUxIEGgAgLw%3D%3D"
    const val CHROME_WINDOWS_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3"
    const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    const val CLIENT_NAME = "WEB_REMIX"
    const val X_CLIENT_NAME = "67"
    const val CLIENT_VERSION = "1.20250416.01.00"
    const val MUSIC_ITEM_RENDERER_MASK = "musicResponsiveListItemRenderer(flexColumns,fixedColumns,thumbnail,navigationEndpoint,badges)"
    const val SEARCH_MASK = "contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents.musicShelfRenderer(continuations,contents.$MUSIC_ITEM_RENDERER_MASK)"
}

@Serializable
data class PlayerBody(
    val context: InnerTubeContext,
    val videoId: String,
    val playlistId: String? = null
)

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
                header("X-YouTube-Client-Name", InnertubeConstants.X_CLIENT_NAME)
                header("X-YouTube-Client-Version", InnertubeConstants.CLIENT_VERSION)
                header("X-Goog-Api-Key", InnertubeConstants.API_KEY)
                // header("X-Goog-FieldMask", InnertubeConstants.SEARCH_MASK) // Temporarily disabled for debugging
                header("X-Origin", InnertubeConstants.YOUTUBE_MUSIC_URL)
                header("X-Youtube-Bootstrap-Logged-In", "false")
                header(HttpHeaders.Referrer, "${InnertubeConstants.YOUTUBE_MUSIC_URL}/")
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

    suspend fun player(videoId: String): PlayerResponse? {
        return try {
            val context = InnerTubeContext(
                client = InnerTubeClient(
                    clientName = InnertubeConstants.CLIENT_NAME,
                    clientVersion = InnertubeConstants.CLIENT_VERSION,
                    platform = "DESKTOP",
                    hl = "en",
                    gl = "US",
                    visitorData = InnertubeConstants.CHROME_WINDOWS_VISITOR_DATA,
                    userAgent = InnertubeConstants.CHROME_WINDOWS_USER_AGENT,
                    referer = "${InnertubeConstants.YOUTUBE_MUSIC_URL}/"
                )
            )
            val body = PlayerBody(context = context, videoId = videoId)
            
            val response = client.post("${InnertubeConstants.YOUTUBE_MUSIC_URL}/youtubei/v1/player") {
                contentType(ContentType.Application.Json)
                header("X-YouTube-Client-Name", InnertubeConstants.X_CLIENT_NAME)
                header("X-YouTube-Client-Version", InnertubeConstants.CLIENT_VERSION)
                header("X-Goog-Api-Key", InnertubeConstants.API_KEY)
                header("X-Origin", InnertubeConstants.YOUTUBE_MUSIC_URL)
                header(HttpHeaders.Referrer, "${InnertubeConstants.YOUTUBE_MUSIC_URL}/")
                userAgent(InnertubeConstants.CHROME_WINDOWS_USER_AGENT)
                parameter("prettyPrint", "false")
                parameter("key", InnertubeConstants.API_KEY)
                setBody(body)
            }
            val responseText = response.bodyAsText()
            json.decodeFromString<PlayerResponse>(responseText)
        } catch (e: Exception) {
            Log.e("Innertube", "Player request failed: ${e.message}", e)
            null
        }
    }
}
