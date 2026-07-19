package com.example.auto_music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auto_music.data.MusicRepository
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.Song
import com.example.auto_music.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(value = false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private var syncManager: SyncManager? = null

    fun setSyncManager(sm: SyncManager) {
        syncManager = sm
    }

    init {
        viewModelScope.launch {
            repository.allPlaylists.collect {
                _playlists.value = it
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            val results = repository.searchSongs(query)
            _searchResults.value = results
            _isLoading.value = false
        }
    }

    fun createPlaylist(name: String, isPublic: Boolean = false) {
        viewModelScope.launch {
            repository.createPlaylist(name, isPublic)
        }
    }

    fun addSongToPlaylist(song: Song, playlist: Playlist) {
        viewModelScope.launch {
            repository.addSongToPlaylist(song, playlist.id)
        }
    }

    fun getSongsInPlaylist(playlistId: Long): kotlinx.coroutines.flow.Flow<List<Song>> {
        return repository.getSongsInPlaylist(playlistId)
    }

    fun removeSongFromPlaylist(song: Song, playlist: Playlist) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(song, playlist.id)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            // Delete from cloud first if sync manager is available
            syncManager?.deleteCloudPlaylist(playlist)
            repository.deletePlaylist(playlist)
        }
    }

    fun reorderSongs(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch {
            repository.updateSongOrder(playlistId, songs)
        }
    }

    fun updatePlaylistPlaybackState(playlistId: Long, songId: String?, position: Long) {
        viewModelScope.launch {
            repository.updatePlaylistPlaybackState(playlistId, songId, position)
        }
    }
}
