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

    private val COMMAND_SKIP_BACK = SessionCommand("COMMAND_SKIP_BACK", Bundle.EMPTY)
    private val COMMAND_SKIP_FORWARD = SessionCommand("COMMAND_SKIP_FORWARD", Bundle.EMPTY)

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3")
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val resolvingDataSourceFactory = ResolvingDataSource.Factory(defaultDataSourceFactory) { dataSpec ->
            val uriString = dataSpec.uri.toString()
            if (uriString.startsWith("file") || uriString.startsWith("content") || uriString.contains("googlevideo.com")) return@Factory dataSpec
            val videoId = dataSpec.key ?: uriString.substringAfter("v=", "").substringBefore("&")
            if (videoId.isEmpty()) return@Factory dataSpec
            val stream = try { runBlocking(Dispatchers.IO) { withTimeoutOrNull(10000) { InnertubeResolver.resolveStream(videoId) } } } catch (e: Exception) { null }
            if (stream != null) {
                val headers = dataSpec.httpRequestHeaders.toMutableMap()
                headers["User-Agent"] = stream.userAgent
                if (stream.userAgent.contains("Mozilla")) headers["Referer"] = "https://music.youtube.com/"
                return@Factory dataSpec.withUri(Uri.parse(stream.url)).withRequestHeaders(headers)
            }
            dataSpec
        }
        return CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(resolvingDataSourceFactory).setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache).setFragmentSize(1024 * 1024)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
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
                        if (file.exists()) serviceScope.launch { repository.updateSongDownloadStatus(songId, file.absolutePath) }
                    }
                    cursor.close()
                }
                context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().remove("pending_$songId").apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "onCreate: v4.13")
        serviceScope.launch { com.example.auto_music.data.remote.Innertube.fetchVisitorData() }
        cache = PlayerCache.getInstance(applicationContext)
        val database = MusicDatabase.getDatabase(applicationContext)
        val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
        val retrofit = Retrofit.Builder().baseUrl("https://www.youtube.com/").client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
        repository = MusicRepository(database.musicDao(), retrofit.create(YouTubeService::class.java), applicationContext)

        val newPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(createDataSourceFactory()))
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true).build()
        
        newPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val playlistId = item.mediaMetadata.extras?.getString("playlistId")?.toLongOrNull()
                    if (playlistId != null) serviceScope.launch { repository.updatePlaylistPlaybackState(playlistId, item.mediaId, 0L) }
                }
            }
        })
        
        serviceScope.launch {
            while (isActive) {
                delay(5000)
                if (newPlayer.isPlaying) {
                    val item = newPlayer.currentMediaItem
                    val playlistId = item?.mediaMetadata?.extras?.getString("playlistId")?.toLongOrNull()
                    if (playlistId != null) repository.updatePlaylistPlaybackState(playlistId, item.mediaId, newPlayer.currentPosition)
                }
            }
        }

        player = newPlayer
        val intent = Intent(this, com.example.auto_music.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaLibrarySession.Builder(this, newPlayer, LibrarySessionCallback()).setSessionActivity(pendingIntent).build()
        
        ContextCompat.registerReceiver(this, downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun updateCustomLayout() {
        val customLayout = ImmutableList.of(
            CommandButton.Builder().setDisplayName("Retrocedir 10s").setSessionCommand(COMMAND_SKIP_BACK).setIconResId(androidx.media3.ui.R.drawable.exo_ic_rewind).build(),
            CommandButton.Builder().setDisplayName("Avançar 10s").setSessionCommand(COMMAND_SKIP_FORWARD).setIconResId(androidx.media3.ui.R.drawable.exo_ic_forward).build()
        )
        mediaSession?.setCustomLayout(customLayout)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(COMMAND_SKIP_BACK).add(COMMAND_SKIP_FORWARD)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
                .add(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH).build()

            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_GET_TIMELINE).build()

            serviceScope.launch {
                updateCustomLayout()
            }

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if (customCommand.customAction == "COMMAND_SKIP_BACK") player?.seekBack()
            else if (customCommand.customAction == "COMMAND_SKIP_FORWARD") player?.seekForward()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val firstItem = mediaItems.firstOrNull() ?: run { future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)); return@launch }
                val playlistId = firstItem.mediaMetadata.extras?.getString("playlistId")?.toLongOrNull()
                if (playlistId != null && mediaItems.size == 1) {
                    val songs = repository.getSongsInPlaylist(playlistId).first()
                    val items = songs.map { createMediaItem(it, playlistId) }
                    val index = songs.indexOfFirst { it.id == firstItem.mediaId }.coerceAtLeast(0)
                    val playlist = repository.getPlaylistById(playlistId)
                    val finalPos = if (playlist?.lastPlayedSongId == firstItem.mediaId) playlist.lastPlayedPositionMs else startPositionMs
                    future.set(MediaSession.MediaItemsWithStartPosition(items, index, finalPos))
                } else {
                    val updated = mediaItems.map { if (it.mediaId.length > 5 && !it.mediaId.startsWith("PLAYLIST_")) repository.getSongById(it.mediaId)?.let { song -> createMediaItem(song, null) } ?: it else it }
                    future.set(MediaSession.MediaItemsWithStartPosition(updated, startIndex, startPositionMs))
                }
            }
            return future
        }

        private fun createMediaItem(song: com.example.auto_music.model.Song, playlistId: Long?): MediaItem {
            val isLocal = song.isDownloaded && song.audioUrl != null && File(song.audioUrl).exists()
            val uri = if (isLocal) Uri.fromFile(File(song.audioUrl)).toString() else "https://music.youtube.com/watch?v=${song.id}"
            val metadata = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setArtworkUri(song.thumbnailUrl.toUri())
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { 
                    if (playlistId != null) putString("playlistId", playlistId.toString())
                    putLong("android.media.metadata.DURATION", song.duration * 1000L)
                    putString("album", song.album)
                }).build()
            return MediaItem.Builder().setMediaId(song.id).setUri(uri).setMimeType("audio/mpeg").setCustomCacheKey(song.id).setMediaMetadata(metadata).build()
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMetadata = MediaMetadata.Builder()
                .setTitle("Auto Music")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setExtras(Bundle().apply { 
                    putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) 
                    putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1) 
                }).build()
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
                    } else future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                } catch (e: Exception) { future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN)) }
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
