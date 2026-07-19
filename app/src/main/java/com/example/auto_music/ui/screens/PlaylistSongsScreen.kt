package com.example.auto_music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.Song
import com.example.auto_music.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistSongsScreen(
    viewModel: MainViewModel,
    playlist: Playlist,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit
) {
    val initialSongs by viewModel.getSongsInPlaylist(playlist.id).collectAsState(initial = emptyList())
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    
    val selectedSongs = remember { mutableStateListOf<Song>() }
    val isSelectionMode by remember { derivedStateOf { selectedSongs.isNotEmpty() } }

    LaunchedEffect(initialSongs) {
        if (songs.isEmpty() || songs.size != initialSongs.size) {
            songs = initialSongs
        }
    }

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialIndex by remember { mutableStateOf<Int?>(null) }
    var totalDragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val itemHeightPx = 100f * density // Estimated item height

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("${selectedSongs.size} seleccionades")
                    } else {
                        Text(playlist.name)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedSongs.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel·la")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Enrere")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedSongs.forEach { viewModel.removeSongFromPlaylist(it, playlist) }
                            selectedSongs.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina seleccionades", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val isDragging = draggedItemIndex == index
                val isSelected = selectedSongs.contains(song)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .zIndex(if (isDragging) 10f else 1f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.05f else 1f
                            scaleY = if (isDragging) 1.05f else 1f
                            alpha = if (isDragging) 0.9f else 1f
                        }
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedSongs.remove(song) else selectedSongs.add(song)
                                } else {
                                    onPlay(song)
                                }
                            },
                            onLongClick = {
                                if (!isSelected) selectedSongs.add(song)
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isDragging -> MaterialTheme.colorScheme.surfaceVariant
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { 
                                                initialIndex = index
                                                draggedItemIndex = index
                                                totalDragOffsetY = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                totalDragOffsetY += dragAmount.y
                                                
                                                val targetIndex = (initialIndex!! + (totalDragOffsetY / itemHeightPx).toInt())
                                                    .coerceIn(0, songs.size - 1)
                                                
                                                if (targetIndex != draggedItemIndex) {
                                                    val mutableSongs = songs.toMutableList()
                                                    val item = mutableSongs.removeAt(draggedItemIndex!!)
                                                    mutableSongs.add(targetIndex, item)
                                                    songs = mutableSongs
                                                    draggedItemIndex = targetIndex
                                                }
                                                dragOffsetY = totalDragOffsetY - (draggedItemIndex!! - initialIndex!!) * itemHeightPx
                                            },
                                            onDragEnd = { 
                                                viewModel.reorderSongs(playlist.id, songs)
                                                draggedItemIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = { 
                                                draggedItemIndex = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    }
                                    .padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.DragHandle, contentDescription = "Reordena", tint = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            Icon(
                                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).padding(end = 8.dp),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (song.isDownloaded) {
                                Text("✓ Descarregada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        if (!isSelectionMode) {
                            IconButton(onClick = { onPlay(song) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Reprodueix")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
