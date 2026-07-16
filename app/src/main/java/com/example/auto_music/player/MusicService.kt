package com.example.auto_music.player

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.auto_music.data.MusicRepository
import com.example.auto_music.data.local.MusicDatabase
import com.example.auto_music.data.remote.YouTubeService
import com.example.auto_music.player.cache.PlayerCache
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

@UnstableApi
class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var repository: MusicRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var cache: SimpleCache

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        android.util.Log.d("MusicService", "Creant DataSourceFactory...")
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(defaultDataSourceFactory) { dataSpec ->
            val uriString = dataSpec.uri.toString()
            
            if (uriString.startsWith("file") || uriString.contains("googlevideo.com")) {
                return@Factory dataSpec
            }

            val videoId = dataSpec.key ?: uriString.substringAfter("v=", "").substringBefore("&")
            
            if (videoId.isEmpty()) {
                return@Factory dataSpec
            }

            android.util.Log.i("MusicService", "Resolving stream for $videoId")
            val stream = try {
                runBlocking(Dispatchers.IO) {
                    InnertubeResolver.resolveStream(videoId)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error resolving $videoId", e)
                null
            }
            
            if (stream != null) {
                android.util.Log.d("MusicService", "Resolved URL for $videoId: ${stream.url.take(50)}...")
                val headers = dataSpec.httpRequestHeaders.toMutableMap()
                headers["User-Agent"] = stream.userAgent
                
                if (stream.userAgent.contains("Mozilla") && 
                    !stream.userAgent.contains("Android") && 
                    !stream.userAgent.contains("iPhone") &&
                    !stream.userAgent.contains("com.google.android")) {
                    headers["Referer"] = "https://music.youtube.com/"
                }

                return@Factory dataSpec.withUri(android.net.Uri.parse(stream.url))
                    .withRequestHeaders(headers)
            } else {
                android.util.Log.e("MusicService", "Failed to resolve stream for $videoId. Playback will likely fail.")
            }

            dataSpec
        }

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(512 * 1024)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != -1L) {
                val songId = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
                    .getString(id.toString(), null)
                
                if (songId == null) return

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    
                    if (statusIndex != -1 && localUriIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val localUri = cursor.getString(localUriIndex)
                            val fileUri = android.net.Uri.parse(localUri)
                            val filePath = fileUri.path
                            
                            filePath?.let { path ->
                                serviceScope.launch {
                                    repository.updateSongDownloadStatus(songId, path)
                                    android.util.Log.d("MusicService", "Descàrrega completada i actualitzada per a songId: $songId a $path")
                                }
                            }
                        }
                    }
                    cursor.close()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        serviceScope.launch {
            com.example.auto_music.data.remote.Innertube.fetchVisitorData()
        }
        
        cache = PlayerCache.getInstance(applicationContext)
        val database = MusicDatabase.getDatabase(applicationContext)
        
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.youtube.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        val youtubeService = retrofit.create(YouTubeService::class.java)
        repository = MusicRepository(database.musicDao(), youtubeService, applicationContext)

        val dataSourceFactory = createDataSourceFactory()

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("MusicService", "Error de reproducció (${error.errorCodeName}): ${error.message}", error)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        android.util.Log.d("MusicService", "Playback state: $playbackState")
                    }
                })
            }

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback()).build()
        
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map { item ->
                if (item.localConfiguration?.uri == null && item.mediaId.length > 5 && !item.mediaId.startsWith("PLAYLIST_") && item.mediaId != "ROOT") {
                    val songId = item.mediaId
                    val uri = "https://music.youtube.com/watch?v=$songId"
                    
                    item.buildUpon()
                        .setUri(uri)
                        .setCustomCacheKey(songId)
                        .setMimeType("audio/mpeg")
                        .build()
                } else {
                    item
                }
            }.toMutableList()
            return Futures.immediateFuture(updatedItems)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            if (parentId == "ROOT") {
                serviceScope.launch {
                    val playlists = repository.allPlaylists.first()
                    val items = playlists.map { playlist ->
                        MediaItem.Builder()
                            .setMediaId("PLAYLIST_${playlist.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(playlist.name)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                    .build()
                            )
                            .build()
                    }
                    future.set(LibraryResult.ofItemList(items, params))
                }
            } else if (parentId.startsWith("PLAYLIST_")) {
                val playlistId = parentId.removePrefix("PLAYLIST_").toLong()
                serviceScope.launch {
                    val songs = repository.getSongsInPlaylist(playlistId).first()
                    val items = songs.map { song ->
                        val uri = if (song.isDownloaded && song.audioUrl != null) {
                            android.net.Uri.fromFile(java.io.File(song.audioUrl)).toString()
                        } else {
                            "https://music.youtube.com/watch?v=${song.id}"
                        }
                        
                        MediaItem.Builder()
                            .setMediaId(song.id)
                            .setUri(uri)
                            .setCustomCacheKey(song.id)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setArtworkUri(song.thumbnailUrl.toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build()
                            )
                            .build()
                    }
                    future.set(LibraryResult.ofItemList(items, params))
                }
            } else {
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
            return future
        }
    }

    override fun onDestroy() {
        unregisterReceiver(downloadReceiver)
        mediaSession.run {
            release()
            if (player.playbackState != ExoPlayer.STATE_IDLE) {
                player.release()
            }
        }
        serviceJob.cancel()
        super.onDestroy()
    }
}
