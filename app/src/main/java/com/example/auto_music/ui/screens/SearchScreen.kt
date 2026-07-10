package com.example.auto_music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.Song
import com.example.auto_music.ui.MainViewModel

@Composable
fun SearchScreen(viewModel: MainViewModel, onPlay: (Song) -> Unit) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search by title, artist, or lyrics") },
            trailingIcon = {
                IconButton(onClick = { viewModel.search(query) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(searchResults) { song ->
                    SongItem(
                        song = song,
                        onPlay = { onPlay(song) },
                        onAddToPlaylist = {
                            selectedSong = song
                            showPlaylistDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    playlists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                selectedSong?.let { viewModel.addSongToPlaylist(it, playlist) }
                                showPlaylistDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(playlist.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SongItem(song: Song, onPlay: () -> Unit, onAddToPlaylist: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play Preview")
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Default.Add, contentDescription = "Add to Playlist")
            }
        }
    }
}
