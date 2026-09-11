package com.danielsalas.auto_music.player.cache

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object PlayerCache {
    private const val TAG = "PlayerCache"
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

    private fun safeCreateCache(dir: File, evictor: androidx.media3.datasource.cache.CacheEvictor, context: Context): SimpleCache? {
        return try {
            if (SimpleCache.isCacheFolderLocked(dir)) {
                Log.w(TAG, "Cache folder ${dir.name} is locked. Attempting recovery.")
                // If it's locked, we can't do much except try a different folder or wait.
                // In some cases, deleting a .lock file works, but Media3 handles this.
                // We'll try to use a slightly different name to avoid the lock.
                val recoveryDir = File(dir.parentFile, "${dir.name}_recovery_${System.currentTimeMillis()}")
                recoveryDir.mkdirs()
                return SimpleCache(recoveryDir, evictor, getDatabaseProvider(context))
            }
            SimpleCache(dir, evictor, getDatabaseProvider(context))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create cache in ${dir.absolutePath}: ${e.message}")
            null
        }
    }

    fun getInstance(context: Context): SimpleCache {
        synchronized(this) {
            if (cache == null) {
                val cacheDir = File(context.applicationContext.cacheDir, "exo_cache_v2")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                
                cache = safeCreateCache(cacheDir, LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024), context)
                    ?: safeCreateCache(File(context.applicationContext.cacheDir, "exo_cache_fallback"), LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024), context)
                    // If everything fails, we must throw or return a dummy that won't crash immediately but might fail to cache
                    ?: throw RuntimeException("Critical: Could not initialize Player Cache")
            }
            return cache!!
        }
    }

    fun getDownloadCache(context: Context): SimpleCache {
        synchronized(this) {
            if (downloadCache == null) {
                val appFilesDir = context.applicationContext.getExternalFilesDir(null)
                val cacheDir = if (appFilesDir != null) {
                    File(appFilesDir, "downloads_v2")
                } else {
                    File(context.applicationContext.filesDir, "downloads_v2")
                }
                
                if (!cacheDir.exists()) cacheDir.mkdirs()
                
                downloadCache = safeCreateCache(cacheDir, androidx.media3.datasource.cache.NoOpCacheEvictor(), context)
                    ?: safeCreateCache(File(context.applicationContext.filesDir, "downloads_fallback"), androidx.media3.datasource.cache.NoOpCacheEvictor(), context)
                    ?: throw RuntimeException("Critical: Could not initialize Download Cache")
            }
            return downloadCache!!
        }
    }

    fun setDownloadFolder(context: Context, path: String) {
        // No-op. Stability first.
    }
}
