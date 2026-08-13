package com.danielsalas.auto_music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielsalas.auto_music.data.MusicRepository
import com.danielsalas.auto_music.data.remote.YouTubePlaylist
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.model.Song
import com.danielsalas.auto_music.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _searchResults = MutableStateFlow<List<Any>>(emptyList())
    val searchResults: StateFlow<List<Any>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _remotePlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val remotePlaylistSongs: StateFlow<List<Song>> = _remotePlaylistSongs.asStateFlow()

    private val _isLoadingRemoteSongs = MutableStateFlow(false)
    val isLoadingRemoteSongs: StateFlow<Boolean> = _isLoadingRemoteSongs.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists.let { flow ->
        val state = MutableStateFlow<List<Playlist>>(emptyList())
        viewModelScope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    private var syncManager: SyncManager? = null
    fun setSyncManager(manager: SyncManager) { syncManager = manager }

    private var mediaController: androidx.media3.session.MediaController? = null
    fun setMediaController(controller: androidx.media3.session.MediaController?) { mediaController = controller }

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val songs = repository.searchSongs(query)
            val playlists = repository.searchPlaylists(query)
            _searchResults.value = songs + playlists
            _isLoading.value = false
        }
    }

    fun loadRemotePlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            _isLoadingRemoteSongs.value = true
            _remotePlaylistSongs.value = repository.getYouTubePlaylistSongs(playlistId)
            _isLoadingRemoteSongs.value = false
        }
    }

    fun clearRemotePlaylistSongs() {
        _remotePlaylistSongs.value = emptyList()
    }

    fun importYouTubePlaylist(ytPlaylist: YouTubePlaylist) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.importYouTubePlaylist(ytPlaylist)
            _isLoading.value = false
        }
    }

    fun getSongsInPlaylist(playlistId: Long) = repository.getSongsInPlaylist(playlistId)

    fun createPlaylist(name: String, isPublic: Boolean) {
        viewModelScope.launch { repository.createPlaylist(name, isPublic) }
    }

    fun addSongToPlaylist(song: Song, playlist: Playlist) {
        viewModelScope.launch { repository.addSongToPlaylist(song, playlist.id) }
    }

    fun removeSongFromPlaylist(song: Song, playlist: Playlist) {
        viewModelScope.launch { repository.removeSongFromPlaylist(song, playlist.id) }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun reorderSongs(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch { repository.updateSongOrder(playlistId, songs) }
    }

    fun checkAndDownloadPlaylist(playlistId: Long) {
        viewModelScope.launch { repository.checkAndDownloadPlaylistSongs(playlistId) }
    }
    
    fun updatePlaylistShuffle(playlist: Playlist, shuffle: Boolean) {
        viewModelScope.launch {
            repository.updatePlaylistShuffle(playlist.id, shuffle)
        }
    }

    fun updatePlaylistNormalization(playlist: Playlist, normalized: Boolean) {
        viewModelScope.launch {
            repository.updatePlaylistNormalization(playlist.id, normalized)
            mediaController?.sendCustomCommand(
                androidx.media3.session.SessionCommand("ACTION_UPDATE_NORMALIZATION", android.os.Bundle.EMPTY),
                android.os.Bundle().apply { putBoolean("enabled", normalized) }
            )
        }
    }
}
