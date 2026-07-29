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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import coil.compose.AsyncImage
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.Song
import com.example.auto_music.ui.MainViewModel
import com.example.auto_music.AppTranslations
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SortMode { MANUAL, AZ, ZA, DUPLICATES }

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
    var sortMode by remember { mutableStateOf(SortMode.MANUAL) }
    
    // Logic to find duplicate conceptual songs (Title + Artist)
    val duplicateIds = remember(initialSongs) {
        initialSongs.groupBy { "${it.title.lowercase().trim()}|${it.artist.lowercase().trim()}" }
            .filter { it.value.size > 1 }
            .flatMap { it.value }
            .map { it.id }
            .toSet()
    }

    val displayedSongs = remember(initialSongs, sortMode) {
        when (sortMode) {
            SortMode.MANUAL -> initialSongs
            SortMode.AZ -> initialSongs.sortedBy { it.title.lowercase() }
            SortMode.ZA -> initialSongs.sortedByDescending { it.title.lowercase() }
            SortMode.DUPLICATES -> initialSongs.filter { it.id in duplicateIds }
        }
    }
    
    var dragSongs by remember(initialSongs) { mutableStateOf(initialSongs) }
    val activeSongs = if (sortMode == SortMode.MANUAL) dragSongs else displayedSongs

    val selectedSongs = remember { mutableStateListOf<Song>() }
    val isSelectionMode by remember { derivedStateOf { selectedSongs.isNotEmpty() } }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var initialIndex by remember { mutableStateOf<Int?>(null) }
    var totalDragOffsetY by remember { mutableFloatStateOf(0f) }

    var isSearchMode by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(playlist.id) { viewModel.checkAndDownloadPlaylist(playlist.id) }

    LaunchedEffect(initialSongs) {
        if (draggedItemIndex == null) {
            if (playlist.lastPlayedSongId != null && !isSearchMode && sortMode == SortMode.MANUAL) {
                val idx = activeSongs.indexOfFirst { it.id == playlist.lastPlayedSongId }
                if (idx != -1) coroutineScope.launch { listState.scrollToItem(idx) }
            }
        }
    }

    LaunchedEffect(localSearchQuery) {
        if (localSearchQuery.isNotBlank()) {
            val idx = activeSongs.indexOfFirst { it.title.contains(localSearchQuery, true) || it.artist.contains(localSearchQuery, true) }
            if (idx != -1) listState.animateScrollToItem(idx)
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val itemHeightPx = 80f * density 

    Scaffold(
        topBar = {
            if (isSearchMode && !isSelectionMode) {
                TopAppBar(
                    title = {
                        TextField(value = localSearchQuery, onValueChange = { localSearchQuery = it }, placeholder = { Text(strings.searchInList) }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
                    },
                    navigationIcon = { IconButton(onClick = { isSearchMode = false; localSearchQuery = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                )
            } else {
                TopAppBar(
                    title = { if (isSelectionMode) Text("${selectedSongs.size} ${strings.selectedItems}") else Text(playlist.name) },
                    navigationIcon = {
                        if (isSelectionMode) IconButton(onClick = { selectedSongs.clear() }) { Icon(Icons.Default.Close, null) }
                        else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = { selectedSongs.forEach { viewModel.removeSongFromPlaylist(it, playlist) }; selectedSongs.clear() }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        } else {
                            IconButton(onClick = { isSearchMode = true }) { Icon(Icons.Default.Search, null) }
                            
                            if (sortMode != SortMode.MANUAL) {
                                IconButton(onClick = { sortMode = SortMode.MANUAL }) { Icon(Icons.Default.List, contentDescription = strings.manualOrder) }
                                if (sortMode != SortMode.DUPLICATES) {
                                    IconButton(onClick = { 
                                        viewModel.reorderSongs(playlist.id, displayedSongs)
                                        sortMode = SortMode.MANUAL 
                                    }) { Icon(Icons.Default.Lock, contentDescription = strings.fixOrder) }
                                }
                            } else {
                                IconButton(onClick = { sortMode = SortMode.DUPLICATES }) { Icon(Icons.Default.Difference, contentDescription = strings.findDuplicates) }
                                IconButton(onClick = { sortMode = SortMode.AZ }) { Icon(Icons.Default.SortByAlpha, contentDescription = strings.sortAZ) }
                                IconButton(onClick = { sortMode = SortMode.ZA }) { Icon(Icons.Default.Sort, modifier = Modifier.graphicsLayer { rotationX = 180f }, contentDescription = strings.sortZA) }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            itemsIndexed(activeSongs, key = { index, song -> "${song.id}_$index" }) { index, song ->
                val isDragging = draggedItemIndex == index
                val isSelected = selectedSongs.contains(song)
                val isMatch = localSearchQuery.isNotBlank() && (song.title.contains(localSearchQuery, true) || song.artist.contains(localSearchQuery, true))
                val isDuplicate = sortMode == SortMode.DUPLICATES
                
                var downloadStatus by remember(song.id, song.isDownloaded) { mutableStateOf(getDownloadStatusText(context, song, strings)) }
                LaunchedEffect(song.id, song.isDownloaded) {
                    if (!song.isDownloaded) {
                        while (true) {
                            val current = getDownloadStatusText(context, song, strings)
                            if (current != downloadStatus) downloadStatus = current
                            if (current == strings.downloaded) break
                            delay(2000)
                        }
                    } else downloadStatus = strings.downloaded
                }

                Card(
                    modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 4.dp).zIndex(if (isDragging) 10f else 1f).graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                        scaleX = if (isDragging) 1.03f else 1f
                        scaleY = if (isDragging) 1.03f else 1f
                        alpha = if (isDragging) 0.8f else 1f
                    }.combinedClickable(onClick = { if (isSelectionMode) { if (isSelected) selectedSongs.remove(song) else selectedSongs.add(song) } else onPlay(song) }, onLongClick = { if (!isSelectionMode) selectedSongs.add(song) }),
                    colors = CardDefaults.cardColors(containerColor = when {
                        isDragging -> MaterialTheme.colorScheme.surfaceVariant
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isMatch -> MaterialTheme.colorScheme.tertiaryContainer
                        isDuplicate -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.surface
                    })
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!isSelectionMode && sortMode == SortMode.MANUAL) {
                            Box(modifier = Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { val cIdx = activeSongs.indexOfFirst { it.id == song.id }; if (cIdx != -1) { initialIndex = cIdx; draggedItemIndex = cIdx; totalDragOffsetY = 0f; dragOffsetY = 0f } },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggedItemIndex == null || initialIndex == null) return@detectDragGestures
                                        totalDragOffsetY += dragAmount.y
                                        val targetIndex = (initialIndex!! + (totalDragOffsetY / itemHeightPx).toInt()).coerceIn(0, activeSongs.size - 1)
                                        if (targetIndex != draggedItemIndex) {
                                            val mutable = dragSongs.toMutableList()
                                            val item = mutable.removeAt(draggedItemIndex!!)
                                            mutable.add(targetIndex, item)
                                            dragSongs = mutable
                                            draggedItemIndex = targetIndex
                                        }
                                        dragOffsetY = totalDragOffsetY - (draggedItemIndex!! - initialIndex!!) * itemHeightPx
                                    },
                                    onDragEnd = { viewModel.reorderSongs(playlist.id, dragSongs); draggedItemIndex = null; initialIndex = null },
                                    onDragCancel = { draggedItemIndex = null; initialIndex = null }
                                )
                            }.padding(end = 12.dp).size(32.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.outline) }
                        } else if (sortMode == SortMode.DUPLICATES) {
                             Icon(Icons.Default.Warning, null, modifier = Modifier.padding(end = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.error)
                        } else if (sortMode != SortMode.MANUAL) {
                             Icon(Icons.Default.SortByAlpha, null, modifier = Modifier.padding(end = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        } else {
                            Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, modifier = Modifier.padding(end = 8.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        }

                        AsyncImage(model = song.thumbnailUrl, contentDescription = null, modifier = Modifier.size(56.dp).padding(end = 8.dp), contentScale = ContentScale.Crop)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val statusColor = when (downloadStatus) { strings.downloaded -> Color(0xFF4CAF50); strings.downloading -> Color(0xFFFF9800); else -> Color.Gray }
                            Text(downloadStatus, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        if (!isSelectionMode) IconButton(onClick = { onPlay(song) }) { Icon(Icons.Default.PlayArrow, null) }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun getDownloadStatusText(context: Context, song: Song, strings: AppTranslations): String {
    if (song.isDownloaded) return strings.downloaded
    val sp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    if (sp.getBoolean("pending_${song.id}", false)) return strings.downloading
    return strings.online
}
