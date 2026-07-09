package com.example.auto_music.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeService {
    // Usaremos la API de Invidious que no requiere clave
    @GET("api/v1/search")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("type") type: String = "video"
    ): List<InvidiousVideoItem>
}

data class InvidiousVideoItem(
    val videoId: String,
    val title: String,
    val author: String,
    val videoThumbnails: List<InvidiousThumbnail>
)

data class InvidiousThumbnail(
    val url: String,
    val width: Int
)
