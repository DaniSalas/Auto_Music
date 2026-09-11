package com.danielsalas.auto_music.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
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
    
    // Use lazy for anything that touches files/DB to avoid constructor crashes
    val downloadCache: Cache by lazy { PlayerCache.getDownloadCache(context) }
    val playerCache: Cache by lazy { PlayerCache.getInstance(context) }
    val databaseProvider by lazy { PlayerCache.getDatabaseProvider(context) }
    private val musicDao by lazy { com.danielsalas.auto_music.data.local.MusicDatabase.getDatabase(context).musicDao() }
    
    private val songUrlCache = StreamUrlCache()
    private val streamHttpClient = OkHttpClient.Builder().build()
    
    // Factory for the DownloadManager - MUST write to downloadCache
    val downloadDataSourceFactory: DataSource.Factory by lazy {
        ResolvingDataSource.Factory(
            CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(streamHttpClient))
                .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(downloadCache))
        ) { dataSpec ->
            resolveDataSpec(dataSpec)
        }
    }

    // Factory for regular playback - Should check downloadCache first, then use playerCache
    val dataSourceFactory: DataSource.Factory by lazy {
        DataSource.Factory {
            val upstreamFactory = OkHttpDataSource.Factory(streamHttpClient)
            
            val playerCacheFactory = CacheDataSource.Factory()
                .setCache(playerCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                
            val downloadCacheFactory = CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(playerCacheFactory)
                .setCacheWriteDataSinkFactory(null) 
                
            ResolvingDataSource(downloadCacheFactory.createDataSource()) { dataSpec ->
                resolveDataSpec(dataSpec)
            }
        }
    }

    private fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
        val uriString = dataSpec.uri.toString()
        val mediaId = if (uriString.startsWith("youtube://")) {
            uriString.removePrefix("youtube://")
        } else {
            // If it's not our scheme, check if we have a key that looks like an ID
            val key = dataSpec.key
            if (key != null && !key.contains("http") && !key.contains("/")) {
                key.substringAfter("|")
            } else {
                return dataSpec
            }
        }
        
        // IMPORTANT: We MUST have a stable cache key (the song ID) 
        // to find the song in the download cache even if the URL changes.
        val fixedDataSpec = if (dataSpec.key == null) {
            dataSpec.buildUpon().setKey(mediaId).build()
        } else {
            dataSpec
        }
        
        // Check our URL cache
        songUrlCache[mediaId]?.let { cachedStream ->
            return fixedDataSpec.withResolvedStream(cachedStream)
        }
        
        val cacheGeneration = songUrlCache.generation(mediaId)

        // Resolve online stream
        val stream = try {
            runBlocking(Dispatchers.IO) {
                val song = musicDao.getSongById(mediaId) ?: com.danielsalas.auto_music.model.Song(id = mediaId, title = "", artist = "", thumbnailUrl = "")
                InnertubeResolver.resolveStream(context, song)
            }
        } catch (e: Exception) {
            Log.e("DownloadUtil", "Resolution failed for $mediaId: ${e.message}")
            return fixedDataSpec 
        }
        
        if (stream.url.isNotEmpty()) {
            Log.d("DownloadUtil", "Resolved URL for $mediaId: ${stream.url.take(50)}...")
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
            
            return fixedDataSpec.withResolvedStream(cached)
        } else {
            return fixedDataSpec
        }
    }

    val downloadNotificationHelper by lazy { DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID) }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            downloadDataSourceFactory,
            Executor { it.run() }
        ).apply {
            maxParallelDownloads = 6 // Aumentado para descargas masivas
            requirements = androidx.media3.exoplayer.scheduler.Requirements(0) 
            
            addListener(object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    val count = try { downloadManager.downloadIndex.getDownloads().count } catch (e: Exception) { -1 }
                    Log.d("DownloadUtil", "DownloadManager Initialized. Items in index: $count")
                    downloadManager.resumeDownloads()
                }

                override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                    Log.d("DownloadUtil", "Download ${download.request.id} -> ${getStateString(download.state)} (${download.percentDownloaded}%)")
                    downloads.update { it.toMutableMap().apply { put(download.request.id, download) } }
                    
                    if (download.state == Download.STATE_COMPLETED) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val song = musicDao.getSongById(download.request.id)
                                if (song != null) {
                                    musicDao.insertSong(song.copy(isDownloaded = true))
                                    Log.i("DownloadUtil", "✅ Descarga completada y guardada: ${song.title}")
                                }
                            } catch (e: Exception) {
                                Log.e("DownloadUtil", "Error al actualizar DB: ${e.message}")
                            }
                        }
                    } else if (download.state == Download.STATE_FAILED) {
                        Log.e("DownloadUtil", "❌ Error en descarga: ${download.request.id}, error: ${finalException?.message}")
                        if (isExpiredStreamError(finalException)) {
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
            resumeDownloads()
        }
    }

    private fun isExpiredStreamError(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                if (current.responseCode == 403 || current.responseCode == 410 || current.responseCode == 416) {
                    return true
                }
            }
            if (current.message?.contains("403") == true || current.message?.contains("410") == true || current.message?.contains("416") == true) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun getStateString(state: Int): String = when(state) {
        Download.STATE_QUEUED -> "QUEUED"
        Download.STATE_DOWNLOADING -> "DOWNLOADING"
        Download.STATE_COMPLETED -> "COMPLETED"
        Download.STATE_FAILED -> "FAILED"
        Download.STATE_REMOVING -> "REMOVING"
        Download.STATE_RESTARTING -> "RESTARTING"
        Download.STATE_STOPPED -> "STOPPED"
        else -> "UNKNOWN"
    }

    init {
        // Run index loading on a background thread but don't block constructor
        scope.launch(Dispatchers.IO) {
            try {
                val result = mutableMapOf<String, Download>()
                val cursor = downloadManager.downloadIndex.getDownloads()
                while (cursor.moveToNext()) {
                    result[cursor.download.request.id] = cursor.download
                }
                downloads.value = result
                Log.d("DownloadUtil", "Init loaded ${result.size} downloads from index")
            } catch (e: Exception) {
                Log.e("DownloadUtil", "Error loading download index: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "DownloadUtil"
        @Volatile
        private var INSTANCE: DownloadUtil? = null

        fun getInstance(context: Context): DownloadUtil {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    DownloadUtil(context.applicationContext).also { INSTANCE = it }
                } catch (e: Exception) {
                    Log.e(TAG, "FATAL: Failed to create DownloadUtil: ${e.message}")
                    // Create a dummy instance or rethrow if it's too critical
                    // For now, rethrow so we can see the crash in logs if possible
                    throw e
                }
            }
        }

        fun reset() {
            // Keep this for now just in case, but usually not needed with fixed folder
        }
    }
}
