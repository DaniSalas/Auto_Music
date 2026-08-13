package com.danielsalas.auto_music.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class SearchBody(
    val query: String?,
    val params: String? = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D",
    val continuation: String? = null,
    val context: InnerTubeContext
)

@Serializable
data class BrowseBody(
    val browseId: String,
    val context: InnerTubeContext
)

@Serializable
data class InnerTubeContext(
    val client: InnerTubeClient,
    val user: User = User(),
    val thirdParty: ThirdParty? = null
) {
    @Serializable
    data class ThirdParty(
        val embedUrl: String? = null
    )
}

@Serializable
data class InnerTubeClient(
    val clientName: String,
    val clientVersion: String,
    val hl: String = "en",
    val gl: String = "US",
    val visitorData: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null
)

@Serializable
data class PlayerBody(
    val context: InnerTubeContext,
    val videoId: String,
    val playlistId: String? = null,
    val cpn: String? = null,
    val playbackContext: PlaybackContext? = null
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext? = null
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int? = null
        )
    }
}

@Serializable
data class User(
    val lockedSafetyMode: Boolean = false
)

@Serializable
data class InnerTubeResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
    val header: Header? = null,
    val responseContext: ResponseContext? = null
)

@Serializable
data class ContinuationContents(
    val musicShelfContinuation: MusicShelfRenderer? = null,
    val musicPlaylistShelfContinuation: MusicShelfRenderer? = null,
    val sectionListContinuation: SectionListContinuation? = null
)

@Serializable
data class SectionListContinuation(
    val contents: List<SectionContent>? = null
)

@Serializable
data class Header(
    val musicDetailHeaderRenderer: MusicDetailHeaderRenderer? = null
)

@Serializable
data class MusicDetailHeaderRenderer(
    val title: Runs? = null
)

@Serializable
data class ResponseContext(
    val visitorData: String? = null
)

@Serializable
data class Contents(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null,
    val sectionListRenderer: SectionListRenderer? = null,
    val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
    val singleColumnBrowseResultsRenderer: SingleColumnBrowseResultsRenderer? = null
)

@Serializable
data class TwoColumnBrowseResultsRenderer(
    val secondaryContents: SecondaryContents? = null,
    val tabs: List<Tab>? = null
)

@Serializable
data class SingleColumnBrowseResultsRenderer(
    val tabs: List<Tab>? = null
)

@Serializable
data class SecondaryContents(
    val sectionListRenderer: SectionListRenderer? = null
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
    val content: SectionListContent? = null,
    val title: String? = null
)

@Serializable
data class SectionListContent(
    val sectionListRenderer: SectionListRenderer? = null
)

@Serializable
data class SectionListRenderer(
    val contents: List<SectionContent>? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SectionContent(
    val musicShelfRenderer: MusicShelfRenderer? = null,
    @JsonNames("musicPlaylistShelfRenderer")
    val musicPlaylistShelfRenderer: MusicShelfRenderer? = null,
    @JsonNames("musicImmersiveCarouselShelfRenderer")
    val musicCarouselShelfRenderer: MusicShelfRenderer? = null,
    val musicCardShelfRenderer: MusicCardShelfRenderer? = null,
    val itemSectionRenderer: ItemSectionRenderer? = null,
    val musicPlaylistShelfContinuation: MusicShelfRenderer? = null,
    val gridRenderer: GridRenderer? = null
)

@Serializable
data class GridRenderer(
    val items: List<MusicItem>? = null,
    val header: GridHeader? = null
)

@Serializable
data class GridHeader(
    val gridHeaderRenderer: GridHeaderRenderer? = null
)

@Serializable
data class GridHeaderRenderer(
    val title: Runs? = null
)

@Serializable
data class MusicCardShelfRenderer(
    val header: MusicCardShelfHeader? = null,
    val contents: List<MusicItem>? = null,
    val buttons: List<ButtonRenderer>? = null
)

@Serializable
data class MusicCardShelfHeader(
    val musicCardShelfHeaderBasicRenderer: MusicCardShelfHeaderBasic? = null
)

@Serializable
data class MusicCardShelfHeaderBasic(
    val title: Runs? = null
)

@Serializable
data class ButtonRenderer(
    val buttonRenderer: ButtonContent? = null
)

@Serializable
data class ButtonContent(
    val navigationEndpoint: NavigationEndpoint? = null
)

@Serializable
data class ItemSectionRenderer(
    val contents: List<SectionContent>? = null
)

@Serializable
data class MusicShelfRenderer(
    val contents: List<MusicItem>? = null,
    val title: Runs? = null
)

@Serializable
data class MusicItem(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

@Serializable
data class MusicResponsiveListItemRenderer(
    val navigationEndpoint: NavigationEndpoint? = null,
    val flexColumns: List<FlexColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val playlistItemData: PlaylistItemData? = null,
    val lengthText: Runs? = null
)

@Serializable
data class PlaylistItemData(
    val videoId: String? = null
)

@Serializable
data class NavigationEndpoint(
    val watchEndpoint: WatchEndpoint? = null,
    val browseEndpoint: BrowseEndpoint? = null
)

@Serializable
data class WatchEndpoint(
    val videoId: String? = null,
    val playlistId: String? = null
)

@Serializable
data class BrowseEndpoint(
    val browseId: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FlexColumn(
    @JsonNames("musicResponsiveListItemFixedColumnRenderer")
    val musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer? = null
)

@Serializable
data class FlexColumnRenderer(
    val text: Runs? = null
)

@Serializable
data class Runs(
    val runs: List<Run>? = null
) {
    val text: String
        get() = runs?.joinToString("") { it.text ?: "" } ?: ""
}

@Serializable
data class Run(
    val text: String? = null,
    val navigationEndpoint: NavigationEndpoint? = null
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

@Serializable
data class YouTubePlaylist(
    val id: String,
    val title: String,
    val author: String,
    val trackCount: Int,
    val thumbnailUrl: String
)
