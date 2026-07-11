package com.example.auto_music.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import android.util.Log

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
        defaultRequest {
            url("https://www.youtube.com/")
            contentType(ContentType.Application.Json)
            header("X-YouTube-Client-Name", "67")
            header("X-YouTube-Client-Version", "1.20250416.01.00")
            header("X-Goog-Api-Key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3")
            header("X-Origin", "https://music.youtube.com")
            header("Referer", "https://music.youtube.com/")
        }
    }

    suspend fun search(query: String): InnerTubeResponse? {
        return try {
            val body = SearchBody(query = query)
            val response = client.post("youtubei/v1/search") {
                setBody(body)
                header("X-Goog-FieldMask", "contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents.musicShelfRenderer(continuations,contents.musicResponsiveListItemRenderer(flexColumns,fixedColumns,thumbnail,navigationEndpoint,badges))")
            }.body<InnerTubeResponse>()
            response
        } catch (e: Exception) {
            Log.e("Innertube", "Search failed", e)
            null
        }
    }
}
