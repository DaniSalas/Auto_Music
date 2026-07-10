package com.example.auto_music.data.remote

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface YouTubeService {
    @Headers("Content-Type: application/json")
    @POST("youtubei/v1/search")
    suspend fun searchVideos(@Body body: InnerTubeRequest): YouTubeMusicResponse
}

data class InnerTubeRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val query: String
)

data class InnerTubeContext(
    val client: InnerTubeClient = InnerTubeClient()
)

data class InnerTubeClient(
    val clientName: String = "WEB_REMIX",
    val clientVersion: String = "1.20240101.01.00"
)

// Estructura simplificada de la respuesta de YouTube Music
data class YouTubeMusicResponse(
    val contents: Contents?
)

data class Contents(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer?
)

data class TabbedSearchResultsRenderer(
    val tabs: List<Tab>?
)

data class Tab(
    val content: TabContent?
)

data class TabContent(
    val sectionListRenderer: SectionListRenderer?
)

data class SectionListRenderer(
    val contents: List<SectionContent>?
)

data class SectionContent(
    val musicShelfRenderer: MusicShelfRenderer?
)

data class MusicShelfRenderer(
    val contents: List<MusicItem>?
)

data class MusicItem(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?
)

data class MusicResponsiveListItemRenderer(
    val navigationEndpoint: NavigationEndpoint?,
    val flexColumns: List<FlexColumn>?,
    val thumbnail: MusicThumbnail?
)

data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint?
)

data class WatchEndpoint(
    val videoId: String?
)

data class FlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer?
)

data class FlexColumnRenderer(
    val text: TextContent?
)

data class TextContent(
    val runs: List<Run>?
)

data class Run(
    val text: String?
)

data class MusicThumbnail(
    val musicThumbnailRenderer: MusicThumbnailRenderer?
)

data class MusicThumbnailRenderer(
    val thumbnail: ThumbnailData?
)

data class ThumbnailData(
    val thumbnails: List<ThumbnailUrl>?
)

data class ThumbnailUrl(
    val url: String?
)
