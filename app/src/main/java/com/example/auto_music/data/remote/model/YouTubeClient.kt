package com.example.auto_music.data.remote.model

import com.example.auto_music.data.remote.InnerTubeClient
import com.example.auto_music.data.remote.InnerTubeContext
import com.example.auto_music.data.remote.User
import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
) {
    fun toContext(visitorData: String?) = InnerTubeContext(
        client = InnerTubeClient(
            clientName = clientName,
            clientVersion = clientVersion,
            visitorData = visitorData,
            userAgent = userAgent,
            platform = if (clientName == "WEB_REMIX") "DESKTOP" else if (clientName == "VISIONOS") "VISIONOS" else "MOBILE",
            osName = if (clientName == "VISIONOS") "visionOS" else null,
            osVersion = if (clientName == "VISIONOS") "1.3.21O771" else null
        )
    )

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"
        const val API_URL_YOUTUBE_MUSIC = "https://music.youtube.com/youtubei/v1/"

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            clientId = "67",
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            loginSupported = false,
            useSignatureTimestamp = false
        )
        
        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260213.00.00",
            clientId = "7",
            userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val TVHTML5_EMBEDDED = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = "85",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            loginSupported = true,
            loginRequired = false,
            useSignatureTimestamp = true,
            isEmbedded = true,
        )

        val ANDROID = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "19.30.36",
            clientId = "3",
            userAgent = "com.google.android.youtube/19.30.36 (Linux; U; Android 14; en_US; Pixel 7 Pro; Build/AP2A.240705.004) [INFO_AND_TRACKING]",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.01.52",
            clientId = "21",
            userAgent = "com.google.android.apps.youtube.music/7.01.52 (Linux; U; Android 14; en_US; Pixel 7 Pro; Build/AP2A.240705.004) [INFO_AND_TRACKING]",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            clientId = "47",
            userAgent = "com.google.android.apps.youtube.vr/1.60.19 (Linux; U; Android 14; en_US; Pixel 7 Pro; Build/AP2A.240705.004) [INFO_AND_TRACKING]",
            loginSupported = true,
            useSignatureTimestamp = true
        )
    }
}
