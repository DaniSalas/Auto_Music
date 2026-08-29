package com.danielsalas.auto_music.data.remote

import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.*
import android.util.Log
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
    val json = Json { 
        ignoreUnknownKeys = true 
        explicitNulls = false 
        encodeDefaults = true 
        coerceInputValues = true 
    }

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

    var visitorData: String? = "CgtuekFiRnJlRGdRRSip0crUBjIoCgJFUxIiEh4SHAsMDg8QERITFBUWFxgZGhscHR4fICEiIyQlJicgamLgAgrdAjE3LllURT1uQ0RuUi1Mc3FyYnlnWEg2TExtRHlrUnpDY29Ba3dmYlZEdEpVdm9ONlBjSVljZUZ2c0ZTQkRGWk5STFZyMkpBYVZxWnBrR1VuZlJNTWpsY01fMGhqV2VFNXVHRGVFcWowMVZ2MnBOYWI0M0FqX0tpVmhKdWhvNW9KNjViSHpSLTVoVDIxRG9kMENFbUlqdURmYlVnVF93QXZBMDhLVUxzamVZcEZtcEJvR2xaSjBOZUNyNzNfdlpiSHpZZ1Fzel9DQnpDYUR0VVpPdUFVcDFrWEZPcGFIbDV3N0NtU0UxeWNodDNWcjJ6dlU4bFhyUmprRjc4Z0U5YTIxbjdBUk5tRkNjMC14MFZSbXpaX0wzMEYya192blZhZ0lhYWJYMHhJcDFOVmluQkxJY21fWjRWM1l2SGhyUzh5aFFPS2o0MFBwck1aLU9sOTZKTUI1NWl1bTRfN2c%3D"


    suspend fun fetchVisitorData() {
        try {
            val response = client.get("${InnertubeConstants.YOUTUBE_URL}/?theme=true") {
                userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            }
            val text = response.bodyAsText()
            Regex("ytcfg\\.set\\(\\{.*?\"VISITOR_DATA\":\"(.*?)\"").find(text)?.groupValues?.get(1)?.let {
                visitorData = it
                Log.i("Innertube", "Updated visitorData: $it")
            }
        } catch (e: Exception) { 
            Log.w("Innertube", "fetchVisitorData failed: ${e.message}") 
        }
    }

    suspend fun search(query: String, params: String? = null): InnerTubeResponse? {
        if (visitorData == null) fetchVisitorData()
        return try {
            val clientType = YouTubeClient.WEB_REMIX
            val context = clientType.toContext(visitorData)
            
            val response = client.post("${InnertubeConstants.YOUTUBE_MUSIC_URL}/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Format-Version", "1")
                header("X-YouTube-Client-Name", "67")
                header("X-YouTube-Client-Version", "1.20240826.01.00")
                header("X-Goog-Api-Key", clientType.apiKey)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                userAgent(clientType.userAgent)
                parameter("key", clientType.apiKey)
                
                setBody(SearchBody(
                    query = query, 
                    context = context,
                    params = params ?: "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
                ))
            }
            if (response.status.value !in 200..299) return null
            json.decodeFromString<InnerTubeResponse>(response.bodyAsText())
        } catch (e: Exception) { null }
    }

    suspend fun browse(browseId: String): InnerTubeResponse? {
        if (visitorData == null) fetchVisitorData()
        return try {
            val clientType = YouTubeClient.WEB_REMIX
            val context = clientType.toContext(visitorData)
            val response = client.post("${InnertubeConstants.YOUTUBE_MUSIC_URL}/youtubei/v1/browse") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Format-Version", "1")
                header("X-YouTube-Client-Name", "67")
                header("X-YouTube-Client-Version", "1.20240826.01.00")
                header("X-Goog-Api-Key", clientType.apiKey)
                userAgent(clientType.userAgent)
                parameter("key", clientType.apiKey)
                setBody(BrowseBody(browseId = browseId, context = context))
            }
            if (response.status.value !in 200..299) return null
            json.decodeFromString<InnerTubeResponse>(response.bodyAsText())
        } catch (e: Exception) { null }
    }

    suspend fun player(videoId: String, clientType: YouTubeClient): PlayerResponse? {
        return try {
            val context = clientType.toContext(visitorData)
            val body = PlayerBody(
                context = context,
                videoId = videoId,
                playbackContext = PlayerBody.PlaybackContext(
                    PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp = 20684)
                )
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
            json.decodeFromString<PlayerResponse>(response.bodyAsText())
        } catch (e: Exception) { 
            Log.e("Innertube", "Player error: ${e.message}")
            null 
        }
    }
}
