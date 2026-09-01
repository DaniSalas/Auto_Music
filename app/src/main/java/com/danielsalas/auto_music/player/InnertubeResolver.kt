package com.danielsalas.auto_music.player

import android.content.Context
import android.util.Log
import com.danielsalas.auto_music.data.remote.Innertube
import com.danielsalas.auto_music.model.Song
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.*
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object InnertubeResolver {
    private const val TAG = "InnertubeResolver"

    private val bundleMutex = Mutex()
    private var extractor: InnerTubeExtractor? = null
    
    private val tokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(
            providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
            usesWebView = true
        )

        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? {
            return com.danielsalas.auto_music.utils.potoken.PoTokenGenerator(com.danielsalas.auto_music.data.remote.Innertube.extractionTransport().httpClient.let { null } ?: return null) // dummy implementation for now, need actual context
                .let { null } // needs better integration
        }
        
        // Use a simpler approach for now: Innertube class handles poToken
        override suspend fun prewarm(cookie: String?) {}
        override suspend fun close() {}
    }

    private val logger = InnerTubeLogger { event ->
        val msg = event.message + event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
        when (event.level) {
            InnerTubeLogLevel.DEBUG -> Log.d(event.tag, msg)
            InnerTubeLogLevel.INFO -> Log.i(event.tag, msg)
            InnerTubeLogLevel.WARN -> Log.w(event.tag, msg)
            InnerTubeLogLevel.ERROR -> Log.e(event.tag, msg)
        }
    }

    fun initialize(context: Context) {
        // Initialization happens lazily in getExtractor()
    }

    private suspend fun getExtractor(context: Context): InnerTubeExtractor {
        extractor?.let { return it }
        return bundleMutex.withLock {
            extractor?.let { return@withLock it }
            
            val transport = Innertube.extractionTransport()
            val configRepo = AndroidPlayerConfigRepository(context.applicationContext)
            val remoteStore = RemotePlayerConfigStore(transport.httpClient, configRepo, logger)
            val cipherService = YouTubeCipherService(transport.httpClient, remoteStore, logger)
            
            val newExtractor = InnerTubeExtractor(
                configParser = YtConfigParserImpl(
                    transport.httpClient,
                    transport.innerTube,
                    remoteStore,
                    logger
                ),
                cipherService = cipherService,
                innerTube = transport.innerTube,
                tokenProvider = object : TokenProvider {
                    override val capabilities = TokenProviderCapabilities(setOf(PoTokenProviderKind.WEB_BOTGUARD), true)
                    override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? {
                        // Integrate with Auto_Music's PoTokenGenerator
                        val gen = com.danielsalas.auto_music.utils.potoken.PoTokenGenerator(context.applicationContext)
                        return gen.getWebClientPoToken(videoId, visitorData)?.let {
                            PoTokenResult(it.playerRequestPoToken, it.streamingDataPoToken, visitorData)
                        }
                    }
                    override suspend fun prewarm(cookie: String?) {}
                    override suspend fun close() {}
                },
                logger = logger
            )
            extractor = newExtractor
            newExtractor
        }
    }

    suspend fun resolveStream(context: Context, song: Song): ResolvedStream {
        val videoId = song.id
        
        if (videoId.startsWith("http") || videoId.startsWith("content") || videoId.startsWith("file")) {
            return ResolvedStream(videoId, "Mozilla/5.0", status = "Raw URL")
        }

        Log.i(TAG, "🔍 Resolving stream for: $videoId")
        
        return try {
            val extractor = getExtractor(context)
            val stream = withContext(Dispatchers.IO) {
                extractor.extract(
                    videoId = videoId,
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                    audioQuality = com.metrolist.innertubex.extraction.AudioQuality.AUTO,
                    hints = ContentHints()
                )
            }
            
            if (stream != null) {
                ResolvedStream(
                    url = stream.audioUrl,
                    userAgent = stream.headers["User-Agent"] ?: "Mozilla/5.0",
                    status = "Success (${stream.clientName})",
                    headers = stream.headers,
                    mimeType = stream.mimeType ?: "audio/mp4",
                    requireBoundedRange = stream.requireBoundedRange,
                    rangeChunkSizeBytes = stream.rangeChunkSizeBytes,
                    useRangeChunks = stream.useRangeChunks,
                    expiresInSeconds = stream.expiresAt?.let { (it.toEpochMilliseconds() - System.currentTimeMillis()) / 1000 }?.toInt() ?: 3600
                )
            } else {
                Log.e(TAG, "❌ Extractor returned null for $videoId")
                ResolvedStream("", "", status = "FAILED", diagnosticLog = "Extractor returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Extraction error for $videoId: ${e.message}")
            ResolvedStream("", "", status = "ERROR", diagnosticLog = e.message ?: "Unknown Error")
        }
    }

    data class ResolvedStream(
        val url: String,
        val userAgent: String,
        val status: String = "OK",
        val diagnosticLog: String = "",
        val mimeType: String = "audio/mp4",
        val headers: Map<String, String> = emptyMap(),
        val requireBoundedRange: Boolean = false,
        val rangeChunkSizeBytes: Long = 0L,
        val useRangeChunks: Boolean = false,
        val expiresInSeconds: Int = 3600
    )
}
