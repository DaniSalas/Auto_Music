package com.danielsalas.auto_music.data.remote.model

import com.danielsalas.auto_music.data.remote.InnerTubeClient
import com.danielsalas.auto_music.data.remote.InnerTubeContext
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
    val androidSdkVersion: String? = null,
    val loginSupported: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val isMusic: Boolean = false
) {
    fun toContext(visitorData: String?) = InnerTubeContext(
        client = InnerTubeClient(
            clientName = clientName,
            clientVersion = clientVersion,
            visitorData = visitorData,
            userAgent = userAgent,
            hl = "en",
            gl = "US",
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion?.toIntOrNull()
        )
    )

    companion object {
        const val KEY_ANDROID = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
        const val KEY_IOS = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
        const val KEY_WEB_REMIX = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            clientId = "28",
            apiKey = KEY_ANDROID,
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3) [INFO_AND_TRACKING]",
            useSignatureTimestamp = false
        )

        val ANDROID_TESTSUITE = YouTubeClient(
            clientName = "ANDROID_TESTSUITE",
            clientVersion = "1.9",
            clientId = "30",
            apiKey = KEY_ANDROID,
            userAgent = "com.google.android.youtube/19.29.35 (Linux; U; Android 14; en_US; Pixel 7) gzip",
            useSignatureTimestamp = false
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.01.52",
            clientId = "21",
            apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = "com.google.android.apps.youtube.music/7.01.52 (Linux; U; Android 14; en_US; Pixel 7 Pro; Build/AP2A.240705.004) [INFO_AND_TRACKING]",
            useSignatureTimestamp = true,
            isMusic = true
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "19.29.1",
            clientId = "5",
            apiKey = KEY_IOS,
            userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)",
            useSignatureTimestamp = true
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20240821.01.00",
            clientId = "67",
            apiKey = KEY_WEB_REMIX,
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3",
            useSignatureTimestamp = true,
            isMusic = true
        )

        val EMBEDDED = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = "85",
            apiKey = KEY_ANDROID,
            userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/6.0 TV Safari/605.1.15",
            useSignatureTimestamp = false,
            isEmbedded = true
        )

        val TVHTML5_EMBEDDED = EMBEDDED
    }
}
