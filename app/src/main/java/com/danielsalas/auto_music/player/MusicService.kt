package com.danielsalas.auto_music.player

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
import com.danielsalas.auto_music.data.MusicRepository
import com.danielsalas.auto_music.data.local.MusicDatabase
import com.danielsalas.auto_music.data.remote.YouTubeService
import com.danielsalas.auto_music.player.cache.PlayerCache
import com.danielsalas.auto_music.player.effects.CustomEqualizerAudioProcessor
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
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.EnvironmentalReverb
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.DefaultRenderersFactory

@UnstableApi
class MusicService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var repository: MusicRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var cache: SimpleCache
    
    private val softwareEqualizer = CustomEqualizerAudioProcessor()
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var presetReverb: PresetReverb? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var currentAudioSessionId: Int = 0

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
            
            val localFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "auto_music/$videoId.mp3")
            if (localFile.exists()) return@Factory dataSpec.withUri(Uri.fromFile(localFile))
            
            val stream = try { 
                runBlocking(Dispatchers.IO) { 
                    withTimeoutOrNull(25000) { InnertubeResolver.resolveStream(videoId) } 
                } 
            } catch (e: Exception) { null }
            
            if (stream != null) {
                val headers = dataSpec.httpRequestHeaders.toMutableMap()
                headers["User-Agent"] = stream.userAgent
                headers["Referer"] = "https://www.youtube.com/"
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
                    if (statusIdx != -1) {
                        val status = cursor.getInt(statusIdx)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            val file = File(dir, "auto_music/$songId.mp3")
                            if (file.exists()) serviceScope.launch { repository.updateSongDownloadStatus(songId, file.absolutePath) }
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
        serviceScope.launch { com.danielsalas.auto_music.data.remote.Innertube.fetchVisitorData() }
        cache = PlayerCache.getInstance(applicationContext)
        val database = MusicDatabase.getDatabase(applicationContext)
        val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
        val retrofit = Retrofit.Builder().baseUrl("https://www.youtube.com/").client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
        repository = MusicRepository(database.musicDao(), retrofit.create(YouTubeService::class.java), applicationContext)

        val audioSink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(softwareEqualizer))
            .build()
        
        val renderersFactory = object : DefaultRenderersFactory(this) {
            @androidx.media3.common.util.UnstableApi
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return audioSink
            }
        }

        val newPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(createDataSourceFactory()))
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true).build()
        
        newPlayer.repeatMode = Player.REPEAT_MODE_ALL
        
        newPlayer.addListener(object : Player.Listener {
            @androidx.media3.common.util.UnstableApi
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (currentAudioSessionId != audioSessionId) {
                    currentAudioSessionId = audioSessionId
                    setupAudioEffects(audioSessionId)
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let { item ->
                    val mId = item.mediaId
                    val playlistId = if (mId.contains("|")) mId.substringBefore("|").removePrefix("PL").toLongOrNull() else null
                    if (playlistId != null) {
                        serviceScope.launch {
                            val p = repository.getPlaylistById(playlistId)
                            applyPlaylistEffects(p)
                        }
                    }
                    val songId = if (mId.contains("|")) mId.substringAfter("|") else mId
                    if (playlistId != null) serviceScope.launch { repository.updatePlaylistPlaybackState(playlistId, songId, 0L) }
                }
            }
        })

        player = newPlayer
        val intent = Intent(this, com.danielsalas.auto_music.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaLibrarySession.Builder(this, newPlayer, LibrarySessionCallback()).setSessionActivity(pendingIntent).build()
        
        ContextCompat.registerReceiver(this, downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun setupAudioEffects(sessionId: Int) {
        try {
            loudnessEnhancer?.release(); equalizer?.release(); presetReverb?.release(); environmentalReverb?.release()
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply { setTargetGain(0); enabled = false }
            equalizer = Equalizer(1000, sessionId).apply { enabled = true }
            presetReverb = PresetReverb(1000, sessionId).apply { enabled = false }
            environmentalReverb = EnvironmentalReverb(1000, sessionId).apply { enabled = false }
            loadGlobalEqualizer()
        } catch (e: Exception) { Log.e("MusicService", "setupAudioEffects error: ${e.message}") }
    }

    private fun applyPlaylistEffects(playlist: com.danielsalas.auto_music.model.Playlist?) {
        val normalize = playlist?.isVolumeNormalized ?: false
        try {
            loudnessEnhancer?.let { 
                it.setTargetGain(if (normalize) 2500 else 0)
                it.enabled = normalize 
            }
        } catch (e: Exception) { Log.e("MusicService", "LoudnessEnhancer error: ${e.message}") }
    }

    private fun loadGlobalEqualizer() {
        val sp = getSharedPreferences("EqualizerPrefs", Context.MODE_PRIVATE)
        val enabled = sp.getBoolean("eq_enabled", false)
        val levels = IntArray(10) { i -> sp.getInt("band_$i", 0) }
        val reverbPreset = sp.getInt("reverb_preset", 0)
        applyEqualizerSettings(enabled, levels, reverbPreset)
    }

    private fun applyEqualizerSettings(isEqEnabled: Boolean, levels: IntArray?, reverbPreset: Int) {
        try {
            softwareEqualizer.updateSettings(isEqEnabled, levels)
            equalizer?.let { eq ->
                if (levels != null) {
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.getBandLevelRange()
                    for (i in 0 until numBands.coerceAtMost(levels.size)) {
                        val level = levels[i].toShort()
                        val coercedLevel = level.coerceIn(range[0], range[1])
                        eq.setBandLevel(i.toShort(), coercedLevel)
                    }
                }
                eq.enabled = false; eq.enabled = isEqEnabled
            }
            presetReverb?.enabled = false; environmentalReverb?.enabled = false
            if (isEqEnabled) {
                when (reverbPreset) {
                    1 -> environmentalReverb?.apply { roomLevel = -500; roomHFLevel = -100; decayTime = 300; reflectionsLevel = -800; reflectionsDelay = 15; reverbLevel = 100; reverbDelay = 20; enabled = true }
                    2 -> presetReverb?.apply { preset = PresetReverb.PRESET_MEDIUMROOM; enabled = true }
                    3 -> presetReverb?.apply { preset = PresetReverb.PRESET_LARGEHALL; enabled = true }
                }
            }
        } catch (e: Exception) { Log.e("MusicService", "applyEqualizerSettings error: ${e.message}") }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand("ACTION_UPDATE_EQ", Bundle.EMPTY))
                .add(SessionCommand("ACTION_UPDATE_NORMALIZATION", Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, Player.Commands.Builder().addAllCommands().build())
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "ACTION_UPDATE_EQ" -> {
                    applyEqualizerSettings(args.getBoolean("enabled"), args.getIntArray("levels"), args.getInt("reverb"))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "ACTION_UPDATE_NORMALIZATION" -> {
                    val enabled = args.getBoolean("enabled")
                    loudnessEnhancer?.let { it.setTargetGain(if (enabled) 2500 else 0); it.enabled = enabled }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        @androidx.media3.common.util.UnstableApi
        override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val firstItem = mediaItems.firstOrNull() ?: run { future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)); return@launch }
                val targetMId = firstItem.mediaId
                val playlistId = if (targetMId.contains("|")) targetMId.substringBefore("|").removePrefix("PL").toLongOrNull() else firstItem.mediaMetadata.extras?.getString("playlistId")?.toLongOrNull()
                if (playlistId != null) {
                    val dbPlaylist = repository.getPlaylistById(playlistId)
                    var songs = repository.getSongsInPlaylist(playlistId).first()
                    if (dbPlaylist?.isShuffle == true) songs = songs.shuffled()
                    val expandedItems = songs.map { createMediaItem(it, playlistId) }
                    val indexInPlaylist = expandedItems.indexOfFirst { it.mediaId.substringAfter("|") == (if (targetMId.contains("|")) targetMId.substringAfter("|") else targetMId) }
                    future.set(MediaSession.MediaItemsWithStartPosition(expandedItems, if (indexInPlaylist != -1) indexInPlaylist else 0, startPositionMs))
                } else {
                    val updated = mediaItems.map { 
                        val songId = if (it.mediaId.contains("|")) it.mediaId.substringAfter("|") else it.mediaId
                        repository.getSongById(songId)?.let { song -> createMediaItem(song, null) } ?: it.buildUpon().setUri("https://music.youtube.com/watch?v=$songId").setMimeType("audio/mpeg").build()
                    }
                    future.set(MediaSession.MediaItemsWithStartPosition(updated, startIndex, startPositionMs))
                }
            }
            return future
        }

        private fun createMediaItem(song: com.danielsalas.auto_music.model.Song, playlistId: Long?): MediaItem {
            val isLocal = song.isDownloaded && song.audioUrl != null && File(song.audioUrl).exists()
            val uri = if (isLocal) Uri.fromFile(File(song.audioUrl)).toString() else "https://music.youtube.com/watch?v=${song.id}"
            val metadata = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setArtworkUri(song.thumbnailUrl.toUri()).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { if (playlistId != null) putString("playlistId", playlistId.toString()); putLong("android.media.metadata.DURATION", song.duration * 1000L); putString("album", song.album) }).build()
            return MediaItem.Builder().setMediaId(if (playlistId != null) "PL$playlistId|${song.id}" else song.id).setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(metadata).build()
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            val meta = MediaMetadata.Builder().setTitle("Auto Music").setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
            return Futures.immediateFuture(LibraryResult.ofItem(MediaItem.Builder().setMediaId("ROOT").setMediaMetadata(meta).build(), params))
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    val items = mutableListOf<MediaItem>()
                    if (parentId == "ROOT") {
                        repository.allPlaylists.first().forEach { p -> items.add(MediaItem.Builder().setMediaId("PLAYLIST_${p.id}").setMediaMetadata(MediaMetadata.Builder().setTitle(p.name).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST).build()).build()) }
                    } else if (parentId.startsWith("PLAYLIST_")) {
                        val pid = parentId.removePrefix("PLAYLIST_").toLong()
                        repository.getSongsInPlaylist(pid).first().forEach { s -> items.add(createMediaItem(s, pid)) }
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                } catch (e: Exception) { future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN)) }
            }
            return future
        }
    }

    override fun onDestroy() {
        unregisterReceiver(downloadReceiver); mediaSession?.release(); player?.release(); serviceJob.cancel()
        loudnessEnhancer?.release(); equalizer?.release(); presetReverb?.release(); environmentalReverb?.release()
        super.onDestroy()
    }
}
