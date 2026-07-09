package com.example.auto_music.data

import android.content.Context
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
            val response = youtubeService.searchVideos(query = query)
            response.map { item ->
                Song(
                    id = item.videoId,
                    title = item.title,
                    artist = item.author,
                    thumbnailUrl = item.videoThumbnails.firstOrNull { it.width > 300 }?.url 
                        ?: item.videoThumbnails.firstOrNull()?.url ?: "",
                    audioUrl = null,
                    duration = 0
                )
            }
        } catch (e: Exception) {
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

    private suspend fun downloadSong(song: Song) {
        // La lógica de descarga se puede implementar con librerías gratuitas 
        // que extraigan el audio del video de YouTube sin usar la API de Google.
        val file = File(context.getExternalFilesDir(null), "${song.id}.mp3")
        val updatedSong = song.copy(audioUrl = file.absolutePath, isDownloaded = true)
        musicDao.insertSong(updatedSong)
    }
}
