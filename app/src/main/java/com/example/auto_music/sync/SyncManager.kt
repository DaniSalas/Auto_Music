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
    private val database = FirebaseDatabase.getInstance().reference
    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncId: String? = null
    private var isUpdatingFromCloud = false

    fun startSync(id: String) {
        if (id.isBlank()) return
        syncId = id
        Log.i("SyncManager", "Iniciant sincronització amb ID: $id")
        
        database.child("sync").child(id).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isUpdatingFromCloud) return
                scope.launch {
                    processCloudData(snapshot)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("SyncManager", "Error en la sincronització: ${error.message}")
            }
        })
    }

    private suspend fun processCloudData(snapshot: DataSnapshot) {
        try {
            isUpdatingFromCloud = true
            Log.d("SyncManager", "Processant dades del núvol...")
            
            val playlistsSnap = snapshot.child("playlists")
            val songsSnap = snapshot.child("songs")
            val refsSnap = snapshot.child("refs")

            if (!playlistsSnap.exists()) {
                Log.w("SyncManager", "No hi ha llistes al núvol per a aquest ID")
                return
            }

            // 1. Obtenir totes les llistes locals actuals
            val localPlaylists = repository.allPlaylists.first()
            val existingSongs = repository.allSongs.first()

            // 2. Processar cada llista del núvol
            playlistsSnap.children.forEach { p ->
                val playlistName = p.child("name").getValue(String::class.java) ?: "Sense nom"
                val cloudId = p.key ?: return@forEach
                
                // Buscar si ja tenim la llista localment (per nom)
                var localPlaylist = localPlaylists.find { it.name == playlistName }
                
                val finalPlaylistId = if (localPlaylist == null) {
                    Log.i("SyncManager", "Creant llista nova des del núvol: $playlistName")
                    repository.createPlaylist(playlistName)
                    // Donem temps a Room per processar i busquem l'ID
                    repository.allPlaylists.first().find { it.name == playlistName }?.id ?: -1L
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
                            title = sSnap.child("title").getValue(String::class.java) ?: "Unknown",
                            artist = sSnap.child("artist").getValue(String::class.java) ?: "Unknown",
                            thumbnailUrl = sSnap.child("thumbnailUrl").getValue(String::class.java) ?: "",
                            audioUrl = null,
                            duration = sSnap.child("duration").getValue(Long::class.java) ?: 0L,
                            isDownloaded = false
                        )
                        
                        // repository.addSongToPlaylist ja gestiona no duplicar i baixar si no està
                        repository.addSongToPlaylist(song, finalPlaylistId)
                    }
                }
            }
            
            Log.i("SyncManager", "Sincronització del núvol finalitzada correctament")
        } catch (e: Exception) {
            Log.e("SyncManager", "Error processant dades: ${e.message}", e)
        } finally {
            isUpdatingFromCloud = false
        }
    }

    fun uploadLocalData() {
        val id = syncId ?: return
        if (isUpdatingFromCloud) return
        
        scope.launch {
            try {
                Log.d("SyncManager", "Forçant pujada de dades locals per a ID: $id...")
                val playlists = repository.allPlaylists.first()
                val songs = repository.allSongs.first()
                
                val syncData = mutableMapOf<String, Any>()
                val playlistsMap = mutableMapOf<String, Any>()
                val refsMap = mutableMapOf<String, Any>()
                
                playlists.forEach { p ->
                    playlistsMap[p.id.toString()] = mapOf("name" to p.name)
                    
                    val pSongs = repository.getSongsInPlaylist(p.id).first()
                    val pRefs = pSongs.map { mapOf("songId" to it.id) }
                    refsMap[p.id.toString()] = pRefs
                }
                
                val songsMap = mutableMapOf<String, Any>()
                songs.forEach { s ->
                    songsMap[s.id] = mapOf(
                        "id" to s.id,
                        "title" to s.title,
                        "artist" to s.artist,
                        "thumbnailUrl" to s.thumbnailUrl,
                        "duration" to s.duration
                    )
                }

                syncData["playlists"] = playlistsMap
                syncData["songs"] = songsMap
                syncData["refs"] = refsMap
                
                database.child("sync").child(id).setValue(syncData)
                    .addOnSuccessListener { Log.i("SyncManager", "Dades pujades manualment amb èxit") }
                    .addOnFailureListener { Log.e("SyncManager", "Error en pujada manual: ${it.message}") }
            } catch (e: Exception) {
                Log.e("SyncManager", "Error en uploadLocalData: ${e.message}", e)
            }
        }
    }
}
