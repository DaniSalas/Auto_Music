package com.example.auto_music.player

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.*
import com.example.auto_music.data.MusicRepository
import com.example.auto_music.data.local.MusicDatabase
import com.example.auto_music.data.remote.YouTubeService
import com.example.auto_music.player.cache.PlayerCache
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import androidx.core.content.ContextCompat

@UnstableApi
class MusicService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var repository: MusicRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var cache: SimpleCache

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3")
        
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(defaultDataSourceFactory) { dataSpec ->
            val uriString = dataSpec.uri.toString()
            // Don't resolve local files or already resolved googlevideo URLs
            if (uriString.startsWith("file") || uriString.startsWith("content") || uriString.contains("googlevideo.com")) {
                return@Factory dataSpec
            }
            
            val videoId = dataSpec.key ?: uriString.substringAfter("v=", "").substringBefore("&")
            if (videoId.isEmpty()) return@Factory dataSpec
            
            // Resolve YouTube stream URL
            val stream = try { 
                runBlocking(Dispatchers.IO) { 
                    withTimeoutOrNull(8000) { InnertubeResolver.resolveStream(videoId) } 
                } 
            } catch (e: Exception) { 
                Log.e("MusicService", "Resolution error for $videoId: ${e.message}")
                null 
            }
            
            if (stream != null) {
                val headers = dataSpec.httpRequestHeaders.toMutableMap()
                headers["User-Agent"] = stream.userAgent
                if (stream.userAgent.contains("Mozilla")) {
                    headers["Referer"] = "https://music.youtube.com/"
                }
                return@Factory dataSpec.withUri(Uri.parse(stream.url)).withRequestHeaders(headers)
            }
            dataSpec
        }
        
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingDataSourceFactory)
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache).setFragmentSize(1024 * 1024))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != -1L) {
                val songId = context.getSharedPreferences("downloads", Context.MODE_PRIVATE).getString(id.toString(), null) ?: return
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx != -1 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(dir, "auto_music/$songId.mp3")
                        if (file.exists()) {
                            serviceScope.launch { repository.updateSongDownloadStatus(songId, file.absolutePath) }
                        }
                    }
                    cursor.close()
                }
                context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().remove("pending_$songId").apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "onCreate: v4.35")
        serviceScope.launch { com.example.auto_music.data.remote.Innertube.fetchVisitorData() }
        cache = PlayerCache.getInstance(applicationContext)
        val database = MusicDatabase.getDatabase(applicationContext)
        val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
        val retrofit = Retrofit.Builder().baseUrl("https://www.youtube.com/").client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
        repository = MusicRepository(database.musicDao(), retrofit.create(YouTubeService::class.java), applicationContext)

        val newPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(createDataSourceFactory()))
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        newPlayer.repeatMode = Player.REPEAT_MODE_ALL
        
        newPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val mId = item.mediaId
                    val playlistId = if (mId.contains("|")) mId.substringBefore("|").removePrefix("PL").toLongOrNull() else null
                    val songId = if (mId.contains("|")) mId.substringAfter("|") else mId
                    if (playlistId != null) {
                        serviceScope.launch { repository.updatePlaylistPlaybackState(playlistId, songId, 0L) }
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e("MusicService", "Player Error: ${error.message}", error)
            }
        })
        
        serviceScope.launch {
            while (isActive) {
                delay(5000)
                if (newPlayer.isPlaying) {
                    val item = newPlayer.currentMediaItem
                    val mId = item?.mediaId ?: ""
                    val playlistId = if (mId.contains("|")) mId.substringBefore("|").removePrefix("PL").toLongOrNull() else null
                    val songId = if (mId.contains("|")) mId.substringAfter("|") else mId
                    if (playlistId != null) {
                        repository.updatePlaylistPlaybackState(playlistId, songId, newPlayer.currentPosition)
                    }
                }
            }
        }

        player = newPlayer
        val intent = Intent(this, com.example.auto_music.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaLibrarySession.Builder(this, newPlayer, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()
        
        ContextCompat.registerReceiver(this, downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
                .build()

            val playerCommands = Player.Commands.Builder().addAllCommands().build()
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val firstItem = mediaItems.firstOrNull() ?: run {
                    future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
                    return@launch
                }
                
                val targetMId = firstItem.mediaId
                
                // Timeline check: match target with current timeline to avoid resets
                val currentPlayer = player
                var alreadyInTimeline = false
                val currentItems = mutableListOf<MediaItem>()
                var existingIndex = -1
                if (currentPlayer != null) {
                    for (i in 0 until currentPlayer.mediaItemCount) {
                        val item = currentPlayer.getMediaItemAt(i)
                        currentItems.add(item)
                        if (item.mediaId == targetMId || (item.mediaId.contains("|") && item.mediaId.substringAfter("|") == targetMId)) {
                            alreadyInTimeline = true
                            existingIndex = i
                        }
                    }
                }

                if (alreadyInTimeline && currentItems.size > 1) {
                    future.set(MediaSession.MediaItemsWithStartPosition(currentItems, existingIndex, startPositionMs))
                } else {
                    val playlistId = if (targetMId.contains("|")) targetMId.substringBefore("|").removePrefix("PL").toLongOrNull() 
                                     else firstItem.mediaMetadata.extras?.getString("playlistId")?.toLongOrNull()
                    
                    if (playlistId != null) {
                        val songs = repository.getSongsInPlaylist(playlistId).first()
                        val expandedItems = songs.map { createMediaItem(it, playlistId) }
                        val targetSongId = if (targetMId.contains("|")) targetMId.substringAfter("|") else targetMId
                        val index = expandedItems.indexOfFirst { it.mediaId.substringAfter("|") == targetSongId }.coerceAtLeast(0)
                        future.set(MediaSession.MediaItemsWithStartPosition(expandedItems, index, startPositionMs))
                    } else {
                        val updated = mediaItems.map { 
                            if (it.mediaId.length > 5 && !it.mediaId.contains("|")) {
                                repository.getSongById(it.mediaId)?.let { song -> createMediaItem(song, null) } ?: it
                            } else it
                        }
                        future.set(MediaSession.MediaItemsWithStartPosition(updated, startIndex, startPositionMs))
                    }
                }
            }
            return future
        }

        private fun createMediaItem(song: com.example.auto_music.model.Song, playlistId: Long?): MediaItem {
            val isLocal = song.isDownloaded && song.audioUrl != null && File(song.audioUrl).exists()
            val uri = if (isLocal) Uri.fromFile(File(song.audioUrl)).toString() else "https://music.youtube.com/watch?v=${song.id}"
            val compositeId = if (playlistId != null) "PL$playlistId|${song.id}" else song.id
            
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(song.thumbnailUrl.toUri())
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { 
                    if (playlistId != null) putString("playlistId", playlistId.toString())
                    putLong("android.media.metadata.DURATION", song.duration * 1000L)
                    putString("album", song.album)
                })
                .build()

            return MediaItem.Builder()
                .setMediaId(compositeId)
                .setUri(uri)
                .setMimeType("audio/mpeg")
                .setCustomCacheKey(song.id)
                .setMediaMetadata(metadata)
                .build()
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMetadata = MediaMetadata.Builder().setTitle("Auto Music").setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setExtras(Bundle().apply { putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1); putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1) }).build()
            return Futures.immediateFuture(LibraryResult.ofItem(MediaItem.Builder().setMediaId("ROOT").setMediaMetadata(rootMetadata).build(), params))
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    if (parentId == "ROOT") {
                        val playlists = repository.allPlaylists.first()
                        val items = playlists.map { playlist ->
                            MediaItem.Builder()
                                .setMediaId("PLAYLIST_${playlist.id}")
                                .setMediaMetadata(MediaMetadata.Builder()
                                    .setTitle(playlist.name)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                    .setExtras(Bundle().apply { putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) })
                                    .build())
                                .build()
                        }
                        future.set(LibraryResult.ofItemList(items, params))
                    } else if (parentId.startsWith("PLAYLIST_")) {
                        val playlistId = parentId.removePrefix("PLAYLIST_").toLong()
                        val songs = repository.getSongsInPlaylist(playlistId).first()
                        val items = songs.map { createMediaItem(it, playlistId) }
                        future.set(LibraryResult.ofItemList(items, params))
                    } else {
                        future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    }
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }
    }

    override fun onDestroy() {
        unregisterReceiver(downloadReceiver)
        mediaSession?.release(); player?.release(); serviceJob.cancel()
        super.onDestroy()
    }
}
