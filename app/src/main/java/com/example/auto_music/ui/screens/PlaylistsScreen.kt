package com.example.auto_music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.auto_music.model.Playlist
import com.example.auto_music.ui.MainViewModel
import com.example.auto_music.AppTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: MainViewModel, 
    strings: AppTranslations,
    onPlaylistClick: (Playlist) -> Unit,
    onManualSync: (() -> Unit)? = null
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newIsPublic by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(strings.playlists) },
                actions = {
                    if (onManualSync != null) {
                        IconButton(onClick = onManualSync) {
                            Icon(Icons.Default.Sync, contentDescription = "Sincronitza")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Crea llista de reproducció")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            val privateLists = playlists.filter { !it.isPublic }
            val publicLists = playlists.filter { it.isPublic }

            if (privateLists.isNotEmpty()) {
                item {
                    Text(strings.autoDownloadPrivate, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(privateLists) { playlist ->
                    PlaylistCard(playlist, strings.isPrivate, Icons.Default.Lock, onPlaylistClick, { playlistToDelete = it })
                }
            }

            if (publicLists.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(strings.autoDownloadPublic, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(publicLists) { playlist ->
                    PlaylistCard(playlist, strings.isPublic, Icons.Default.Public, onPlaylistClick, { playlistToDelete = it })
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nova llista de reproducció") },
            text = {
                Column {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nom") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (newIsPublic) strings.createPublic else strings.createPrivate)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = newIsPublic, onCheckedChange = { newIsPublic = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName, newIsPublic)
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Crea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(strings.deletePlaylist) },
            text = { Text("Estàs segur que vols eliminar la llista \"${playlistToDelete?.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToDelete?.let { viewModel.deletePlaylist(it) }
                        playlistToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    typeLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick(playlist) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = playlist.name, style = MaterialTheme.typography.titleLarge)
                Text(text = typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = { onDelete(playlist) }) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
