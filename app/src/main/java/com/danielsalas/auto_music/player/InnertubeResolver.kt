package com.danielsalas.auto_music.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.danielsalas.auto_music.data.remote.Innertube
import com.danielsalas.auto_music.data.remote.model.YouTubeClient
import com.danielsalas.auto_music.model.Song
import com.danielsalas.auto_music.utils.YouTubeSolver
import java.net.URLDecoder

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

    suspend fun resolveStream(context: Context, song: Song): ResolvedStream {
        val videoId = song.id
        
        if (videoId.startsWith("http") || videoId.startsWith("content") || videoId.startsWith("file")) {
            return ResolvedStream(videoId, "Mozilla/5.0", status = "Raw URL")
        }

        val now = System.currentTimeMillis()
        if (cachedUrls.containsKey(videoId)) {
            val (stream, expiry) = cachedUrls[videoId]!!
            if (now < expiry) return stream
        }

        Log.i(TAG, "🔍 Resolving stream via YouTubeSolver (JS Engine) for: $videoId")

        try {
            // Use WEB_REMIX as it's the most stable for music
            val client = YouTubeClient.WEB_REMIX
            val response = Innertube.player(videoId, client)
            
            if (response != null && response.streamingData != null) {
                val formats = response.streamingData.adaptiveFormats
                val format = formats?.filter { it.mimeType?.contains("audio") == true }
                    ?.maxByOrNull { it.bitrate ?: 0 }
                
                if (format != null) {
                    var finalUrl: String? = null
                    val playerScriptUrl = response.assets?.js ?: ""
                    
                    if (format.url != null) {
                        finalUrl = format.url
                    } else if (format.signatureCipher != null) {
                        // Extract sig and sp from cipher
                        val cipher = format.signatureCipher
                        val params = cipher.split("&").associate { 
                            val parts = it.split("=")
                            if (parts.size >= 2) {
                                parts[0] to URLDecoder.decode(parts[1], "UTF-8")
                            } else {
                                parts[0] to ""
                            }
                        }
                        val s = params["s"]
                        val sp = params["sp"] ?: "sig"
                        val baseUrl = params["url"]
                        
                        // Solve signature using JS Engine
                        val solved = YouTubeSolver.solve(context, playerScriptUrl, null, s)
                        val solvedSig = solved.second
                        
                        if (solvedSig != null && baseUrl != null) {
                            finalUrl = "$baseUrl&$sp=$solvedSig"
                        }
                    }
                    
                    if (finalUrl != null) {
                        // Solve N-Sig (nsig) to avoid throttling
                        val uri = Uri.parse(finalUrl)
                        val nParam = uri.getQueryParameter("n")
                        if (nParam != null) {
                            val solved = YouTubeSolver.solve(context, playerScriptUrl, nParam, null)
                            val solvedN = solved.first
                            if (solvedN != null) {
                                finalUrl = finalUrl.replace("n=$nParam", "n=$solvedN")
                            }
                        }
                        
                        val headers = mutableMapOf<String, String>()
                        headers["User-Agent"] = client.userAgent
                        headers["Referer"] = "https://music.youtube.com/"
                        headers["Origin"] = "https://music.youtube.com"
                        
                        val stream = ResolvedStream(
                            url = finalUrl,
                            userAgent = client.userAgent,
                            status = "Success",
                            headers = headers,
                            mimeType = format.mimeType ?: "audio/mp4"
                        )
                        
                        cachedUrls[videoId] = stream to (System.currentTimeMillis() + 300000)
                        return stream
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resolution error: ${e.message}")
        }

        Log.e(TAG, "❌ Resolution failed for $videoId")
        return ResolvedStream("", "", status = "FAILED", diagnosticLog = "Solver failed")
    }
}
