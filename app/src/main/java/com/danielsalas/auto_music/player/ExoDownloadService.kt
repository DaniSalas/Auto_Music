package com.danielsalas.auto_music.player

import android.app.Notification
import android.content.Context
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
    1000L,
    CHANNEL_ID,
    R.string.downloading,
    0
) {
    override fun getDownloadManager(): DownloadManager = DownloadUtil.getInstance(this).downloadManager

    override fun getScheduler(): Scheduler? = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val title = if (downloads.size == 1) {
            Util.fromUtf8Bytes(downloads[0].request.data)
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
