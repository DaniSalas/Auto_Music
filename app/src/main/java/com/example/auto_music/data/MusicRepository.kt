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
            
            // Intentar obtenir la llista de seccions des de tabs o directament
            val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
                ?: response.contents?.sectionListRenderer?.contents
            
            Log.i("MusicRepository", "Seccions trobades: ${contents?.size ?: 0}")

            if (contents == null) {
                Log.w("MusicRepository", "No s'han trobat continguts a la resposta")
                return emptyList()
            }

            // Cercar el musicShelfRenderer a qualsevol de les seccions
            contents.forEachIndexed { index, section ->
                val musicShelf = section.musicShelfRenderer 
                    ?: section.musicPlaylistShelfRenderer
                    ?: section.musicCarouselShelfRenderer
                
                if (musicShelf != null) {
                    Log.i("MusicRepository", "Processant musicShelf a secció $index amb ${musicShelf.contents?.size ?: 0} ítems")
                }

                musicShelf?.contents?.forEach { item ->
                    val renderer = item.musicResponsiveListItemRenderer
                    if (renderer == null) {
                        Log.v("MusicRepository", "Renderer nul per a un ítem")
                        return@forEach
                    }

                    val videoId = renderer.navigationEndpoint?.watchEndpoint?.videoId
                        ?: renderer.playlistItemData?.videoId
                        ?: renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
                    
                    if (videoId == null) {
                        Log.v("MusicRepository", "videoId nul per a un ítem")
                        return@forEach
                    }
                    
                    Log.i("MusicRepository", "Trobat videoId: $videoId")
                    
                    // Extreure el títol (columna 0)
                    val title = renderer.flexColumns?.getOrNull(0)
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.text ?: "Desconegut"
                    
                    // Extreure l'artista (columna 1)
                    // Normalment el primer run de la segona columna és l'artista
                    val artist = renderer.flexColumns?.getOrNull(1)
                        ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: "Artista desconegut"
                    
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
            
            // Si encara no hi ha resultats, intentem el mètode de cerca profunda (per si el model falla)
            if (songs.isEmpty()) {
                Log.w("MusicRepository", "Cerca buida amb el model, intentant parsing manual...")
            }
            
            Log.i("MusicRepository", "S'han trobat ${songs.size} resultats")
            songs
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error en la cerca de YouTube Music: ${e.message}", e)
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

    suspend fun removeSongFromPlaylist(song: Song, playlistId: Long) {
        musicDao.removeSongFromPlaylist(playlistId, song.id)
        
        // Verificar si la canción todavía está en alguna otra lista
        val count = musicDao.getSongOccurrenceCount(song.id)
        if (count == 0) {
            // Si no está en ninguna lista, borrar archivo y de la tabla de canciones
            song.audioUrl?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                        Log.d("MusicRepository", "Arxiu esborrat: $path")
                    }
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Error esborrant arxiu: ${e.message}")
                }
            }
            musicDao.deleteSong(song)
        }
    }

    suspend fun updateSongDownloadStatus(songId: String, localPath: String) {
        val song = musicDao.getSongById(songId)
        song?.let {
            musicDao.insertSong(it.copy(audioUrl = localPath, isDownloaded = true))
        }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> {
        return musicDao.getSongsInPlaylist(playlistId)
    }

    private fun downloadSong(song: Song) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val videoId = song.id
                val audioStreamUrl = com.example.auto_music.player.InnertubeResolver.resolveStreamUrl(videoId)
                
                if (audioStreamUrl == null) {
                    Log.e("MusicRepository", "No s'ha pogut resoldre la URL per descarregar ${song.title}")
                    return@launch
                }

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Auto_Music")
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val fileName = "${song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.mp3"
                val file = File(directory, fileName)

                val request = DownloadManager.Request(Uri.parse(audioStreamUrl))
                    .setTitle("Descarregant ${song.title}")
                    .setDescription("Descarregant música d'Auto Music")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(file))
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .addRequestHeader("Referer", "https://music.youtube.com/")

                val downloadId = downloadManager.enqueue(request)
                context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
                    .edit().putString(downloadId.toString(), song.id).apply()

                Log.d("MusicRepository", "Descàrrega en cua (ID: $downloadId) per a ${song.title} a: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error en la descàrrega de ${song.title}", e)
            }
        }
    }
}
