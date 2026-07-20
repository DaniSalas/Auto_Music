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
import java.util.concurrent.TimeUnit

object InnertubeConstants {
    const val YOUTUBE_MUSIC_URL = "https://music.youtube.com"
    const val CHROME_WINDOWS_VISITOR_DATA = "Cgtfa01kaENlQ0p4Zyj938LFBjIKCgJVUxIEGgAgLw%3D%3D"
    const val CHROME_WINDOWS_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3"
    const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    const val CLIENT_NAME = "WEB_REMIX"
    const val X_CLIENT_NAME = "67"
    const val CLIENT_VERSION = "1.20260213.01.00"
}

object Innertube {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
                writeTimeout(15, TimeUnit.SECONDS)
            }
        }
    }

    var visitorData: String = InnertubeConstants.CHROME_WINDOWS_VISITOR_DATA

    suspend fun fetchVisitorData() {
        try {
            val response = client.get("https://music.youtube.com/sw.js_data") {
                userAgent(InnertubeConstants.CHROME_WINDOWS_USER_AGENT)
            }
            val text = response.bodyAsText()
            Regex("Cg[a-zA-Z0-9_-]{35,45}").find(text)?.value?.let {
                visitorData = it
                Log.i("Innertube", "Updated visitorData: $it")
            }
        } catch (e: Exception) { Log.w("Innertube", "fetchVisitorData failed, using fallback") }
    }

    suspend fun search(query: String): InnerTubeResponse? {
        return try {
            val context = InnerTubeContext(client = InnerTubeClient(clientName = InnertubeConstants.CLIENT_NAME, clientVersion = InnertubeConstants.CLIENT_VERSION, hl = "en", gl = "US", visitorData = visitorData, userAgent = InnertubeConstants.CHROME_WINDOWS_USER_AGENT, referer = "${InnertubeConstants.YOUTUBE_MUSIC_URL}/"))
            val response = client.post("${InnertubeConstants.YOUTUBE_MUSIC_URL}/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                header("X-YouTube-Client-Name", InnertubeConstants.X_CLIENT_NAME)
                header("X-YouTube-Client-Version", InnertubeConstants.CLIENT_VERSION)
                header("X-Goog-Visitor-Id", visitorData)
                userAgent(InnertubeConstants.CHROME_WINDOWS_USER_AGENT)
                parameter("key", InnertubeConstants.API_KEY)
                setBody(SearchBody(query = query, context = context))
            }
            json.decodeFromString<InnerTubeResponse>(response.bodyAsText())
        } catch (e: Exception) { null }
    }

    suspend fun player(videoId: String, clientType: YouTubeClient): PlayerResponse? {
        return try {
            val context = clientType.toContext(visitorData)
            val body = PlayerBody(context = context, videoId = videoId, cpn = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString(""), playbackContext = PlayerBody.PlaybackContext(PlayerBody.PlaybackContext.ContentPlaybackContext(20465)))
            val baseUrl = if (clientType.isMusic) InnertubeConstants.YOUTUBE_MUSIC_URL else "https://www.youtube.com"
            val response = client.post("${baseUrl}/youtubei/v1/player") {
                contentType(ContentType.Application.Json)
                header("X-YouTube-Client-Name", clientType.clientId)
                header("X-YouTube-Client-Version", clientType.clientVersion)
                header("X-Goog-Visitor-Id", visitorData)
                userAgent(clientType.userAgent)
                parameter("key", clientType.apiKey)
                setBody(body)
            }
            json.decodeFromString<PlayerResponse>(response.bodyAsText())
        } catch (e: Exception) { null }
    }
}
