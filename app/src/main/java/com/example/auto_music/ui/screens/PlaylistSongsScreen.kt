package com.example.auto_music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.Song
import com.example.auto_music.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSongsScreen(
    viewModel: MainViewModel,
    playlist: Playlist,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit
) {
    val songs by viewModel.getSongsInPlaylist(playlist.id).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(songs) { song ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium)
                            if (song.isDownloaded) {
                                Text("✓ Downloaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { onPlay(song) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                    }
                }
            }
        }
    }
}
