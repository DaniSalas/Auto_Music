package com.example.auto_music.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.auto_music.data.local.MusicDao
import com.example.auto_music.data.remote.Innertube
import com.example.auto_music.data.remote.YouTubeService
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.PlaylistSongCrossRef
import com.example.auto_music.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MusicRepository(
    private val musicDao: MusicDao,
    private val youtubeService: YouTubeService,
    private val context: Context,
) {
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val allSongs: Flow<List<Song>> = musicDao.getAllSongs()

    suspend fun searchSongs(query: String): List<Song> {
        return try {
            val response = Innertube.search(query) ?: return emptyList()
            val songs = mutableListOf<Song>()
            val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
                ?: response.contents?.sectionListRenderer?.contents
            
            if (contents == null) return emptyList()

            contents.forEach { section ->
                val musicShelf = section.musicShelfRenderer 
                    ?: section.musicPlaylistShelfRenderer
                    ?: section.musicCarouselShelfRenderer

                musicShelf?.contents?.forEach { item ->
                    val renderer = item.musicResponsiveListItemRenderer ?: return@forEach
                    val videoId = renderer.navigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.playlistItemData?.videoId
                        ?: renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
                    
                    if (videoId == null) return@forEach
                    
                    val title = renderer.flexColumns?.getOrNull(0)
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.text ?: "Desconegut"
                    val artist = renderer.flexColumns?.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: "Artista desconegut"
                    val thumb = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""
                    
                    // Extract duration
                    val lengthText = renderer.lengthText?.runs?.firstOrNull()?.text 
                        ?: renderer.flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.lastOrNull()?.text
                    
                    val durationSeconds = lengthText?.let { parseDuration(it) } ?: 0L

                    songs.add(Song(id = videoId, title = title, artist = artist, thumbnailUrl = thumb, audioUrl = null, duration = durationSeconds))
                }
            }
            songs
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error en la cerca: ${e.message}")
            emptyList()
        }
    }

    private fun parseDuration(text: String): Long {
        return try {
            val parts = text.split(":")
            if (parts.size == 2) {
                parts[0].toLong() * 60 + parts[1].toLong()
            } else if (parts.size == 3) {
                parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
            } else 0L
        } catch (e: Exception) { 0L }
    }

    suspend fun getSongById(songId: String): Song? = musicDao.getSongById(songId)
    suspend fun getPlaylistById(playlistId: Long): Playlist? = musicDao.getPlaylistById(playlistId)
    
    suspend fun updatePlaylistPlaybackState(playlistId: Long, songId: String?, position: Long) {
        musicDao.updatePlaylistPlaybackState(playlistId, songId, position)
    }

    suspend fun createPlaylist(name: String, isPublic: Boolean = false, cloudId: String? = null): Long {
        val playlists = musicDao.getAllPlaylists().first()
        // Anti-duplicate check: match by cloudId if provided, or name+privacy
        val existing = if (cloudId != null) {
            playlists.find { it.cloudId == cloudId }
        } else {
            playlists.find { it.name == name && it.isPublic == isPublic }
        }
        
        if (existing != null) return existing.id
        
        val finalCloudId = cloudId ?: if (isPublic) java.util.UUID.randomUUID().toString() else null
        return musicDao.insertPlaylist(Playlist(name = name, isPublic = isPublic, cloudId = finalCloudId))
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: Long) {
        val existingMetadata = musicDao.getSongById(song.id)
        if (existingMetadata == null) {
            musicDao.insertSong(song)
        } else if (existingMetadata.duration == 0L && song.duration > 0) {
            musicDao.insertSong(existingMetadata.copy(duration = song.duration))
        }
        
        val songsInPlaylist = musicDao.getSongsInPlaylist(playlistId).first()
        if (songsInPlaylist.none { it.id == song.id }) {
            val maxPos = musicDao.getMaxPosition(playlistId) ?: -1
            musicDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id, maxPos + 1))
        }
        
        // Trigger download check
        val currentSong = musicDao.getSongById(song.id) ?: song
        if (!currentSong.isDownloaded) {
            val playlist = musicDao.getPlaylistById(playlistId)
            val sharedPrefs = context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE)
            val shouldDownload = if (playlist?.isPublic == true) {
                sharedPrefs.getBoolean("auto_download_public", true)
            } else {
                sharedPrefs.getBoolean("auto_download_private", true)
            }
            
            if (shouldDownload) {
                downloadSong(currentSong)
            }
        }
    }

    suspend fun updateSongOrder(playlistId: Long, songs: List<Song>) {
        musicDao.updateSongOrder(playlistId, songs.map { it.id })
    }

    suspend fun removeSongFromPlaylist(song: Song, playlistId: Long) {
        musicDao.removeSongFromPlaylist(playlistId, song.id)
        cleanupOrphanedSong(song.id)
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        val songs = musicDao.getSongsInPlaylist(playlist.id).first()
        musicDao.deletePlaylist(playlist)
        songs.forEach { cleanupOrphanedSong(it.id) }
    }
    
    private suspend fun cleanupOrphanedSong(songId: String) {
        val count = musicDao.getSongOccurrenceCount(songId)
        if (count == 0) {
            val song = musicDao.getSongById(songId)
            song?.audioUrl?.let { path ->
                try { 
                    val file = File(path)
                    if (file.exists()) file.delete()
                } catch (e: Exception) { }
            }
            song?.let { musicDao.deleteSong(it) }
        }
    }

    suspend fun updateSongDownloadStatus(songId: String, localPath: String) {
        val song = musicDao.getSongById(songId)
        song?.let {
            musicDao.insertSong(it.copy(audioUrl = localPath, isDownloaded = true))
            Log.d("MusicRepository", "Download success: $songId saved at $localPath")
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = musicDao.getSongsInPlaylist(playlistId)

    fun downloadSong(song: Song) {
        val sp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        if (sp.contains("pending_${song.id}")) {
            Log.d("MusicRepository", "Already downloading ${song.id}")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stream = com.example.auto_music.player.InnertubeResolver.resolveStream(song.id) ?: run {
                    Log.e("MusicRepository", "Failed to resolve stream for ${song.id}")
                    return@launch
                }
                
                val fileName = "${song.id}.mp3"
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "auto_music")
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, fileName)
                if (file.exists()) {
                    updateSongDownloadStatus(song.id, file.absolutePath)
                    return@launch
                }

                Log.i("MusicRepository", "Requesting download: ${song.title} -> ${file.absolutePath}")
                val request = DownloadManager.Request(Uri.parse(stream.url))
                    .setTitle("Auto Music: ${song.title}")
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "auto_music/$fileName")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .addRequestHeader("User-Agent", stream.userAgent)
                    .addRequestHeader("Referer", "https://music.youtube.com/")

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadId = downloadManager.enqueue(request)
                
                sp.edit()
                    .putString(downloadId.toString(), song.id)
                    .putBoolean("pending_${song.id}", true)
                    .apply()
            } catch (e: Exception) {
                Log.e("MusicRepository", "Download task failed: ${e.message}")
                sp.edit().remove("pending_${song.id}").apply()
            }
        }
    }
}
