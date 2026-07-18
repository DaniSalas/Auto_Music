package com.example.auto_music.player

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
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
import androidx.core.content.ContextCompat
import java.io.File

@UnstableApi
class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var repository: MusicRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var cache: SimpleCache

    private fun createDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

        return ResolvingDataSource.Factory(defaultDataSourceFactory) { dataSpec ->
            val uriString = dataSpec.uri.toString()
            if (uriString.startsWith("file") || uriString.startsWith("content") || uriString.contains("googlevideo.com")) return@Factory dataSpec
            val videoId = dataSpec.key ?: uriString.substringAfter("v=", "").substringBefore("&")
            if (videoId.isEmpty()) return@Factory dataSpec

            val stream = try { runBlocking(Dispatchers.IO) { InnertubeResolver.resolveStream(videoId) } } catch (e: Exception) { null }
            if (stream != null) {
                val headers = dataSpec.httpRequestHeaders.toMutableMap()
                headers["User-Agent"] = stream.userAgent
                if (stream.userAgent.contains("Mozilla") && !stream.userAgent.contains("Android")) headers["Referer"] = "https://music.youtube.com/"
                return@Factory dataSpec.withUri(Uri.parse(stream.url)).withRequestHeaders(headers)
            }
            dataSpec
        }.let { resolvingFactory ->
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(resolvingFactory)
                .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache).setFragmentSize(512 * 1024))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MusicService", "onCreate: Iniciant servei v4.2")
        serviceScope.launch { com.example.auto_music.data.remote.Innertube.fetchVisitorData() }
        cache = PlayerCache.getInstance(applicationContext)
        val database = MusicDatabase.getDatabase(applicationContext)
        val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        val retrofit = Retrofit.Builder().baseUrl("https://www.youtube.com/").client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
        repository = MusicRepository(database.musicDao(), retrofit.create(YouTubeService::class.java), applicationContext)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(createDataSourceFactory()))
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback()).build()
        
        ContextCompat.registerReceiver(this, downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == -1L) return
            val songId = context.getSharedPreferences("downloads", Context.MODE_PRIVATE).getString(id.toString(), null) ?: return
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
            if (cursor != null && cursor.moveToFirst()) {
                val status = try { cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) } catch (e: Exception) { -1 }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    Uri.parse(localUri).path?.let { path -> serviceScope.launch { repository.updateSongDownloadStatus(songId, path) } }
                }
                cursor.close()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SET_MEDIA_ITEM)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .build()

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
                val updatedItems = mutableListOf<MediaItem>()
                val item = mediaItems.firstOrNull() ?: run {
                    future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
                    return@launch
                }
                
                val playlistId = item.mediaMetadata.extras?.getString("playlistId")?.toLongOrNull()
                if (playlistId != null && mediaItems.size == 1) {
                    val playlistSongs = repository.getSongsInPlaylist(playlistId).first()
                    val indexInPlaylist = playlistSongs.indexOfFirst { it.id == item.mediaId }.coerceAtLeast(0)
                    playlistSongs.forEach { updatedItems.add(createMediaItem(it, playlistId)) }
                    future.set(MediaSession.MediaItemsWithStartPosition(updatedItems, indexInPlaylist, startPositionMs))
                } else {
                    for (it in mediaItems) {
                        if (it.mediaId.length > 5 && !it.mediaId.startsWith("PLAYLIST_")) {
                            repository.getSongById(it.mediaId)?.let { song -> updatedItems.add(createMediaItem(song, null)) } ?: updatedItems.add(it)
                        } else updatedItems.add(it)
                    }
                    future.set(MediaSession.MediaItemsWithStartPosition(updatedItems, startIndex, startPositionMs))
                }
            }
            return future
        }

        private fun createMediaItem(song: com.example.auto_music.model.Song, playlistId: Long?): MediaItem {
            val localFile = song.audioUrl?.let { File(it) }
            val finalUri = if (localFile?.exists() == true) Uri.fromFile(localFile).toString() else "https://music.youtube.com/watch?v=${song.id}"
            return MediaItem.Builder().setMediaId(song.id).setUri(finalUri).setMimeType("audio/mpeg").setMediaMetadata(
                MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setArtworkUri(song.thumbnailUrl.toUri())
                    .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setExtras(Bundle().apply {
                        if (playlistId != null) putString("playlistId", playlistId.toString())
                        putLong("android.media.metadata.DURATION", song.duration * 1000L)
                    }).build()
            ).build()
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder().setMediaId("ROOT").setMediaMetadata(MediaMetadata.Builder().setTitle("Auto Music").setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setExtras(Bundle().apply {
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }).build()).build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    if (parentId == "ROOT") {
                        val items = repository.allPlaylists.first().map { playlist ->
                            MediaItem.Builder().setMediaId("PLAYLIST_${playlist.id}").setMediaMetadata(MediaMetadata.Builder().setTitle(playlist.name).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST).setExtras(Bundle().apply { putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) }).build()).build()
                        }
                        future.set(LibraryResult.ofItemList(items, params))
                    } else if (parentId.startsWith("PLAYLIST_")) {
                        val playlistId = parentId.removePrefix("PLAYLIST_").toLong()
                        val items = repository.getSongsInPlaylist(playlistId).first().map { song -> createMediaItem(song, playlistId) }
                        future.set(LibraryResult.ofItemList(items, params))
                    } else future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                } catch (e: Exception) { future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)) }
            }
            return future
        }
    }

    override fun onDestroy() {
        unregisterReceiver(downloadReceiver)
        mediaSession.release()
        player.release()
        serviceJob.cancel()
        super.onDestroy()
    }
}
