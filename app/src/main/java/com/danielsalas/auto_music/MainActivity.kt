package com.danielsalas.auto_music

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.danielsalas.auto_music.data.MusicRepository
import com.danielsalas.auto_music.sync.SyncManager
import com.danielsalas.auto_music.data.local.MusicDatabase
import com.danielsalas.auto_music.data.remote.YouTubeService
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.model.Song
import com.danielsalas.auto_music.ui.MainViewModel
import com.danielsalas.auto_music.ui.screens.PlaylistsScreen
import com.danielsalas.auto_music.ui.screens.PlaylistSongsScreen
import com.danielsalas.auto_music.ui.screens.SearchScreen
import com.danielsalas.auto_music.ui.theme.Auto_MusicTheme
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
        }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requestPermissionLauncher.launch(permissions)

        val database = MusicDatabase.getDatabase(this)
        val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
        val retrofit = Retrofit.Builder().baseUrl("https://www.youtube.com/").client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
        val repository = MusicRepository(database.musicDao(), retrofit.create(YouTubeService::class.java), this)
        val syncManager = SyncManager(this, repository)
        
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository) as T
            }
        })[MainViewModel::class.java]

        val sessionToken = SessionToken(this, android.content.ComponentName(this, com.danielsalas.auto_music.player.MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        setContent {
            var controller by remember { mutableStateOf<MediaController?>(null) }
            val sp = remember { getSharedPreferences("Settings", Context.MODE_PRIVATE) }
            var isDarkTheme by remember { mutableStateOf(sp.getBoolean("dark_mode", false)) }
            
            DisposableEffect(Unit) {
                controllerFuture.addListener({ 
                    try { controller = controllerFuture.get() } catch (e: Exception) { Log.e("MainActivity", "Controller error: ${e.message}") }
                }, MoreExecutors.directExecutor())
                onDispose { MediaController.releaseFuture(controllerFuture) }
            }

            Auto_MusicTheme(darkTheme = isDarkTheme) {
                MainApp(viewModel, repository, controller, isDarkTheme, syncManager) { isDarkTheme = it }
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: MainViewModel,
    repository: MusicRepository,
    controller: MediaController?,
    isDarkTheme: Boolean,
    syncManager: SyncManager,
    onThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val sp = remember { context.getSharedPreferences("Settings", Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(sp.getString("language", "ESPANOL_LATINO") ?: "ESPANOL_LATINO") }
    var syncId by remember { mutableStateOf(sp.getString("sync_id", "") ?: "") }
    var backgroundColor by remember { mutableLongStateOf(sp.getLong("bg_color", 0xFFFFFFFF.toLong())) }
    var autoDownloadPrivate by remember { mutableStateOf(sp.getBoolean("auto_dl_private", false)) }
    var autoDownloadPublic by remember { mutableStateOf(sp.getBoolean("auto_dl_public", false)) }
    
    val strings = remember(language) { getTranslations(language) }
    var currentScreen by remember { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    var isMaintenanceRunning by remember { mutableStateOf(false) }
    var maintenanceSummary by remember { mutableStateOf<com.danielsalas.auto_music.data.MaintenanceSummary?>(null) }

    BackHandler(enabled = drawerState.isOpen || selectedPlaylist != null || currentScreen != 0 || isPlayerExpanded) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            isPlayerExpanded -> isPlayerExpanded = false
            selectedPlaylist != null -> selectedPlaylist = null
            currentScreen != 0 -> {
                currentScreen = 0
                searchQuery = ""
            }
        }
    }

    LaunchedEffect(syncId) { if (syncId.isNotBlank()) syncManager.startSync(syncId) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(label = { Text(strings.search) }, selected = currentScreen == 0, onClick = { scope.launch { drawerState.close() }; currentScreen = 0; selectedPlaylist = null }, icon = { Icon(Icons.Default.Search, null) })
                NavigationDrawerItem(label = { Text(strings.playlists) }, selected = currentScreen == 1, onClick = { scope.launch { drawerState.close() }; currentScreen = 1; selectedPlaylist = null }, icon = { Icon(Icons.AutoMirrored.Filled.List, null) })
                NavigationDrawerItem(label = { Text(strings.equalizerTitle) }, selected = currentScreen == 5, onClick = { scope.launch { drawerState.close() }; currentScreen = 5; selectedPlaylist = null }, icon = { Icon(Icons.Default.GraphicEq, null) })
                NavigationDrawerItem(label = { Text(strings.language) }, selected = currentScreen == 6, onClick = { scope.launch { drawerState.close() }; currentScreen = 6; selectedPlaylist = null }, icon = { Icon(Icons.Default.Language, null) })
                NavigationDrawerItem(label = { Text(strings.configTitle) }, selected = currentScreen == 2, onClick = { scope.launch { drawerState.close() }; currentScreen = 2; selectedPlaylist = null }, icon = { Icon(Icons.Default.Settings, null) })
                NavigationDrawerItem(label = { Text(strings.manualTitle) }, selected = currentScreen == 3, onClick = { scope.launch { drawerState.close() }; currentScreen = 3; selectedPlaylist = null }, icon = { Icon(Icons.Default.Help, null) })
                
                NavigationDrawerItem(
                    label = { Text(strings.maintenanceTitle) }, 
                    selected = false, 
                    onClick = { 
                        scope.launch { 
                            drawerState.close()
                            isMaintenanceRunning = true
                            maintenanceSummary = repository.performLibraryMaintenance()
                            isMaintenanceRunning = false
                        } 
                    }, 
                    icon = { if (isMaintenanceRunning) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Build, null) }
                )
                
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                NavigationDrawerItem(label = { Text(strings.donationTitle) }, selected = currentScreen == 4, onClick = { scope.launch { drawerState.close() }; currentScreen = 4; selectedPlaylist = null }, icon = { Icon(Icons.Default.Favorite, null) })
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("Auto Music") }, navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") } })
            },
            bottomBar = {
                Column(modifier = if (selectedPlaylist != null) Modifier.navigationBarsPadding() else Modifier) {
                    controller?.let { c ->
                        MiniPlayer(
                            controller = c, 
                            isExpanded = isPlayerExpanded,
                            onToggleExpand = { isPlayerExpanded = !isPlayerExpanded },
                            onAlbumClick = { q ->
                                isPlayerExpanded = false
                                selectedPlaylist = null
                                searchQuery = q
                                currentScreen = 0
                            }
                        ) 
                    }
                    if (selectedPlaylist == null && currentScreen < 2) {
                        NavigationBar {
                            NavigationBarItem(selected = currentScreen == 0, onClick = { currentScreen = 0; searchQuery = "" }, icon = { Icon(Icons.Default.Search, null) }, label = { Text(strings.search) })
                            NavigationBarItem(selected = currentScreen == 1, onClick = { currentScreen = 1 }, icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text(strings.playlists) })
                        }
                    }
                }
            }
        ) { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding).fillMaxSize(), color = if (isDarkTheme) MaterialTheme.colorScheme.background else Color(backgroundColor.toInt())) {
                if (selectedPlaylist != null) {
                    PlaylistSongsScreen(viewModel, strings, selectedPlaylist!!, { selectedPlaylist = null }, { song ->
                        playSong(song, controller, selectedPlaylist?.id)
                        if (!song.isDownloaded) scope.launch { viewModel.addSongToPlaylist(song, selectedPlaylist!!) }
                    })
                } else {
                    when (currentScreen) {
                        0 -> SearchScreen(viewModel, strings, searchQuery, { playSong(it, controller, null) })
                        1 -> PlaylistsScreen(viewModel, strings, { selectedPlaylist = it }, {
                            if (syncId.isNotBlank()) {
                                Toast.makeText(context, strings.syncing, Toast.LENGTH_SHORT).show()
                                syncManager.uploadLocalData { success, err ->
                                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, if (success) strings.syncSuccess else "Error: ${err ?: strings.syncError}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else Toast.makeText(context, strings.setupId, Toast.LENGTH_SHORT).show()
                        }, onMaintenance = {
                            scope.launch {
                                isMaintenanceRunning = true
                                maintenanceSummary = repository.performLibraryMaintenance()
                                isMaintenanceRunning = false
                            }
                        })
                        2 -> ConfigScreen(strings, Color(backgroundColor.toInt()), isDarkTheme, syncId, autoDownloadPrivate, autoDownloadPublic,
                            { syncId = it; sp.edit().putString("sync_id", it).apply() },
                            { onThemeChange(it); sp.edit().putBoolean("dark_mode", it).apply() },
                            { autoDownloadPrivate = it; sp.edit().putBoolean("auto_dl_private", it).apply() },
                            { autoDownloadPublic = it; sp.edit().putBoolean("auto_dl_public", it).apply() },
                            { backgroundColor = it.value.toLong(); sp.edit().putLong("bg_color", it.value.toLong()).apply() }
                        )
                        3 -> ManualScreen(strings, language)
                        4 -> DonationScreen(strings)
                        5 -> EqualizerScreen(strings, controller)
                        6 -> LanguageScreen(strings, language) { 
                            language = it
                            sp.edit().putString("language", it).apply()
                            currentScreen = 0
                        }
                    }
                }
            }
            
            if (maintenanceSummary != null) {
                AlertDialog(
                    onDismissRequest = { maintenanceSummary = null },
                    title = { Text(strings.maintenanceSummaryTitle) },
                    text = {
                        Column {
                            Text("${strings.filesCleanedLabel}: ${maintenanceSummary!!.filesCleaned}")
                            Text("${strings.songsRequeuedLabel}: ${maintenanceSummary!!.songsRequeued}")
                            Text("${strings.songsRestoredLabel}: ${maintenanceSummary!!.songsRestored}")
                            if (maintenanceSummary!!.errors.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(strings.maintenanceErrorsTitle, color = MaterialTheme.colorScheme.error)
                                maintenanceSummary!!.errors.forEach { Text("- $it", fontSize = 12.sp) }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { maintenanceSummary = null }) { Text(strings.close) } }
                )
            }
        }
    }
}

@UnstableApi
fun playSong(song: Song, controller: MediaController?, playlistId: Long?) {
    controller?.let { c ->
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title).setArtist(song.artist).setArtworkUri(android.net.Uri.parse(song.thumbnailUrl))
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsBrowsable(false).setIsPlayable(true)
            .build()
        val mediaId = if (playlistId != null) "PL$playlistId|${song.id}" else song.id
        // Pattern similar to kreate_imp for identifying YouTube streams
        val item = MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata)
            .setUri("youtube://${song.id}").setMimeType("audio/mpeg").build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(controller: MediaController, isExpanded: Boolean, onToggleExpand: () -> Unit, onAlbumClick: (String) -> Unit) {
    var metadata by remember { mutableStateOf(controller.mediaMetadata) }
    var isPlaying by remember { mutableStateOf(controller.isPlaying) }
    var position by remember { mutableLongStateOf(controller.currentPosition) }
    var duration by remember { mutableLongStateOf(controller.duration) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(m: MediaMetadata) { metadata = m }
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(s: Int) { duration = controller.duration }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = controller.currentPosition
            delay(1000)
        }
    }

    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    fun formatTime(ms: Long): String {
        val s = ms / 1000; val m = s / 60; val rs = s % 60
        return "%d:%02d".format(m, rs)
    }

    if (isExpanded) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleExpand, modifier = Modifier.align(Alignment.Start)) { Icon(Icons.Default.KeyboardArrowDown, null) }
                Spacer(Modifier.height(24.dp))
                AsyncImage(model = metadata.artworkUri, contentDescription = null, modifier = Modifier.size(320.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(32.dp))
                Text(metadata.title?.toString() ?: "No Title", style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(metadata.artist?.toString() ?: "Unknown Artist", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { metadata.artist?.let { onAlbumClick(it.toString()) } })
                Spacer(Modifier.height(32.dp))
                Slider(value = progress, onValueChange = { controller.seekTo((it * duration).toLong()) }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
                    Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(32.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { controller.seekToPrevious() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(48.dp)) }
                    FloatingActionButton(onClick = { if (isPlaying) controller.pause() else controller.play() }, containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(80.dp)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(48.dp)) }
                    IconButton(onClick = { controller.seekToNext() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(48.dp)) }
                }
            }
        }
    } else {
        Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().height(72.dp).clickable { onToggleExpand() }, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp).fillMaxHeight()) {
                    AsyncImage(model = metadata.artworkUri, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(metadata.title?.toString() ?: "No Title", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(metadata.artist?.toString() ?: "Unknown Artist", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    IconButton(onClick = { if (isPlaying) controller.pause() else controller.play() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                }
            }
        }
    }
}

@Composable
fun ManualScreen(strings: AppTranslations, language: String) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ManualHeader(strings.manWelcomeTitle)
        ManualSection(strings.manWelcomeDesc, "")
        ManualHeader(strings.search)
        ManualSection(strings.manSearchDesc, "")
        ManualHeader(strings.playlists)
        ManualSection(strings.manPlaylistsDesc, "")
        ManualHeader(strings.manSongsTitle)
        ManualSection(strings.manSongsDesc, "")
        IconExplanation(Icons.Default.DragHandle, strings.manIconDrag)
        IconExplanation(Icons.Default.Shuffle, strings.manIconShuffle)
        IconExplanation(Icons.Default.VolumeUp, strings.manIconNorm)
        IconExplanation(Icons.Default.Difference, strings.manIconDup)
        IconExplanation(Icons.Default.SortByAlpha, strings.manIconAZ)
        IconExplanation(Icons.Default.Lock, strings.manIconFix)
        IconExplanation(Icons.AutoMirrored.Filled.List, strings.manIconManual)
        IconExplanation(Icons.Default.Search, strings.manIconSearch)
        ManualHeader(strings.maintenanceTitle)
        ManualSection(strings.manMaintenanceDesc, "")
        IconExplanation(Icons.Default.PlayArrow, strings.manIconPlay)
        IconExplanation(Icons.Default.Add, strings.manIconAdd)
        ManualHeader(strings.configTitle)
        ManualSection(strings.manConfigDark, "")
        ManualSection(strings.manConfigAuto, "")
        ManualSection(strings.manConfigSync, "")
        ManualSection(strings.manConfigColor, "")
        ManualHeader(strings.equalizerTitle)
        ManualSection(strings.manEqDesc, "")
    }
}

@OptIn(UnstableApi::class)
@Composable
fun EqualizerScreen(strings: AppTranslations, controller: MediaController?) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("EqualizerPrefs", Context.MODE_PRIVATE) }
    var isEnabled by remember { mutableStateOf(sp.getBoolean("eq_enabled", false)) }
    val bands = remember { mutableStateListOf(*IntArray(10) { i -> sp.getInt("band_$i", 0) }.toTypedArray()) }
    var reverbPreset by remember { mutableIntStateOf(sp.getInt("reverb_preset", 0)) }
    var currentPresetName by remember { mutableStateOf(sp.getString("last_preset", "CUSTOM")) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.equalizerTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Switch(checked = isEnabled, onCheckedChange = { 
                isEnabled = it
                sp.edit().putBoolean("eq_enabled", it).apply()
                updateServiceEq(controller, isEnabled, bands.toIntArray(), reverbPreset)
            })
        }
        
        Spacer(Modifier.height(16.dp))
        Text(strings.presets, style = MaterialTheme.typography.titleMedium)
        
        val pList = listOf("ROCK", "POP", "BASS", "DANCE", "CLASSIC", "JAZZ", "METAL", "VOCAL", "FLAT", "CUSTOM")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pList.chunked(5).forEach { rowPresets ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPresets.forEach { name ->
                        val isSelected = currentPresetName == name
                        Button(
                            onClick = { 
                                currentPresetName = name
                                applyPreset(name, sp)
                                sp.edit().putString("last_preset", name).apply()
                                isEnabled = true
                                (0..9).forEach { bands[it] = sp.getInt("band_$it", 0) }
                                updateServiceEq(controller, true, bands.toIntArray(), reverbPreset)
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text(name, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(strings.graphicEq, style = MaterialTheme.typography.titleMedium)
        
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val frequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
            bands.forEachIndexed { index, level ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text("${level}dB", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(isEnabled) {
                                if (!isEnabled) return@pointerInput
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val newLevel = ((1f - (change.position.y / size.height)) * 15f).toInt().coerceIn(0, 15)
                                    if (bands[index] != newLevel) {
                                        bands[index] = newLevel
                                        currentPresetName = "CUSTOM"
                                        sp.edit().putInt("band_$index", newLevel).putString("last_preset", "CUSTOM").apply()
                                        updateServiceEq(controller, true, bands.toIntArray(), reverbPreset)
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(level / 15f)
                                .align(Alignment.BottomCenter).background(if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray)
                        )
                    }
                    Text(frequencies[index], fontSize = 9.sp)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text(strings.reverb, style = MaterialTheme.typography.titleMedium)
        Column {
            val reverbOptions = listOf(strings.none, strings.carSpace, strings.mediumRoom, strings.largeHall)
            reverbOptions.forEachIndexed { index, name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.fillMaxWidth().clickable { 
                        reverbPreset = index
                        sp.edit().putInt("reverb_preset", index).apply()
                        updateServiceEq(controller, isEnabled, bands.toIntArray(), reverbPreset)
                    }.padding(vertical = 4.dp)
                ) {
                    RadioButton(selected = reverbPreset == index, onClick = null, modifier = Modifier.size(32.dp))
                    Text(name, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@UnstableApi
fun updateServiceEq(controller: MediaController?, enabled: Boolean, levels: IntArray, reverb: Int) {
    val bundle = Bundle().apply {
        putBoolean("enabled", enabled)
        putIntArray("levels", levels)
        putInt("reverb", reverb)
    }
    controller?.sendCustomCommand(SessionCommand("ACTION_UPDATE_EQ", Bundle.EMPTY), bundle)
}

fun applyPreset(name: String, sp: SharedPreferences) {
    val levels = when(name) {
        "ROCK" -> intArrayOf(4, 3, 2, 0, 0, 0, 0, 1, 2, 3)
        "POP" -> intArrayOf(0, 0, 1, 2, 2, 2, 1, 0, 0, 0)
        "BASS" -> intArrayOf(8, 7, 5, 2, 0, 0, 0, 0, 0, 0)
        "DANCE" -> intArrayOf(5, 4, 2, 0, 1, 2, 4, 4, 3, 0)
        "CLASSIC" -> intArrayOf(4, 3, 2, 1, 0, 0, 0, 2, 3, 4)
        "JAZZ" -> intArrayOf(3, 2, 1, 2, 0, 0, 0, 1, 2, 3)
        "METAL" -> intArrayOf(4, 3, 2, 1, 0, 0, 2, 3, 4, 4)
        "VOCAL" -> intArrayOf(0, 0, 0, 1, 3, 3, 3, 1, 0, 0)
        "FLAT" -> intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        else -> return
    }
    val editor = sp.edit()
    levels.forEachIndexed { i, v -> editor.putInt("band_$i", v) }
    editor.apply()
}

@Composable
fun ManualHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun ManualSection(text: String, note: String) {
    Column {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
        if (note.isNotEmpty()) Text(text = note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun IconExplanation(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DonationScreen(strings: AppTranslations) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Favorite, null, modifier = Modifier.size(64.dp), tint = Color.Red)
        Spacer(Modifier.height(16.dp))
        Text(strings.donationTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(strings.donationText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun LanguageScreen(strings: AppTranslations, current: String, onSelect: (String) -> Unit) {
    val langs = listOf("ESPANOL_LATINO", "ENGLISH", "EUSKARA", "GALEGO", "CATALA", "FRANCAIS", "DEUTSCH", "ITALIANO", "KOREAN", "JAPANESE")
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(strings.language, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        langs.forEach { lang ->
            val isSelected = current == lang
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(lang) }.padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(text = lang.replace("_", " "), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun ConfigScreen(strings: AppTranslations, bgColor: Color, isDark: Boolean, sId: String, dlPriv: Boolean, dlPub: Boolean, onSId: (String) -> Unit, onDark: (Boolean) -> Unit, onDlPriv: (Boolean) -> Unit, onDlPub: (Boolean) -> Unit, onColor: (Color) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(strings.configTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.darkMode, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = isDark, onCheckedChange = onDark)
        }
        Text(strings.darkThemeNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        
        if (!isDark) {
            Spacer(Modifier.height(24.dp))
            Text(strings.selectColor, style = MaterialTheme.typography.titleMedium)
            val colors = listOf(Color.White, Color(0xFFF0F0F0), Color(0xFFE0F7FA), Color(0xFFF1F8E9), Color(0xFFFFF3E0), Color(0xFFFCE4EC))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                colors.forEach { color ->
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(color).clickable { onColor(color) }.let { if (bgColor == color) it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else it }) {
                        if (bgColor == color) Icon(Icons.Default.Check, null, modifier = Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text(strings.autoDownloadTitle, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.autoDownloadPrivate, modifier = Modifier.weight(1f)); Switch(checked = dlPriv, onCheckedChange = onDlPriv) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.autoDownloadPublic, modifier = Modifier.weight(1f)); Switch(checked = dlPub, onCheckedChange = onDlPub) }
        
        Spacer(Modifier.height(32.dp))
        Text(strings.syncTitle, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = sId, onValueChange = onSId, label = { Text(strings.syncIdLabel) }, modifier = Modifier.fillMaxWidth())
        Text(strings.syncHelp, style = MaterialTheme.typography.labelSmall)
        Button(onClick = { onSId(java.util.UUID.randomUUID().toString().substring(0, 8)) }, modifier = Modifier.padding(top = 12.dp)) { Text(strings.generate) }
    }
}

data class AppTranslations(
    val search: String, val playlists: String, val language: String, val configTitle: String, val donationTitle: String,
    val donationText: String, val selectColor: String, val close: String, val brightness: String, val preview: String,
    val darkMode: String, val darkThemeNote: String, val syncTitle: String, val syncIdLabel: String, val syncHelp: String,
    val generate: String, val deletePlaylist: String, val syncSuccess: String, val syncError: String, val autoDownloadTitle: String,
    val autoDownloadPrivate: String, val autoDownloadPublic: String, val isPublic: String, val isPrivate: String,
    val createPublic: String, val createPrivate: String, val selectedItems: String, val searchPlaceholder: String,
    val noResults: String, val addToPlaylist: String, val cancel: String, val downloaded: String, val downloading: String,
    val online: String, val syncing: String, val setupId: String, val newPlaylist: String, val nameField: String,
    val create: String, val maintenanceTitle: String, val maintenanceRunning: String, val maintenanceSummaryTitle: String,
    val filesCleanedLabel: String, val songsRequeuedLabel: String, val songsRestoredLabel: String, val maintenanceErrorsTitle: String,
    val resumePlayback: String, val resumePlaylist: String, val sortAZ: String, val sortZA: String, val searchInList: String,
    val fixOrder: String, val manualOrder: String, val findDuplicates: String, val songsCountLabel: String,
    val moveToPosition: String, val manualTitle: String, val manWelcomeTitle: String, val manWelcomeDesc: String,
    val manSearchDesc: String, val manPlaylistsDesc: String, val manSongsTitle: String, val manSongsDesc: String,
    val manIconDrag: String, val manIconShuffle: String, val manIconDup: String, val manIconAZ: String,
    val manIconFix: String, val manIconManual: String, val manIconSearch: String, val manMaintenanceDesc: String,
    val manIconPlay: String, val manIconAdd: String, val manConfigDark: String, val manConfigAuto: String,
    val manConfigSync: String, val manConfigColor: String, val manIconNorm: String, val volumeNormalization: String,
    val equalizerTitle: String, val presets: String, val reverb: String, val manEqDesc: String,
    val graphicEq: String, val none: String, val carSpace: String, val mediumRoom: String, val largeHall: String,
    val savePreset: String
)

fun getTranslations(lang: String): AppTranslations {
    val english = AppTranslations(
        search = "Search", playlists = "Playlists", language = "Language", configTitle = "Configuration", donationTitle = "Donation",
        donationText = "If you liked my app you can donate the amount you consider.",
        selectColor = "Select background color", close = "Close", brightness = "Brightness", preview = "Preview",
        darkMode = "Dark Mode", darkThemeNote = "Custom color is disabled in dark mode",
        syncTitle = "Cloud Sync", syncIdLabel = "Sync ID", syncHelp = "Use the same ID on all your devices to share your lists.",
        generate = "Generate", deletePlaylist = "Delete list", syncSuccess = "Sync successful",
        syncError = "Error during sync", autoDownloadTitle = "Auto Downloads",
        autoDownloadPrivate = "Private Lists", autoDownloadPublic = "Public Lists", isPublic = "Public",
        isPrivate = "Private", createPublic = "Create Public", createPrivate = "Create Private",
        selectedItems = "selected", searchPlaceholder = "Search by title, artist or lyrics",
        noResults = "No results found for", addToPlaylist = "Add to playlist",
        cancel = "Cancel", downloaded = "✓ Downloaded", downloading = "⏳ Downloading...", online = "🌐 Online",
        syncing = "Syncing...", setupId = "Setup ID", newPlaylist = "New Playlist",
        nameField = "Name", create = "Create", maintenanceTitle = "Library Maintenance",
        maintenanceRunning = "Cleaning and verifying files...", maintenanceSummaryTitle = "Maintenance Summary",
        filesCleanedLabel = "Files cleaned", songsRequeuedLabel = "Songs requeued",
        songsRestoredLabel = "Songs restored", maintenanceErrorsTitle = "Unresolved errors",
        resumePlayback = "▶ Resume playback", resumePlaylist = "▶ Resume playlist",
        sortAZ = "Sort A-Z", sortZA = "Sort Z-A", searchInList = "Search in list",
        fixOrder = "Fix this order", manualOrder = "Manual order", findDuplicates = "Find duplicates",
        songsCountLabel = "songs", moveToPosition = "Move to position", manualTitle = "User Manual",
        manWelcomeTitle = "Welcome", manWelcomeDesc = "Auto Music is a hybrid player designed for the car.",
        manSearchDesc = "Search for songs by title or artist.",
        manPlaylistsDesc = "Manage your collections. Public lists are shared, Private lists only among your devices.",
        manSongsTitle = "Songs Screen", manSongsDesc = "Professional control of your music within a list.",
        manIconDrag = "Drag: Long press and move to change order.",
        manIconShuffle = "Shuffle: The system remembers your preference per list.",
        manIconDup = "Duplicates: Filter to show only repeated songs.",
        manIconAZ = "A-Z: Temporary visual sort to help searching.",
        manIconFix = "Fix: Permanently saves the current visual order as your official order.",
        manIconManual = "Manual: Returns to your favorite personal order.",
        manIconSearch = "Search: Jump directly to a song without stopping the music.",
        manMaintenanceDesc = "Maintenance: Cleans orphaned files and ensures offline readiness.",
        manIconPlay = "Play: Start online or local playback instantly.",
        manIconAdd = "Add (+): Save the song into one of your lists.",
        manConfigDark = "Dark Mode: Switch between light and dark theme.",
        manConfigAuto = "Auto-Download: Automatically download songs from your lists.",
        manConfigSync = "Cloud Sync: Enter your ID to have your lists on all your devices.",
        manConfigColor = "Background Color: Customize the look in light mode.",
        manIconNorm = "Normalization: Equalize volume for all songs in a list for safe driving.",
        volumeNormalization = "Normalise Volume", equalizerTitle = "Equalizer", presets = "Presets",
        reverb = "Reverberation", manEqDesc = "Professional 10-band EQ with musical presets and 3D car space simulation.",
        graphicEq = "Graphic Equalizer", none = "None", carSpace = "Car Space", mediumRoom = "Medium Room",
        largeHall = "Large Hall", savePreset = "Save Preset"
    )
    
    return when(lang) {
        "ESPANOL_LATINO" -> english.copy(
            search = "Buscar", playlists = "Listas", language = "Idioma", configTitle = "Configuración", donationTitle = "Donación",
            donationText = "Si te gustó mi aplicación puedes donar la cantidad que consideres.",
            selectColor = "Selecciona el color de fondo", close = "Cerrar", brightness = "Brillo", preview = "Vista previa",
            darkMode = "Modo oscuro", darkThemeNote = "El color personalizado se desactiva en modo oscuro",
            syncTitle = "Sincronización en la Nube", syncIdLabel = "ID de Sincronización",
            syncHelp = "Usa el mismo ID en todos tus dispositivos para compartir tus listas.",
            generate = "Generar", deletePlaylist = "Eliminar lista", syncSuccess = "Sincronización correcta",
            syncError = "Error en la sincronización", autoDownloadTitle = "Descargas Automáticas",
            autoDownloadPrivate = "Listas Privadas", autoDownloadPublic = "Listas Públicas", isPublic = "Pública",
            isPrivate = "Privada", createPublic = "Crear Pública", createPrivate = "Crear Private",
            selectedItems = "seleccionadas", searchPlaceholder = "Busca por título, artista o letra",
            noResults = "No se han encontrado resultados para", addToPlaylist = "Añadir a la lista",
            cancel = "Cancelar", downloaded = "✓ Descargada", downloading = "⏳ Descargando...", online = "🌐 Online",
            syncing = "Sincronizando...", setupId = "Configura el ID", newPlaylist = "Nueva lista de reproducción",
            nameField = "Nombre", create = "Crear", maintenanceTitle = "Mantenimiento de la librería",
            maintenanceRunning = "Limpiando y verificando archivos...", maintenanceSummaryTitle = "Resumen del Mantenimiento",
            filesCleanedLabel = "Archivos limpiados", songsRequeuedLabel = "Canciones reencoladas",
            songsRestoredLabel = "Canciones restauradas", maintenanceErrorsTitle = "Errores sin resolver",
            resumePlayback = "▶ Continuar última reproducción", resumePlaylist = "▶ Continuar esta lista",
            sortAZ = "Ordenar A-Z", sortZA = "Ordenar Z-A", searchInList = "Buscar en la lista",
            fixOrder = "Fijar este orden", manualOrder = "Orden personalizado", findDuplicates = "Buscar duplicados",
            songsCountLabel = "canciones", moveToPosition = "Mover a posición", manualTitle = "Manual de Instrucciones",
            manWelcomeTitle = "Bienvenido", manWelcomeDesc = "Auto Music es un reproductor híbrido diseñado para el coche.",
            manSearchDesc = "Busca canciones por título o artista.",
            manPlaylistsDesc = "Gestiona tus colecciones. Las listas Públicas se sincronizan con cualquier ID; las Privadas solo entre usuarios con el mismo ID.",
            manSongsTitle = "Pantalla de Canciones", manSongsDesc = "Control profesional de tu música dentro de una lista.",
            manIconDrag = "Arrastrar: Mantén pulsado y mueve para cambiar el orden. La lista se desplaza sola en los bordes.",
            manIconShuffle = "Aleatorio: El sistema recordará tu preferencia para cada lista, incluso en Android Auto.",
            manIconDup = "Duplicados: Filtra la lista para mostrar solo las canciones repetidas por título y artista.",
            manIconAZ = "A-Z: Orden visual temporal para ayudarte a buscar canciones rápido.",
            manIconFix = "Fijar: Guarda permanentemente el orden visual actual como tu orden oficial.",
            manIconManual = "Manual: Vuelve a tu orden favorito después de haber ordenado alfabéticamente.",
            manIconSearch = "Buscador: Escribe para saltar directamente a una canción sin detener la música.",
            manMaintenanceDesc = "Mantenimiento: Limpia archivos huérfanos y asegura que tus canciones estén listas para usar offline.",
            manIconPlay = "Reproducir: Pulsa el icono de play para iniciar la reproducción online o local al instante.",
            manIconAdd = "Añadir (+): Pulsa este icono para guardar la canción en una de tus listas.",
            manConfigDark = "Modo Oscuro: Cambia entre el tema claro y el oscuro para mayor comodidad.",
            manConfigAuto = "Auto-Descarga: Permite que la app baje automáticamente las canciones de tus listas.",
            manConfigSync = "Sincro en Nube: Introduce tu ID para tener tus listas en todos tus dispositivos.",
            manConfigColor = "Color de Fondo: Personaliza el aspecto de la aplicación cuando no usas el modo oscuro.",
            manIconNorm = "Normalización: Botón para igualar el volumen de todas las canciones de una lista para una conducción segura.",
            volumeNormalization = "Igualar Volumen", equalizerTitle = "Ecualizador", presets = "Ajustes Pregrabados",
            reverb = "Reverberación", manEqDesc = "EQ profesional de 10 bandas con perfiles musicales y simulación de espacios 3D para el coche.",
            graphicEq = "Ecualizador Gráfico", none = "Ninguno", carSpace = "Espacio Coche", mediumRoom = "Habitación Pequeña",
            largeHall = "Gran Sala", savePreset = "Guardar Ajust"
        )
        "EUSKARA" -> english.copy(
            search = "Bilatu", playlists = "Zerrendak", language = "Hizkuntza", configTitle = "Konfigurazioa",
            donationTitle = "Dohaintza", donationText = "Nire aplikazioa gustatu bazaizu, nahi duzun zenbatekoa eman dezakezu.",
            selectColor = "Hautatu atzeko planoko kolorea", close = "Itxi", brightness = "Distira", preview = "Aurreikuspena",
            darkMode = "Modu iluna", darkThemeNote = "Kolore pertsonalizatua desgaituta dago modu ilunean",
            syncTitle = "Hodeiko Sinkronizazioa", syncIdLabel = "Sinkronizazio IDa", syncHelp = "Erabili ID bera gailu guztietan.",
            generate = "Sortu", deletePlaylist = "Zerrenda ezabatu", syncSuccess = "Sinkronizazio arrakastatsua",
            syncError = "Errorea sinkronizatzean", autoDownloadTitle = "Deskarga Automatikoak",
            autoDownloadPrivate = "Zerrenda Pribatuak", autoDownloadPublic = "Zerrenda Publikoak", isPublic = "Publikoa",
            isPrivate = "Pribatua", createPublic = "Publikoa Sortu", createPrivate = "Pribatua Sortu",
            selectedItems = "hautatuta", searchPlaceholder = "Bilatu abestiaren izenburua, artista...",
            noResults = "Ez da emaitzarik aurkitu honetarako:", addToPlaylist = "Gehitu erreprodukzio-zerrendara",
            cancel = "Utzi", downloaded = "✓ Deskargatuta", downloading = "⏳ Deskargatzen...", online = "🌐 Online",
            syncing = "Sinkronizatzen...", setupId = "Konfiguratu IDa", newPlaylist = "Erreprodukzio-zerrenda berria",
            nameField = "Izena", create = "Sortu", maintenanceTitle = "Liburutegiaren mantentzea",
            maintenanceRunning = "Fitxategiak garbitzen eta egiaztatzen...", maintenanceSummaryTitle = "Mantentze-lanen laburpena",
            filesCleanedLabel = "Garbitutako fitxategiak", songsRequeuedLabel = "Berriro ilaran jarritako abestiak",
            songsRestoredLabel = "Leheneratutako abestiak", maintenanceErrorsTitle = "Ebatzi gabeko erroreak",
            resumePlayback = "▶ Erreprodukzioa jarraitu", resumePlaylist = "▶ Erreprodukzio-zerrenda jarraitu",
            sortAZ = "Ordenatu A-Z", sortZA = "Ordenatu Z-A", searchInList = "Zerrendan bilatu",
            fixOrder = "Finkatu ordena hau", manualOrder = "Ordena pertsonalizatua", findDuplicates = "Bilatu bikoiztuak",
            songsCountLabel = "abestiak", moveToPosition = "Mugitu posiziora", manualTitle = "Argibide Eskuliburua",
            manWelcomeTitle = "Ongi etorri", manWelcomeDesc = "Auto Music autorako diseinatutako erreproduzitzaile hibridoa da.",
            manSearchDesc = "Bilatu abestiak izenburuaren edo artistaren arabera.",
            manPlaylistsDesc = "Kudeatu bildumak. Zerrenda publikoak partekatu egiten dira, pribatuak gailu berberen artean soilik.",
            manSongsTitle = "Abestien Pantaila", manSongsDesc = "Zure abestien kudeaketa profesionala zerrenda baten barruan.",
            manIconDrag = "Arrastatu: Eduki sakatuta ordena aldatzeko. Zerrenda bera mugitzen da.",
            manIconShuffle = "Ausazkoa: Sistemak zerrenda bakoitzeko hobespena gogoratzen du.",
            manIconDup = "Bikoiztuak: Errepikatutako abestiak soilik erakusteko iragazkia.",
            manIconAZ = "A-Z: Bilatzeko aldi baterako ordenazio bisuala.",
            manIconFix = "Finkatu: Uneko ordena bisuala ordena ofizial gisa gordetzen du.",
            manIconManual = "Eskuzkoa: Zure gogoko ordena pertsonalizatura itzuli.",
            manIconSearch = "Bilatu: Abesti batera zuzenean joateko audioa gelditu gabe.",
            manMaintenanceDesc = "Mantentzea: Fitxategiak garbitu eta offline prestatu.",
            manIconPlay = "Erreproduzitu: Online edo tokiko erreprodukzioa berehala hasi.",
            manIconAdd = "Gehitu (+): Erabili ikur hau abestia zerrenda batean gordetzeko.",
            manConfigDark = "Modu Iluna: Gaia argia eta ilunaren artean aldatu.",
            manConfigAuto = "Auto-Deskarga: Zerrendetako abestiak automatikoki deskargatu.",
            manConfigSync = "Sinkro: Zure IDa sartu gailuen artean sinkronizatzeko.",
            manConfigColor = "Atzeko kolorea: App-aren itxura pertsonalizatu modu argian.",
            manIconNorm = "Normalizazioa: Zerrendako abesti guztien bolumena berdintzeko botoia, gidatze segururako.",
            volumeNormalization = "Bolumena Berdindu", equalizerTitle = "Ekualizadorea", presets = "Presets",
            reverb = "Erreberberazioa", manEqDesc = "10 bandako EQ profesionala presets musikaltiekin eta autorako 3D espazioekin.",
            graphicEq = "Ekualizadore Grafikoa", none = "Bat ere ez", carSpace = "Auto Gunea", mediumRoom = "Gela Ertaina",
            largeHall = "Areto Handia", savePreset = "Gorde Preset"
        )
        "GALEGO" -> english.copy(
            search = "Buscar", playlists = "Listas", language = "Idioma", configTitle = "Configuración",
            donationTitle = "Doazón", donationText = "Se che gustou a miña aplicación podes doar a cantidade que consideres.",
            selectColor = "Selecciona a cor de fondo", close = "Pechar", brightness = "Brillo", preview = "Vista previa",
            darkMode = "Modo escuro", darkThemeNote = "A cor personalizada desactívase no modo escuro",
            syncTitle = "Sincronización na Nube", syncIdLabel = "ID de Sincronización", syncHelp = "Usa o mesmo ID en todos os teus dispositivos.",
            generate = "Xerar", deletePlaylist = "Eliminar lista", syncSuccess = "Sincronización correcta",
            syncError = "Erro na sincronización", autoDownloadTitle = "Descargas Automáticas",
            autoDownloadPrivate = "Listas Privadas", autoDownloadPublic = "Listas Públicas", isPublic = "Pública",
            isPrivate = "Privada", createPublic = "Crear Pública", createPrivate = "Crear Privada",
            selectedItems = "seleccionadas", searchPlaceholder = "Busca por título, artista ou letra",
            noResults = "Non se atoparon resultados para:", addToPlaylist = "Engadir á lista",
            cancel = "Cancelar", downloaded = "✓ Descargada", downloading = "⏳ Descargando...", online = "🌐 Online",
            syncing = "Sincronizando...", setupId = "Configura o ID", newPlaylist = "Nova lista de reprodución",
            nameField = "Nome", create = "Crear", maintenanceTitle = "Mantemento da librería",
            maintenanceRunning = "Limpando e verificando arquivos...", maintenanceSummaryTitle = "Resumo do Mantemento",
            filesCleanedLabel = "Arquivos limpados", songsRequeuedLabel = "Cancións reencoladas",
            songsRestoredLabel = "Cancións restauradas", maintenanceErrorsTitle = "Erros sen resolver",
            resumePlayback = "▶ Continuar última reprodución", resumePlaylist = "▶ Continuar esta lista",
            sortAZ = "Ordenar A-Z", sortZA = "Ordenar Z-A", searchInList = "Buscar na lista",
            fixOrder = "Fixar esta orde", manualOrder = "Orde personalizada", findDuplicates = "Buscar duplicados",
            songsCountLabel = "cancións", moveToPosition = "Mover a posición", manualTitle = "Manual de Instrucións",
            manWelcomeTitle = "Benvido", manWelcomeDesc = "Auto Music é un reprodutor híbrido deseñado para o coche.",
            manSearchDesc = "Busca cancións por título ou artista.",
            manPlaylistsDesc = "Xestiona as túas coleccións. As listas públicas compártense, as privadas só entre os teus dispositivos.",
            manSongsTitle = "Pantalla de Cancións", manSongsDesc = "Control profesional da túa música nunha lista.",
            manIconDrag = "Arrastrar: Mantén premido e move para cambiar a orde.",
            manIconShuffle = "Aleatorio: O sistema lembra a túa preferencia por lista.",
            manIconDup = "Duplicados: Filtra para amosar só cancións repetidas.",
            manIconAZ = "A-Z: Orde visual temporal para axudarte a buscar.",
            manIconFix = "Fixar: Garda a orde visual actual como a oficial.",
            manIconManual = "Manual: Volve á túa orde favorita.",
            manIconSearch = "Buscador: Salta a unha canción sin deter a música.",
            manMaintenanceDesc = "Mantemento: Limpa arquivos e prepara o uso offline.",
            manIconPlay = "Reproducir: Inicia a reprodución online ou local ao instante.",
            manIconAdd = "Engadir (+): Garda a canción nunha das túas listas.",
            manConfigDark = "Modo Escuro: Cambia entre o tema claro e o escuro.",
            manConfigAuto = "Auto-Descarga: Baixa automaticamente as cancións das listas.",
            manConfigSync = "Sincro: Introduce o teu ID para ter as listas en todos os teus dispositivos.",
            manConfigColor = "Cor de fondo: Personaliza o aspecto en modo claro.",
            manIconNorm = "Normalización: Botón para igualar o volume de todas as cancións, para conducir seguro.",
            volumeNormalization = "Igualar Volume", equalizerTitle = "Ecualizador", presets = "Axustes",
            reverb = "Reverberación", manEqDesc = "EQ profesional de 10 bandas con perfiles musicais e simulación de coche 3D.",
            graphicEq = "Ecualizador Gráfico", none = "Ningún", carSpace = "Espazo Coche", mediumRoom = "Sala Mediana",
            largeHall = "Gran Sala", savePreset = "Gardar Axuste"
        )
        "CATALA" -> english.copy(
            search = "Buscar", playlists = "Llistes", language = "Idioma", configTitle = "Configuració",
            donationTitle = "Donació", donationText = "Si t'ha agradat la meva aplicació pots donar la quantitat que consideris.",
            selectColor = "Selecciona el color de fons", close = "Tancar", brightness = "Brillantor", preview = "Vista prèvia",
            darkMode = "Mode fosc", darkThemeNote = "El color personalitzat es desactiva en mode fosc",
            syncTitle = "Sincronització al Núvol", syncIdLabel = "ID de Sincronització", syncHelp = "Fes servir el mateix ID a tots els teus dispositius.",
            generate = "Generar", deletePlaylist = "Eliminar llista", syncSuccess = "Sincronització correcta",
            syncError = "Error en la sincronització", autoDownloadTitle = "Descarregues Automàtiques",
            autoDownloadPrivate = "Llistes Privades", autoDownloadPublic = "Llistes Públiques", isPublic = "Pública",
            isPrivate = "Privada", createPublic = "Crear Pública", createPrivate = "Crear Privada",
            selectedItems = "seleccionades", searchPlaceholder = "Busca per títol, artista...",
            noResults = "No s'han trobat resultats per a:", addToPlaylist = "Afegir a la llista",
            cancel = "Cancel·lar", downloaded = "✓ Descarregada", downloading = "⏳ Descarregant...", online = "🌐 Online",
            syncing = "Sincronitzant...", setupId = "Configura l'ID", newPlaylist = "Nova llista de reproducció",
            nameField = "Nom", create = "Crear", maintenanceTitle = "Manteniment de la llibreria",
            maintenanceRunning = "Netejant i verificant arxius...", maintenanceSummaryTitle = "Resum del Mantenimiento",
            filesCleanedLabel = "Arxius netejats", songsRequeuedLabel = "Cançons reencuades",
            songsRestoredLabel = "Cançons restaurades", maintenanceErrorsTitle = "Errors sense resoldre",
            resumePlayback = "▶ Continuar última reproducció", resumePlaylist = "▶ Continuar aquesta llista",
            sortAZ = "Ordenar A-Z", sortZA = "Ordenar Z-A", searchInList = "Buscar a la llista",
            fixOrder = "Fixar aquest ordre", manualOrder = "Ordre personalitzat", findDuplicates = "Buscar duplicats",
            songsCountLabel = "cançons", moveToPosition = "Moure a posició", manualTitle = "Manual d'Instruccions",
            manWelcomeTitle = "Benvingut", manWelcomeDesc = "Auto Music és un reproductor híbrid dissenyat per al cotxe.",
            manSearchDesc = "Busca cançons per títol o artista.",
            manPlaylistsDesc = "Gestiona les teves col·leccions. Les llistes públiques es comparteixen, les privades només entre els teus dispositius.",
            manSongsTitle = "Pantalla de Cançons", manSongsDesc = "Control professional de la teva música en una llista.",
            manIconDrag = "Arrossegar: Mantén premut i mou per canviar l'ordre.",
            manIconShuffle = "Aleatori: El sistema recorda la teva preferència per llista.",
            manIconDup = "Duplicats: Filtra per mostrar només cançons repetidas.",
            manIconAZ = "A-Z: Ordre visual temporal per ajudar-te a buscar.",
            manIconFix = "Fixar: Guarda l'ordre visual actual com l'oficial.",
            manIconManual = "Manual: Torna al teu ordre favorit.",
            manIconSearch = "Cercador: Salta a una cançó sense aturar la música.",
            manMaintenanceDesc = "Manteniment: Neteja arxius i prepara l'ús offline.",
            manIconPlay = "Reproduir: Inicia la reproducció online o local a l'instant.",
            manIconAdd = "Afegir (+): Guarda la cançó en una de les teves llistes.",
            manConfigDark = "Mode Fosc: Canvia entre el tema clar i el fosc.",
            manConfigAuto = "Auto-Descarrega: Baixa automàticament les cançons de les llistes.",
            manConfigSync = "Sincro: Introdueix el teu ID per tenir les llistes a tots els teus dispositius.",
            manConfigColor = "Color de fons: Personalitza l'aspecte en mode clar.",
            manIconNorm = "Normalització: Botó per igualar el volume de totes les cançons, per conduir segur.",
            volumeNormalization = "Igualar Volum", equalizerTitle = "Equalitzador", presets = "Ajustos",
            reverb = "Reverberació", manEqDesc = "EQ profesional de 10 bandes amb perfils musicals i simulació de cotxe 3D.",
            graphicEq = "Equalitzador Gràfic", none = "Cap", carSpace = "Espai Cotxe", mediumRoom = "Sala Mitjana",
            largeHall = "Gran Sala", savePreset = "Guardar Ajust"
        )
        "FRANCAIS" -> english.copy(
            search = "Recherche", playlists = "Listes", language = "Langue", configTitle = "Configuration",
            volumeNormalization = "Normalisation", equalizerTitle = "Égaliseur", carSpace = "Espace Voiture"
        )
        "DEUTSCH" -> english.copy(
            search = "Suche", playlists = "Listen", language = "Sprache", configTitle = "Konfiguration",
            volumeNormalization = "Lautstärkenormalisierung", equalizerTitle = "Equalizer", carSpace = "Auto-Raum"
        )
        "ITALIANO" -> english.copy(
            search = "Cerca", playlists = "Playlist", language = "Lingua", configTitle = "Configurazione",
            volumeNormalization = "Normalizzazione Volume", equalizerTitle = "Equalizzatore", carSpace = "Spazio Auto"
        )
        "KOREAN" -> english.copy(
            search = "검색", playlists = "재생 목록", language = "언어", configTitle = "설정",
            volumeNormalization = "음량 정규화", equalizerTitle = "이퀄ライザー", carSpace = "자동차 공간"
        )
        "JAPANESE" -> english.copy(
            search = "検索", playlists = "プレイリスト", language = "言語", configTitle = "設定",
            volumeNormalization = "音量の正規化", equalizerTitle = "イコライザー", carSpace = "車内空間"
        )
        else -> english
    }
}
