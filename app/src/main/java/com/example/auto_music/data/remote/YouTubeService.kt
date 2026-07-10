package com.example.auto_music.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeService {
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
    val url: String?, 
    val title: String?,
    val uploaderName: String?,
    val thumbnail: String?,
    val duration: Long?,
    val type: String? // "stream", "playlist", etc.
)
