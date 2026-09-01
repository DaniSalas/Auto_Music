package com.danielsalas.auto_music.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.*
import android.util.Log
import com.danielsalas.auto_music.data.remote.model.YouTubeClient
import com.danielsalas.auto_music.utils.potoken.PoTokenGenerator
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.io.File
import com.metrolist.innertubex.InnerTube as InnerTubeX
import com.metrolist.innertubex.models.YouTubeClient as LibYouTubeClient

object InnertubeConstants {
    const val YOUTUBE_MUSIC_URL = "https://music.youtube.com"
    const val YOUTUBE_URL = "https://www.youtube"
}

object Innertube {
    val json = Json { 
        ignoreUnknownKeys = true 
        explicitNulls = false 
        encodeDefaults = true 
        coerceInputValues = true 
    }

    val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }
        engine {
            config {
                connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
                protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                retryOnConnectionFailure(true)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
        defaultRequest {
            url("https://music.youtube.com/youtubei/v1/")
            header("Accept", "application/json")
            header("Cache-Control", "no-cache")
        }
    }

    private val innerTubeX = InnerTubeX(client)
    private var transportGeneration = 0L

    class ExtractionTransport(
        val innerTube: InnerTubeX,
        val httpClient: HttpClient,
        val generation: Long,
    )

    var visitorData: String?
        get() = innerTubeX.visitorData
        set(value) { innerTubeX.visitorData = value }

    private var poTokenGenerator: PoTokenGenerator? = null
    
    fun initPoToken(context: android.content.Context) {
        if (poTokenGenerator == null) {
            poTokenGenerator = PoTokenGenerator(context)
        }
    }

    suspend fun fetchVisitorData() {
        try {
            innerTubeX.fetchFreshVisitorData()
        } catch (e: Exception) { 
            Log.w("Innertube", "fetchVisitorData failed: ${e.message}") 
        }
    }

    suspend fun search(query: String, params: String? = null): InnerTubeResponse? {
        return try {
            val response = innerTubeX.search(
                client = LibYouTubeClient.WEB_REMIX,
                query = query,
                params = params ?: "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
            )
            if (response.status.value !in 200..299) return null
            response.body<InnerTubeResponse>()
        } catch (e: Exception) {
            Log.e("Innertube", "Search error: ${e.message}")
            null
        }
    }

    suspend fun browse(browseId: String): InnerTubeResponse? {
        return try {
            val response = innerTubeX.browse(
                client = LibYouTubeClient.WEB_REMIX,
                browseId = browseId
            )
            if (response.status.value !in 200..299) return null
            response.body<InnerTubeResponse>()
        } catch (e: Exception) {
            Log.e("Innertube", "Browse error: ${e.message}")
            null
        }
    }

    // Keep the manual player call for now as a fallback, or replace it if library's player works well
    suspend fun player(videoId: String, clientType: YouTubeClient): com.danielsalas.auto_music.data.remote.model.PlayerResponse? {
        return try {
            val tokens = poTokenGenerator?.getWebClientPoToken(videoId, visitorData ?: "")
            
            val libClient = when (clientType.clientName) {
                "WEB_REMIX" -> LibYouTubeClient.WEB_REMIX
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> LibYouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER
                "MWEB" -> LibYouTubeClient.MWEB
                else -> LibYouTubeClient.WEB_REMIX
            }

            val response = innerTubeX.player(
                client = libClient,
                videoId = videoId,
                playlistId = null,
                signatureTimestamp = 20710,
                poToken = tokens?.streamingDataPoToken
            )
            
            if (response.status.value !in 200..299) return null
            response.body<com.danielsalas.auto_music.data.remote.model.PlayerResponse>()
        } catch (e: Exception) { 
            Log.e("Innertube", "Player error: ${e.message}")
            null 
        }
    }
    
    fun extractionTransport() = ExtractionTransport(innerTubeX, client, transportGeneration)
}
