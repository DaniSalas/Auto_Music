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
                val musicShelf = section.musicShelfRenderer ?: section.musicPlaylistShelfRenderer ?: section.musicCarouselShelfRenderer
                musicShelf?.contents?.forEach { item ->
                    val renderer = item.musicResponsiveListItemRenderer ?: return@forEach
                    val videoId = renderer.navigationEndpoint?.watchEndpoint?.videoId ?: renderer.playlistItemData?.videoId
                        ?: renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
                    if (videoId == null) return@forEach
                    
                    val title = renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.text ?: "Desconegut"
                    val artistRuns = renderer.flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                    val artist = artistRuns?.firstOrNull()?.text ?: "Artista desconegut"
                    val album = if (artistRuns != null && artistRuns.size >= 3) artistRuns[2].text else null
                    val thumb = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""
                    val lengthText = renderer.lengthText?.runs?.firstOrNull()?.text ?: artistRuns?.lastOrNull()?.text
                    val durationSeconds = lengthText?.let { parseDuration(it) } ?: 0L

                    songs.add(Song(id = videoId, title = title, artist = artist, album = album, thumbnailUrl = thumb, audioUrl = null, duration = durationSeconds))
                }
            }
            songs
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error cerca: ${e.message}")
            emptyList()
        }
    }

    private fun parseDuration(text: String): Long {
        return try {
            val parts = text.split(":"); val s = parts.size
            if (s == 2) parts[0].toLong() * 60 + parts[1].toLong()
            else if (s == 3) parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
            else 0L
        } catch (e: Exception) { 0L }
    }

    suspend fun getSongById(songId: String): Song? = musicDao.getSongById(songId)
    suspend fun getPlaylistById(playlistId: Long): Playlist? = musicDao.getPlaylistById(playlistId)
    suspend fun updatePlaylistPlaybackState(playlistId: Long, songId: String?, position: Long) = musicDao.updatePlaylistPlaybackState(playlistId, songId, position)
    
    suspend fun createPlaylist(name: String, isPublic: Boolean = false, cloudId: String? = null): Long {
        val playlists = musicDao.getAllPlaylists().first()
        val existing = if (cloudId != null) playlists.find { it.cloudId == cloudId } else playlists.find { it.name == name && it.isPublic == isPublic }
        if (existing != null) return existing.id
        return musicDao.insertPlaylist(Playlist(name = name, isPublic = isPublic, cloudId = cloudId ?: if (isPublic) java.util.UUID.randomUUID().toString() else null))
    }

    suspend fun addSongToPlaylist(song: Song, playlistId: Long) {
        val existing = musicDao.getSongById(song.id)
        if (existing == null) musicDao.insertSong(song)
        else if ((existing.duration == 0L && song.duration > 0) || (existing.album == null && song.album != null)) musicDao.insertSong(existing.copy(duration = song.duration, album = song.album))
        
        val songsInPlaylist = musicDao.getSongsInPlaylist(playlistId).first()
        if (songsInPlaylist.none { it.id == song.id }) {
            val maxPos = musicDao.getMaxPosition(playlistId) ?: -1
            musicDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id, maxPos + 1))
        }
        
        // Re-fetch to get latest state
        val current = musicDao.getSongById(song.id) ?: song
        if (!current.isDownloaded) {
            triggerAutoDownload(current, playlistId)
        }
    }

    private suspend fun triggerAutoDownload(song: Song, playlistId: Long) {
        val p = musicDao.getPlaylistById(playlistId)
        val sp = context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE)
        val should = if (p?.isPublic == true) sp.getBoolean("auto_download_public", true) else sp.getBoolean("auto_download_private", true)
        if (should) downloadSong(song)
    }

    suspend fun checkAndDownloadPlaylistSongs(playlistId: Long) {
        val songs = musicDao.getSongsInPlaylist(playlistId).first()
        songs.forEach { song ->
            if (!song.isDownloaded) {
                triggerAutoDownload(song, playlistId)
            }
        }
    }

    suspend fun updateSongOrder(playlistId: Long, songs: List<Song>) = musicDao.updateSongOrder(playlistId, songs.map { it.id })
    suspend fun removeSongFromPlaylist(song: Song, playlistId: Long) { musicDao.removeSongFromPlaylist(playlistId, song.id); cleanupOrphanedSong(song.id) }
    suspend fun deletePlaylist(playlist: Playlist) { val songs = musicDao.getSongsInPlaylist(playlist.id).first(); musicDao.deletePlaylist(playlist); songs.forEach { cleanupOrphanedSong(it.id) } }
    
    private suspend fun cleanupOrphanedSong(songId: String) {
        if (musicDao.getSongOccurrenceCount(songId) == 0) {
            val song = musicDao.getSongById(songId)
            song?.audioUrl?.let { try { File(it).let { f -> if (f.exists()) f.delete() } } catch (e: Exception) { } }
            song?.let { musicDao.deleteSong(it) }
        }
    }

    suspend fun updateSongDownloadStatus(songId: String, localPath: String) {
        musicDao.getSongById(songId)?.let { musicDao.insertSong(it.copy(audioUrl = localPath, isDownloaded = true)) }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = musicDao.getSongsInPlaylist(playlistId)

    fun downloadSong(song: Song) {
        val sp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        if (sp.contains("pending_${song.id}")) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stream = com.example.auto_music.player.InnertubeResolver.resolveStream(song.id) ?: return@launch
                val fileName = "${song.id}.mp3"; val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "auto_music")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                if (file.exists()) { updateSongDownloadStatus(song.id, file.absolutePath); return@launch }
                val request = DownloadManager.Request(Uri.parse(stream.url)).setTitle("Auto Music: ${song.title}").setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "auto_music/$fileName").setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN).addRequestHeader("User-Agent", stream.userAgent).addRequestHeader("Referer", "https://music.youtube.com/")
                val downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                sp.edit().putString(downloadId.toString(), song.id).putBoolean("pending_${song.id}", true).apply()
            } catch (e: Exception) { sp.edit().remove("pending_${song.id}").apply() }
        }
    }
}
