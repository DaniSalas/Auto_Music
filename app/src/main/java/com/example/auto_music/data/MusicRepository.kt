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
            Log.i("MusicRepository", "Cercant a YouTube Music (Innertube Ktor) per: $query")
            val response = Innertube.search(query)
            
            if (response == null) {
                Log.e("MusicRepository", "Resposta nul·la d'Innertube")
                return emptyList()
            }

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
                    
                    songs.add(Song(id = videoId, title = title, artist = artist, thumbnailUrl = thumb, audioUrl = null, duration = 0))
                }
            }
            songs
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error en la cerca: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSongById(songId: String): Song? {
        return musicDao.getSongById(songId)
    }

    suspend fun getPlaylistById(playlistId: Long): Playlist? {
        return musicDao.getPlaylistById(playlistId)
    }

    suspend fun createPlaylist(name: String, isPublic: Boolean = false): Long {
        val cloudId = if (isPublic) java.util.UUID.randomUUID().toString() else null
        return musicDao.insertPlaylist(Playlist(name = name, isPublic = isPublic, cloudId = cloudId))
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: Long) {
        musicDao.insertSong(song)
        val songsInPlaylist = musicDao.getSongsInPlaylist(playlistId).first()
        if (songsInPlaylist.none { it.id == song.id }) {
            val maxPos = musicDao.getMaxPosition(playlistId) ?: -1
            musicDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id, maxPos + 1))
        }
        
        // Check if we should download
        if (!song.isDownloaded) {
            val playlist = musicDao.getPlaylistById(playlistId)
            val sharedPrefs = context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE)
            val shouldDownload = if (playlist?.isPublic == true) {
                sharedPrefs.getBoolean("auto_download_public", true)
            } else {
                sharedPrefs.getBoolean("auto_download_private", true)
            }
            
            if (shouldDownload) {
                downloadSong(song)
            }
        }
    }

    suspend fun updateSongOrder(playlistId: Long, songs: List<Song>) {
        musicDao.updateSongOrder(playlistId, songs.map { it.id })
    }

    suspend fun removeSongFromPlaylist(song: Song, playlistId: Long) {
        musicDao.removeSongFromPlaylist(playlistId, song.id)
        val count = musicDao.getSongOccurrenceCount(song.id)
        if (count == 0) {
            song.audioUrl?.let { path ->
                try { File(path).delete() } catch (e: Exception) { }
            }
            musicDao.deleteSong(song)
        }
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        val songs = musicDao.getSongsInPlaylist(playlist.id).first()
        musicDao.deletePlaylist(playlist)
        songs.forEach { song ->
            if (musicDao.getSongOccurrenceCount(song.id) == 0) {
                song.audioUrl?.let { path -> try { File(path).delete() } catch (e: Exception) { } }
                musicDao.deleteSong(song)
            }
        }
    }

    suspend fun updateSongDownloadStatus(songId: String, localPath: String) {
        val song = musicDao.getSongById(songId)
        song?.let { musicDao.insertSong(it.copy(audioUrl = localPath, isDownloaded = true)) }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = musicDao.getSongsInPlaylist(playlistId)

    private fun downloadSong(song: Song) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stream = com.example.auto_music.player.InnertubeResolver.resolveStream(song.id) ?: return@launch
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val fileName = "${song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.mp3"
                val request = DownloadManager.Request(Uri.parse(stream.url))
                    .setTitle("Descarregant ${song.title}")
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "auto_music/$fileName")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .addRequestHeader("User-Agent", stream.userAgent)
                    .addRequestHeader("Referer", "https://music.youtube.com/")

                val downloadId = downloadManager.enqueue(request)
                context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().putString(downloadId.toString(), song.id).apply()
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error descàrrega: ${e.message}")
            }
        }
    }
}
