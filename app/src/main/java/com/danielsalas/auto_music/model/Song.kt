package com.danielsalas.auto_music.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val thumbnailUrl: String,
    val audioUrl: String? = null,
    val duration: Long = 0,
    val isDownloaded: Boolean = false
)
