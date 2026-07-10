package com.example.auto_music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auto_music.data.MusicRepository
import com.example.auto_music.data.local.MusicDatabase
import com.example.auto_music.data.remote.YouTubeService
import com.example.auto_music.ui.MainViewModel
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
        
        val database = androidx.room.Room.databaseBuilder(
            applicationContext,
            MusicDatabase::class.java,
            "music_db"
        ).build()

        // Cliente OkHttp configurado para imitar al cliente web de YouTube Music (como RiMusic)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        // Usamos directamente el endpoint interno de YouTube Music (InnerTube)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://music.youtube.com/") 
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
                    val sessionToken = androidx.media3.session.SessionToken(
                        context,
                        android.content.ComponentName(context, com.example.auto_music.player.MusicService::class.java)
                    )
                    val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
                    controllerFuture.addListener({
                        controller = controllerFuture.get()
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == 0,
                    onClick = { currentScreen = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = currentScreen == 1,
                    onClick = { currentScreen = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Playlists") },
                    label = { Text("Playlists") }
                )
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                0 -> SearchScreen(viewModel, onPlay = { song ->
                    controller?.let {
                        val mediaItem = androidx.media3.common.MediaItem.Builder()
                            .setMediaId(song.id)
                            // Para streaming directo usamos un servidor de Invidious que extraiga el audio
                            .setUri("https://inv.tux.pizza/latest_version?id=${song.id}&itag=140")
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .build()
                            )
                            .build()
                        it.setMediaItem(mediaItem)
                        it.prepare()
                        it.play()
                    }
                })
                1 -> PlaylistsScreen(viewModel, onPlaylistClick = { /* Ver canciones */ })
            }
        }
    }
}
