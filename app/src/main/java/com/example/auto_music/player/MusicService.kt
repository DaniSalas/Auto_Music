package com.example.auto_music.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.auto_music.data.MusicRepository
import com.example.auto_music.data.local.MusicDatabase
import com.example.auto_music.data.remote.YouTubeService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var repository: MusicRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        
        val database = androidx.room.Room.databaseBuilder(applicationContext, MusicDatabase::class.java, "music_db").build()
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://music.youtube.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        val youtubeService = retrofit.create(YouTubeService::class.java)
        repository = MusicRepository(database.musicDao(), youtubeService, applicationContext)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
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
                        MediaItem.Builder()
                            .setMediaId(song.id)
                            .setUri(song.audioUrl ?: "https://inv.tux.pizza/latest_version?id=${song.id}&itag=140")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
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
