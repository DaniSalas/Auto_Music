package com.example.auto_music.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class SearchBody(
    val context: InnerTubeContext,
    val query: String,
    val params: String? = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
)

@Serializable
data class InnerTubeContext(
    val client: InnerTubeClient,
    val user: User = User()
)

@Serializable
data class InnerTubeClient(
    val clientName: String,
    val clientVersion: String,
    val hl: String = "en",
    val gl: String = "US",
    val visitorData: String? = null,
    val userAgent: String? = null,
    val referer: String? = null
)

@Serializable
data class User(
    val lockedSafetyMode: Boolean = false
)

@Serializable
data class InnerTubeResponse(
    val contents: Contents? = null
)

@Serializable
data class Contents(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null
)

@Serializable
data class TabbedSearchResultsRenderer(
    val tabs: List<Tab>? = null
)

@Serializable
data class Tab(
    val tabRenderer: TabRenderer? = null
)

@Serializable
data class TabRenderer(
    val content: SectionList? = null
)

@Serializable
data class SectionList(
    val sectionListRenderer: SectionListRenderer? = null
)

@Serializable
data class SectionListRenderer(
    val contents: List<SectionContent>? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SectionContent(
    @JsonNames("musicPlaylistShelfRenderer")
    val musicShelfRenderer: MusicShelfRenderer? = null
)

@Serializable
data class MusicShelfRenderer(
    val contents: List<MusicItem>? = null
)

@Serializable
data class MusicItem(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

@Serializable
data class MusicResponsiveListItemRenderer(
    val navigationEndpoint: NavigationEndpoint? = null,
    val flexColumns: List<FlexColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null
)

@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null
)

@Serializable
data class WatchEndpoint(
    val videoId: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FlexColumn(
    @JsonNames("musicResponsiveListItemFixedColumnRenderer")
    val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer? = null
)

@Serializable
data class FlexColumnRenderer(
    val text: TextContent? = null
)

@Serializable
data class TextContent(
    val runs: List<Run>? = null
)

@Serializable
data class Run(
    val text: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null
)

@Serializable
data class MusicThumbnailRenderer(
    val thumbnail: ThumbnailData? = null
)

@Serializable
data class ThumbnailData(
    val thumbnails: List<ThumbnailUrl>? = null
)

@Serializable
data class ThumbnailUrl(
    val url: String? = null
)
