package com.example.auto_music.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String, // YouTube Video ID
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val audioUrl: String?, // Local path if downloaded
    val duration: Long,
    val isDownloaded: Boolean = false
)
