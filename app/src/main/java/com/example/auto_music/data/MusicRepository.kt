package com.example.auto_music.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.auto_music.data.local.MusicDao
import com.example.auto_music.data.remote.YouTubeService
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.PlaylistSongCrossRef
import com.example.auto_music.model.Song
import kotlinx.coroutines.flow.Flow
import java.io.File

class MusicRepository(
    private val musicDao: MusicDao,
    private val youtubeService: YouTubeService,
    private val context: Context
) {
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val allSongs: Flow<List<Song>> = musicDao.getAllSongs()

    suspend fun searchSongs(query: String): List<Song> {
        return try {
            Log.d("MusicRepository", "Searching for: $query")
            val response = youtubeService.searchVideos(query = query)
            response.items.mapNotNull { item ->
                // Solo procesamos elementos de tipo stream (videos)
                if (item.type != "stream" && item.type != null) return@mapNotNull null
                
                val videoId = item.url?.substringAfter("v=") ?: return@mapNotNull null
                Song(
                    id = videoId,
                    title = item.title ?: "Unknown",
                    artist = item.uploaderName ?: "Unknown",
                    thumbnailUrl = item.thumbnail ?: "",
                    audioUrl = null,
                    duration = item.duration ?: 0
                )
            }.also {
                Log.d("MusicRepository", "Found ${it.size} results")
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Search failed", e)
            emptyList()
        }
    }

    suspend fun createPlaylist(name: String) {
        musicDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: Long) {
        musicDao.insertSong(song)
        musicDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id))
        
        if (!song.isDownloaded) {
            downloadSong(song)
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> {
        return musicDao.getSongsInPlaylist(playlistId)
    }

    private fun downloadSong(song: Song) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // Usamos la instancia de Garuda Linux para mayor velocidad
            val audioStreamUrl = "https://piped-api.garudalinux.org/streams/${song.id}"
            
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Auto_Music")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileName = "${song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.mp3"
            val file = File(directory, fileName)

            val request = DownloadManager.Request(Uri.parse(audioStreamUrl))
                .setTitle("Downloading ${song.title}")
                .setDescription("Auto Music Download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            downloadManager.enqueue(request)

            Log.d("MusicRepository", "Download queued to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Download failed to queue", e)
        }
    }
}
