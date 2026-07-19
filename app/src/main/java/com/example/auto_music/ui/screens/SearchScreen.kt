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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.Song
import com.example.auto_music.ui.MainViewModel

@Composable
fun SearchScreen(
    viewModel: MainViewModel, 
    initialQuery: String = "",
    onPlay: (Song) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var hasSearched by remember { mutableStateOf(initialQuery.isNotBlank()) }
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            query = initialQuery
            viewModel.search(initialQuery)
            hasSearched = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                hasSearched = false // Reset state when typing
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Busca per títol, artista o lletra") },
            trailingIcon = {
                IconButton(onClick = { 
                    if (query.isNotBlank()) {
                        viewModel.search(query)
                        hasSearched = true
                        keyboardController?.hide()
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Cerca")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (query.isNotBlank()) {
                    viewModel.search(query)
                    hasSearched = true
                    keyboardController?.hide()
                }
            }),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (hasSearched && searchResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No s'han trobat resultats per a \"$query\"", style = MaterialTheme.typography.bodyLarge)
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
            title = { Text("Afegir a la llista de reproducció") },
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
                    Text("Cancel·la")
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
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).padding(end = 8.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reprodueix")
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Default.Add, contentDescription = "Afegeix a la llista")
            }
        }
    }
}
