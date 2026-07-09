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

    suspend fun searchSongs(query: String, apiKey: String): List<Song> {
        val response = youtubeService.searchVideos(query = query, apiKey = apiKey)
        return response.items.map { item ->
            Song(
                id = item.id.videoId,
                title = item.snippet.title,
                artist = item.snippet.channelTitle,
                thumbnailUrl = item.snippet.thumbnails.high.url,
                audioUrl = null,
                duration = 0 // Needs another API call for contentDetails if needed
            )
        }
    }

    suspend fun createPlaylist(name: String) {
        musicDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: Long) {
        musicDao.insertSong(song)
        musicDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id))
        
        // Trigger download if not downloaded
        if (!song.isDownloaded) {
            downloadSong(song)
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> {
        return musicDao.getSongsInPlaylist(playlistId)
    }

    private suspend fun downloadSong(song: Song) {
        // Placeholder for download logic.
        // In a real app, you'd use a YouTube audio extractor and Media3 DownloadManager.
        val file = File(context.getExternalFilesDir(null), "${song.id}.mp3")
        // logic to download stream to file...
        
        val updatedSong = song.copy(audioUrl = file.absolutePath, isDownloaded = true)
        musicDao.insertSong(updatedSong)
    }
}
