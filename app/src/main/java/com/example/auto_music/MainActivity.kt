@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.auto_music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.auto_music.data.MusicRepository
import com.example.auto_music.data.local.MusicDatabase
import com.example.auto_music.data.remote.YouTubeService
import com.example.auto_music.ui.MainViewModel
import com.example.auto_music.model.Playlist
import com.example.auto_music.ui.screens.PlaylistSongsScreen
import com.example.auto_music.ui.screens.PlaylistsScreen
import com.example.auto_music.ui.screens.SearchScreen
import com.example.auto_music.ui.theme.Auto_MusicTheme
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlin.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = MusicDatabase.getDatabase(applicationContext)

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.youtube.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        val youtubeService = retrofit.create(YouTubeService::class.java)
        val repository = MusicRepository(database.musicDao(), youtubeService, applicationContext)

        setContent {
            Auto_MusicTheme {
                val viewModel: MainViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(repository) as T
                    }
                })

                var controller by remember { mutableStateOf<androidx.media3.session.MediaController?>(null) }
                val context = androidx.compose.ui.platform.LocalContext.current
                
                LaunchedEffect(Unit) {
                    android.util.Log.d("MainActivity", "Iniciant MediaController...")
                    val sessionToken = androidx.media3.session.SessionToken(
                        context,
                        android.content.ComponentName(context, com.example.auto_music.player.MusicService::class.java)
                    )
                    val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
                    controllerFuture.addListener({
                        try {
                            val newController = controllerFuture.get()
                            newController.addListener(object : androidx.media3.common.Player.Listener {
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    android.util.Log.e("MainActivity", "Controller Player Error: ${error.message}", error)
                                }
                                override fun onPlaybackStateChanged(state: Int) {
                                    android.util.Log.d("MainActivity", "Controller Playback State: $state")
                                }
                                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                                    android.util.Log.d("MainActivity", "Controller MediaItem Transition: ${mediaItem?.mediaId}")
                                }
                            })
                            controller = newController
                            android.util.Log.d("MainActivity", "MediaController connectat")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error connectant MediaController", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }

                MainApp(viewModel, controller)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel, controller: androidx.media3.session.MediaController?) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE) }
    
    var currentLanguage by remember {
        val saved = sharedPrefs.getString("language", "ESPANOL")
        mutableStateOf(saved ?: "ESPANOL")
    }
    
    var backgroundColor by remember {
        mutableLongStateOf(sharedPrefs.getLong("bg_color", Color.White.toArgb().toLong()))
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDonationDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    val strings = getTranslations(currentLanguage)

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.language) },
            text = {
                Column {
                    listOf("ENGLISH", "ESPANOL", "CATALA").forEach { lang ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                currentLanguage = lang
                                sharedPrefs.edit().putString("language", lang).apply()
                                showLanguageDialog = false
                            }.padding(16.dp)
                        ) {
                            RadioButton(selected = currentLanguage == lang, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(lang.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(strings.close) } }
        )
    }

    if (showDonationDialog) {
        AlertDialog(
            onDismissRequest = { showDonationDialog = false },
            title = { Text(strings.donationTitle) },
            text = { Text(strings.donationText) },
            confirmButton = { TextButton(onClick = { showDonationDialog = false }) { Text(strings.close) } }
        )
    }

    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text(strings.configTitle) },
            text = {
                Column {
                    Text(strings.selectColor)
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(Color.White, Color(0xFFF5F5F5), Color(0xFFE3F2FD), Color(0xFFF1F8E9), Color(0xFFFFF3E0)).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, CircleShape)
                                    .clickable {
                                        backgroundColor = color.toArgb().toLong()
                                        sharedPrefs.edit().putLong("bg_color", backgroundColor).apply()
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (backgroundColor == color.toArgb().toLong()) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showConfigDialog = false }) { Text(strings.close) } }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Auto Music",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(strings.language) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showLanguageDialog = true
                    },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text(strings.configTitle) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showConfigDialog = true
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text(strings.donationTitle) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showDonationDialog = true
                    },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Auto Music") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            bottomBar = {
                Column(modifier = if (selectedPlaylist != null) Modifier.navigationBarsPadding() else Modifier) {
                    controller?.let { MiniPlayer(it) }
                    if (selectedPlaylist == null) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == 0,
                                onClick = { currentScreen = 0 },
                                icon = { Icon(Icons.Default.Search, contentDescription = strings.search) },
                                label = { Text(strings.search) }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 1,
                                onClick = { currentScreen = 1 },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = strings.playlists) },
                                label = { Text(strings.playlists) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                color = Color(backgroundColor.toInt())
            ) {
                if (selectedPlaylist != null) {
                    PlaylistSongsScreen(
                        viewModel = viewModel,
                        playlist = selectedPlaylist!!,
                        onBack = { selectedPlaylist = null },
                        onPlay = { song ->
                            android.util.Log.d("MainActivity", "onPlay cridat per a: ${song.title}")
                            controller?.let {
                                android.util.Log.d("MainActivity", "Reproduint: ${song.title} (${song.id})")
                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                    .setMediaId(song.id)
                                    .setUri(
                                        if (song.audioUrl != null && !song.audioUrl.startsWith("http"))
                                            android.net.Uri.fromFile(java.io.File(song.audioUrl)).toString()
                                        else
                                            "https://music.youtube.com/watch?v=${song.id}"
                                    )
                                    .setMimeType("audio/mpeg")
                                    .setCustomCacheKey(song.id)
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(song.title)
                                            .setArtist(song.artist)
                                            .setArtworkUri(song.thumbnailUrl.toUri())
                                            .build()
                                    )
                                    .build()
                                it.setMediaItem(mediaItem)
                                it.prepare()
                                it.play()
                            } ?: android.util.Log.w("MainActivity", "Controller és nul")
                        }
                    )
                } else {
                    when (currentScreen) {
                        0 -> SearchScreen(viewModel, onPlay = { song ->
                            android.util.Log.d("MainActivity", "onPlay (cerca) cridat per a: ${song.title}")
                            controller?.let {
                                android.util.Log.d("MainActivity", "Reproduint (cerca): ${song.title} (${song.id})")
                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                    .setMediaId(song.id)
                                    .setUri(
                                        if (song.audioUrl != null && !song.audioUrl.startsWith("http"))
                                            android.net.Uri.fromFile(java.io.File(song.audioUrl)).toString()
                                        else
                                            "https://music.youtube.com/watch?v=${song.id}"
                                    )
                                    .setMimeType("audio/mpeg")
                                    .setCustomCacheKey(song.id)
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(song.title)
                                            .setArtist(song.artist)
                                            .setArtworkUri(song.thumbnailUrl.toUri())
                                            .build()
                                    )
                                    .build()
                                it.setMediaItem(mediaItem)
                                it.prepare()
                                it.play()
                            } ?: android.util.Log.w("MainActivity", "Controller és nul a la cerca")
                        })
                        1 -> PlaylistsScreen(viewModel, onPlaylistClick = { playlist ->
                            selectedPlaylist = playlist
                        })
                    }
                }
            }
        }
    }
}

data class AppTranslations(
    val search: String,
    val playlists: String,
    val language: String,
    val configTitle: String,
    val donationTitle: String,
    val donationText: String,
    val selectColor: String,
    val close: String
)

fun getTranslations(lang: String): AppTranslations {
    return when (lang) {
        "ENGLISH" -> AppTranslations(
            "Search", "Playlists", "Language", "Configuration", "Donation",
            "If you liked my application you can donate the amount you consider by doing a bizzum to +34655533304",
            "Select background color", "Close"
        )
        "CATALA" -> AppTranslations(
            "Cerca", "Llistes", "Idioma", "Configuració", "Donació",
            "Si t'ha agradat la meva aplicació pots donar per Bizzum al +34655533304",
            "Selecciona el color de fons", "Tancar"
        )
        else -> AppTranslations( // ESPANOL
            "Buscar", "Listas", "Idioma", "Configuración", "Donación",
            "Si te gustó mi aplicación puedes donar la cantidad que consideres haciendo un bizzum al +34655533304",
            "Selecciona el color de fondo", "Cerrar"
        )
    }
}

@Composable
fun MiniPlayer(controller: androidx.media3.session.MediaController) {
    var title by remember { mutableStateOf(controller.mediaMetadata.title?.toString() ?: "") }
    var artist by remember { mutableStateOf(controller.mediaMetadata.artist?.toString() ?: "") }
    var artworkUri by remember { mutableStateOf(controller.mediaMetadata.artworkUri) }
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
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
        }
        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(isPlaying, playbackState) {
        if (isPlaying) {
            while (true) {
                position = controller.currentPosition
                duration = controller.duration
                kotlinx.coroutines.delay(500)
            }
        }
    }

    if (title.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp
        ) {
            Column {
                if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (duration > 0) {
                    LinearProgressIndicator(
                        progress = { if (duration > 0) position.toFloat() / duration.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(end = 12.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        if (isPlaying) controller.pause() else controller.play()
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausa" else "Reprodueix"
                        )
                    }
                }
            }
        }
    }
}
