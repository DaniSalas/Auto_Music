package com.danielsalas.auto_music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.content.Context
import coil.compose.AsyncImage
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.model.Song
import com.danielsalas.auto_music.ui.MainViewModel
import com.danielsalas.auto_music.AppTranslations
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
    var isShuffle by remember(playlist.id) { mutableStateOf(playlist.isShuffle) }
    
    // Efficiently calculate stats and duplicates
    val stats = remember(initialSongs) {
        val totalSec = initialSongs.sumOf { it.duration }
        val h = totalSec / 3600; val m = (totalSec % 3600) / 60
        val timeText = if (h > 0) "${h}h ${m}min" else "${m}min"
        Pair(initialSongs.size, timeText)
    }

    val duplicateIds = remember(initialSongs) {
        if (initialSongs.size > 2000) emptySet() // Safety limit for extreme lists
        else initialSongs.groupBy { "${it.title.lowercase().trim()}|${it.artist.lowercase().trim()}" }
            .filter { it.value.size > 1 }.flatMap { it.value }.map { it.id }.toSet()
    }

    val displayedSongs = remember(initialSongs, sortMode) {
        when (sortMode) {
            SortMode.MANUAL -> initialSongs
            SortMode.AZ -> initialSongs.sortedBy { it.title.lowercase() }
            SortMode.ZA -> initialSongs.sortedByDescending { it.title.lowercase() }
            SortMode.DUPLICATES -> initialSongs.filter { it.id in duplicateIds }
        }
    }
    
    // Stable drag state management
    var dragSongs by remember(playlist.id) { mutableStateOf(initialSongs) }
    LaunchedEffect(initialSongs) {
        // Only reset local list if size changes to preserve order during downloads
        if (dragSongs.size != initialSongs.size) {
            dragSongs = initialSongs
        }
    }
    
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
    var showMoveDialog by remember { mutableStateOf<Song?>(null) }

    // Centralized download status observer (Ultra-efficient)
    var pendingIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        while(true) {
            val sp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
            pendingIds = sp.all.keys.filter { it.startsWith("pending_") }.map { it.removePrefix("pending_") }.toSet()
            delay(3000)
        }
    }

    LaunchedEffect(playlist.id) { viewModel.checkAndDownloadPlaylist(playlist.id) }

    LaunchedEffect(initialSongs) {
        if (draggedItemIndex == null && playlist.lastPlayedSongId != null && !isSearchMode && sortMode == SortMode.MANUAL) {
            val idx = activeSongs.indexOfFirst { it.id == playlist.lastPlayedSongId }
            if (idx != -1) listState.scrollToItem(idx)
        }
    }

    LaunchedEffect(localSearchQuery) {
        if (localSearchQuery.isNotBlank()) {
            val idx = activeSongs.indexOfFirst { it.title.contains(localSearchQuery, true) || it.artist.contains(localSearchQuery, true) }
            if (idx != -1) listState.animateScrollToItem(idx)
        }
    }

    LaunchedEffect(draggedItemIndex, dragOffsetY) {
        if (draggedItemIndex != null) {
            while (true) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val draggedItem = visibleItems.find { it.index == draggedItemIndex }
                if (draggedItem != null) {
                    val containerHeight = layoutInfo.viewportEndOffset
                    val threshold = 150f
                    if (draggedItem.offset + dragOffsetY < threshold) { listState.scrollBy(-15f) }
                    else if (draggedItem.offset + dragOffsetY + draggedItem.size > containerHeight - threshold) { listState.scrollBy(15f) }
                }
                delay(16)
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val itemHeightPx = 80f * density 

    Scaffold(
        topBar = {
            if (isSearchMode && !isSelectionMode) {
                TopAppBar(
                    title = { TextField(value = localSearchQuery, onValueChange = { localSearchQuery = it }, placeholder = { Text(strings.searchInList) }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)) },
                    navigationIcon = { IconButton(onClick = { isSearchMode = false; localSearchQuery = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                )
            } else {
                TopAppBar(
                    title = { 
                        Column {
                            Text(if (isSelectionMode) "${selectedSongs.size} ${strings.selectedItems}" else playlist.name)
                            if (!isSelectionMode) Text("${stats.first} ${strings.songsCountLabel} • ${stats.second}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) IconButton(onClick = { selectedSongs.clear() }) { Icon(Icons.Default.Close, null) }
                        else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = { selectedSongs.forEach { viewModel.removeSongFromPlaylist(it, playlist) }; selectedSongs.clear() }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        } else {
                            IconButton(onClick = { isShuffle = !isShuffle; viewModel.updatePlaylistShuffle(playlist, isShuffle) }) { 
                                Icon(if (isShuffle) Icons.Default.ShuffleOn else Icons.Default.Shuffle, contentDescription = null, tint = if (isShuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                            }
                            IconButton(onClick = { isSearchMode = true }) { Icon(Icons.Default.Search, null) }
                            if (sortMode != SortMode.MANUAL) {
                                IconButton(onClick = { sortMode = SortMode.MANUAL }) { Icon(Icons.Default.List, contentDescription = null) }
                                if (sortMode != SortMode.DUPLICATES) { IconButton(onClick = { viewModel.reorderSongs(playlist.id, displayedSongs); sortMode = SortMode.MANUAL }) { Icon(Icons.Default.Lock, contentDescription = null) } }
                            } else {
                                IconButton(onClick = { sortMode = SortMode.DUPLICATES }) { Icon(Icons.Default.Difference, contentDescription = null) }
                                IconButton(onClick = { sortMode = SortMode.AZ }) { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                                IconButton(onClick = { sortMode = SortMode.ZA }) { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.graphicsLayer { rotationX = 180f }) }
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
                
                val downloadStatus = when {
                    song.isDownloaded -> strings.downloaded
                    song.id in pendingIds -> strings.downloading
                    else -> strings.online
                }

                Card(
                    modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 4.dp).zIndex(if (isDragging) 10f else 1f).graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                        scaleX = if (isDragging) 1.03f else 1f; scaleY = if (isDragging) 1.03f else 1f
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
                            TextButton(onClick = { showMoveDialog = song }, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) { Text("${index + 1}", style = MaterialTheme.typography.labelMedium) }
                            Box(modifier = Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { val cIdx = activeSongs.indexOfFirst { it.id == song.id }; if (cIdx != -1) { initialIndex = cIdx; draggedItemIndex = cIdx; totalDragOffsetY = 0f; dragOffsetY = 0f } },
                                    onDrag = { change, dragAmount ->
                                        change.consume(); if (draggedItemIndex == null || initialIndex == null) return@detectDragGestures
                                        totalDragOffsetY += dragAmount.y
                                        val targetIndex = (initialIndex!! + (totalDragOffsetY / itemHeightPx).toInt()).coerceIn(0, activeSongs.size - 1)
                                        if (targetIndex != draggedItemIndex) {
                                            val mutable = dragSongs.toMutableList()
                                            val item = mutable.removeAt(draggedItemIndex!!)
                                            mutable.add(targetIndex, item); dragSongs = mutable; draggedItemIndex = targetIndex
                                        }
                                        dragOffsetY = totalDragOffsetY - (draggedItemIndex!! - initialIndex!!) * itemHeightPx
                                    },
                                    onDragEnd = { viewModel.reorderSongs(playlist.id, dragSongs); draggedItemIndex = null; initialIndex = null },
                                    onDragCancel = { draggedItemIndex = null; initialIndex = null }
                                )
                            }.padding(end = 4.dp).size(32.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.DragHandle, null, tint = MaterialTheme.colorScheme.outline) }
                        } else if (sortMode == SortMode.DUPLICATES) { Icon(Icons.Default.Warning, null, modifier = Modifier.padding(end = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.error) }
                        else if (sortMode != SortMode.MANUAL) { Icon(Icons.Default.SortByAlpha, null, modifier = Modifier.padding(end = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.outlineVariant) }
                        else { Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, modifier = Modifier.padding(end = 8.dp), tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }

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

    if (showMoveDialog != null) {
        var posInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveDialog = null },
            title = { Text(strings.moveToPosition) },
            text = { OutlinedTextField(value = posInput, onValueChange = { if (it.all { c -> c.isDigit() }) posInput = it }, label = { Text("1 - ${activeSongs.size}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val target = posInput.toIntOrNull()?.minus(1)
                if (target != null && target in activeSongs.indices) {
                    val mutable = activeSongs.toMutableList()
                    val item = mutable.removeAt(activeSongs.indexOf(showMoveDialog!!))
                    mutable.add(target, item); viewModel.reorderSongs(playlist.id, mutable)
                }
                showMoveDialog = null
            }) { Text(strings.close) } }
        )
    }
}
