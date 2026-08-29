package com.danielsalas.auto_music

import com.danielsalas.auto_music.player.InnertubeResolver
import com.danielsalas.auto_music.model.Song
import com.danielsalas.auto_music.data.remote.Innertube
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResolverTest {
    @Test
    fun testU2Resolution() = runBlocking {
        val videoId = "oNvWDP_GkiY"
        val song = Song(id = videoId, title = "With or Without You", artist = "U2", thumbnailUrl = "")
        val stream = InnertubeResolver.resolveStream(song)
        
        println("U2 Resolved Source: ${stream.status}")
        println("U2 URL: ${stream.url}")
        
        assertTrue("U2 should resolve to something", stream.url.isNotEmpty())
        verifyUrlIsPlayable(stream)
    }

    @Test
    fun testMClanResolution() = runBlocking {
        val videoId = "Yo7SJEDYf_k"
        val song = Song(id = videoId, title = "Carolina", artist = "M-Clan", thumbnailUrl = "")
        val stream = InnertubeResolver.resolveStream(song)
        
        println("M-Clan Resolved Source: ${stream.status}")
        println("M-Clan URL: ${stream.url}")
        
        assertTrue("M-Clan should resolve to something", stream.url.isNotEmpty())
        verifyUrlIsPlayable(stream)
    }

    private suspend fun verifyUrlIsPlayable(stream: InnertubeResolver.ResolvedStream) {
        try {
            val response = Innertube.client.get(stream.url) {
                stream.headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=0-1024") // Just the beginning
            }
            println("HTTP Status for ${stream.url.take(50)}... : ${response.status}")
            assertTrue("URL should be reachable (200 or 206), got ${response.status}", 
                response.status.value == 200 || response.status.value == 206)
            
            val contentType = response.headers[HttpHeaders.ContentType]
            println("Content-Type: $contentType")
            assertTrue("Should be audio or video, got $contentType", 
                contentType?.contains("audio") == true || contentType?.contains("video") == true || contentType?.contains("application/octet-stream") == true)
        } catch (e: Exception) {
            println("❌ Playability check failed: ${e.message}")
            throw e
        }
    }
}
