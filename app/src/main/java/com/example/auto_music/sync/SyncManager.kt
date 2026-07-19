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
import kotlinx.coroutines.tasks.await

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
            database = FirebaseDatabase.getInstance().reference
            Log.i("SyncManager", "Firebase inicialitzat correctament")
        } catch (e: Exception) {
            Log.e("SyncManager", "Error inicialitzant Firebase: ${e.message}")
        }
    }

    fun startSync(id: String) {
        if (id.isBlank()) return
        syncId = id
        val db = database ?: return
        
        Log.i("SyncManager", "Iniciant sincronització amb ID: $id")
        
        // Listen to private data
        db.child("sync").child(id).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isUpdatingFromCloud) return
                scope.launch { processCloudData(snapshot, isPublic = false) }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("SyncManager", "Error: ${error.message}") }
        })

        // Listen to global public data
        db.child("public_playlists").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isUpdatingFromCloud) return
                scope.launch { processCloudData(snapshot, isPublic = true) }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("SyncManager", "Error Public: ${error.message}") }
        })
    }

    private suspend fun processCloudData(snapshot: DataSnapshot, isPublic: Boolean) {
        if (!snapshot.exists()) return
        try {
            isUpdatingFromCloud = true
            val playlistsSnap = snapshot.child("playlists")
            val songsSnap = snapshot.child("songs")
            val refsSnap = snapshot.child("refs")

            val localPlaylists = repository.allPlaylists.first()
            val existingSongs = repository.allSongs.first()

            playlistsSnap.children.forEach { p ->
                val playlistName = p.child("name").getValue(String::class.java) ?: "Nova llista"
                val cloudId = p.key ?: return@forEach
                
                // Search by cloudId for public lists, or name for private
                var localPlaylist = if (isPublic) {
                    localPlaylists.find { it.cloudId == cloudId }
                } else {
                    localPlaylists.find { it.name == playlistName && !it.isPublic }
                }

                val finalPlaylistId = if (localPlaylist == null) {
                    // Create playlist using the EXACT cloudId from Firebase to avoid future duplicates
                    repository.createPlaylist(playlistName, isPublic, cloudId)
                } else {
                    localPlaylist.id
                }

                if (finalPlaylistId != -1L) {
                    val cloudSongsRefs = refsSnap.child(cloudId).children
                    cloudSongsRefs.forEach { r ->
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

    fun uploadLocalData(onComplete: (Boolean, String?) -> Unit) {
        val id = syncId ?: run { onComplete(false, "ID buit"); return }
        val db = database ?: run { onComplete(false, "Firebase no configurat"); return }
        if (isUpdatingFromCloud) { onComplete(false, "Sincronització en curs"); return }
        
        scope.launch {
            try {
                val playlists = repository.allPlaylists.first()
                
                // 1. Upload Private
                val privatePlaylists = playlists.filter { !it.isPublic }
                val privateSync = prepareSyncData(privatePlaylists)
                db.child("sync").child(id).setValue(privateSync).await()
                
                // 2. Upload Public
                for (p in playlists.filter { it.isPublic }) {
                    val cloudKey = p.cloudId ?: p.id.toString()
                    val pData = prepareSyncData(listOf(p))
                    
                    db.child("public_playlists").child("playlists").child(cloudKey).child("name").setValue(p.name)
                    db.child("public_playlists").child("refs").child(cloudKey).setValue(pData["refs"])
                    
                    val songsPart = (pData["songs"] as? Map<*, *>) ?: emptyMap<Any, Any>()
                    for ((sid, sdata) in songsPart) {
                        db.child("public_playlists").child("songs").child(sid.toString()).setValue(sdata)
                    }
                }

                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    private suspend fun prepareSyncData(playlists: List<Playlist>): Map<String, Any> {
        val playlistsMap = mutableMapOf<String, Any>()
        val refsMap = mutableMapOf<String, Any>()
        val songsMap = mutableMapOf<String, Any>()
        
        for (p in playlists) {
            val key = p.cloudId ?: p.id.toString()
            playlistsMap[key] = mapOf("name" to p.name)
            
            val pSongs = repository.getSongsInPlaylist(p.id).first()
            refsMap[key] = pSongs.map { mapOf("songId" to it.id) }
            
            for (s in pSongs) {
                songsMap[s.id] = mapOf(
                    "id" to s.id,
                    "title" to s.title,
                    "artist" to s.artist,
                    "thumbnailUrl" to s.thumbnailUrl,
                    "duration" to s.duration
                )
            }
        }
        return mapOf("playlists" to playlistsMap, "songs" to songsMap, "refs" to refsMap)
    }
}
