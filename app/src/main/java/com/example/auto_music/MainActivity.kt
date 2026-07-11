package com.example.auto_music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                            controller = controllerFuture.get()
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
    var currentScreen by remember { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        bottomBar = {
            Column(modifier = if (selectedPlaylist != null) Modifier.navigationBarsPadding() else Modifier) {
                controller?.let { MiniPlayer(it) }
                if (selectedPlaylist == null) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen == 0,
                            onClick = { currentScreen = 0 },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Cerca") },
                            label = { Text("Cerca") }
                        )
                        NavigationBarItem(
                            selected = currentScreen == 1,
                            onClick = { currentScreen = 1 },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Llistes") },
                            label = { Text("Llistes") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
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
                        progress = { position.toFloat() / duration.coerceAtLeast(1L) },
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
