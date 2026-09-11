package com.danielsalas.auto_music.player

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.danielsalas.auto_music.R

@OptIn(UnstableApi::class)
class ExoDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("ExoDownloadService", "Service Created")
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music downloads progress"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    override fun getDownloadManager(): DownloadManager = DownloadUtil.getInstance(this).downloadManager

    override fun getScheduler(): Scheduler? = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val activeDownload = downloads.find { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING }
            ?: downloads.firstOrNull()

        val title = if (activeDownload != null) {
            val songTitle = try {
                Util.fromUtf8Bytes(activeDownload.request.data)
            } catch (e: Exception) {
                "Song"
            }
            if (downloads.size > 1) {
                "$songTitle (+${downloads.size - 1})"
            } else {
                songTitle
            }
        } else {
            resources.getQuantityString(R.plurals.n_song, downloads.size, downloads.size)
        }
        
        return DownloadUtil.getInstance(this).downloadNotificationHelper.buildProgressNotification(
            this,
            R.drawable.download,
            null,
            title,
            downloads,
            notMetRequirements
        )
    }

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001
        const val JOB_ID = 1001
    }
}
