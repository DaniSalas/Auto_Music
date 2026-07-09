package com.example.auto_music.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeService {
    // Probamos con la API de Piped, que suele ser más estable que Invidious
    @GET("search")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("filter") filter: String = "videos"
    ): PipedSearchResponse
}

data class PipedSearchResponse(
    val items: List<PipedVideoItem>
)

data class PipedVideoItem(
    val url: String?, // Contiene "/watch?v=..."
    val title: String?,
    val uploaderName: String?,
    val thumbnail: String?,
    val duration: Long?
)
