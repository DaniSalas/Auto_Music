package com.danielsalas.auto_music.player.cache

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
    private var downloadCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null

    fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        synchronized(this) {
            if (databaseProvider == null) {
                databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            }
            return databaseProvider!!
        }
    }

    fun getInstance(context: Context): SimpleCache {
        synchronized(this) {
            if (cache == null) {
                val cacheDir = File(context.applicationContext.cacheDir, "exo_cache")
                val cacheEvictor = LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024) // 200MB
                cache = SimpleCache(cacheDir, cacheEvictor, getDatabaseProvider(context))
            }
            return cache!!
        }
    }

    fun getDownloadCache(context: Context): SimpleCache {
        synchronized(this) {
            if (downloadCache == null) {
                val cacheDir = File(context.applicationContext.getExternalFilesDir(null), "downloads")
                // No evictor for downloads to keep them forever until manually deleted
                downloadCache = SimpleCache(cacheDir, androidx.media3.datasource.cache.NoOpCacheEvictor(), getDatabaseProvider(context))
            }
            return downloadCache!!
        }
    }
}
