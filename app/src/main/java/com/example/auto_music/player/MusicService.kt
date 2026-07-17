package com.example.auto_music.player

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
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
import java.io.File

@UnstableApi
class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var repository: MusicRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var cache: SimpleCache

    private val COMMAND_RW_10 = SessionCommand("CUSTOM_COMMAND_RW_10", Bundle.EMPTY)
    private val COMMAND_FF_30 = SessionCommand("CUSTOM_COMMAND_FF_30", Bundle.EMPTY)

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(defaultDataSourceFactory) { dataSpec ->
            val uriString = dataSpec.uri.toString()
            
            if (uriString.startsWith("file") || uriString.startsWith("content") || uriString.contains("googlevideo.com")) {
                return@Factory dataSpec
            }

            val videoId = dataSpec.key ?: uriString.substringAfter("v=", "").substringBefore("&")
            
            if (videoId.isEmpty()) {
                return@Factory dataSpec
            }

            val stream = try {
                runBlocking(Dispatchers.IO) {
                    InnertubeResolver.resolveStream(videoId)
                }
            } catch (e: Exception) {
                null
            }
            
            if (stream != null) {
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
            .setSeekForwardIncrementMs(30000)
            .setSeekBackIncrementMs(10000)
            .build()

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
        
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(COMMAND_RW_10)
                .add(COMMAND_FF_30)
                .build()
            
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SET_MEDIA_ITEM)
                .add(Player.COMMAND_PREPARE)
                .build()

            val customLayout = listOf(
                CommandButton.Builder()
                    .setDisplayName("RW 10s")
                    .setIconResId(android.R.drawable.ic_media_rew)
                    .setSessionCommand(COMMAND_RW_10)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName("FF 30s")
                    .setIconResId(android.R.drawable.ic_media_ff)
                    .setSessionCommand(COMMAND_FF_30)
                    .build()
            )

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "CUSTOM_COMMAND_RW_10" -> player.seekBack()
                "CUSTOM_COMMAND_FF_30" -> player.seekForward()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            
            serviceScope.launch {
                val updatedItems = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    val songId = item.mediaId
                    val playlistId = item.mediaMetadata.extras?.getString("playlistId")?.toLongOrNull()

                    if (playlistId != null && mediaItems.size == 1) {
                        val playlistSongs = repository.getSongsInPlaylist(playlistId).first()
                        val startIndex = playlistSongs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                        
                        // Rotamos la lista para que la canción seleccionada sea la primera en la cola
                        val rotatedList = playlistSongs.drop(startIndex) + playlistSongs.take(startIndex)
                        rotatedList.forEach { updatedItems.add(createMediaItem(it, playlistId)) }
                        future.set(updatedItems)
                        return@launch
                    } else if (songId.length > 5 && !songId.startsWith("PLAYLIST_") && songId != "ROOT") {
                        val song = repository.getSongById(songId)
                        if (song != null) {
                            updatedItems.add(createMediaItem(song, null))
                        } else {
                            updatedItems.add(item)
                        }
                    } else {
                        updatedItems.add(item)
                    }
                }
                future.set(updatedItems)
            }
            
            return future
        }

        private fun createMediaItem(song: com.example.auto_music.model.Song, playlistId: Long?): MediaItem {
            val localUri = if (song.isDownloaded && song.audioUrl != null) {
                val file = File(song.audioUrl)
                if (file.exists()) Uri.fromFile(file).toString() else null
            } else null

            val finalUri = localUri ?: "https://music.youtube.com/watch?v=${song.id}"
            
            val extras = Bundle().apply {
                if (playlistId != null) putString("playlistId", playlistId.toString())
                putLong("android.media.metadata.DURATION", song.duration * 1000L)
            }
            
            return MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(finalUri)
                .setCustomCacheKey(song.id)
                .setMimeType("audio/mpeg")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(song.thumbnailUrl.toUri())
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }
            
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Auto Music")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(extras)
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
                        val extras = Bundle().apply {
                            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                        }
                        MediaItem.Builder()
                            .setMediaId("PLAYLIST_${playlist.id}")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(playlist.name)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                    .setExtras(extras)
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
                        val localUri = if (song.isDownloaded && song.audioUrl != null) {
                            val file = File(song.audioUrl)
                            if (file.exists()) Uri.fromFile(file).toString() else null
                        } else null

                        val finalUri = localUri ?: "https://music.youtube.com/watch?v=${song.id}"
                        
                        val extras = Bundle().apply {
                            putString("playlistId", playlistId.toString())
                            putLong("android.media.metadata.DURATION", song.duration * 1000L)
                            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
                        }

                        MediaItem.Builder()
                            .setMediaId(song.id)
                            .setUri(finalUri)
                            .setCustomCacheKey(song.id)
                            .setMimeType("audio/mpeg")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setArtworkUri(song.thumbnailUrl.toUri())
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .setExtras(extras)
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
