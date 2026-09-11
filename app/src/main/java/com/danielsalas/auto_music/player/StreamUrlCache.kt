package com.danielsalas.auto_music.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import java.util.*
import kotlin.collections.HashMap

internal data class CachedStreamUrl(
    val url: String,
    val requestHeaders: Map<String, String>,
    val clientName: String,
    val expiresInSeconds: Int,
    val requireBoundedRange: Boolean = false,
    val rangeChunkSizeBytes: Long = 0L,
    val useRangeChunks: Boolean = false,
)

@UnstableApi
internal fun DataSpec.withResolvedStream(stream: CachedStreamUrl): DataSpec {
    val builder = buildUpon()
        .setUri(Uri.parse(stream.url))
        .setHttpRequestHeaders(httpRequestHeaders + stream.requestHeaders)
    
    // If it's a full download (length == UNSET or large) and we are not forcing chunks, don't subrange.
    // DownloadManager usually sets flags or custom data, but we can check if length is UNSET.
    if ((!stream.requireBoundedRange && !stream.useRangeChunks) || stream.rangeChunkSizeBytes <= 0L) {
        return builder.build()
    }
    
    // Only subrange for transient playback if requested. 
    // For downloads, we should probably ignore this and get the full file.
    // If DataSpec has FLAG_ALLOW_CACHE_FRAGMENTATION or similar, it might be a download.
    if (length == C.LENGTH_UNSET.toLong()) {
        // If length is unset, it's likely starting a full download/stream.
        // We only cap it if the stream ABSOLUTELY requires it to work.
        if (stream.requireBoundedRange) {
            builder.setLength(stream.rangeChunkSizeBytes)
        }
    }
    
    return builder.build()
}

internal class StreamUrlCache(
    private val maxEntries: Int = 500,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val stream: CachedStreamUrl,
        val expiresAtMillis: Long,
    )

    private val entries = object : LinkedHashMap<String, Entry>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }
    private val generations = HashMap<String, Long>()

    operator fun get(mediaId: String): CachedStreamUrl? = synchronized(entries) {
        val entry = entries[mediaId] ?: return@synchronized null
        if (entry.expiresAtMillis <= currentTimeMillis()) {
            entries.remove(mediaId)
            advanceGeneration(mediaId)
            null
        } else {
            entry.stream
        }
    }

    fun generation(mediaId: String): Long = synchronized(entries) {
        generations[mediaId] ?: 0L
    }

    fun put(
        mediaId: String,
        url: String,
        requestHeaders: Map<String, String>,
        clientName: String,
        expiresInSeconds: Int,
        requireBoundedRange: Boolean = false,
        rangeChunkSizeBytes: Long = 0L,
        useRangeChunks: Boolean = false,
        expectedGeneration: Long = generation(mediaId),
    ): Boolean {
        val now = currentTimeMillis()
        val ttlMillis = expiresInSeconds.coerceAtLeast(0).toLong() * 1_000L
        val expiresAtMillis = now + ttlMillis

        synchronized(entries) {
            if ((generations[mediaId] ?: 0L) != expectedGeneration) return false
            entries[mediaId] = Entry(
                stream = CachedStreamUrl(
                    url = url,
                    requestHeaders = requestHeaders.toMap(),
                    clientName = clientName,
                    expiresInSeconds = expiresInSeconds,
                    requireBoundedRange = requireBoundedRange,
                    rangeChunkSizeBytes = rangeChunkSizeBytes,
                    useRangeChunks = useRangeChunks,
                ),
                expiresAtMillis = expiresAtMillis,
            )
            return true
        }
    }

    fun invalidate(mediaId: String) {
        synchronized(entries) {
            entries.remove(mediaId)
            advanceGeneration(mediaId)
        }
    }

    private fun advanceGeneration(mediaId: String) {
        generations[mediaId] = (generations[mediaId] ?: 0L) + 1L
    }
}
