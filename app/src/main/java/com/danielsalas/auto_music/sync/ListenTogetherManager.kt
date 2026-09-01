package com.danielsalas.auto_music.sync

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.firebase.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class ListenTogetherManager(
    private val context: Context,
    private val controller: MediaController?
) {
    private val TAG = "ListenTogetherManager"
    private val database = FirebaseDatabase.getInstance().reference.child("listening_rooms")
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var roomId: String? = null
    private var isHost = false
    private var roomListener: ValueEventListener? = null
    
    private var lastRemoteSongId: String? = null
    private var isProcessingRemoteChange = false

    fun createRoom(onResult: (String?) -> Unit) {
        val newRoomId = java.util.UUID.randomUUID().toString().substring(0, 6).uppercase()
        roomId = newRoomId
        isHost = true
        
        scope.launch {
            try {
                database.child(newRoomId).setValue(mapOf(
                    "host_id" to getDeviceId(),
                    "current_song_id" to (controller?.currentMediaItem?.mediaId ?: ""),
                    "is_playing" to (controller?.isPlaying ?: false),
                    "position_ms" to (controller?.currentPosition ?: 0L),
                    "timestamp" to ServerValue.TIMESTAMP
                )).await()
                
                startHostSync()
                onResult(newRoomId)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating room: ${e.message}")
                onResult(null)
            }
        }
    }

    fun joinRoom(code: String, onResult: (Boolean) -> Unit) {
        val cleanCode = code.trim().uppercase()
        roomId = cleanCode
        isHost = false
        
        scope.launch {
            try {
                val snapshot = database.child(cleanCode).get().await()
                if (snapshot.exists()) {
                    startParticipantSync(cleanCode)
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error joining room: ${e.message}")
                onResult(false)
            }
        }
    }

    fun leaveRoom() {
        roomId?.let { id ->
            if (isHost) {
                database.child(id).removeValue()
            }
            roomListener?.let { database.child(id).removeEventListener(it) }
        }
        roomId = null
        isHost = false
        roomListener = null
    }

    fun setAsOwner(isOwner: Boolean) {
        this.isHost = isOwner
        if (isOwner) {
            startHostSync()
            roomListener?.let { roomId?.let { id -> database.child(id).removeEventListener(it) } }
        } else {
            roomId?.let { startParticipantSync(it) }
        }
    }

    private fun startHostSync() {
        scope.launch {
            while (roomId != null && isHost) {
                delay(3000)
                roomId?.let { id ->
                    controller?.let { c ->
                        val mediaId = c.currentMediaItem?.mediaId ?: ""
                        val playlistId = if (mediaId.contains("|")) mediaId.substringBefore("|").removePrefix("PL") else null
                        
                        database.child(id).updateChildren(mapOf(
                            "current_song_id" to mediaId,
                            "playlist_id" to playlistId,
                            "is_playing" to c.isPlaying,
                            "position_ms" to c.currentPosition,
                            "timestamp" to ServerValue.TIMESTAMP
                        ))
                    }
                }
            }
        }
    }

    private fun startParticipantSync(id: String) {
        roomListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isHost || isProcessingRemoteChange) return
                
                val songId = snapshot.child("current_song_id").getValue(String::class.java) ?: return
                val playlistId = snapshot.child("playlist_id").getValue(String::class.java)
                val isPlaying = snapshot.child("is_playing").getValue(Boolean::class.java) ?: false
                val remotePos = snapshot.child("position_ms").getValue(Long::class.java) ?: 0L
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                
                applyRemoteState(songId, playlistId, isPlaying, remotePos, timestamp)
            }
            override fun onCancelled(error: DatabaseError) { Log.e(TAG, "Room error: ${error.message}") }
        }
        database.child(id).addValueEventListener(roomListener!!)
    }

    private fun applyRemoteState(songId: String, playlistId: String?, remotePlaying: Boolean, remotePos: Long, timestamp: Long) {
        controller?.let { c ->
            isProcessingRemoteChange = true
            
            val now = System.currentTimeMillis()
            val latency = if (timestamp > 0) now - timestamp else 0
            val adjustedPos = remotePos + (if (remotePlaying) latency else 0)

            if (c.currentMediaItem?.mediaId != songId) {
                // Here we would ideally trigger playSong(songId)
                // But manager doesn't have repository or UI scope.
                // We'll rely on Media3 identifying the item if it's already in the queue or reachable.
                
                // For now, let's at least try to set the media item if we can find it
                // This part needs a callback to UI or ViewModel
            }

            // Sync play/pause
            if (remotePlaying && !c.isPlaying) c.play()
            else if (!remotePlaying && c.isPlaying) c.pause()

            // Sync position if difference > 2 seconds
            if (Math.abs(c.currentPosition - adjustedPos) > 2000) {
                c.seekTo(adjustedPos)
            }
            
            isProcessingRemoteChange = false
        }
    }

    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    }
}
