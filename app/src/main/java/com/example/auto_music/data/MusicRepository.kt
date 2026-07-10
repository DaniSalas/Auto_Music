package com.example.auto_music.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.auto_music.data.local.MusicDao
import com.example.auto_music.data.remote.InnerTubeRequest
import com.example.auto_music.data.remote.YouTubeService
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.PlaylistSongCrossRef
import com.example.auto_music.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
            Log.d("MusicRepository", "Searching YouTube Music for: $query")
            val response = youtubeService.searchVideos(InnerTubeRequest(query = query))
            
            val songs = mutableListOf<Song>()
            
            // Navegamos por el laberinto de JSON de YouTube Music
            response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.content?.sectionListRenderer?.contents?.forEach { section ->
                    section.musicShelfRenderer?.contents?.forEach { item ->
                        val renderer = item.musicResponsiveListItemRenderer ?: return@forEach
                        
                        val videoId = renderer.navigationEndpoint?.watchEndpoint?.videoId ?: return@forEach
                        val title = renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: "Unknown"
                        val artist = renderer.flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: "Unknown"
                        val thumb = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""
                        
                        songs.add(
                            Song(
                                id = videoId,
                                title = title,
                                artist = artist,
                                thumbnailUrl = thumb,
                                audioUrl = null,
                                duration = 0
                            )
                        )
                    }
                }
            
            Log.d("MusicRepository", "Found ${songs.size} results on YouTube Music")
            songs
        } catch (e: Exception) {
            Log.e("MusicRepository", "YouTube Music search failed", e)
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

    suspend fun updateSongDownloadStatus(songId: String, localPath: String) {
        // Buscamos la canción actual para no perder otros datos
        val allSongsInDb = allSongs.first()
        val song = allSongsInDb.find { it.id == songId }
        song?.let {
            musicDao.insertSong(it.copy(audioUrl = localPath, isDownloaded = true))
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> {
        return musicDao.getSongsInPlaylist(playlistId)
    }

    private fun downloadSong(song: Song) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // Usamos un proveedor de streams fiable (Cobalt o similar, o un proxy de Invidious)
            // Para que sea 100% gratuito y sin keys, usamos el truco de 'latest_version' de Invidious 
            // que suele ser el más directo.
            val audioStreamUrl = "https://inv.tux.pizza/latest_version?id=${song.id}&itag=140"
            
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

            Log.d("MusicRepository", "Download queued for ${song.title} to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Download failed for ${song.title}", e)
        }
    }
}
