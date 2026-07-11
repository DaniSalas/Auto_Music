package com.example.auto_music.data.remote

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

object Innertube {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun search(query: String): InnerTubeResponse? {
        return try {
            val context = InnerTubeContext(
                client = InnerTubeClient(
                    clientName = InnertubeConstants.CLIENT_NAME,
                    clientVersion = InnertubeConstants.CLIENT_VERSION,
                    hl = "en",
                    gl = "US",
                    visitorData = InnertubeConstants.CHROME_WINDOWS_VISITOR_DATA,
                    userAgent = InnertubeConstants.CHROME_WINDOWS_USER_AGENT,
                    referer = "${InnertubeConstants.YOUTUBE_MUSIC_URL}/"
                )
            )
            val body = SearchBody(context = context, query = query)
            
            val response = client.post("${InnertubeConstants.YOUTUBE_MUSIC_URL}/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                header("X-YouTube-Client-Name", InnertubeConstants.X_CLIENT_NAME)
                header("X-YouTube-Client-Version", InnertubeConstants.CLIENT_VERSION)
                header("X-Goog-Api-Key", InnertubeConstants.API_KEY)
                header("X-Goog-FieldMask", InnertubeConstants.SEARCH_MASK)
                header("X-Origin", InnertubeConstants.YOUTUBE_MUSIC_URL)
                header(HttpHeaders.Referrer, "${InnertubeConstants.YOUTUBE_MUSIC_URL}/")
                userAgent(InnertubeConstants.CHROME_WINDOWS_USER_AGENT)
                parameter("prettyPrint", "false")
                parameter("key", InnertubeConstants.API_KEY)
                setBody(body)
            }.body<InnerTubeResponse>()
            
            response
        } catch (e: Exception) {
            Log.e("Innertube", "Search failed: ${e.message}", e)
            null
        }
    }
}
