package com.example.auto_music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.auto_music.AppTranslations
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistSongsScreen(
    viewModel: MainViewModel,
    strings: AppTranslations,
    playlist: Playlist,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit
) {
    val initialSongs by viewModel.getSongsInPlaylist(playlist.id).collectAsState(initial = emptyList())
    var songs by remember(playlist.id) { mutableStateOf(emptyList<Song>()) }
    
    val selectedSongs = remember { mutableStateListOf<Song>() }
    val isSelectionMode by remember { derivedStateOf { selectedSongs.isNotEmpty() } }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialIndex by remember { mutableStateOf<Int?>(null) }
    var totalDragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playlist.id) {
        viewModel.checkAndDownloadPlaylist(playlist.id)
    }

    LaunchedEffect(initialSongs) {
        if (draggedItemIndex == null) {
            songs = initialSongs
            if (playlist.lastPlayedSongId != null && songs.isNotEmpty()) {
                val idx = songs.indexOfFirst { it.id == playlist.lastPlayedSongId }
                if (idx != -1) coroutineScope.launch { listState.scrollToItem(idx) }
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val itemHeightPx = 80f * density // Adjusted for typical row height

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (isSelectionMode) Text("${selectedSongs.size} ${strings.selectedItems}") else Text(playlist.name) },
                navigationIcon = {
                    if (isSelectionMode) IconButton(onClick = { selectedSongs.clear() }) { Icon(Icons.Default.Close, null) }
                    else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedSongs.forEach { viewModel.removeSongFromPlaylist(it, playlist) }
                            selectedSongs.clear()
                        }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val isDragging = draggedItemIndex == index
                val isSelected = selectedSongs.contains(song)
                
                Card(
                    modifier = Modifier
                        .animateItem() // Smooth sliding of other items
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .zIndex(if (isDragging) 10f else 1f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.03f else 1f
                            scaleY = if (isDragging) 1.03f else 1f
                            alpha = if (isDragging) 0.8f else 1f
                        }
                        .combinedClickable(
                            onClick = { if (isSelectionMode) { if (isSelected) selectedSongs.remove(song) else selectedSongs.add(song) } else onPlay(song) },
                            onLongClick = { if (!isSelectionMode) selectedSongs.add(song) }
                        ),
                    colors = CardDefaults.cardColors(containerColor = when {
                        isDragging -> MaterialTheme.colorScheme.surfaceVariant
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    })
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .pointerInput(Unit) { // Static key to prevent gesture cancellation during swap
                                        detectDragGestures(
                                            onDragStart = { 
                                                // Find current index dynamically to handle list updates
                                                val currentIdx = songs.indexOfFirst { it.id == song.id }
                                                if (currentIdx != -1) {
                                                    initialIndex = currentIdx
                                                    draggedItemIndex = currentIdx
                                                    totalDragOffsetY = 0f
                                                    dragOffsetY = 0f
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                if (draggedItemIndex == null || initialIndex == null) return@detectDragGestures
                                                
                                                totalDragOffsetY += dragAmount.y
                                                val targetIndex = (initialIndex!! + (totalDragOffsetY / itemHeightPx).toInt()).coerceIn(0, songs.size - 1)
                                                
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
                                                initialIndex = null
                                            },
                                            onDragCancel = { draggedItemIndex = null; initialIndex = null }
                                        )
                                    }
                                    .padding(end = 12.dp)
                                    .size(32.dp),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.outline) }
                        } else {
                            Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, modifier = Modifier.padding(end = 8.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        }

                        AsyncImage(model = song.thumbnailUrl, contentDescription = null, modifier = Modifier.size(56.dp).padding(end = 8.dp), contentScale = ContentScale.Crop)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (song.isDownloaded) {
                                Text("✓ Descarregada", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                        if (!isSelectionMode) IconButton(onClick = { onPlay(song) }) { Icon(Icons.Default.PlayArrow, null) }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
