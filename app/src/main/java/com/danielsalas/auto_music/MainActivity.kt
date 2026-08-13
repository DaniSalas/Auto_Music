@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.danielsalas.auto_music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
                        try { 
                            val c = controllerFuture.get()
                            controller = c
                            viewModel.setMediaController(c)
                        } catch (e: Exception) { android.util.Log.e("MainActivity", "Controller error", e) }
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
                NavigationDrawerItem(label = { Text(strings.equalizerTitle) }, selected = currentScreen == 6, onClick = { scope.launch { drawerState.close() }; currentScreen = 6; selectedPlaylist = null }, icon = { Icon(Icons.Default.Tune, null) })
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
                            6 -> EqualizerScreen(strings, controller)
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
        
        ManualHeader(strings.search)
        Text(strings.manSearchDesc, style = MaterialTheme.typography.bodyMedium)
        IconExplanation(Icons.Default.PlayArrow, strings.manIconPlay)
        IconExplanation(Icons.Default.Add, strings.manIconAdd)
        
        ManualHeader(strings.playlists)
        Text(strings.manPlaylistsDesc, style = MaterialTheme.typography.bodyMedium)
        
        ManualHeader(strings.manSongsTitle)
        Text(strings.manSongsDesc, style = MaterialTheme.typography.bodyMedium)
        IconExplanation(Icons.Default.DragHandle, strings.manIconDrag)
        IconExplanation(Icons.Default.Shuffle, strings.manIconShuffle)
        IconExplanation(Icons.Default.Difference, strings.manIconDup)
        IconExplanation(Icons.Default.SortByAlpha, strings.manIconAZ)
        IconExplanation(Icons.Default.Lock, strings.manIconFix)
        IconExplanation(Icons.Default.List, strings.manIconManual)
        IconExplanation(Icons.Default.Search, strings.manIconSearch)
        IconExplanation(Icons.Default.VolumeUp, strings.manIconNorm)
        
        ManualHeader(strings.configTitle)
        ManualSection(strings.darkMode, strings.manConfigDark)
        ManualSection(strings.autoDownloadTitle, strings.manConfigAuto)
        ManualSection(strings.syncTitle, strings.manConfigSync)
        ManualSection(strings.selectColor, strings.manConfigColor)
        
        ManualHeader(strings.equalizerTitle)
        Text(strings.manEqDesc, style = MaterialTheme.typography.bodyMedium)
        
        ManualHeader(strings.maintenanceTitle)
        Text(strings.manMaintenanceDesc, style = MaterialTheme.typography.bodyMedium)
        
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun EqualizerScreen(strings: AppTranslations, controller: androidx.media3.session.MediaController?) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("EqualizerPrefs", Context.MODE_PRIVATE) }
    var eqEnabled by remember { mutableStateOf(sp.getBoolean("eq_enabled", false)) }
    
    val presets = listOf("Flat", "Rock", "Jazz", "Metal", "Classical", "Acoustic")
    val reverbs = listOf(strings.none, strings.carSpace, strings.mediumRoom, strings.largeHall)
    
    val frequencies = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    val bandLevels = remember { mutableStateListOf<Int>().apply { for (i in 0 until 10) add(sp.getInt("band_$i", 0)) } }
    
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    val customPresets = remember { mutableStateListOf<String>().apply { addAll(sp.getStringSet("custom_presets", emptySet()) ?: emptySet()) } }
    var activePreset by remember { mutableStateOf(sp.getString("active_preset", "Flat") ?: "Flat") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.equalizerTitle, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Switch(checked = eqEnabled, onCheckedChange = { 
                eqEnabled = it
                sp.edit().putBoolean("eq_enabled", it).apply()
                updateServiceEq(controller, it, bandLevels.toIntArray(), sp.getInt("reverb_preset", 0))
            })
        }
        
        Spacer(Modifier.height(24.dp))
        Text(strings.graphicEq, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        
        Box(modifier = Modifier.fillMaxWidth().height(350.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(8.dp)) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until 10) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Text(frequencies[i], fontSize = 9.sp, maxLines = 1)
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newValue = (bandLevels[i] - (dragAmount.y / size.height * 1500)).toInt().coerceIn(0, 1500)
                                    bandLevels[i] = newValue
                                    sp.edit().putInt("band_$i", newValue).apply()
                                    activePreset = "Custom"
                                    sp.edit().putString("active_preset", "Custom").apply()
                                    updateServiceEq(controller, eqEnabled, bandLevels.toIntArray(), sp.getInt("reverb_preset", 0))
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val newValue = ((1f - (offset.y / size.height)) * 1500).toInt().coerceIn(0, 1500)
                                    bandLevels[i] = newValue
                                    sp.edit().putInt("band_$i", newValue).apply()
                                    activePreset = "Custom"
                                    sp.edit().putString("active_preset", "Custom").apply()
                                    updateServiceEq(controller, eqEnabled, bandLevels.toIntArray(), sp.getInt("reverb_preset", 0))
                                }
                            }, 
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight((bandLevels[i].toFloat() / 1500f).coerceAtLeast(0.01f))
                                .background(
                                    if (eqEnabled) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), 
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                            )
                        }
                        Text("${bandLevels[i] / 100}dB", fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp)); Text(strings.presets, style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                FilterChip(selected = activePreset == p, onClick = { 
                    applyPreset(p, sp); activePreset = p; sp.edit().putString("active_preset", p).apply()
                    for (i in 0 until 10) bandLevels[i] = sp.getInt("band_$i", 0)
                    updateServiceEq(controller, eqEnabled, bandLevels.toIntArray(), sp.getInt("reverb_preset", 0))
                }, label = { Text(p) })
            }
            customPresets.forEach { p ->
                FilterChip(selected = activePreset == p, onClick = { 
                    applyCustomPreset(p, sp); activePreset = p; sp.edit().putString("active_preset", p).apply()
                    for (i in 0 until 10) bandLevels[i] = sp.getInt("band_$i", 0)
                    updateServiceEq(controller, eqEnabled, bandLevels.toIntArray(), sp.getInt("reverb_preset", 0))
                }, label = { Text(p) }, trailingIcon = {
                    IconButton(onClick = { 
                        val set = sp.getStringSet("custom_presets", emptySet())?.toMutableSet() ?: mutableSetOf()
                        set.remove(p); sp.edit().putStringSet("custom_presets", set).apply(); customPresets.remove(p)
                        val editor = sp.edit(); for (i in 0 until 10) editor.remove("custom_${p}_band_$i")
                        editor.apply()
                    }, modifier = Modifier.size(16.dp)) { Icon(Icons.Default.Close, null) }
                })
            }
        }
        
        Button(onClick = { showSavePresetDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(strings.savePreset) }
        Spacer(Modifier.height(24.dp)); Text(strings.reverb, style = MaterialTheme.typography.titleMedium)
        var selectedReverb by remember { mutableIntStateOf(sp.getInt("reverb_preset", 0)) }
        reverbs.forEachIndexed { index, name ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { 
                selectedReverb = index; sp.edit().putInt("reverb_preset", index).apply()
                updateServiceEq(controller, eqEnabled, bandLevels.toIntArray(), index) 
            }.padding(8.dp)) { RadioButton(selected = selectedReverb == index, onClick = null); Spacer(Modifier.width(8.dp)); Text(name) }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showSavePresetDialog) {
        AlertDialog(onDismissRequest = { showSavePresetDialog = false }, title = { Text(strings.savePreset) }, text = { OutlinedTextField(value = newPresetName, onValueChange = { newPresetName = it }, label = { Text(strings.nameField) }) }, confirmButton = {
                TextButton(onClick = {
                    if (newPresetName.isNotBlank()) {
                        val set = sp.getStringSet("custom_presets", emptySet())?.toMutableSet() ?: mutableSetOf()
                        set.add(newPresetName); val editor = sp.edit(); editor.putStringSet("custom_presets", set)
                        for (i in 0 until 10) editor.putInt("custom_${newPresetName}_band_$i", bandLevels[i])
                        editor.apply(); customPresets.add(newPresetName); activePreset = newPresetName; sp.edit().putString("active_preset", newPresetName).apply(); showSavePresetDialog = false; newPresetName = ""
                    }
                }) { Text(strings.create) }
            }, dismissButton = { TextButton(onClick = { showSavePresetDialog = false }) { Text(strings.cancel) } })
    }
}

private fun applyCustomPreset(name: String, sp: android.content.SharedPreferences) {
    val editor = sp.edit(); for (i in 0 until 10) { val level = sp.getInt("custom_${name}_band_$i", 0); editor.putInt("band_$i", level) }
    editor.apply()
}

private fun applyPreset(name: String, sp: android.content.SharedPreferences) {
    val levels = when(name) {
        "Rock" -> intArrayOf(400, 300, 0, 0, 100, 300, 400, 500, 500, 500)
        "Jazz" -> intArrayOf(200, 100, 100, 200, 0, 0, 0, 100, 200, 300)
        "Metal" -> intArrayOf(300, 200, 100, 0, 0, 0, 100, 200, 400, 500)
        "Classical" -> intArrayOf(300, 200, 100, 100, 0, 0, 0, 100, 200, 200)
        "Acoustic" -> intArrayOf(200, 100, 0, 0, 100, 100, 200, 300, 200, 100)
        else -> intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }
    val editor = sp.edit(); levels.forEachIndexed { i, l -> editor.putInt("band_$i", l) }; editor.apply()
}

private fun updateServiceEq(controller: androidx.media3.session.MediaController?, enabled: Boolean, levels: IntArray, reverb: Int) {
    val bundle = Bundle().apply { putBoolean("enabled", enabled); putIntArray("levels", levels); putInt("reverb", reverb) }
    controller?.sendCustomCommand(androidx.media3.session.SessionCommand("ACTION_UPDATE_EQ", Bundle.EMPTY), bundle)
}

@Composable
fun ManualHeader(title: String) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.primaryContainer)
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(12.dp)); Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun LanguageScreen(strings: AppTranslations, currentLanguage: String, onLanguageChange: (String) -> Unit) {
    val languages = listOf("ENGLISH" to "English", "ESPANOL_LATINO" to "Español Latino", "CATALA" to "Català", "GALEGO" to "Galego", "EUSKARA" to "Euskara", "FRANCAIS" to "Français", "DEUTSCH" to "Deutsch", "ITALIANO" to "Italiano", "KOREAN" to "한국어", "JAPANESE" to "日本語")
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(strings.language, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(16.dp))
        languages.forEach { pair ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onLanguageChange(pair.first) }.padding(16.dp)) {
                RadioButton(selected = currentLanguage == pair.first, onClick = null); Spacer(Modifier.width(16.dp)); Text(pair.second, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ConfigScreen(strings: AppTranslations, backgroundColor: Color, isDarkTheme: Boolean, syncId: String, autoDownloadPublic: Boolean, autoDownloadPrivate: Boolean, onSyncIdChange: (String) -> Unit, onAutoDownloadPublicChange: (Boolean) -> Unit, onAutoDownloadPrivateChange: (Boolean) -> Unit, onDarkThemeChange: (Boolean) -> Unit, onColorChange: (Color) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(strings.configTitle, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.darkMode, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); Switch(checked = isDarkTheme, onCheckedChange = onDarkThemeChange) }
        Spacer(Modifier.height(24.dp)); Text(strings.autoDownloadTitle, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.autoDownloadPrivate); Spacer(Modifier.weight(1f)); Switch(checked = autoDownloadPrivate, onCheckedChange = onAutoDownloadPrivateChange) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.autoDownloadPublic); Spacer(Modifier.weight(1f)); Switch(checked = autoDownloadPublic, onCheckedChange = onAutoDownloadPublicChange) }
        Spacer(Modifier.height(24.dp)); Text(strings.syncTitle, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = syncId, onValueChange = onSyncIdChange, label = { Text(strings.syncIdLabel) }, modifier = Modifier.weight(1f), singleLine = true); Spacer(Modifier.width(8.dp)); Button(onClick = { onSyncIdChange((100000..999999).random().toString()) }) { Text(strings.generate) }
        }
        Text(strings.syncHelp, style = MaterialTheme.typography.bodySmall, color = Color.Gray); Spacer(Modifier.height(24.dp)); Text(strings.selectColor, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(16.dp))
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
        Text(strings.donationTitle, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(32.dp)); Icon(Icons.Default.Favorite, null, modifier = Modifier.size(100.dp), tint = Color.Red); Spacer(Modifier.height(32.dp)); Text(strings.donationText, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) { title = mediaMetadata.title?.toString() ?: ""; artist = mediaMetadata.artist?.toString() ?: ""; artworkUri = mediaMetadata.artworkUri; album = mediaMetadata.extras?.getString("album") ?: "" }
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
                        AsyncImage(model = artworkUri, contentDescription = null, modifier = Modifier.size(200.dp).background(Color.LightGray, RoundedCornerShape(12.dp)).clickable { onAlbumClick(if (album.isNotBlank()) album else artist) }, contentScale = ContentScale.Crop)
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
                        AsyncImage(model = artworkUri, contentDescription = null, modifier = Modifier.size(48.dp).padding(end = 12.dp).clickable { onAlbumClick(if (album.isNotBlank()) album else artist) }, contentScale = ContentScale.Crop)
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
    val equalizerTitle: String, val presets: String, val reverb: String, val manEqDesc: String, val graphicEq: String,
    val none: String, val carSpace: String, val mediumRoom: String, val largeHall: String, val savePreset: String
)

fun getTranslations(lang: String): AppTranslations {
    val english = AppTranslations(
        search = "Search", playlists = "Playlists", language = "Language", configTitle = "Configuration", donationTitle = "Donation",
        donationText = "If you liked my application you can donate the amount you consider.", selectColor = "Select background color", close = "Close",
        brightness = "Brightness", preview = "Preview", darkMode = "Dark Mode", darkThemeNote = "Custom color is disabled in Dark Mode",
        syncTitle = "Cloud Synchronization", syncIdLabel = "Sync ID", syncHelp = "Use the same ID on all devices to share your playlists.", generate = "Generate",
        deletePlaylist = "Delete Playlist", syncSuccess = "Synchronization successful", syncError = "Synchronization error",
        autoDownloadTitle = "Automatic Downloads", autoDownloadPrivate = "Private Playlists", autoDownloadPublic = "Public Playlists",
        isPublic = "Public", isPrivate = "Private", createPublic = "Create Public", createPrivate = "Create Private",
        selectedItems = "selected", searchPlaceholder = "Search by title, artist or lyrics", noResults = "No results found for",
        addToPlaylist = "Add to playlist", cancel = "Cancel", downloaded = "✓ Downloaded", downloading = "⏳ Downloading...",
        online = "🌐 Online", syncing = "Syncing...", setupId = "Set up ID", newPlaylist = "New playlist", nameField = "Name", create = "Create",
        maintenanceTitle = "Library Maintenance", maintenanceRunning = "Cleaning and verifying files...",
        maintenanceSummaryTitle = "Maintenance Summary", filesCleanedLabel = "Files cleaned", songsRequeuedLabel = "Songs requeued",
        songsRestoredLabel = "Songs restored", maintenanceErrorsTitle = "Unresolved errors",
        resumePlayback = "▶ Resume last playback", resumePlaylist = "▶ Resume this playlist", sortAZ = "Sort A-Z", sortZA = "Sort Z-A",
        searchInList = "Search in list", fixOrder = "Fix this order", manualOrder = "Custom order", findDuplicates = "Find duplicates",
        songsCountLabel = "songs", moveToPosition = "Move to position", manualTitle = "User Manual", manWelcomeTitle = "Welcome",
        manWelcomeDesc = "Auto Music is a hybrid player designed for the car. It combines online music and local downloads so the music never stops.",
        manSearchDesc = "Search for songs by title or artist.", 
        manPlaylistsDesc = "Manage your collections. Public lists are shared with any user, while Private ones are only shared between devices with the same ID.",
        manSongsTitle = "Songs Screen", manSongsDesc = "Professional management of your tracks inside a list.", 
        manIconDrag = "Long press and drag to reorder. It auto-scrolls at the edges.",
        manIconShuffle = "Shuffle: The system remembers this preference per list.",
        manIconDup = "Duplicates: Filters the list to show only repeated tracks.",
        manIconAZ = "A-Z: Visual sort to help searching (temporary).",
        manIconFix = "Fix: Saves the current visual order permanently.",
        manIconManual = "Manual: Returns to your custom favorite order.",
        manIconSearch = "Search: Jump directly to a track without stopping audio.",
        manMaintenanceDesc = "Maintenance: Cleans and ensures offline availability.",
        manIconPlay = "Play: Starts online or local playback immediately.",
        manIconAdd = "Add (+): Use this icon to add the song to one of your lists.",
        manConfigDark = "Dark Mode: Toggle between light and dark themes.",
        manConfigAuto = "Auto-Download: Automatically download new songs in lists.",
        manConfigSync = "Cloud Sync: Enter your ID to sync your lists across devices.",
        manConfigColor = "Background Color: Personalize the app's look in light mode.",
        manIconNorm = "Normalization: Button to equalize the volume of all songs in a list for safe driving.",
        volumeNormalization = "Volume Normalization",
        equalizerTitle = "Equalizer", presets = "Presets", reverb = "Reverb",
        manEqDesc = "10-band professional EQ with musical presets and 3D reverb spaces for car.",
        graphicEq = "Graphic Equalizer", none = "None", carSpace = "Car Space", mediumRoom = "Medium Room", 
        largeHall = "Large Hall", savePreset = "Save Preset"
    )

    return when (lang) {
        "ENGLISH" -> english
        "CATALA" -> english.copy(
            search = "Cerca", playlists = "Llistes", language = "Idioma", configTitle = "Configuració", donationTitle = "Donació",
            donationText = "Si t'ha agradat la meva aplicació pots fer una donació amb l'import que consideris.",
            selectColor = "Selecciona el color de fons", close = "Tancar", brightness = "Brillantor", preview = "Vista prèvia",
            darkMode = "Mode fosc", darkThemeNote = "El color personalitzat es desactiva en mode fosc",
            syncTitle = "Sincronització al Núvol", syncIdLabel = "ID de Sincronització",
            syncHelp = "Utilitza el mateix ID en tots els dispositius per compartir les teves llistes.",
            generate = "Generar", deletePlaylist = "Eliminar llista", syncSuccess = "Sincronització correcta",
            syncError = "Error en la sincronització", autoDownloadTitle = "Descàrregues Automàtiques",
            autoDownloadPrivate = "Llistes Privades", autoDownloadPublic = "Llistes Públiques", isPublic = "Pública",
            isPrivate = "Privada", createPublic = "Crea Pública", createPrivate = "Crea Privada",
            selectedItems = "seleccionades", searchPlaceholder = "Busca per títol, artista o lletra",
            noResults = "No s'han trobat resultats per a", addToPlaylist = "Afegir a la llista de reproducció",
            cancel = "Cancel·la", downloaded = "✓ Descarregada", downloading = "⏳ Descarregant...", online = "🌐 Online",
            syncing = "Sincronitzant...", setupId = "Configura l'ID", newPlaylist = "Nova llista de reproducció",
            nameField = "Nom", create = "Crea", maintenanceTitle = "Manteniment de la llibreria",
            maintenanceRunning = "Netejant i verificant fitxers...", maintenanceSummaryTitle = "Resum del Manteniment",
            filesCleanedLabel = "Fitxers netejats", songsRequeuedLabel = "Cançons reencuades",
            songsRestoredLabel = "Cançons restaurades", maintenanceErrorsTitle = "Errors sense resoldre",
            resumePlayback = "▶ Continuar última reproducció", resumePlaylist = "▶ Continuar aquesta llista",
            sortAZ = "Ordena A-Z", sortZA = "Ordena Z-A", searchInList = "Cerca a la llista",
            fixOrder = "Fixa aquest ordre", manualOrder = "Ordre personalitzat", findDuplicates = "Busca duplicats",
            songsCountLabel = "cançons", moveToPosition = "Moure a posició", manualTitle = "Manual d'Instruccions",
            manWelcomeTitle = "Benvingut", manWelcomeDesc = "Auto Music és un reproductor híbrid dissenyat pel cotxe.",
            manSearchDesc = "Busca cançons per títol o artista.",
            manPlaylistsDesc = "Gestiona les col·leccions. Les llistes Públiques se sincronitzen amb tothom, les Privades només entre els teus dispositius.",
            manSongsTitle = "Pantalla de Cançons", manSongsDesc = "Gestió professional de la teva música dins d'una llista.",
            manIconDrag = "Arrossegar: Mantén premut per reordenar. La llista llisca sola als marges.",
            manIconShuffle = "Aleatori: El sistema recorda la teva preferència per cada llista.",
            manIconDup = "Duplicats: Filtra per mostrar només les cançons repetides.",
            manIconAZ = "A-Z: Ordenació visual temporal per ajudar a la cerca.",
            manIconFix = "Fixar: Desa l'ordre visual actual com el teu ordre oficial.",
            manIconManual = "Manual: Torna al teu ordre personalitzat preferit.",
            manIconSearch = "Cerca: Salta a una cançó sense aturar la música.",
            manMaintenanceDesc = "Manteniment: Neteja fitxers i assegura la disponibilitat offline.",
            manIconPlay = "Reproduir: Inicia la reproducció online o local al moment.",
            manIconAdd = "Afegir (+): Prem aquesta icona per afegir la cançó a una llista.",
            manConfigDark = "Mode Fosc: Canvia entre el tema clar i el fosc.",
            manConfigAuto = "Auto-Descàrrega: Baixa automàticament les cançons de les llistes.",
            manConfigSync = "Sincro Núvol: Posa el teu ID per tenir les llistes a tot arreu.",
            manConfigColor = "Color de Fons: Personalitza l'aspecte de l'app en mode clar.",
            manIconNorm = "Normalització: Botó per igualar el volum de totes les cançons d'una llista per a una conducció segura.",
            volumeNormalization = "Igualació de Volum", equalizerTitle = "Ecualitzador", presets = "Presets",
            reverb = "Reverberació", manEqDesc = "EQ professional de 10 bandes amb presets musicals i espais 3D pel cotxe.",
            graphicEq = "Equalitzador Gràfic", none = "Cap", carSpace = "Espai Cotxe", mediumRoom = "Habitació Mitjana",
            largeHall = "Sala Gran", savePreset = "Desar Preset"
        )
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
            manWelcomeTitle = "Bienvenido", manWelcomeDesc = "Auto Music es un reproductor híbrido diseñado para el auto.",
            manSearchDesc = "Busca canciones por título o artista.",
            manPlaylistsDesc = "Gestiona tus colecciones. Las listas Públicas se sincronizan con cualquier ID; las Privadas solo entre usuarios con el mismo ID.",
            manSongsTitle = "Pantalla de Canciones", manSongsDesc = "Control profesional de tu música dentro de una lista.",
            manIconDrag = "Arrastrar: Mantén presionado y mueve para cambiar el orden. La lista se desplaza sola en los bordes.",
            manIconShuffle = "Aleatorio: El sistema recordará tu preferencia para cada lista, incluso en Android Auto.",
            manIconDup = "Duplicados: Filtra la lista para mostrar solo las canciones repetidas por título y artista.",
            manIconAZ = "A-Z: Orden visual temporal para ayudarte a buscar canciones rápido.",
            manIconFix = "Fijar: Guarda permanentemente el orden visual actual como tu orden oficial.",
            manIconManual = "Manual: Vuelve a tu orden favorito después de haber ordenado alfabéticamente.",
            manIconSearch = "Buscador: Escribe para saltar directamente a una canción sin detener la música.",
            manMaintenanceDesc = "Mantenimiento: Limpia archivos huérfanos y asegura que tus canciones estén listas para usar offline.",
            manIconPlay = "Reproducir: Presiona el ícono de play para iniciar la reproducción online o local al instante.",
            manIconAdd = "Agregar (+): Presiona este ícono para guardar la canción en una de tus listas.",
            manConfigDark = "Modo Oscuro: Cambia entre el tema claro y el oscuro para mayor comodidad.",
            manConfigAuto = "Auto-Descarga: Permite que la app baje automáticamente las canciones de tus listas.",
            manConfigSync = "Sincro en Nube: Ingresa tu ID para tener tus listas en todos tus dispositivos.",
            manConfigColor = "Color de Fondo: Personaliza el aspecto de la aplicación cuando no usas el modo oscuro.",
            manIconNorm = "Normalización: Botón para igualar el volumen de todas las canciones de una lista para una conducción segura.",
            volumeNormalization = "Igualar Volumen", equalizerTitle = "Ecualizador", presets = "Ajustes Pregrabados",
            reverb = "Reverberación", manEqDesc = "EQ profesional de 10 bandas con perfiles musicales y simulación de espacios 3D para el coche.",
            graphicEq = "Ecualizador Gráfico", none = "Ninguno", carSpace = "Espacio Coche", mediumRoom = "Habitación Pequeña",
            largeHall = "Gran Sala", savePreset = "Guardar Ajuste"
        )
        "GALEGO" -> english.copy(
            search = "Cerca", playlists = "Listas", language = "Lingua", configTitle = "Configuración", donationTitle = "Doazón",
            donationText = "Se che gustou a miña aplicació podes doar o importe que consideres.",
            selectColor = "Selecciona a cor de fondo", close = "Pechar", brightness = "Brillo", preview = "Vista previa",
            darkMode = "Modo escuro", darkThemeNote = "A cor personalizada desactívase no modo escuro",
            syncTitle = "Sincronización na Nube", syncIdLabel = "ID de Sincronización",
            syncHelp = "Usa o mesmo ID en todos os teus dispositivos.",
            generate = "Xerar", deletePlaylist = "Eliminar lista", syncSuccess = "Sincronización correcta",
            syncError = "Error na sincronización", autoDownloadTitle = "Descargas Automáticas",
            autoDownloadPrivate = "Listas Privadas", autoDownloadPublic = "Listas Públicas", isPublic = "Pública",
            isPrivate = "Privada", createPublic = "Crear Pública", createPrivate = "Crear Privada",
            selectedItems = "seleccionadas", searchPlaceholder = "Busca por título, artista ou letra",
            noResults = "Non se atoparon resultados para", addToPlaylist = "Engadir á lista de reprodución",
            cancel = "Cancelar", downloaded = "✓ Descargada", downloading = "⏳ Descargando...", online = "🌐 En liña",
            syncing = "Sincronizando...", setupId = "Configura o ID", newPlaylist = "Nova lista de reprodución",
            nameField = "Nome", create = "Crear", maintenanceTitle = "Mantemento da librería",
            maintenanceRunning = "Limpando e verificando ficheiros...", maintenanceSummaryTitle = "Resumo do Mantemento",
            filesCleanedLabel = "Ficheiros limpados", songsRequeuedLabel = "Cancións reencoladas",
            songsRestoredLabel = "Cancións restauradas", maintenanceErrorsTitle = "Erros sin resolver",
            resumePlayback = "▶ Continuar última reproducció", resumePlaylist = "▶ Continuar esta lista",
            sortAZ = "Ordenar A-Z", sortZA = "Ordenar Z-A", searchInList = "Buscar na lista",
            fixOrder = "Fixar esta orde", manualOrder = "Orde personalizada", findDuplicates = "Buscar duplicados",
            songsCountLabel = "cancións", moveToPosition = "Mover a posición", manualTitle = "Manual de Instrucións",
            manWelcomeTitle = "Benvido", manWelcomeDesc = "Auto Music é un reprodutor híbrid para o coche.",
            manSearchDesc = "Atopa cancións por título ou artista.",
            manPlaylistsDesc = "Xestiona as túas listas. As Públicas compártense con calquera; as Privadas só entre os teus dispositivos.",
            manSongsTitle = "Pantalla de Cancións", manSongsDesc = "Xestión profesional das túas cancións nunha lista.",
            manIconDrag = "Arrastrar: Mantén premido para reordenar. La lista móvese sola.",
            manIconShuffle = "Aleatorio: Lembra a túa preferencia para cada lista.",
            manIconDup = "Duplicados: Filtra para amosar só cancións repetidas.",
            manIconAZ = "A-Z: Ordenación visual temporal para buscar.",
            manIconFix = "Fixar: Garda a orde actual como a oficial.",
            manIconManual = "Manual: Volve á túa orde personalizada favorita.",
            manIconSearch = "Cerca: Salta á canción sen parar a música.",
            manMaintenanceDesc = "Mantemento: Asegura que todo estea listo para usar sen conexión.",
            manIconPlay = "Reproducir: Inicia a música online o local de inmediato.",
            manIconAdd = "Engadir (+): Usa esta icona para gardar a canción nunha lista.",
            manConfigDark = "Modo Escuro: Cambia entre el tema claro e o escuro.",
            manConfigAuto = "Descarga Auto: Baixa as cancións das listas de xeito automático.",
            manConfigSync = "Nube: Pon o teu ID para sincronizar listas entre dispositivos.",
            manConfigColor = "Cor de fondo: Personaliza o estilo da app no modo claro.",
            manIconNorm = "Normalización: Botón para igualar o volume de todas as cancións dunha lista para una condución segura.",
            volumeNormalization = "Normalización de Volume", equalizerTitle = "Ecualizador", presets = "Presets",
            reverb = "Reverberación", manEqDesc = "EQ de 10 bandas con sons de estudio e espazos 3D para o coche.",
            graphicEq = "Ecualizador Gráfico", none = "Ningunha", carSpace = "Espazo Coche", mediumRoom = "Cuarto Medio",
            largeHall = "Sala Grande", savePreset = "Gardar Preset"
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
            selectedItems = "hautatuta", searchPlaceholder = "Bilatu izenburuaren, artistaren edo letren arabera",
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
            manIconManual = "Eskuzkoa: Zure gogoko ordena pertsonalizatura itzultzen da.",
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
            volumeNormalization = "음량 정규화", equalizerTitle = "이퀄라이저", carSpace = "자동차 공간"
        )
        "JAPANESE" -> english.copy(
            search = "検索", playlists = "プレイリスト", language = "言語", configTitle = "設定",
            volumeNormalization = "音量の正規化", equalizerTitle = "イコライザー", carSpace = "車内空間"
        )
        else -> english.copy(
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
            largeHall = "Gran Sala", savePreset = "Guardar Ajuste"
        )
    }
}
