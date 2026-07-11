package com.example.auto_music.player.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object PlayerCache {
    private var cache: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        synchronized(this) {
            if (cache == null) {
                val cacheDir = File(context.cacheDir, "exo_cache")
                val cacheEvictor = LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024) // 200MB
                val databaseProvider = StandaloneDatabaseProvider(context)
                cache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
            }
            return cache!!
        }
    }
}
