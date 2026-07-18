package com.example.auto_music.sync

import android.content.Context
import android.util.Log
import com.example.auto_music.data.MusicRepository
import com.example.auto_music.model.Playlist
import com.example.auto_music.model.Song
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncManager(
    private val context: Context,
    private val repository: MusicRepository
) {
    private var database: DatabaseReference? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncId: String? = null
    private var isUpdatingFromCloud = false

    init {
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            // Intentem obtenir la instància. Si no hi ha URL al JSON, això pot fallar o donar una instància invàlida
            val firebaseInstance = FirebaseDatabase.getInstance()
            database = firebaseInstance.reference
            Log.i("SyncManager", "Firebase inicialitzat correctament")
        } catch (e: Exception) {
            Log.e("SyncManager", "Error inicialitzant Firebase: ${e.message}. El fitxer google-services.json podria estar incomplet.")
            database = null
        }
    }

    fun startSync(id: String) {
        if (id.isBlank()) return
        syncId = id
        
        // Si no s'ha pogut inicialitzar abans (p.ex. per falta de URL al JSON), ho tornem a intentar
        if (database == null) initializeFirebase()
        
        val db = database ?: run {
            Log.e("SyncManager", "No es pot iniciar la sincronització: DatabaseReference és nul")
            return
        }
        
        Log.i("SyncManager", "Escoltant canvis al núvol per a ID: $id")
        
        try {
            db.child("sync").child(id).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isUpdatingFromCloud) return
                    scope.launch {
                        processCloudData(snapshot)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("SyncManager", "Firebase Error (${error.code}): ${error.message}")
                    if (error.code == DatabaseError.PERMISSION_DENIED) {
                        Log.e("SyncManager", "REVISAR: Les regles de la base de dades de Firebase no permeten l'accés.")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("SyncManager", "Error en startSync: ${e.message}")
        }
    }

    private suspend fun processCloudData(snapshot: DataSnapshot) {
        if (!snapshot.exists()) {
            Log.d("SyncManager", "ID de sincronització nou o buit al núvol")
            return
        }
        
        try {
            isUpdatingFromCloud = true
            val playlistsSnap = snapshot.child("playlists")
            val songsSnap = snapshot.child("songs")
            val refsSnap = snapshot.child("refs")

            val localPlaylists = repository.allPlaylists.first()
            val existingSongs = repository.allSongs.first()

            playlistsSnap.children.forEach { p ->
                val playlistName = p.child("name").getValue(String::class.java) ?: "Sincronitzada"
                val cloudId = p.key ?: return@forEach
                
                var localPlaylist = localPlaylists.find { it.name == playlistName }
                val finalPlaylistId = if (localPlaylist == null) {
                    Log.i("SyncManager", "Sincronitzant llista nova: $playlistName")
                    repository.createPlaylist(playlistName)
                } else {
                    localPlaylist.id
                }

                if (finalPlaylistId != -1L) {
                    val cloudSongs = refsSnap.child(cloudId).children
                    cloudSongs.forEach { r ->
                        val songId = r.child("songId").getValue(String::class.java) ?: return@forEach
                        val sSnap = songsSnap.child(songId)
                        if (!sSnap.exists()) return@forEach

                        val song = existingSongs.find { it.id == songId } ?: Song(
                            id = songId,
                            title = sSnap.child("title").getValue(String::class.java) ?: "Desconeguda",
                            artist = sSnap.child("artist").getValue(String::class.java) ?: "Desconegut",
                            thumbnailUrl = sSnap.child("thumbnailUrl").getValue(String::class.java) ?: "",
                            audioUrl = null,
                            duration = sSnap.child("duration").getValue(Long::class.java) ?: 0L,
                            isDownloaded = false
                        )
                        repository.addSongToPlaylist(song, finalPlaylistId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Error processant dades: ${e.message}")
        } finally {
            isUpdatingFromCloud = false
        }
    }

    fun uploadLocalData(onComplete: (Boolean) -> Unit = {}) {
        val id = syncId ?: run { onComplete(false); return }
        if (database == null) initializeFirebase()
        val db = database ?: run { onComplete(false); return }
        
        if (isUpdatingFromCloud) {
            onComplete(false)
            return
        }
        
        scope.launch {
            try {
                val playlists = repository.allPlaylists.first()
                val playlistsMap = mutableMapOf<String, Any>()
                val refsMap = mutableMapOf<String, Any>()
                val songsMap = mutableMapOf<String, Any>()
                
                for (p in playlists) {
                    playlistsMap[p.id.toString()] = mapOf("name" to p.name)
                    val pSongs = repository.getSongsInPlaylist(p.id).first()
                    refsMap[p.id.toString()] = pSongs.map { mapOf("songId" to it.id) }
                    
                    for (s in pSongs) {
                        if (!songsMap.containsKey(s.id)) {
                            songsMap[s.id] = mapOf(
                                "id" to s.id,
                                "title" to s.title,
                                "artist" to s.artist,
                                "thumbnailUrl" to s.thumbnailUrl,
                                "duration" to s.duration
                            )
                        }
                    }
                }

                val syncData = mapOf(
                    "playlists" to playlistsMap,
                    "songs" to songsMap,
                    "refs" to refsMap
                )
                
                db.child("sync").child(id).setValue(syncData)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i("SyncManager", "Pujada completada")
                            onComplete(true)
                        } else {
                            Log.e("SyncManager", "Error en pujar: ${task.exception?.message}")
                            onComplete(false)
                        }
                    }
            } catch (e: Exception) {
                Log.e("SyncManager", "Error en uploadLocalData: ${e.message}")
                onComplete(false)
            }
        }
    }
}
