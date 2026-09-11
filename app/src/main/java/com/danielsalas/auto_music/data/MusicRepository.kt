package com.danielsalas.auto_music.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.danielsalas.auto_music.data.local.MusicDao
import com.danielsalas.auto_music.data.remote.*
import com.danielsalas.auto_music.data.remote.YouTubePlaylist
import com.danielsalas.auto_music.data.remote.YouTubeService
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.model.PlaylistSongCrossRef
import com.danielsalas.auto_music.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@androidx.media3.common.util.UnstableApi
class MusicRepository(
    private val musicDao: MusicDao,
    private val youtubeService: YouTubeService,
    private val context: Context,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()
    val allSongs: Flow<List<Song>> = musicDao.getAllSongs()

    suspend fun searchSongs(query: String): List<Song> {
        return try {
            Log.d("MusicRepository", "Searching songs for: $query")
            val response = Innertube.search(query, params = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val songs = if (response != null) parseSongsFromResponse(response) else mutableListOf()
            
            if (songs.isEmpty()) {
                Log.d("MusicRepository", "Strict search empty, trying general search")
                val generalResponse = Innertube.search(query, params = null)
                generalResponse?.let { songs.addAll(parseSongsFromResponse(it)) }
            }
            
            Log.d("MusicRepository", "Found ${songs.size} songs")
            songs.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error cerca: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseSongsFromResponse(response: InnerTubeResponse): MutableList<Song> {
        val songs = mutableListOf<Song>()
        val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: response.contents?.sectionListRenderer?.contents
        
        contents?.forEach { section ->
            parseSection(section, songs)
        }
        return songs
    }

    private fun parseSection(section: SectionContent, songs: MutableList<Song>) {
        section.itemSectionRenderer?.contents?.forEach { subSection ->
            parseSection(subSection, songs)
        }

        section.musicCardShelfRenderer?.let { card ->
            card.contents?.forEach { item ->
                parseMusicItem(item, songs)
            }
        }

        val shelf = section.musicShelfRenderer ?: section.musicPlaylistShelfRenderer ?: section.musicCarouselShelfRenderer ?: section.musicPlaylistShelfContinuation
        shelf?.contents?.forEach { item ->
            parseMusicItem(item, songs)
        }

        section.gridRenderer?.items?.forEach { item ->
            parseMusicItem(item, songs)
        }
    }

    private fun parseMusicItem(item: MusicItem, songs: MutableList<Song>) {
        val renderer = item.musicResponsiveListItemRenderer ?: return
        val videoId = renderer.navigationEndpoint?.watchEndpoint?.videoId 
            ?: renderer.playlistItemData?.videoId
            ?: renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstNotNullOfOrNull { it.navigationEndpoint?.watchEndpoint?.videoId }
        
        if (videoId != null) {
            val title = renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.joinToString("") { it.text ?: "" } ?: "Unknown"
            val subtitleRuns = renderer.flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            
            var artist = "Unknown"
            var album: String? = null
            var durationSeconds = 0L

            if (subtitleRuns != null) {
                val texts = subtitleRuns.mapNotNull { it.text }.filter { it != " • " && it != "•" }
                
                if (texts.size >= 2) {
                    artist = texts[0]
                    if (artist == "Song" || artist == "Canción") {
                        artist = texts.getOrNull(1) ?: "Unknown"
                        album = texts.getOrNull(2)
                    } else {
                        album = texts.getOrNull(1)
                    }
                }
                
                val lengthText = renderer.lengthText?.runs?.firstOrNull()?.text 
                    ?: texts.lastOrNull()?.takeIf { it.contains(":") }
                durationSeconds = lengthText?.let { parseDuration(it) } ?: 0L
            }

            val thumb = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""
            songs.add(Song(id = videoId, title = title, artist = artist, album = album, thumbnailUrl = thumb, duration = durationSeconds))
        }
    }

    suspend fun searchPlaylists(query: String): List<YouTubePlaylist> {
        return try {
            val response = Innertube.search(query, params = "EgeKAQQoAEABagoQAxAEEAoQCRAF") ?: return emptyList()
            val playlists = mutableListOf<YouTubePlaylist>()
            val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents
                ?: response.contents?.sectionListRenderer?.contents
            
            contents?.forEach { section ->
                val shelf = section.musicShelfRenderer ?: section.musicPlaylistShelfRenderer ?: section.musicCarouselShelfRenderer
                shelf?.contents?.forEach { item ->
                    val renderer = item.musicResponsiveListItemRenderer ?: return@forEach
                    val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return@forEach
                    
                    val title = renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.joinToString("") { it.text ?: "" } ?: "Llista"
                    val subtitleRuns = renderer.flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                    val author = subtitleRuns?.firstOrNull()?.text ?: "YouTube"
                    val countText = subtitleRuns?.lastOrNull()?.text ?: "0"
                    val trackCount = countText.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val thumb = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url ?: ""

                    playlists.add(YouTubePlaylist(id = browseId, title = title, author = author, trackCount = trackCount, thumbnailUrl = thumb))
                }
            }
            playlists.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error cerca llistes: ${e.message}")
            emptyList()
        }
    }

    suspend fun getYouTubePlaylistSongs(playlistId: String): List<Song> {
        Log.d("MusicRepository", "Fetching songs for playlist: $playlistId")
        val browseId = if (playlistId.startsWith("PL") && !playlistId.startsWith("VL")) "VL$playlistId" else playlistId
        
        val response = Innertube.browse(browseId) ?: return emptyList()
        val songs = mutableListOf<Song>()
        
        response.contents?.let { contents ->
            contents.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents?.forEach { parseSection(it, songs) }
            contents.twoColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { parseSection(it, songs) }
            }
            contents.singleColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { parseSection(it, songs) }
            }
            contents.sectionListRenderer?.contents?.forEach { parseSection(it, songs) }
            contents.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { parseSection(it, songs) }
        }
        
        response.continuationContents?.let { cc ->
            cc.musicShelfContinuation?.contents?.forEach { parseMusicItem(it, songs) }
            cc.musicPlaylistShelfContinuation?.contents?.forEach { parseMusicItem(it, songs) }
            cc.sectionListContinuation?.contents?.forEach { parseSection(it, songs) }
        }
        
        Log.d("MusicRepository", "Found ${songs.size} songs in playlist $playlistId")
        return songs.distinctBy { it.id }
    }

    suspend fun importYouTubePlaylist(ytPlaylist: YouTubePlaylist) {
        val songs = getYouTubePlaylistSongs(ytPlaylist.id)
        val playlistId = createPlaylist(ytPlaylist.title, isPublic = false)
        songs.forEach { song ->
            addSongToPlaylist(song, playlistId)
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
        try {
            val existing = musicDao.getSongById(song.id)
            if (existing == null) musicDao.insertSong(song)
            else if ((existing.duration == 0L && song.duration > 0) || (existing.album == null && song.album != null)) {
                musicDao.insertSong(existing.copy(duration = song.duration, album = song.album))
            }
            
            val songsInPlaylist = musicDao.getSongsInPlaylist(playlistId).first()
            if (songsInPlaylist.none { it.id == song.id }) {
                val maxPos = musicDao.getMaxPosition(playlistId) ?: -1
                musicDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id, maxPos + 1))
            }
            checkAndDownloadPlaylistSongs(playlistId)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error adding song to playlist: ${e.message}")
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

    suspend fun updateSongDownloadStatus(songId: String, localPath: String, lyrics: String? = null) {
        musicDao.getSongById(songId)?.let { musicDao.insertSong(it.copy(audioUrl = localPath, isDownloaded = true, lyrics = lyrics ?: it.lyrics)) }
    }

    suspend fun updateLyrics(songId: String, lyrics: String) {
        musicDao.getSongById(songId)?.let { musicDao.insertSong(it.copy(lyrics = lyrics)) }
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = musicDao.getSongsInPlaylist(playlistId)

    suspend fun checkAndDownloadPlaylistSongs(playlistId: Long) {
        val p = musicDao.getPlaylistById(playlistId) ?: return
        val sp = context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE)
        val shouldDownload = if (p.isPublic) sp.getBoolean("auto_download_public", true) else sp.getBoolean("auto_download_private", true)
        if (!shouldDownload) return

        val songs = musicDao.getSongsInPlaylist(playlistId).first()
        val downloadUtil = com.danielsalas.auto_music.player.DownloadUtil.getInstance(context)
        val downloadIndex = downloadUtil.downloadManager.downloadIndex

        songs.forEach { song ->
            val download = downloadIndex.getDownload(song.id)
            Log.d("MusicRepository", "Checking download for ${song.title}: state=${download?.state ?: "NOT_IN_INDEX"}")
            
            if (download != null) {
                if (download.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                    if (!song.isDownloaded) {
                        musicDao.insertSong(song.copy(isDownloaded = true))
                    }
                } else if (download.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED || download.state == androidx.media3.exoplayer.offline.Download.STATE_STOPPED) {
                    downloadSong(song)
                }
            } else if (!song.isDownloaded) {
                downloadSong(song)
            }
        }
    }

    fun downloadSong(song: Song, force: Boolean = false) {
        val sp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        if (!force && sp.contains("pending_${song.id}")) return
        
        repositoryScope.launch {
            try {
                val downloadRequest = androidx.media3.exoplayer.offline.DownloadRequest.Builder(song.id, android.net.Uri.parse("youtube://${song.id}"))
                    .setData(song.title.toByteArray())
                    .build()
                
                Log.d("MusicRepository", "Sending addDownload for ${song.title} (${song.id})")
                androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                    context,
                    com.danielsalas.auto_music.player.ExoDownloadService::class.java,
                    downloadRequest,
                    true
                )
                
                sp.edit().putBoolean("pending_${song.id}", true).apply()
            } catch (e: Exception) { 
                Log.e("MusicRepository", "Download triggering error for ${song.title}: ${e.message}")
                sp.edit().remove("pending_${song.id}").apply() 
            }
        }
    }

    suspend fun downloadMissingLyrics(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val songsMissingLyrics = musicDao.getAllSongsList().filter { it.lyrics == null }
        Log.i("MusicRepository", "Found ${songsMissingLyrics.size} songs missing lyrics")
        
        songsMissingLyrics.forEach { song ->
            try {
                val lyrics = com.danielsalas.auto_music.api.lrclib.LrcLib.getLyrics(song.title, song.artist, song.duration.toInt())
                if (lyrics != null) {
                    musicDao.insertSong(song.copy(lyrics = lyrics))
                    count++
                    delay(500) // Avoid rate limiting
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Failed to fetch lyrics for ${song.title}: ${e.message}")
            }
        }
        count
    }

    // Removed executeDownload as it's now handled by DownloadService

    suspend fun cancelAllDownloads() {
        val downloadUtil = com.danielsalas.auto_music.player.DownloadUtil.getInstance(context)
        downloadUtil.downloadManager.removeAllDownloads()
        context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun performLibraryMaintenance(): MaintenanceSummary = withContext(Dispatchers.IO) {
        val errors = mutableListOf<MaintenanceError>()
        var cleanedFiles = 0; var totalRequeued = 0; var restoredSongs = 0
        
        try {
            val downloadUtil = com.danielsalas.auto_music.player.DownloadUtil.getInstance(context)
            val downloadIndex = downloadUtil.downloadManager.downloadIndex
            
            // 1. Clean up orphan downloads
            val allSongsInDb = musicDao.getAllSongsList()
            val validSongIds = allSongsInDb.map { it.id }.toSet()
            
            downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val download = cursor.download
                    if (download.request.id !in validSongIds) {
                        downloadUtil.downloadManager.removeDownload(download.request.id)
                        cleanedFiles++
                    }
                }
            }
            
            val sp = context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE)
            val downloadSp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
            
            // Clear pending flags to allow fresh requeueing
            downloadSp.edit().clear().apply()
            
            val autoDownloadPublic = sp.getBoolean("auto_download_public", true)
            val autoDownloadPrivate = sp.getBoolean("auto_download_private", true)
            val shouldDownloadLyrics = sp.getBoolean("download_lyrics", true)
            
            val playlists = musicDao.getAllPlaylists().first()
            val uniqueSongs = mutableSetOf<String>()
            
            // 2. Requeue missing songs FIRST
            for (playlist in playlists) {
                val shouldDownload = if (playlist.isPublic) autoDownloadPublic else autoDownloadPrivate
                val songsInPlaylist = musicDao.getSongsInPlaylist(playlist.id).first()
                
                for (song in songsInPlaylist) {
                    if (song.id in uniqueSongs) continue
                    uniqueSongs.add(song.id)
                    
                    val download = downloadIndex.getDownload(song.id)
                    if (download != null) {
                        if (download.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                            if (!song.isDownloaded) { 
                                musicDao.insertSong(song.copy(isDownloaded = true))
                                restoredSongs++ 
                            } 
                        } else if (download.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED) {
                            downloadSong(song, force = true)
                            totalRequeued++
                        }
                    } else if (shouldDownload) {
                        if (song.isDownloaded) musicDao.insertSong(song.copy(isDownloaded = false))
                        downloadSong(song, force = true)
                        totalRequeued++
                        delay(100)
                    }
                }
            }

            // 3. Identify missing lyrics
            val songsMissingLyrics = allSongsInDb.filter { it.lyrics == null }
            val lyricsPending = songsMissingLyrics.size
            
            if (shouldDownloadLyrics && lyricsPending > 0) {
                repositoryScope.launch {
                    Log.i("MusicRepository", "Waiting for song downloads to finish before fetching $lyricsPending lyrics...")
                    
                    // Wait until no active downloads
                    while (isActive) {
                        val activeCount = try { downloadUtil.downloadManager.currentDownloads.size } catch (e: Exception) { 0 }
                        if (activeCount == 0) break
                        delay(5000)
                    }
                    
                    Log.i("MusicRepository", "Starting batch lyrics download...")
                    downloadMissingLyrics()
                }
            }

            MaintenanceSummary(cleanedFiles, totalRequeued, restoredSongs, lyricsPending, errors)
        } catch (e: Exception) {
            Log.e("MusicRepository", "Maintenance error: ${e.message}", e)
            errors.add(MaintenanceError("Global", "Error: ${e.message}"))
            MaintenanceSummary(cleanedFiles, totalRequeued, restoredSongs, 0, errors)
        }
    }
    
    suspend fun updatePlaylistShuffle(playlistId: Long, shuffle: Boolean) {
        musicDao.updatePlaylistShuffle(playlistId, shuffle)
    }

    suspend fun updatePlaylistNormalization(playlistId: Long, normalized: Boolean) {
        musicDao.updatePlaylistNormalization(playlistId, normalized)
    }
}

data class MaintenanceSummary(
    val filesCleaned: Int, 
    val songsRequeued: Int, 
    val songsRestored: Int, 
    val lyricsPending: Int, 
    val errors: List<MaintenanceError>
)
data class MaintenanceError(val title: String, val reason: String)
