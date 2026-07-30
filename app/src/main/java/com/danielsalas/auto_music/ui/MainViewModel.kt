package com.danielsalas.auto_music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielsalas.auto_music.data.MusicRepository
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.model.Song
import com.danielsalas.auto_music.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists.let { flow ->
        val state = MutableStateFlow<List<Playlist>>(emptyList())
        viewModelScope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    private var syncManager: SyncManager? = null
    fun setSyncManager(manager: SyncManager) { syncManager = manager }

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _searchResults.value = repository.searchSongs(query)
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
}
