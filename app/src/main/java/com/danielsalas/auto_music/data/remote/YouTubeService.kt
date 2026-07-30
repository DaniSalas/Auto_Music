package com.danielsalas.auto_music.data.remote

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Header

interface YouTubeService {
    @Headers("Content-Type: application/json")
    @POST("youtubei/v1/search")
    suspend fun searchVideos(
        @Body body: SearchBody,
        @Query("key") apiKey: String,
        @Header("X-YouTube-Client-Name") clientName: String = "67",
        @Header("X-YouTube-Client-Version") clientVersion: String = "1.20250407.01.00",
        @Header("X-Goog-Api-Key") googApiKey: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
        @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3",
        @Header("X-Goog-FieldMask") fieldMask: String = "contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents.musicShelfRenderer(continuations,contents.musicResponsiveListItemRenderer(flexColumns,fixedColumns,thumbnail,navigationEndpoint,badges))"
    ): InnerTubeResponse
}
