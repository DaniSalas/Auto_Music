package com.danielsalas.auto_music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.model.Song
import com.danielsalas.auto_music.ui.MainViewModel
import com.danielsalas.auto_music.AppTranslations
import com.danielsalas.auto_music.data.remote.YouTubePlaylist

@Composable
fun SearchScreen(
    viewModel: MainViewModel, 
    strings: AppTranslations,
    initialQuery: String = "",
    onPlay: (Song) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var hasSearched by remember { mutableStateOf(initialQuery.isNotBlank()) }
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    
    val remoteSongs by viewModel.remotePlaylistSongs.collectAsState()
    val isLoadingRemote by viewModel.isLoadingRemoteSongs.collectAsState()
    var selectedYouTubePlaylist by remember { mutableStateOf<YouTubePlaylist?>(null) }

    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = selectedYouTubePlaylist != null) {
        selectedYouTubePlaylist = null
        viewModel.clearRemotePlaylistSongs()
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            query = initialQuery
            viewModel.search(initialQuery)
            hasSearched = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedYouTubePlaylist == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { 
                    query = it
                    hasSearched = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.searchPlaceholder) },
                trailingIcon = {
                    IconButton(onClick = { 
                        if (query.isNotBlank()) {
                            viewModel.search(query)
                            hasSearched = true
                            keyboardController?.hide()
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = strings.search)
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
                    Text("${strings.noResults} \"$query\"", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(searchResults) { item ->
                        when (item) {
                            is Song -> {
                                SongItem(
                                    song = item,
                                    onPlay = { onPlay(item) },
                                    onAddToPlaylist = {
                                        selectedSong = item
                                        showPlaylistDialog = true
                                    }
                                )
                            }
                            is YouTubePlaylist -> {
                                YouTubePlaylistItem(
                                    playlist = item,
                                    strings = strings,
                                    onImport = { viewModel.importYouTubePlaylist(item) },
                                    onClick = {
                                        selectedYouTubePlaylist = item
                                        viewModel.loadRemotePlaylistSongs(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Remote Playlist Detail View
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { 
                    selectedYouTubePlaylist = null
                    viewModel.clearRemotePlaylistSongs()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                Text(
                    text = selectedYouTubePlaylist!!.title, 
                    style = MaterialTheme.typography.titleLarge, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (selectedYouTubePlaylist!!.title.length > 20) 18.sp else 22.sp
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = { 
                    viewModel.importYouTubePlaylist(selectedYouTubePlaylist!!)
                    selectedYouTubePlaylist = null
                    viewModel.clearRemotePlaylistSongs()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlaylistAdd, null)
                Spacer(Modifier.width(8.dp))
                Text(strings.createPrivate)
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (isLoadingRemote) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (remoteSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron canciones", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(remoteSongs) { song ->
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
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text(strings.addToPlaylist) },
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
                    Text(strings.cancel)
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
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun YouTubePlaylistItem(playlist: YouTubePlaylist, strings: AppTranslations, onImport: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).padding(end = 8.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.author} • ${playlist.trackCount} ${strings.songsCountLabel}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onImport() }) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Import Playlist")
            }
        }
    }
}
