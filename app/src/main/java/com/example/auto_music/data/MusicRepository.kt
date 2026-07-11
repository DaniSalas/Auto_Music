package com.example.auto_music.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import com.example.auto_music.data.local.MusicDao
import com.example.auto_music.data.remote.Innertube
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
    private val context: Context,
) {
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val allSongs: Flow<List<Song>> = musicDao.getAllSongs()

    suspend fun searchSongs(query: String): List<Song> {
        return try {
            Log.d("MusicRepository", "Cercant a YouTube Music (Innertube Ktor) per: $query")
            val response = Innertube.search(query)
            
            val songs = mutableListOf<Song>()
            
            // Lògica de parsing de kreate_imp
            val musicShelf = response?.contents
                ?.tabbedSearchResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.firstNotNullOfOrNull { it.musicShelfRenderer }

            musicShelf?.contents?.forEach { item ->
                val renderer = item.musicResponsiveListItemRenderer ?: return@forEach
                val videoId = renderer.navigationEndpoint?.watchEndpoint?.videoId ?: return@forEach
                
                // Extreure el títol (columna 0)
                val title = renderer.flexColumns?.getOrNull(0)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                    ?.joinToString("") { it.text ?: "" } ?: "Desconegut"
                
                // Extreure l'artista (columna 1)
                val artist = renderer.flexColumns?.getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                    ?.joinToString("") { it.text ?: "" } ?: "Artista desconegut"
                
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
            
            Log.d("MusicRepository", "S'han trobat ${songs.size} resultats")
            songs
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error en la cerca de YouTube Music", e)
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
            val audioStreamUrl = "https://inv.tux.pizza/latest_version?id=${song.id}&itag=140"
            
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Auto_Music")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileName = "${song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.mp3"
            val file = File(directory, fileName)

            val request = DownloadManager.Request(audioStreamUrl.toUri())
                .setTitle("Descarregant ${song.title}")
                .setDescription("Descarregant música d'Auto Music")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            downloadManager.enqueue(request)

            Log.d("MusicRepository", "Descàrrega en cua per a ${song.title} a: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error en la descàrrega de ${song.title}", e)
        }
    }
}
