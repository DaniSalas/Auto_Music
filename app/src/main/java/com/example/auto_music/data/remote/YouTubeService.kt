package com.example.auto_music.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeService {
    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") category: String = "10", // Music
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

data class YouTubeSearchResponse(
    val items: List<YouTubeVideoItem>
)

data class YouTubeVideoItem(
    val id: VideoId,
    val snippet: Snippet
)

data class VideoId(
    val videoId: String
)

data class Snippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: Thumbnails
)

data class Thumbnails(
    val default: ThumbnailDetails,
    val medium: ThumbnailDetails,
    val high: ThumbnailDetails
)

data class ThumbnailDetails(
    val url: String
)
