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
    
    suspend fun createPlaylist(name: String, isPublic: Boolean = false): Long {
        val cloudId = if (isPublic) java.util.UUID.randomUUID().toString() else null
        return musicDao.insertPlaylist(Playlist(name = name, isPublic = isPublic, cloudId = cloudId))
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: Long) {
        Log.d("MusicRepository", "Afegint cançó ${song.title} a la llista $playlistId")
        // First, check if we already have this song metadata in DB to avoid resetting audioUrl/isDownloaded
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
        
        // Use updated song status
        val currentSong = musicDao.getSongById(song.id) ?: song
        
        if (!currentSong.isDownloaded) {
            val playlist = musicDao.getPlaylistById(playlistId)
            val sharedPrefs = context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE)
            val shouldDownload = if (playlist?.isPublic == true) {
                sharedPrefs.getBoolean("auto_download_public", true)
            } else {
                sharedPrefs.getBoolean("auto_download_private", true)
            }
            
            Log.d("MusicRepository", "shouldDownload=$shouldDownload per la llista ${playlist?.name} (public=${playlist?.isPublic})")
            if (shouldDownload) {
                downloadSong(currentSong)
            }
        } else {
            Log.d("MusicRepository", "La cançó ja està descarregada: ${currentSong.audioUrl}")
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
                    if (file.exists()) {
                        file.delete()
                        Log.d("MusicRepository", "Fitxer esborrat per ser orfe: $path")
                    }
                } catch (e: Exception) { 
                    Log.e("MusicRepository", "Error esborrant fitxer orfe: ${e.message}")
                }
            }
            song?.let { musicDao.deleteSong(it) }
        }
    }

    suspend fun updateSongDownloadStatus(songId: String, localPath: String) {
        val song = musicDao.getSongById(songId)
        song?.let {
            musicDao.insertSong(it.copy(audioUrl = localPath, isDownloaded = true))
            Log.d("MusicRepository", "Estat descàrrega actualitzat per a $songId a $localPath")
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = musicDao.getSongsInPlaylist(playlistId)

    private fun downloadSong(song: Song) {
        // Prevent multiple simultaneous downloads of the same song
        val sharedPrefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        if (sharedPrefs.contains("pending_${song.id}")) {
            Log.d("MusicRepository", "Cançó ${song.title} ja està en cua de descàrrega")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("MusicRepository", "Iniciant descàrrega per a ${song.title}")
                val stream = com.example.auto_music.player.InnertubeResolver.resolveStream(song.id) ?: run {
                    Log.e("MusicRepository", "No s'ha pogut resoldre l'stream per a ${song.id}")
                    return@launch
                }
                
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val fileName = "${song.id}.mp3"
                
                // Destination directory: use standard Downloads folder for better visibility/access
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "auto_music")
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, fileName)
                if (file.exists()) {
                    Log.d("MusicRepository", "El fitxer ja existeix localment: ${file.absolutePath}")
                    updateSongDownloadStatus(song.id, file.absolutePath)
                    return@launch
                }

                val request = DownloadManager.Request(Uri.parse(stream.url))
                    .setTitle("Auto Music: ${song.title}")
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "auto_music/$fileName")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                    .addRequestHeader("User-Agent", stream.userAgent)
                    .addRequestHeader("Referer", "https://music.youtube.com/")

                val downloadId = downloadManager.enqueue(request)
                sharedPrefs.edit()
                    .putString(downloadId.toString(), song.id)
                    .putBoolean("pending_${song.id}", true)
                    .apply()
                Log.d("MusicRepository", "Descàrrega en cua amb ID $downloadId")
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error descàrrega: ${e.message}")
                sharedPrefs.edit().remove("pending_${song.id}").apply()
            }
        }
    }
}
