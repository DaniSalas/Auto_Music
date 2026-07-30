@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.danielsalas.auto_music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.danielsalas.auto_music.data.MusicRepository
import com.danielsalas.auto_music.data.local.MusicDatabase
import com.danielsalas.auto_music.data.remote.YouTubeService
import com.danielsalas.auto_music.ui.MainViewModel
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.ui.screens.PlaylistSongsScreen
import com.danielsalas.auto_music.ui.screens.PlaylistsScreen
import com.danielsalas.auto_music.ui.screens.SearchScreen
import com.danielsalas.auto_music.ui.theme.Auto_MusicTheme
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlin.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.widget.Toast
import com.danielsalas.auto_music.sync.SyncManager
import java.io.File

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) { android.util.Log.e("MainActivity", "Permissions denied") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requestPermissionLauncher.launch(permissions)
        
        val database = MusicDatabase.getDatabase(applicationContext)
        val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()
        val retrofit = Retrofit.Builder().baseUrl("https://www.youtube.com/").client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
        val repository = MusicRepository(database.musicDao(), retrofit.create(YouTubeService::class.java), applicationContext)
        val syncManager = SyncManager(applicationContext, repository)

        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE) }
            var useDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", false)) }

            Auto_MusicTheme(darkTheme = useDarkTheme) {
                val viewModel: MainViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        val vm = MainViewModel(repository)
                        vm.setSyncManager(syncManager)
                        return vm as T
                    }
                })

                var controller by remember { mutableStateOf<androidx.media3.session.MediaController?>(null) }
                
                LaunchedEffect(Unit) {
                    val sessionToken = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, com.danielsalas.auto_music.player.MusicService::class.java))
                    val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
                    controllerFuture.addListener({
                        try { controller = controllerFuture.get() } catch (e: Exception) { android.util.Log.e("MainActivity", "Controller error", e) }
                    }, ContextCompat.getMainExecutor(context))
                }

                MainApp(viewModel, repository, controller, useDarkTheme, syncManager) { isDark ->
                    useDarkTheme = isDark
                    sharedPrefs.edit().putBoolean("dark_theme", isDark).apply()
                }
            }
        }
    }
}

@Composable
fun MainApp(
    viewModel: MainViewModel,
    repository: MusicRepository,
    controller: androidx.media3.session.MediaController?,
    isDarkTheme: Boolean,
    syncManager: SyncManager,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE) }
    var currentLanguage by remember { mutableStateOf(sharedPrefs.getString("language", "ESPANOL_LATINO") ?: "ESPANOL_LATINO") }
    var backgroundColor by remember { mutableLongStateOf(sharedPrefs.getLong("bg_color", Color(0xFFF9F6F0).toArgb().toLong())) }
    var autoDownloadPublic by remember { mutableStateOf(sharedPrefs.getBoolean("auto_download_public", true)) }
    var autoDownloadPrivate by remember { mutableStateOf(sharedPrefs.getBoolean("auto_download_private", true)) }
    var syncId by remember { mutableStateOf(sharedPrefs.getString("sync_id", "") ?: "") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val strings = getTranslations(currentLanguage)

    // Maintenance State
    var isMaintenanceRunning by remember { mutableStateOf(false) }
    var maintenanceSummary by remember { mutableStateOf<com.danielsalas.auto_music.data.MaintenanceSummary?>(null) }

    BackHandler(enabled = drawerState.isOpen || selectedPlaylist != null || currentScreen != 0 || isPlayerExpanded) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            isPlayerExpanded -> isPlayerExpanded = false
            selectedPlaylist != null -> selectedPlaylist = null
            currentScreen != 0 -> currentScreen = 0
        }
    }

    LaunchedEffect(syncId) { if (syncId.isNotBlank()) syncManager.startSync(syncId) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("Auto Music", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                NavigationDrawerItem(label = { Text(strings.search) }, selected = currentScreen == 0, onClick = { scope.launch { drawerState.close() }; currentScreen = 0; selectedPlaylist = null }, icon = { Icon(Icons.Default.Search, null) })
                NavigationDrawerItem(label = { Text(strings.playlists) }, selected = currentScreen == 1, onClick = { scope.launch { drawerState.close() }; currentScreen = 1; selectedPlaylist = null }, icon = { Icon(Icons.AutoMirrored.Filled.List, null) })
                NavigationDrawerItem(label = { Text(strings.language) }, selected = currentScreen == 2, onClick = { scope.launch { drawerState.close() }; currentScreen = 2; selectedPlaylist = null }, icon = { Icon(Icons.Default.Language, null) })
                NavigationDrawerItem(label = { Text(strings.configTitle) }, selected = currentScreen == 3, onClick = { scope.launch { drawerState.close() }; currentScreen = 3; selectedPlaylist = null }, icon = { Icon(Icons.Default.Settings, null) })
                NavigationDrawerItem(label = { Text(strings.manualTitle) }, selected = currentScreen == 5, onClick = { scope.launch { drawerState.close() }; currentScreen = 5; selectedPlaylist = null }, icon = { Icon(Icons.Default.Help, null) })
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
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
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
                            }, isMaintenanceRunning = isMaintenanceRunning)
                            2 -> LanguageScreen(strings, currentLanguage, { currentLanguage = it; sharedPrefs.edit().putString("language", it).apply() })
                            3 -> ConfigScreen(strings, Color(backgroundColor.toInt()), isDarkTheme, syncId, autoDownloadPublic, autoDownloadPrivate, { syncId = it; sharedPrefs.edit().putString("sync_id", it).apply() }, { autoDownloadPublic = it; sharedPrefs.edit().putBoolean("auto_download_public", it).apply() }, { autoDownloadPrivate = it; sharedPrefs.edit().putBoolean("auto_download_private", it).apply() }, onDarkThemeChange, { backgroundColor = it.toArgb().toLong(); sharedPrefs.edit().putLong("bg_color", backgroundColor).apply() })
                            4 -> DonationScreen(strings)
                            5 -> ManualScreen(strings, currentLanguage)
                        }
                    }
                }
            }
        }
    }

    if (isMaintenanceRunning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(strings.maintenanceTitle) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(strings.maintenanceRunning)
                }
            },
            confirmButton = {}
        )
    }

    maintenanceSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { maintenanceSummary = null },
            title = { Text(strings.maintenanceSummaryTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("${strings.filesCleanedLabel}: ${summary.filesCleaned}")
                    Text("${strings.songsRequeuedLabel}: ${summary.songsRequeued}")
                    Text("${strings.songsRestoredLabel}: ${summary.songsRestored}")
                    if (summary.errors.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(strings.maintenanceErrorsTitle, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                        summary.errors.forEach { error ->
                            Text("• ${error.title}: ${error.reason}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { maintenanceSummary = null }) { Text(strings.close) }
            }
        )
    }
}

fun playSong(song: com.danielsalas.auto_music.model.Song, controller: androidx.media3.session.MediaController?, playlistId: Long?) {
    controller?.let {
        val compositeId = if (playlistId != null) "PL$playlistId|${song.id}" else song.id
        val isLocal = song.isDownloaded && song.audioUrl != null && File(song.audioUrl).exists()
        val finalUri = if (isLocal) android.net.Uri.fromFile(File(song.audioUrl)).toString() else "https://music.youtube.com/watch?v=${song.id}"
        
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(song.thumbnailUrl.toUri())
            .setExtras(Bundle().apply { 
                if (playlistId != null) putString("playlistId", playlistId.toString())
                putString("album", song.album) 
            }).build()
            
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(compositeId)
            .setUri(finalUri)
            .setMimeType("audio/mpeg")
            .setCustomCacheKey(song.id)
            .setMediaMetadata(metadata)
            .build()
            
        it.setMediaItem(mediaItem)
        it.prepare(); it.play()
    }
}

@Composable
fun ManualScreen(strings: AppTranslations, lang: String) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(strings.manualTitle, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        
        ManualSection(strings.manWelcomeTitle, strings.manWelcomeDesc)
        ManualSection(strings.search, strings.manSearchDesc)
        ManualSection(strings.playlists, strings.manPlaylistsDesc)
        ManualSection(strings.manSongsTitle, strings.manSongsDesc)
        
        IconExplanation(Icons.Default.DragHandle, strings.manIconDrag)
        IconExplanation(Icons.Default.Shuffle, strings.manIconShuffle)
        IconExplanation(Icons.Default.Difference, strings.manIconDup)
        IconExplanation(Icons.Default.SortByAlpha, strings.manIconAZ)
        IconExplanation(Icons.Default.Lock, strings.manIconFix)
        IconExplanation(Icons.Default.List, strings.manIconManual)
        IconExplanation(Icons.Default.Search, strings.manIconSearch)
        
        ManualSection(strings.maintenanceTitle, strings.manMaintenanceDesc)
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ManualSection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Text(content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun IconExplanation(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun LanguageScreen(strings: AppTranslations, currentLanguage: String, onLanguageChange: (String) -> Unit) {
    val languages = listOf("ENGLISH" to "English", "ESPANOL_LATINO" to "Español Latino", "CATALA" to "Català", "GALEGO" to "Galego", "EUSKARA" to "Euskara", "FRANCAIS" to "Français", "DEUTSCH" to "Deutsch", "ITALIANO" to "Italiano", "KOREAN" to "한국어", "JAPANESE" to "日本語")
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(strings.language, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        languages.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onLanguageChange(key) }.padding(16.dp)) {
                RadioButton(selected = currentLanguage == key, onClick = null)
                Spacer(Modifier.width(16.dp)); Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ConfigScreen(strings: AppTranslations, backgroundColor: Color, isDarkTheme: Boolean, syncId: String, autoDownloadPublic: Boolean, autoDownloadPrivate: Boolean, onSyncIdChange: (String) -> Unit, onAutoDownloadPublicChange: (Boolean) -> Unit, onAutoDownloadPrivateChange: (Boolean) -> Unit, onDarkThemeChange: (Boolean) -> Unit, onColorChange: (Color) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(strings.configTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.darkMode, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); Switch(checked = isDarkTheme, onCheckedChange = onDarkThemeChange) }
        Spacer(Modifier.height(24.dp)); Text(strings.autoDownloadTitle, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.autoDownloadPrivate); Spacer(Modifier.weight(1f)); Switch(checked = autoDownloadPrivate, onCheckedChange = onAutoDownloadPrivateChange) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.autoDownloadPublic); Spacer(Modifier.weight(1f)); Switch(checked = autoDownloadPublic, onCheckedChange = onAutoDownloadPublicChange) }
        Spacer(Modifier.height(24.dp)); Text(strings.syncTitle, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = syncId, onValueChange = onSyncIdChange, label = { Text(strings.syncIdLabel) }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp)); Button(onClick = { onSyncIdChange((100000..999999).random().toString()) }) { Text(strings.generate) }
        }
        Text(strings.syncHelp, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(24.dp)); Text(strings.selectColor, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(16.dp))
        var r by remember { mutableFloatStateOf(backgroundColor.red) }; var g by remember { mutableFloatStateOf(backgroundColor.green) }; var b by remember { mutableFloatStateOf(backgroundColor.blue) }; var brightness by remember { mutableFloatStateOf(1f) }
        val updateColor = { red: Float, gr: Float, bl: Float, bri: Float -> onColorChange(Color(red * bri, gr * bri, bl * bri)) }
        Text("R: ${(r * 255).toInt()}", style = MaterialTheme.typography.bodySmall); Slider(value = r, onValueChange = { r = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)
        Text("G: ${(g * 255).toInt()}", style = MaterialTheme.typography.bodySmall); Slider(value = g, onValueChange = { g = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)
        Text("B: ${(b * 255).toInt()}", style = MaterialTheme.typography.bodySmall); Slider(value = b, onValueChange = { b = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)
        Text(strings.brightness, style = MaterialTheme.typography.bodySmall); Slider(value = brightness, onValueChange = { brightness = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)
        Spacer(Modifier.height(32.dp)); Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(if (isDarkTheme) Color.Gray else Color(r * brightness, g * brightness, b * brightness), RoundedCornerShape(16.dp)).padding(16.dp), contentAlignment = Alignment.Center) { Text(strings.preview, color = if (brightness < 0.5f) Color.White else Color.Black) }
    }
}

@Composable
fun DonationScreen(strings: AppTranslations) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(strings.donationTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp)); Icon(Icons.Default.Favorite, null, modifier = Modifier.size(100.dp), tint = Color.Red); Spacer(Modifier.height(32.dp)); Text(strings.donationText, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp)); Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) { Text("BIZZUM: +34 655 53 33 04", modifier = Modifier.padding(16.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
    }
}

@Composable
fun MiniPlayer(
    controller: androidx.media3.session.MediaController, 
    isExpanded: Boolean, 
    onToggleExpand: () -> Unit,
    onAlbumClick: (String) -> Unit
) {
    var title by remember { mutableStateOf(controller.mediaMetadata.title?.toString() ?: "") }
    var artist by remember { mutableStateOf(controller.mediaMetadata.artist?.toString() ?: "") }
    var artworkUri by remember { mutableStateOf(controller.mediaMetadata.artworkUri) }
    var album by remember { mutableStateOf(controller.mediaMetadata.extras?.getString("album") ?: "") }
    var isPlaying by remember { mutableStateOf(controller.isPlaying) }
    var playbackState by remember { mutableIntStateOf(controller.playbackState) }
    var position by remember { mutableLongStateOf(controller.currentPosition) }
    var duration by remember { mutableLongStateOf(controller.duration) }
    DisposableEffect(controller) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) { 
                title = mediaMetadata.title?.toString() ?: ""
                artist = mediaMetadata.artist?.toString() ?: ""
                artworkUri = mediaMetadata.artworkUri
                album = mediaMetadata.extras?.getString("album") ?: ""
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { playbackState = state }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }
    LaunchedEffect(isPlaying, playbackState) { if (isPlaying) { while (true) { position = controller.currentPosition; duration = controller.duration; kotlinx.coroutines.delay(500) } } }
    fun formatTime(ms: Long): String { val totalSeconds = (ms / 1000).toInt(); return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60) }
    if (title.isNotEmpty()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() }, tonalElevation = 8.dp) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = if (isExpanded) 16.dp else 4.dp)) {
                if (isExpanded) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = artworkUri, 
                            contentDescription = null, 
                            modifier = Modifier.size(200.dp).background(Color.LightGray, RoundedCornerShape(12.dp)).clickable { onAlbumClick(if (album.isNotBlank()) album else artist) }, 
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(16.dp)); Text(text = title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(text = artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(16.dp)); Slider(value = if (duration > 0) position.toFloat() else 0f, onValueChange = { controller.seekTo(it.toLong()) }, valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(text = formatTime(position), fontSize = 12.sp); Text(text = formatTime(duration), fontSize = 12.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { controller.seekBack() }) { Icon(Icons.Default.Replay10, null) }
                            IconButton(onClick = { controller.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, null) }
                            IconButton(onClick = { if (isPlaying) controller.pause() else controller.play() }, modifier = Modifier.size(64.dp)) { Icon(imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, modifier = Modifier.fillMaxSize()) }
                            IconButton(onClick = { controller.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null) }
                            IconButton(onClick = { controller.seekForward() }) { Icon(Icons.Default.Forward10, null) }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = artworkUri, 
                            contentDescription = null, 
                            modifier = Modifier.size(48.dp).padding(end = 12.dp).clickable { onAlbumClick(if (album.isNotBlank()) album else artist) },
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) { Text(text = title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(text = artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        IconButton(onClick = { controller.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, null) }
                        IconButton(onClick = { if (isPlaying) controller.pause() else controller.play() }) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                        IconButton(onClick = { controller.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null) }
                    }
                    if (playbackState == Player.STATE_BUFFERING) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) } else if (duration > 0) { LinearProgressIndicator(progress = { position.toFloat() / duration.toFloat() }, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

data class AppTranslations(val search: String, val playlists: String, val language: String, val configTitle: String, val donationTitle: String, val donationText: String, val selectColor: String, val close: String, val brightness: String, val preview: String, val darkMode: String, val darkThemeNote: String, val syncTitle: String, val syncIdLabel: String, val syncHelp: String, val generate: String, val deletePlaylist: String, val syncSuccess: String, val syncError: String, val autoDownloadTitle: String, val autoDownloadPrivate: String, val autoDownloadPublic: String, val isPublic: String, val isPrivate: String, val createPublic: String, val createPrivate: String, val selectedItems: String, val searchPlaceholder: String, val noResults: String, val addToPlaylist: String, val cancel: String, val downloaded: String, val downloading: String, val online: String, val syncing: String, val setupId: String, val newPlaylist: String, val nameField: String, val create: String, val maintenanceTitle: String, val maintenanceRunning: String, val maintenanceSummaryTitle: String, val filesCleanedLabel: String, val songsRequeuedLabel: String, val songsRestoredLabel: String, val maintenanceErrorsTitle: String, val resumePlayback: String, val resumePlaylist: String, val sortAZ: String, val sortZA: String, val searchInList: String, val fixOrder: String, val manualOrder: String, val findDuplicates: String, val songsCountLabel: String, val moveToPosition: String, val manualTitle: String, val manWelcomeTitle: String, val manWelcomeDesc: String, val manSearchDesc: String, val manPlaylistsDesc: String, val manSongsTitle: String, val manSongsDesc: String, val manIconDrag: String, val manIconShuffle: String, val manIconDup: String, val manIconAZ: String, val manIconFix: String, val manIconManual: String, val manIconSearch: String, val manMaintenanceDesc: String)

fun getTranslations(lang: String): AppTranslations {
    return when (lang) {
        "ENGLISH" -> AppTranslations("Search", "Playlists", "Language", "Configuration", "Donation", "If you liked my application you can donate the amount you consider.", "Select background color", "Close", "Brightness", "Preview", "Dark Mode", "Custom color is disabled in Dark Mode", "Cloud Synchronization", "Sync ID", "Use the same ID on all devices to share your playlists.", "Generate", "Delete Playlist", "Synchronization successful", "Synchronization error", "Automatic Downloads", "Private Playlists", "Public Playlists", "Public", "Private", "Create Public", "Create Private", "selected", "Search by title, artist or lyrics", "No results found for", "Add to playlist", "Cancel", "✓ Downloaded", "⏳ Downloading...", "🌐 Online", "Syncing...", "Set up ID", "New playlist", "Name", "Create", "Library Maintenance", "Cleaning and verifying files...", "Maintenance Summary", "Files cleaned", "Songs requeued", "Songs restored", "Unresolved errors", "▶ Resume last playback", "▶ Resume this playlist", "Sort A-Z", "Sort Z-A", "Search in list", "Fix this order", "Custom order", "Find duplicates", "songs", "Move to position", "User Manual", "Welcome", "Auto Music is a hybrid player designed for the car. It combines online music and local downloads so the music never stops.", "Search for songs by title or artist. Tap one to listen or long press to add it to a list.", "Here you see your collections. You can create Public lists (synced with your cloud ID) or Private ones (this device only).", "Songs Screen", "Inside a list you can manage your music with professional precision.", "Long press and drag to change the order. If you reach the edge, the list will scroll itself.", "Shuffle Mode. The system will remember your preference for each list, even in Android Auto.", "Duplicate Detector. Filters the list to show only songs repeated by title and artist.", "Alphabetical Sort (A-Z). It's only visual to help you search, it doesn't change your favorite order.", "Fix Order. Permanently saves the current alphabetical order as your official order.", "Manual Order. Returns to your favorite order after sorting alphabetically.", "Internal Search. Type to jump directly to a song without stopping the music.", "Cleans orphaned files, repairs the database and ensures all your songs are ready for offline mode.")
        "CATALA" -> AppTranslations("Cerca", "Llistes", "Idioma", "Configuració", "Donació", "Si t'ha agradat la meva aplicació pots fer una donació amb l'import que consideris.", "Selecciona el color de fons", "Tancar", "Brillantor", "Vista prèvia", "Mode fosc", "El color personalitzat es desactiva en mode fosc", "Sincronització al Núvol", "ID de Sincronització", "Utilitza el mateix ID en tots els dispositius per compartir les teves llistes.", "Generar", "Eliminar llista", "Sincronització correcta", "Error en la sincronització", "Descàrregues Automàtiques", "Llistes Privades", "Llistes Públiques", "Pública", "Privada", "Crea Pública", "Crea Privada", "seleccionades", "Busca per títol, artista o lletra", "No s'han trobat resultats per a", "Afegir a la llista de reproducció", "Cancel·la", "✓ Descarregada", "⏳ Descarregant...", "🌐 Online", "Sincronitzant...", "Configura l'ID", "Nova llista de reproducció", "Nom", "Crea", "Manteniment de la llibreria", "Netejant i verificant fitxers...", "Resum del Manteniment", "Fitxers netejats", "Cançons reencuades", "Cançons restaurades", "Errors sense resoldre", "▶ Continuar última reproducció", "▶ Continuar aquesta llista", "Ordena A-Z", "Ordena Z-A", "Cerca a la llista", "Fixa aquest ordre", "Ordre personalitzat", "Busca duplicats", "cançons", "Moure a posició", "Manual d'Instruccions", "Benvingut", "Auto Music és un reproductor híbrid dissenyat pel cotxe. Combina música online i descàrregues locals perquè mai s'aturi la música.", "Busca cançons per títol o artista. Toca una per escoltar-la o mantén premut per afegir-la a una llista.", "Aquí veus les teves col·leccions. Pots crear llistes Públiques (es sincronitzen amb el teu ID al núvol) o Privades (només en aquest dispositiu).", "Pantalla de Cançons", "Dins d'una llista pots gestionar la teva música amb precisió professional.", "Mantén premut i arrossega per canviar l'ordre. Si arribes al límit, la llista es desplaçarà sola.", "Mode Aleatori. El sistema recordarà la teva preferència per cada llista, fins i tot a Android Auto.", "Detector de Duplicats. Filtra la llista per mostrar només les cançons repetides per títol i artista.", "Ordre Alfabètic (A-Z). És només visual per ajudar-te a buscar, no canvia el teu ordre preferit.", "Fixar Ordre. Desa permanentment l'ordre alfabètic actual com el teu ordre oficial.", "Ordre Manual. Torna al teu ordre preferit després d'haver ordenat alfabèticament.", "Cercador Intern. Escriu per saltar directament a una cançó sense aturar la música.", "Neteja fitxers orfes, repara la base de dades i assegura que totes les cançons estiguin a punt pel mode offline.")
        "GALEGO" -> AppTranslations("Cerca", "Listas", "Lingua", "Configuración", "Doazón", "Se che gustou a miña aplicació podes doar o importe que consideres.", "Selecciona a cor de fondo", "Pechar", "Brillo", "Vista previa", "Modo escuro", "A cor personalizada desactívase no modo escuro", "Sincronización na Nube", "ID de Sincronización", "Usa o mesmo ID en todos os teus dispositivos.", "Xerar", "Eliminar lista", "Sincronización correcta", "Error na sincronización", "Descargas Automáticas", "Listas Privadas", "Listas Públicas", "Pública", "Privada", "Crear Pública", "Crear Privada", "seleccionadas", "Busca por título, artista ou letra", "Non se atoparon resultados para", "Engadir á lista de reprodución", "Cancelar", "✓ Descargada", "⏳ Descargando...", "🌐 En liña", "Sincronizando...", "Configura o ID", "Nova lista de reprodución", "Nome", "Crear", "Mantemento da librería", "Limpando e verificando ficheiros...", "Resumo do Mantemento", "Ficheiros limpados", "Cancións reencoladas", "Cancións restauradas", "Erros sen resolver", "▶ Continuar última reprodución", "▶ Continuar esta lista", "Ordenar A-Z", "Ordenar Z-A", "Buscar na lista", "Fixar esta orde", "Orde personalizada", "Buscar duplicados", "cancións", "Mover a posición", "Manual de Instrucións", "Benvido", "Auto Music é un reprodutor híbrido deseñado para o coche. Combina música online e descargas locais.", "Busca cancións por título ou artista. Toca unha para escoitala ou mantén premido para engadila.", "Aquí ves as túas coleccións. Podes crear listas Públicas (sincronizadas) ou Privadas.", "Pantalla de Cancións", "Dentro dunha lista podes xestionar a túa música con precisión profesional.", "Mantén premido e arrastra para cambiar a orde. A lista desprazarase sola ao chegar ao bordo.", "Modo Aleatorio. O sistema lembrará a túa preferencia para cada lista.", "Detector de Duplicados. Filtra a lista para amosar só cancións repetidas.", "Orde Alfabética (A-Z). É visual para axudar a buscar, non cambia a túa orde favorita.", "Fixar Orde. Garda permanentemente a orde alfabética actual como oficial.", "Orde Manual. Volve á túa orde favorita despois de ordenar alfabeticamente.", "Buscador Interno. Escribe para saltar directamente a unha canción.", "Limpa ficheiros orfos e asegura que as cancións estean listas para o modo offline.")
        "EUSKARA" -> AppTranslations("Bilatu", "Zerrendak", "Hizkuntza", "Konfigurazioa", "Dohaintza", "Nire aplikazioa gustatu bazaizu, nahi duzun zenbatekoa eman dezakezu.", "Hautatu atzeko planoko kolorea", "Itxi", "Distira", "Aurreikuspena", "Modu iluna", "Kolore pertsonalizatua desgaituta dago modu ilunean", "Hodeiko Sinkronizazioa", "Sinkronizazio IDa", "Erabili ID bera gailu guztietan.", "Sortu", "Zerrenda ezabatu", "Sinkronizazio arrakastatsua", "Errorea sinkronizatzean", "Deskarga Automatikoak", "Zerrenda Pribatuak", "Zerrenda Publikoak", "Publikoa", "Pribatua", "Publikoa Sortu", "Pribatua Sortu", "hautatuta", "Bilatu izenburuaren, artistaren edo letren arabera", "Ez da emaitzarik aurkitu honetarako:", "Gehitu erreprodukzio-zerrendara", "Utzi", "✓ Deskargatuta", "⏳ Deskargatzen...", "🌐 Online", "Sinkronizatzen...", "Konfiguratu IDa", "Erreprodukzio-zerrenda berria", "Izena", "Sortu", "Liburutegiaren mantentzea", "Fitxategiak garbitzen eta egiaztatzen...", "Mantentze-lanen laburpena", "Garbitutako fitxategiak", "Berriro ilaran jarritako abestiak", "Leheneratutako abestiak", "Ebatzi gabeko erroreak", "▶ Erreprodukzioa jarraitu", "▶ Erreprodukzio-zerrenda jarraitu", "Ordenatu A-Z", "Ordenatu Z-A", "Zerrendan bilatu", "Finkatu ordena hau", "Ordena pertsonalizatua", "Bilatu bikoiztuak", "abestiak", "Mugitu posiziora", "Argibide Eskuliburua", "Ongi etorri", "Auto Music autorako diseinatutako erreproduzitzaile hibridoa da.", "Bilatu abestiak izenburuaren edo artistaren arabera.", "Hemen zure bildumak ikusten dituzu. Zerrenda publikoak edo pribatuak sor ditzakezu.", "Abestien Pantaila", "Zerrenda baten barruan zure musika zehaztasun profesionalarekin kudea dezakezu.", "Eduki sakatuta eta arrastatu ordena aldatzeko.", "Ausazko Modua. Sistemak zerrenda bakoitzeko zure lehentasuna gogoratuko du.", "Bikoiztuen Detektatzailea. Zerrenda iragazten du errepikatutako abestiak soilik erakusteko.", "A-Z Ordena. Bilatzen laguntzeko bisuala soilik da, ez du zure ordena gogokoena aldatzen.", "Finkatu Ordena. Uneko ordena alfabetikoa zure ordena ofizial gisa gordetzen du betiko.", "Eskuzko Ordena. Zure ordena gogokoenera itzultzen da alfabetikoki ordenatu ondoren.", "Barne Bilatzailea. Idatzi abesti batera zuzenean joateko.", "Fitxategi umezurtzak garbitzen ditu eta abesti guztiak offline modurako prest daudela ziurtatzen du.")
        "FRANCAIS" -> AppTranslations("Recherche", "Listes", "Langue", "Configuration", "Don", "Si vous avez aimé mon application, vous pouvez donner le montant que vous considérez.", "Sélectionnez la couleur de fondo", "Fermer", "Luminosité", "Aperçu", "Mode sombre", "La couleur personalizada est désactivée en mode sombre", "Synchronisation Cloud", "ID de Synchro", "Utilisez le même ID sur tous vos appareils.", "Générer", "Supprimer la liste", "Synchronisation réussie", "Erreur de synchronización", "Téléchargements Automatiques", "Listes Privées", "Listes Publiques", "Publique", "Privée", "Créer Publique", "Créer Privée", "sélectionnées", "Recherche par titre, artiste ou paroles", "Aucun résultat trouvé pour", "Ajouter à la playlist", "Annuler", "✓ Téléchargé", "⏳ Téléchargement...", "🌐 En ligne", "Synchronisation...", "Configurer l'ID", "Nouvelle playlist", "Nom", "Créer", "Maintenance de la bibliothèque", "Nettoyage et vérification des fichiers...", "Résumé de la maintenance", "Fichiers nettoyés", "Chansons réenfilées", "Chansons restaurées", "Erreurs non résolues", "▶ Reprendre la lecture", "▶ Reprendre cette playlist", "Trier A-Z", "Trier Z-A", "Rechercher dans la liste", "Fixer cet ordre", "Ordre personnalisé", "Trouver les doublons", "chansons", "Déplacer à la position", "Manuel d'Instructions", "Bienvenue", "Auto Music est un lecteur hybride conçu pour la voiture.", "Recherchez des chansons par titre ou artiste.", "Ici vous voyez vos collections. Vous pouvez créer des listes publiques ou privées.", "Écran des Chansons", "Dans une liste, vous pouvez gérer votre musique avec une précision professionnelle.", "Maintenez et faites glisser pour changer l'ordre.", "Mode Aléatoire. Le système se souviendra de votre préférence pour chaque liste.", "Détecteur de Doublons. Filtre la liste pour n'afficher que les chansons répétées.", "Tri Alphabétique (A-Z). C'est seulement visuel, cela ne change pas votre ordre favori.", "Fixer l'Ordre. Enregistre définitivement l'ordre actuel comme officiel.", "Ordre Manuel. Revient à votre ordre favori après un tri alphabétique.", "Recherche Interne. Tapez pour sauter directement à une chanson.", "Nettoie les fichiers orphelins et assure le mode hors ligne.")
        "DEUTSCH" -> AppTranslations("Suche", "Listen", "Sprache", "Konfiguration", "Spende", "Wenn Ihnen meine App gefallen hat, können Sie den von Ihnen gewünschten Betrag spenden.", "Hintergrundfarbe auswählen", "Schließen", "Helligkeit", "Vorschau", "Dunkelmodus", "Benutzerdefinierte Farbe ist im Dunkelmodus desactiviert", "Cloud-Synchronisation", "Sync-ID", "Verwenden Sie dieselbe ID auf allen Geräten.", "Generieren", "Wiedergabeliste löschen", "Synchronisierung erfolgreich", "Synchronisierungsfehler", "Automatische Downloads", "Private Playlists", "Öffentliche Playlists", "Öffentlich", "Privat", "Öffentlich Erstellen", "Privat Erstellen", "ausgewählt", "Suche nach Titel, Künstler oder Songtext", "Keine Ergebnisse gefunden für", "Zur Playlist hinzufügen", "Abbrechen", "✓ Heruntergeladen", "⏳ Herunterladen...", "🌐 Online", "Synchronisierung...", "ID einrichten", "Neue Playlist", "Name", "Erstellen", "Bibliothekswartung", "Dateien werden bereigt und überprüft...", "Wartungszusammenfassung", "Gereinigte Dateien", "Wieder in die Warteschlange gestellte Songs", "Wiederhergestellte Songs", "Ungelöste Fehler", "▶ Wiedergabe fortsetzen", "▶ Playlist fortsetzen", "Sortieren A-Z", "Sortieren Z-A", "In der Liste suchen", "Diese Reihenfolge fixieren", "Eigene Reihenfolge", "Duplikate finden", "Lieder", "An Position verschieben", "Bedienungsanleitung", "Willkommen", "Auto Music ist ein Hybrid-Player für das Auto.", "Suchen Sie Songs nach Titel oder Künstler.", "Hier sehen Sie Ihre Sammlungen. Öffentliche oder private Listen.", "Lied-Bildschirm", "In einer Liste können Sie Ihre Musik professionell verwalten.", "Gedrückt halten und ziehen, um die Reihenfolge zu ändern.", "Zufallsmodus. Das System merkt sich Ihre Vorliebe für jede Liste.", "Duplikat-Finder. Filtert die Liste nach doppelten Songs.", "Alphabetische Sortierung (A-Z). Nur visuell, ändert nicht Ihre Lieblingsreihenfolge.", "Ordnung fixieren. Speichert die aktuelle Reihenfolge als offiziell.", "Manuelle Ordnung. Kehrt nach der Sortierung zur Lieblingsreihenfolge zurück.", "Interne Suche. Tippen Sie, um direkt zu einem Song zu springen.", "Bereinigt verwaiste Dateien und stellt den Offline-Modus sicher.")
        "ITALIANO" -> AppTranslations("Cerca", "Liste", "Lingua", "Configurazione", "Donazione", "Se ti è piaciuta la mia app, puedes donare l'importo que consideri.", "Seleziona el colore dello sfondo", "Chiudi", "Luminosità", "Anteprima", "Modalità scura", "Il colore personalizado è disabilitato in modalidad scura", "Sincronizzazione Cloud", "ID Sincronizzazione", "Usa lo stesso ID su tutti i dispositivos.", "Genera", "Elimina playlist", "Sincronizzazione riuscita", "Errore di sincronizzazione", "Download Automatici", "Playlist Private", "Playlist Pubbliche", "Pubblica", "Privata", "Crea Pubblica", "Crea Privata", "selezionate", "Cerca per titolo, artista o testo", "Nessun resultado trovato per", "Aggiungi alla playlist", "Annulla", "✓ Scaricato", "⏳ Download in corso...", "🌐 Online", "Sincronizzazione...", "Imposta ID", "Nuova playlist", "Nome", "Crea", "Manutenzione libreria", "Pulizia e verifica dei file...", "Riepilogo manutenzione", "File puliti", "Canzoni rimesse in coda", "Canzoni ripristinate", "Errori non risolti", "▶ Continua riproduzione", "▶ Continua questa playlist", "Ordina A-Z", "Ordina Z-A", "Cerca nella lista", "Fissa questo ordine", "Ordine personalizzato", "Trova duplicati", "canzoni", "Sposta in posizione", "Manuale d'Istruzioni", "Benvenuto", "Auto Music è un lettore ibrido progettato per l'auto.", "Cerca canzoni per titolo o artista.", "Qui vedi le tue collezioni. Liste pubbliche o private.", "Schermata Canzoni", "In una lista puoi gestire la tua musica con precisione professionale.", "Tieni premuto e trascina per cambiare l'ordine.", "Modalità Casuale. Il sistema ricorderà la tua preferenza per ogni lista.", "Rilevatore Duplicati. Filtra la lista per mostrare solo i brani ripetuti.", "Ordine Alfabetico (A-Z). Solo visivo, non cambia il tuo ordine preferito.", "Fissa Ordine. Salva permanentemente l'ordine attuale come ufficiale.", "Ordine Manuale. Torna all'ordine preferito dopo il tri alfabetico.", "Ricerca Interna. Digita per saltare direttamente a un brano.", "Pulisce i file orfani e assicura la modalità offline.")
        "KOREAN" -> AppTranslations("검색", "재생 목록", "언어", "설정", "기부", "내 애플리케이션이 마음에 들면 원하는 금액을 기부할 수 있습니다.", "배경색 선택", "닫기", "밝기", "미리보기", "다크 모드", "다크 모드에서는 사용자 정의 색상이 bi활성화됩니다.", "클라우드 동기화", "동기화 ID", "모든 장치에서 동일한 ID를 사용하여 재생 목록을 공유하십시오.", "생성", "재생 목록 삭제", "동기화 성공", "동기화 오류", "자동 다운로드", "개인 재생 목록", "공개 재생 목록", "공개", "비공개", "공개 생성", "비공개 생성", "선택됨", "제목, 아티스트 또는 가사로 검색", "에 대한 결과를 찾을 수 없습니다", "재생 목록에 추가", "취소", "✓ 다운로드됨", "⏳ 다운로드 중...", "🌐 온라인", "동기화 중...", "ID 설정", "새 재생 목록", "이름", "생성", "라이브러리 유지 관리", "파일 정리 및 확인 중...", "유지 관리 요약", "정리된 파일", "재대기된 노래", "복구된 노래", "해결되지 않은 오류", "▶ 재생 계속", "▶ 이 목록 계속", "A-Z 정렬", "Z-A 정렬", "목록에서 검색", "이 순서 고정", "사용자 지정 순서", "중복 찾기", "곡", "위치로 이동", "사용 설명서", "환영합니다", "Auto Music은 차량용 하이브리드 플레이어입니다.", "제목이나 아티스트로 노래를 검색하세요.", "여기에서 컬렉션을 볼 수 있습니다. 공개 또는 비공개 목록.", "노래 화면", "목록 내에서 전문가 수준으로 음악을 관리할 수 있습니다.", "길게 눌러 드래그하면 순서가 변경됩니다.", "셔플 모드. 시스템은 각 목록에 대한 기본 설정을 기억합니다.", "중복 감지기. 제목과 아티스트별로 중복된 노래만 표시합니다.", "알파벳순 정렬 (A-Z). 검색을 돕기 위한 시각적 기능이며 선호하는 순서는 변경되지 않습니다.", "순서 고정. 현재 알파벳순을 공식 순서로 영구 저장합니다.", "수동 순서. 알파벳순 정렬 후 선호하는 순서로 돌아갑니다.", "내부 검색. 입력하면 노래로 바로 이동합니다.", "분실된 파일을 정리하고 오프라인 모드를 보장합니다.")
        "JAPANESE" -> AppTranslations("検索", "プレイリスト", "言語", "設定", "寄付", "私のアプリケーションが気に入ったら、検討している金額を寄付できます。", "背景色を選択", "閉じる", "明るさ", "プレビュー", "ダークモード", "ダークモードではカスタムカラーが無効になります", "クラウド同期", "同期ID", "すべてのデバイスで同じIDを使用してプレイリスト를 공유하십시오.", "生成", "プレイリスト를 削除", "同期에 成功しました", "同期エラー", "自動ダウンロード", "プライベートプレイリスト", "公開プレイリスト", "公開", "秘密", "公開作成", "秘密作成", "선택됨", "タイトル、アーティスト、または歌詞で検索", "の結果が見つかりませんでした", "プレイリストに追加", "キャンセル", "✓ ダウンロード済み", "⏳ ダウンロード中...", "🌐 オンライン", "同期中...", "IDを設定", "新しいプレイリスト", "名前", "作成", "라이브러리 メンテナンス", "ファイルのクリーンアップと確認中...", "メンテナンス概要", "クリーンアップされたファイル", "再キューイングされた曲", "復元된곡", "未解決のエラー", "▶ 再生を続行", "▶ このリストを続行", "A-Z順に並べ替え", "Z-A順に並べ替え", "リスト内を検索", "この順序を固定", "カスタム順序", "重複を検索", "曲", "位置に移動", "取扱説明書", "ようこそ", "Auto Musicは車用に設計されたハイブリッドプレーヤーです。", "タイトルまたはアーティストで曲を検索します。", "ここでコレクションを確認できます。公開または非公開リスト。", "曲画面", "リスト内でプロ級の精度で音楽を管理できます。", "長押ししてドラッグすると順序が変わります。", "シャッフルモード。システムはリストごとの好みを記憶します。", "重複検出器。タイトルとアーティストで重複した曲のみを表示します。", "アルファベット順 (A-Z)。検索用の視覚的機能で、お気に入りの順序は変わりません。", "順序を固定。現在のアルファベット順を公式順序として保存します。", "手動順序。アルファベット順の後に、お気に入りの順序に戻ります。", "内部検索。入力して曲に直接ジャンプします。", "不要なファイルをクリーンアップし、オフラインモードを保証します。")
        "ESPANOL_LATINO" -> AppTranslations("Buscar", "Listas", "Idioma", "Configuración", "Donación", "Si te gustó mi aplicación puedes donar la cantidad que consideres.", "Selecciona el color de fondo", "Cerrar", "Brillo", "Vista previa", "Modo oscuro", "El color personalizado se desactiva en modo oscuro", "Sincronización en la Nube", "ID de Sincronización", "Usa el mismo ID en todos tus dispositivos para compartir tus listas.", "Generar", "Eliminar lista", "Sincronización correcta", "Error en la sincronización", "Descargas Automáticas", "Listas Privadas", "Listas Públicas", "Pública", "Privada", "Crear Pública", "Crear Privada", "seleccionadas", "Busca por título, artista o letra", "No se han encontrado resultados para", "Añadir a la lista", "Cancelar", "✓ Descargada", "⏳ Descargando...", "🌐 Online", "Sincronizando...", "Configura el ID", "Nueva lista de reproducción", "Nombre", "Crear", "Mantenimiento de la librería", "Limpiando y verificando archivos...", "Resumen del Mantenimiento", "Archivos limpiados", "Canciones reencoladas", "Canciones restauradas", "Errores sin resolver", "▶ Continuar última reproducción", "▶ Continuar esta lista", "Ordenar A-Z", "Ordenar Z-A", "Buscar en la lista", "Fijar este orden", "Orden personalizado", "Buscar duplicados", "canciones", "Mover a posición", "Manual de Instrucciones", "Bienvenido", "Auto Music es un reproductor híbrido diseñado para el auto. Combina música online y descargas locales para que nunca pare la música.", "Busca canciones por título o artista. Toca una para escucharla o mantén presionado para agregarla a una lista.", "Aquí ves tus colecciones. Puedes crear listas Públicas (se sincronizan con tu ID en la nube) o Privadas (solo en este dispositivo).", "Pantalla de Canciones", "Dentro de una lista puedes gestionar tu música con precisión profesional.", "Mantén presionado y arrastra para cambiar el orden. Si llegas al borde, la lista se desplazará sola.", "Modo Aleatorio. El sistema recordará tu preferencia para cada lista, incluso en Android Auto.", "Detector de Duplicados. Filtra la lista para mostrar solo las canciones repetidas por título y artista.", "Orden Alfabético (A-Z). Es solo visual para ayudarte a buscar, no cambia tu orden favorito.", "Fijar Orden. Guarda permanentemente el orden alfabético actual como tu orden oficial.", "Orden Manual. Vuelve a tu orden favorito después de haber ordenado alfabéticamente.", "Buscador Interno. Escribe para saltar directamente a una canción sin detener la música.", "Limpia archivos huérfanos, repara la base de datos y asegura que todas tus canciones estén listas para el modo offline.")
        else -> AppTranslations("Buscar", "Listas", "Idioma", "Configuración", "Donación", "Si te gustó mi aplicación puedes donar la cantidad que consideres.", "Selecciona el color de fondo", "Cerrar", "Brillo", "Vista previa", "Modo oscuro", "El color personalizado se desactiva en modo oscuro", "Sincronización en la Nube", "ID de Sincronización", "Usa el mismo ID en todos tus dispositivos para compartir tus listas.", "Generar", "Eliminar lista", "Sincronización correcta", "Error en la sincronización", "Descargas Automáticas", "Listas Privadas", "Listas Públicas", "Pública", "Privada", "Crear Pública", "Crear Privada", "seleccionadas", "Busca por título, artista o letra", "No se han encontrado resultados para", "Añadir a la lista", "Cancelar", "✓ Descargada", "⏳ Descargando...", "🌐 Online", "Sincronizando...", "Configura el ID", "Nueva lista de reproducción", "Nombre", "Crear", "Mantenimiento de la librería", "Limpiando y verificando archivos...", "Resumen del Mantenimiento", "Archivos limpiados", "Canciones reencoladas", "Canciones restauradas", "Errores sin resolver", "▶ Continuar última reproducción", "▶ Continuar esta lista", "Ordenar A-Z", "Ordenar Z-A", "Buscar en la lista", "Fijar este orden", "Orden personalizado", "Buscar duplicados", "canciones", "Mover a posición", "Manual de Instrucciones", "Bienvenido", "Auto Music es un reproductor híbrido diseñado para el coche. Combina música online y descargas locales para que nunca pare la música.", "Busca canciones por título o artista. Toca una para escucharla o mantén pulsado para añadirla a una lista.", "Aquí ves tus colecciones. Puedes crear listas Públicas (se sincronizan con tu ID en la nube) o Privadas (solo en este dispositivo).", "Pantalla de Canciones", "Dentro de una lista puedes gestionar tu música con precisión profesional.", "Mantén pulsado y arrastra para cambiar el orden. Si llegas al borde, la lista se desplazará sola.", "Modo Aleatorio. El sistema recordará tu preferencia para cada lista, incluso en Android Auto.", "Detector de Duplicados. Filtra la lista para mostrar solo las canciones repetidas por título y artista.", "Orden Alfabético (A-Z). Es solo visual para ayudarte a buscar, no cambia tu orden favorito.", "Fijar Orden. Guarda permanentemente el orden alfabético actual como tu orden oficial.", "Orden Manual. Vuelve a tu orden favorito después de haber ordenado alfabéticamente.", "Buscador Interno. Escribe para saltar directamente a una canción sin detener la música.", "Limpia archivos huérfanos, repara la base de datos y asegura que todas tus canciones estén listas para el modo offline.")
    }
}
