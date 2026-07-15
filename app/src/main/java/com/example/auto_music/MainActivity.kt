@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.auto_music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE) }
            
            var useDarkTheme by remember {
                mutableStateOf(sharedPrefs.getBoolean("dark_theme", false))
            }

            Auto_MusicTheme(darkTheme = useDarkTheme) {
                val viewModel: MainViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(repository) as T
                    }
                })

                var controller by remember { mutableStateOf<androidx.media3.session.MediaController?>(null) }
                
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

                MainApp(viewModel, controller, useDarkTheme) { isDark ->
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
    controller: androidx.media3.session.MediaController?,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("AutoMusicPrefs", Context.MODE_PRIVATE) }
    
    var currentLanguage by remember {
        val saved = sharedPrefs.getString("language", "ESPANOL")
        mutableStateOf(saved ?: "ESPANOL")
    }
    
    var backgroundColor by remember {
        mutableLongStateOf(sharedPrefs.getLong("bg_color", Color(0xFFF9F6F0).toArgb().toLong()))
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    
    val strings = getTranslations(currentLanguage)

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
                    selected = currentScreen == 2,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = 2
                        selectedPlaylist = null
                    },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text(strings.configTitle) },
                    selected = currentScreen == 3,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = 3
                        selectedPlaylist = null
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text(strings.donationTitle) },
                    selected = currentScreen == 4,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = 4
                        selectedPlaylist = null
                    },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) }
                )
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    label = { Text(strings.search) },
                    selected = currentScreen == 0,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = 0
                        selectedPlaylist = null
                    },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text(strings.playlists) },
                    selected = currentScreen == 1,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = 1
                        selectedPlaylist = null
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurface
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
                        if (selectedPlaylist == null && currentScreen < 2) {
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
                    color = if (isDarkTheme) MaterialTheme.colorScheme.background else Color(backgroundColor.toInt())
                ) {
                    if (selectedPlaylist != null) {
                        PlaylistSongsScreen(
                            viewModel = viewModel,
                            playlist = selectedPlaylist!!,
                            onBack = { selectedPlaylist = null },
                            onPlay = { song ->
                                playSong(song, controller)
                            }
                        )
                    } else {
                        when (currentScreen) {
                            0 -> SearchScreen(viewModel, onPlay = { song ->
                                playSong(song, controller)
                            })
                            1 -> PlaylistsScreen(viewModel, onPlaylistClick = { playlist ->
                                selectedPlaylist = playlist
                            })
                            2 -> LanguageScreen(
                                strings = strings,
                                currentLanguage = currentLanguage,
                                onLanguageChange = { lang ->
                                    currentLanguage = lang
                                    sharedPrefs.edit().putString("language", lang).apply()
                                }
                            )
                            3 -> ConfigScreen(
                                strings = strings,
                                backgroundColor = Color(backgroundColor.toInt()),
                                isDarkTheme = isDarkTheme,
                                onDarkThemeChange = onDarkThemeChange,
                                onColorChange = { color ->
                                    backgroundColor = color.toArgb().toLong()
                                    sharedPrefs.edit().putLong("bg_color", backgroundColor).apply()
                                }
                            )
                            4 -> DonationScreen(strings = strings)
                        }
                    }
                }
            }
        }
    }
}

fun playSong(song: com.example.auto_music.model.Song, controller: androidx.media3.session.MediaController?) {
    android.util.Log.d("MainActivity", "Reproduint: ${song.title} (${song.id})")
    controller?.let {
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

@Composable
fun LanguageScreen(strings: AppTranslations, currentLanguage: String, onLanguageChange: (String) -> Unit) {
    val languages = listOf(
        "ENGLISH" to "English",
        "ESPANOL" to "Español",
        "CATALA" to "Català",
        "GALEGO" to "Galego",
        "EUSKARA" to "Euskara",
        "FRANCAIS" to "Français",
        "DEUTSCH" to "Deutsch",
        "ITALIANO" to "Italiano",
        "KOREAN" to "한국어",
        "JAPANESE" to "日本語"
    )
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(strings.language, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        languages.forEach { (key, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageChange(key) }
                    .padding(16.dp)
            ) {
                RadioButton(selected = currentLanguage == key, onClick = null)
                Spacer(Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ConfigScreen(
    strings: AppTranslations, 
    backgroundColor: Color, 
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onColorChange: (Color) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(strings.configTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.darkMode, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Switch(checked = isDarkTheme, onCheckedChange = onDarkThemeChange)
        }

        Spacer(Modifier.height(24.dp))
        Text(strings.selectColor, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        
        var r by remember { mutableFloatStateOf(backgroundColor.red) }
        var g by remember { mutableFloatStateOf(backgroundColor.green) }
        var b by remember { mutableFloatStateOf(backgroundColor.blue) }
        var brightness by remember { mutableFloatStateOf(1f) }

        val updateColor = { red: Float, green: Float, blue: Float, bri: Float ->
            onColorChange(Color(red * bri, green * bri, blue * bri))
        }

        Text("R: ${(r * 255).toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(value = r, onValueChange = { r = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)
        
        Text("G: ${(g * 255).toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(value = g, onValueChange = { g = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)

        Text("B: ${(b * 255).toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(value = b, onValueChange = { b = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)

        Text(strings.brightness, style = MaterialTheme.typography.bodySmall)
        Slider(value = brightness, onValueChange = { brightness = it; updateColor(r, g, b, brightness) }, valueRange = 0f..1f)
        
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(if (isDarkTheme) Color.Gray else Color(r * brightness, g * brightness, b * brightness), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(strings.preview, color = if (brightness < 0.5f) Color.White else Color.Black)
        }
        if (isDarkTheme) {
            Text(strings.darkThemeNote, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun DonationScreen(strings: AppTranslations) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(strings.donationTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color.Red)
        Spacer(Modifier.height(32.dp))
        Text(
            strings.donationText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "BIZZUM: +34 655 53 33 04",
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
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
    val close: String,
    val brightness: String,
    val preview: String,
    val darkMode: String,
    val darkThemeNote: String
)

fun getTranslations(lang: String): AppTranslations {
    return when (lang) {
        "ENGLISH" -> AppTranslations(
            "Search", "Playlists", "Language", "Configuration", "Donation",
            "If you liked my application you can donate the amount you consider by doing a bizzum to +34655533304",
            "Select background color", "Close", "Brightness", "Preview", "Dark Mode", "Custom color is disabled in Dark Mode"
        )
        "CATALA" -> AppTranslations(
            "Cerca", "Llistes", "Idioma", "Configuració", "Donació",
            "Si t'ha agradat la meva aplicació pots donar per Bizzum al +34655533304",
            "Selecciona el color de fons", "Tancar", "Brillantor", "Vista prèvia", "Mode fosc", "El color personalitzat es desactiva en mode fosc"
        )
        "GALEGO" -> AppTranslations(
            "Cerca", "Listas", "Lingua", "Configuración", "Doazón",
            "Se che gustou a miña aplicación podes doar por Bizzum ao +34655533304",
            "Selecciona a cor de fondo", "Pechar", "Brillo", "Vista previa", "Modo escuro", "A cor personalizada desactívase no modo escuro"
        )
        "EUSKARA" -> AppTranslations(
            "Bilatu", "Zerrendak", "Hizkuntza", "Konfigurazioa", "Dohaintza",
            "Nire aplikazioa gustatu bazaizu, bizzum bat egin dezakezu +34655533304 zenbakira",
            "Hautatu atzeko planoko kolorea", "Itxi", "Distira", "Aurreikuspena", "Modu iluna", "Kolore pertsonalizatua desgaituta dago modu ilunean"
        )
        "FRANCAIS" -> AppTranslations(
            "Recherche", "Listes", "Langue", "Configuration", "Don",
            "Si vous avez aimé mon application, vous pouvez faire un bizzum au +34655533304",
            "Sélectionnez la couleur de fond", "Fermer", "Luminosité", "Aperçu", "Mode sombre", "La couleur personnalisée est désactivée en mode sombre"
        )
        "DEUTSCH" -> AppTranslations(
            "Suche", "Listen", "Sprache", "Konfiguration", "Spende",
            "Wenn Ihnen meine App gefallen hat, können Sie ein Bizzum an +34655533304 senden",
            "Hintergrundfarbe auswählen", "Schließen", "Helligkeit", "Vorschau", "Dunkelmodus", "Benutzerdefinierte Farbe ist im Dunkelmodus deaktiviert"
        )
        "ITALIANO" -> AppTranslations(
            "Cerca", "Liste", "Lingua", "Configurazione", "Donazione",
            "Se ti è piaciuta la mia app, puoi fare un bizzum al +34655533304",
            "Seleziona il colore dello sfondo", "Chiudi", "Luminosità", "Anteprima", "Modalità scura", "Il colore personalizzato è disabilitato in modalità scura"
        )
        "KOREAN" -> AppTranslations(
            "검색", "재생 목록", "언어", "설정", "기부",
            "내 애플리케이션이 마음에 들면 +34655533304로 bizzum을 수행하여 고려하는 금액을 기부할 수 있습니다.",
            "배경색 선택", "닫기", "밝기", "미리보기", "다크 모드", "다크 모드에서는 사용자 정의 색상이 비활성화됩니다."
        )
        "JAPANESE" -> AppTranslations(
            "検索", "プレイリスト", "言語", "設定", "寄付",
            "私のアプリケーションが気に入ったら、+34655533304にbizzumを行うことで、検討している金額を寄付できます。",
            "背景色を選択", "閉じる", "明るさ", "プレビュー", "ダークモード", "ダークモードではカスタムカラーが無効になります"
        )
        else -> AppTranslations( // ESPANOL
            "Buscar", "Listas", "Idioma", "Configuración", "Donación",
            "Si te gustó mi aplicación puedes donar la cantidad que consideres haciendo un bizzum al +34655533304",
            "Selecciona el color de fondo", "Cerrar", "Brillo", "Vista previa", "Modo oscuro", "El color personalizado se desactiva en modo oscuro"
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
