package com.danielsalas.auto_music

import com.danielsalas.auto_music.player.InnertubeResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverTest {
    @Test
    fun testResolveKnownSong() = runBlocking {
        // Test with a well-known stable video ID (e.g., Rick Astley - Never Gonna Give You Up: dQw4w9WgXcQ)
        val videoId = "dQw4w9WgXcQ"
        val stream = InnertubeResolver.resolveStream(videoId)
        
        println("Resolved stream result: $stream")
        assertNotNull("Stream should not be null", stream)
        assertTrue("Stream URL should not be blank", !stream?.url.isNullOrBlank())
    }
}
