package com.danielsalas.auto_music.data.local

import androidx.room.*
import com.danielsalas.auto_music.model.Playlist
import com.danielsalas.auto_music.model.PlaylistSongCrossRef
import com.danielsalas.auto_music.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Transaction
    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId 
        WHERE playlist_song_cross_ref.playlistId = :playlistId 
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>>

    @Query("SELECT MAX(position) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Query("UPDATE playlists SET lastPlayedSongId = :songId, lastPlayedPositionMs = :position WHERE id = :playlistId")
    suspend fun updatePlaylistPlaybackState(playlistId: Long, songId: String?, position: Long)

    @Query("UPDATE playlists SET isShuffle = :shuffle WHERE id = :playlistId")
    suspend fun updatePlaylistShuffle(playlistId: Long, shuffle: Boolean)

    @Query("UPDATE playlists SET isVolumeNormalized = :normalized WHERE id = :playlistId")
    suspend fun updatePlaylistNormalization(playlistId: Long, normalized: Boolean)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE songId = :songId")
    suspend fun getSongOccurrenceCount(songId: String): Int

    @Delete
    suspend fun deleteSong(song: Song)
    
    @Query("SELECT * FROM songs")
    suspend fun getAllSongsList(): List<Song>
    
    @Transaction
    suspend fun updateSongOrder(playlistId: Long, songIds: List<String>) {
        songIds.forEachIndexed { index, songId ->
            updateSongPosition(playlistId, songId, index)
        }
    }

    @Query("UPDATE playlist_song_cross_ref SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateSongPosition(playlistId: Long, songId: String, position: Int)
}
