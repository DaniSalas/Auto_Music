package com.danielsalas.auto_music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.ui.MainViewModel
import com.danielsalas.auto_music.AppTranslations

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    viewModel: MainViewModel, 
    strings: AppTranslations,
    onPlaylistClick: (Playlist) -> Unit,
    onManualSync: (() -> Unit)? = null,
    onMaintenance: (() -> Unit)? = null,
    isMaintenanceRunning: Boolean = false
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newIsPublic by remember { mutableStateOf(false) }
    
    val selectedPlaylists = remember { mutableStateListOf<Playlist>() }
    val isSelectionMode by remember { derivedStateOf { selectedPlaylists.isNotEmpty() } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("${selectedPlaylists.size} ${strings.selectedItems}")
                    } else {
                        Text(strings.playlists)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedPlaylists.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancel)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedPlaylists.forEach { viewModel.deletePlaylist(it) }
                            selectedPlaylists.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = strings.deletePlaylist, tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = strings.newPlaylist)
                        }
                        if (onMaintenance != null) {
                            IconButton(onClick = onMaintenance, enabled = !isMaintenanceRunning) {
                                if (isMaintenanceRunning) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Build, contentDescription = strings.maintenanceTitle)
                            }
                        }
                        if (onManualSync != null) {
                            IconButton(onClick = onManualSync) {
                                Icon(Icons.Default.Sync, contentDescription = strings.syncTitle)
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            val privateLists = playlists.filter { !it.isPublic }
            val publicLists = playlists.filter { it.isPublic }

            if (privateLists.isNotEmpty()) {
                item {
                    Text(strings.autoDownloadPrivate, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(privateLists, key = { it.id }) { playlist ->
                    val isSelected = selectedPlaylists.contains(playlist)
                    PlaylistCard(
                        playlist = playlist,
                        typeLabel = strings.isPrivate,
                        icon = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Lock,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedPlaylists.remove(playlist) else selectedPlaylists.add(playlist)
                            } else {
                                onPlaylistClick(playlist)
                            }
                        },
                        onLongClick = {
                            if (!isSelected) selectedPlaylists.add(playlist)
                        }
                    )
                }
            }

            if (publicLists.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(strings.autoDownloadPublic, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                }
                items(publicLists, key = { it.id }) { playlist ->
                    val isSelected = selectedPlaylists.contains(playlist)
                    PlaylistCard(
                        playlist = playlist,
                        typeLabel = strings.isPublic,
                        icon = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Public,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedPlaylists.remove(playlist) else selectedPlaylists.add(playlist)
                            } else {
                                onPlaylistClick(playlist)
                            }
                        },
                        onLongClick = {
                            if (!isSelected) selectedPlaylists.add(playlist)
                        }
                    )
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(strings.newPlaylist) },
            text = {
                Column {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(strings.nameField) },
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
                    Text(strings.create)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: Playlist,
    typeLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                modifier = Modifier.size(24.dp), 
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = playlist.name, style = MaterialTheme.typography.titleLarge)
                Text(text = typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
