package com.danielsalas.auto_music.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.danielsalas.auto_music.player.cache.PlayerCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.Executor

@OptIn(UnstableApi::class)
class DownloadUtil(private val context: Context) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    
    val downloadCache: Cache = PlayerCache.getDownloadCache(context)
    val playerCache: Cache = PlayerCache.getInstance(context)
    val databaseProvider = PlayerCache.getDatabaseProvider(context)
    private val musicDao = com.danielsalas.auto_music.data.local.MusicDatabase.getDatabase(context).musicDao()
    
    private val songUrlCache = StreamUrlCache()
    private val streamHttpClient = OkHttpClient.Builder().build()
    
    val dataSourceFactory = ResolvingDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(playerCache) // Use playerCache for intermediate caching
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(streamHttpClient))
            .setCacheWriteDataSinkFactory(null) 
    ) { dataSpec ->
        val mediaId = if (dataSpec.key != null && !dataSpec.key!!.contains("http")) {
            dataSpec.key!!.substringAfter("|")
        } else {
            val uriString = dataSpec.uri.toString()
            if (uriString.startsWith("youtube://")) {
                uriString.removePrefix("youtube://")
            } else {
                return@Factory dataSpec
            }
        }
        
        // Check playerCache first
        if (playerCache.isCached(mediaId, dataSpec.position, if (dataSpec.length >= 0) dataSpec.length else 1)) {
            return@Factory dataSpec
        }

        // Check our URL cache
        songUrlCache[mediaId]?.let { cachedStream ->
            return@Factory dataSpec.withResolvedStream(cachedStream)
        }
        
        val cacheGeneration = songUrlCache.generation(mediaId)

        // Resolve online stream
        val stream = try {
            runBlocking(Dispatchers.IO) {
                val song = musicDao.getSongById(mediaId) ?: com.danielsalas.auto_music.model.Song(id = mediaId, title = "", artist = "", thumbnailUrl = "")
                InnertubeResolver.resolveStream(context, song)
            }
        } catch (e: Exception) {
            throw IOException("Resolution failed for $mediaId: ${e.message}")
        }
        
        if (stream.url.isNotEmpty()) {
            val cached = CachedStreamUrl(
                url = stream.url,
                requestHeaders = stream.headers,
                clientName = stream.status,
                expiresInSeconds = stream.expiresInSeconds,
                requireBoundedRange = stream.requireBoundedRange,
                rangeChunkSizeBytes = stream.rangeChunkSizeBytes,
                useRangeChunks = stream.useRangeChunks
            )
            
            songUrlCache.put(
                mediaId = mediaId,
                url = stream.url,
                requestHeaders = stream.headers,
                clientName = stream.status,
                expiresInSeconds = stream.expiresInSeconds,
                requireBoundedRange = stream.requireBoundedRange,
                rangeChunkSizeBytes = stream.rangeChunkSizeBytes,
                useRangeChunks = stream.useRangeChunks,
                expectedGeneration = cacheGeneration
            )
            
            dataSpec.withResolvedStream(cached)
        } else {
            throw IOException("Resolution returned empty URL for $mediaId: ${stream.diagnosticLog}")
        }
    }

    val downloadNotificationHelper = DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    val downloadManager: DownloadManager = DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        dataSourceFactory,
        Executor { it.run() }
    ).apply {
        maxParallelDownloads = 3
        addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                downloads.update { it.toMutableMap().apply { put(download.request.id, download) } }
                
                if (download.state == Download.STATE_COMPLETED) {
                    scope.launch {
                        val song = musicDao.getSongById(download.request.id)
                        if (song != null) {
                            musicDao.insertSong(song.copy(isDownloaded = true))
                        }
                        context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().remove("pending_${download.request.id}").apply()
                    }
                } else if (download.state == Download.STATE_FAILED) {
                    context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().remove("pending_${download.request.id}").apply()
                    if (finalException is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && (finalException.responseCode == 403 || finalException.responseCode == 410)) {
                        songUrlCache.invalidate(download.request.id)
                    }
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                downloads.update { it.toMutableMap().apply { remove(download.request.id) } }
                scope.launch {
                    val song = musicDao.getSongById(download.request.id)
                    if (song != null) {
                        musicDao.insertSong(song.copy(isDownloaded = false))
                    }
                }
            }
        })
    }

    init {
        val result = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            result[cursor.download.request.id] = cursor.download
        }
        downloads.value = result
    }

    companion object {
        @Volatile
        private var INSTANCE: DownloadUtil? = null

        fun getInstance(context: Context): DownloadUtil {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadUtil(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
