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
    val apiKey: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
    val isMusic: Boolean = false
) {
    fun toContext(visitorData: String?) = InnerTubeContext(
        client = InnerTubeClient(
            clientName = clientName,
            clientVersion = clientVersion,
            visitorData = visitorData,
            userAgent = userAgent,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            platform = when (clientName) {
                "WEB_REMIX" -> "DESKTOP"
                "WEB" -> "DESKTOP"
                "IOS", "VISIONOS" -> "IOS"
                "ANDROID", "ANDROID_MUSIC", "ANDROID_VR" -> "ANDROID"
                else -> null
            }
        )
    )

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/537.36 Safari/537.36"
        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"
        const val API_URL_YOUTUBE_MUSIC = "https://music.youtube.com/youtubei/v1/"
        const val API_URL_YOUTUBE = "https://www.youtube.com/youtubei/v1/"

        const val KEY_WEB_REMIX = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        const val KEY_IOS = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
        const val KEY_ANDROID = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
        const val KEY_ANDROID_MUSIC = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"
        const val KEY_WEB = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3"

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            clientId = "67",
            apiKey = KEY_WEB_REMIX,
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
            isMusic = true
        )

        val WEB_CREATOR = YouTubeClient(
            clientName = "WEB_CREATOR",
            clientVersion = "1.20260213.00.00",
            clientId = "62",
            apiKey = KEY_WEB_REMIX,
            userAgent = USER_AGENT_WEB,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.03.1",
            clientId = "5",
            apiKey = KEY_IOS,
            userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
            osVersion = "18.2.22C152",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            useSignatureTimestamp = true
        )

        val MWEB = YouTubeClient(
            clientName = "MWEB",
            clientVersion = "2.20240722.00.00",
            clientId = "6",
            apiKey = KEY_WEB,
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
            useSignatureTimestamp = true
        )

        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            apiKey = KEY_IOS,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            loginSupported = false,
            useSignatureTimestamp = false
        )
        
        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260213.00.00",
            clientId = "7",
            apiKey = KEY_WEB_REMIX,
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
            apiKey = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            loginSupported = true,
            loginRequired = false,
            useSignatureTimestamp = true,
            isEmbedded = true,
        )

        val ANDROID = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.03.38",
            clientId = "3",
            apiKey = KEY_ANDROID,
            userAgent = "com.google.android.youtube/21.03.38 (Linux; U; Android 14) gzip",
            loginSupported = true,
            useSignatureTimestamp = true,
            androidSdkVersion = 35
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.01.52",
            clientId = "21",
            apiKey = KEY_ANDROID_MUSIC,
            userAgent = "com.google.android.apps.youtube.music/7.01.52 (Linux; U; Android 14; en_US; Pixel 7 Pro; Build/AP2A.240705.004) [INFO_AND_TRACKING]",
            loginSupported = true,
            useSignatureTimestamp = true,
            isMusic = true
        )

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            clientId = "28",
            apiKey = KEY_ANDROID,
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            useSignatureTimestamp = true
        )
    }
}
