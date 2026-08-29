package com.danielsalas.auto_music.player

import android.util.Log
import com.danielsalas.auto_music.data.remote.Innertube
import com.danielsalas.auto_music.data.remote.model.YouTubeClient
import com.danielsalas.auto_music.data.remote.model.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import com.danielsalas.auto_music.model.Song
import kotlinx.serialization.json.*
import java.net.URLDecoder
import java.net.URLEncoder
import io.ktor.client.call.body
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"
    private val cachedUrls = mutableMapOf<String, Pair<ResolvedStream, Long>>()

    data class ResolvedStream(
        val url: String,
        val userAgent: String,
        val status: String = "OK",
        val diagnosticLog: String = "",
        val mimeType: String = "audio/mp4",
        val headers: Map<String, String> = emptyMap()
    )

    suspend fun resolveStream(song: Song): ResolvedStream {
        val videoId = song.id
        
        // Safety: If the ID is already a URL, return it immediately to avoid loops
        if (videoId.startsWith("http")) {
            Log.i(TAG, "ID is already a URL, returning as-is: $videoId")
            return ResolvedStream(videoId, "Mozilla/5.0", status = "Source: Raw URL")
        }

        if (cachedUrls.containsKey(videoId)) {
            val (stream, expiry) = cachedUrls[videoId]!!
            if (System.currentTimeMillis() < expiry) return stream
        }

        val logBuilder = StringBuilder()
        var currentSong = song
        Log.i(TAG, "🔍 Starting resolution for: $videoId (${currentSong.title})")

        // LEVEL 0: Direct Innertube Player API
        val clients = listOf(
            YouTubeClient.IOS,
            YouTubeClient.ANDROID_VR,
            YouTubeClient.TV_EMBEDDED,
            YouTubeClient.WEB_REMIX
        )
        for (client in clients) {
            try {
                val res = withTimeoutOrNull(6000) {
                    Log.i(TAG, "Level 0: Trying ${client.clientName}...")
                    val playerResponse = Innertube.player(videoId, client)
                    
                    // Update metadata if unknown (Critical for Archive.org fallback)
                    if (currentSong.title == "Unknown" || currentSong.artist == "Unknown") {
                        playerResponse?.videoDetails?.let { details ->
                            currentSong = currentSong.copy(
                                title = details.title ?: currentSong.title,
                                artist = details.author ?: currentSong.artist
                            )
                            Log.i(TAG, "Updated metadata: ${currentSong.title} by ${currentSong.artist}")
                        }
                    }

                    if (playerResponse?.playabilityStatus?.status == "LOGIN_REQUIRED" || 
                        playerResponse?.playabilityStatus?.status == "UNPLAYABLE") {
                        Log.w(TAG, "Client ${client.clientName} blocked: ${playerResponse.playabilityStatus.reason}")
                        null
                    } else {
                        playerResponse
                    }
                }

                if (res != null) {
                    val directUrl = extractUrlFromResponse(res)
                    if (directUrl != null) {
                        Log.i(TAG, "✅ Success with ${client.clientName}")
                        val headers = mutableMapOf(
                            "User-Agent" to client.userAgent,
                            "Referer" to if (client.isMusic) "https://music.youtube.com/" else "https://www.youtube.com/",
                            "Origin" to if (client.isMusic) "https://music.youtube.com" else "https://www.youtube.com"
                        )
                        val stream = ResolvedStream(
                            url = directUrl, 
                            userAgent = client.userAgent, 
                            status = "Source: YouTube (${client.clientName})",
                            headers = headers
                        )
                        cache(videoId, stream, res)
                        return stream
                    }
                }
                logBuilder.append("${client.clientName}:Fail; ")
            } catch (e: Exception) { 
                Log.e(TAG, "Client ${client.clientName} error: ${e.message}")
                logBuilder.append("${client.clientName}:Err; ") 
            }
        }

        // LEVEL 1: Piped API (Region Lock Bypass)
        try {
            Log.i(TAG, "Level 1: Trying Piped API for $videoId")
            val pipedUrl = withTimeoutOrNull(7000) { fetchFromPipedApi(videoId) }
            if (pipedUrl != null) {
                Log.i(TAG, "✅ Level 1 Success (Piped)!")
                return ResolvedStream(
                    url = pipedUrl, 
                    userAgent = "Mozilla/5.0", 
                    status = "Source: Piped API",
                    headers = mapOf("User-Agent" to "Mozilla/5.0")
                )
            }
        } catch (e: Exception) { Log.e(TAG, "Piped error: ${e.message}") }

        // LEVEL 2: Archive.org Fallback (Direct Mirror)
        try {
            Log.i(TAG, "Level 2: Trying Archive.org Fallback for ${currentSong.title}...")
            val archiveUrl = withTimeoutOrNull(10000) { fetchFromArchiveFallback(currentSong) }
            if (archiveUrl != null) {
                Log.i(TAG, "✅ Level 2 Success (Archive.org)!")
                return ResolvedStream(
                    url = archiveUrl, 
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36", 
                    status = "Source: Archive.org Fallback",
                    mimeType = if (archiveUrl.contains(".mp3")) "audio/mpeg" else "video/webm",
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
                        "Referer" to "https://archive.org/",
                        "Accept" to "*/*"
                    )
                )
            } else logBuilder.append("Archive:NoMatch; ")
        } catch (e: Exception) { Log.e(TAG, "Archive error: ${e.message}") }

        // LEVEL 3: Proxied Invidious Tunnels
        try {
            Log.i(TAG, "Level 3: Trying Proxy Pool...")
            val proxyResult = fetchFromProxyPool(videoId)
            if (proxyResult is ProxyResult.Success) {
                Log.i(TAG, "✅ Level 3 Success (Proxy)")
                return ResolvedStream(proxyResult.url, "Mozilla/5.0", status = "Source: Proxy Tunnel")
            }
        } catch (e: Exception) { Log.e(TAG, "Proxy error: ${e.message}") }

        Log.e(TAG, "❌ All strategies failed for $videoId")
        return ResolvedStream("", "", status = "FAILED", diagnosticLog = logBuilder.toString())
    }

    private suspend fun fetchFromPipedApi(videoId: String): String? {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://piped-api.lunar.icu",
            "https://api.piped.mha.fi"
        )
        
        for (instance in instances) {
            try {
                val res = withTimeoutOrNull(5000) { Innertube.client.get("$instance/streams/$videoId") } ?: continue
                if (res.status.value == 200) {
                    val json = Innertube.json.parseToJsonElement(res.bodyAsText()).jsonObject
                    val audioStreams = json["audioStreams"]?.jsonArray
                    val stream = audioStreams?.filter { 
                        it.jsonObject["format"]?.jsonPrimitive?.content == "M4A" || 
                        it.jsonObject["format"]?.jsonPrimitive?.content == "WEB_M"
                    }?.maxByOrNull { 
                        it.jsonObject["bitrate"]?.jsonPrimitive?.int ?: 0
                    }?.jsonObject?.get("url")?.jsonPrimitive?.content
                    
                    if (!stream.isNullOrBlank()) return stream
                }
            } catch (e: Exception) { }
        }
        return null
    }

    private suspend fun fetchFromConversionApi(videoId: String): String? {
        val engines = listOf(
            "https://yt-api.savetube.me/video/info/$videoId",
            "https://api.cobalt.tools/",
            "https://cobalt.meowing.de/"
        )
        
        for (engine in engines) {
            try {
                if (engine.contains("savetube")) {
                    val res = withTimeoutOrNull(5000) { Innertube.client.get(engine) } ?: continue
                    if (res.status.value == 200) {
                        val json = Innertube.json.parseToJsonElement(res.bodyAsText()).jsonObject
                        val streamUrl = json["url"]?.jsonPrimitive?.content
                        if (!streamUrl.isNullOrBlank()) return streamUrl
                    }
                } else if (engine.contains("cobalt")) {
                    val res = withTimeoutOrNull(5000) {
                        Innertube.client.post(engine) {
                            header(HttpHeaders.ContentType, "application/json")
                            header(HttpHeaders.Accept, "application/json")
                            setBody(buildJsonObject { 
                                put("url", "https://www.youtube.com/watch?v=$videoId")
                                put("downloadMode", "audio")
                                put("audioFormat", "mp3")
                            } as JsonElement)
                        }
                    } ?: continue
                    if (res.status.value == 200) {
                        val json = Innertube.json.parseToJsonElement(res.bodyAsText()).jsonObject
                        return json["url"]?.jsonPrimitive?.content
                    }
                }
            } catch (e: Exception) { 
                Log.w(TAG, "Engine $engine failed for $videoId: ${e.message}")
            }
        }
        return null
    }

    private suspend fun fetchFromSoundCloudFallback(videoId: String): String? {
        return null
    }

    private suspend fun fetchFromProxyPool(videoId: String): ProxyResult {
        val instances = listOf(
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://inv.nadeko.net",
            "https://invidious.f5.si",
            "https://iv.ggtyler.dev"
        ).shuffled()
        
        var errs = ""
        for (instance in instances) {
            try {
                val target = "$instance/latest_version?id=$videoId&itag=140&local=true"
                Log.d(TAG, "Trying Invidious Proxy: $instance")
                
                val response = withTimeoutOrNull(5000) {
                    Innertube.client.get(target) {
                        header(HttpHeaders.Range, "bytes=0-1024")
                        header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    }
                } ?: continue

                if (response.status.value < 400) {
                    val contentType = response.headers[HttpHeaders.ContentType] ?: ""
                    if (contentType.contains("audio") || contentType.contains("video") || contentType.contains("application/octet-stream")) {
                         Log.i(TAG, "✅ Proxy Success: $instance")
                         return ProxyResult.Success(target)
                    } else errs += "NotAudio(${response.status.value}); "
                } else errs += "${response.status.value}; "
            } catch (e: Exception) { 
                Log.w(TAG, "Proxy $instance failed: ${e.message}")
                errs += "Err; " 
            }
        }
        return ProxyResult.Failure(errs.take(50))
    }

    private fun cache(videoId: String, stream: ResolvedStream, response: PlayerResponse) {
        val expiresIn = response.streamingData?.expiresInSeconds?.toLong() ?: 21600L
        cachedUrls[videoId] = stream to (System.currentTimeMillis() + (expiresIn * 1000) - 60000)
    }

    private suspend fun fetchFromArchiveFallback(song: Song): String? {
        val idSearchUrl = "https://archive.org/advancedsearch.php?q=${song.id}&output=json"
        try {
            val idRes = Innertube.client.get(idSearchUrl)
            if (idRes.status.value == 200) {
                val json = Innertube.json.parseToJsonElement(idRes.bodyAsText()).jsonObject
                val docs = json["response"]?.jsonObject?.get("docs")?.jsonArray
                docs?.firstOrNull()?.jsonObject?.get("identifier")?.jsonPrimitive?.content?.let { id ->
                    val fileUrl = findFileInItem(id, song.id)
                    if (fileUrl != null) return fileUrl
                }
            }
        } catch (e: Exception) { Log.w(TAG, "Archive ID search failed") }

        val query = URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
        val searchUrl = "https://archive.org/advancedsearch.php?q=$query+format:mp3&output=json&limit=5"
        
        return try {
            val response = Innertube.client.get(searchUrl)
            if (response.status.value == 200) {
                val json = Innertube.json.parseToJsonElement(response.bodyAsText()).jsonObject
                val docs = json["response"]?.jsonObject?.get("docs")?.jsonArray
                
                for (doc in docs ?: emptyList()) {
                    val id = doc.jsonObject["identifier"]?.jsonPrimitive?.content ?: continue
                    val fileUrl = findFileInItem(id, song.title)
                    if (fileUrl != null) return fileUrl
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Archive.org fallback failed: ${e.message}")
            null
        }
    }

    private suspend fun findFileInItem(itemId: String, matchHint: String): String? {
        return try {
            val fileListUrl = "https://archive.org/metadata/$itemId/files"
            val filesRes = Innertube.client.get(fileListUrl)
            val filesJson = Innertube.json.parseToJsonElement(filesRes.bodyAsText()).jsonObject
            val files = filesJson["result"]?.jsonArray ?: return null
            
            val bestFile = files.filter { 
                val name = it.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                val fmt = it.jsonObject["format"]?.jsonPrimitive?.content ?: ""
                (fmt.contains("MP3") || fmt.contains("MPEG-4 Audio") || fmt.contains("WebM")) &&
                (name.contains(matchHint, ignoreCase = true) || name.contains(matchHint.replace("-", "_")))
            }.minByOrNull { 
                val fmt = it.jsonObject["format"]?.jsonPrimitive?.content ?: ""
                if (fmt.contains("MP3") || fmt.contains("Audio")) 0 else 1
            }?.jsonObject?.get("name")?.jsonPrimitive?.content

            bestFile?.let { "https://archive.org/download/$itemId/$it" }
        } catch (e: Exception) { null }
    }

    private fun extractUrlFromResponse(response: PlayerResponse): String? {
        val streamingData = response.streamingData ?: return null
        if (!streamingData.hlsManifestUrl.isNullOrBlank()) return streamingData.hlsManifestUrl
        
        val formats = (streamingData.adaptiveFormats ?: emptyList()) + (streamingData.formats ?: emptyList())
        val bestFormat = formats.filter { it.isAudio && it.url != null }
            .sortedByDescending { if (it.itag == 140) 1000000 else it.bitrate ?: 0 }
            .firstOrNull() ?: formats.firstOrNull { it.url != null }

        if (bestFormat?.url != null) return bestFormat.url
        
        Log.w(TAG, "Only ciphered formats available, skipping to fallback.")
        return null
    }

    sealed class ProxyResult {
        data class Success(val url: String) : ProxyResult()
        data class Failure(val message: String) : ProxyResult()
    }
}
