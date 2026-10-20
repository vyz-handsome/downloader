package com.vyz.downloader.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vyz.downloader.CHANNEL_DONE
import com.vyz.downloader.CHANNEL_PROGRESS
import com.vyz.downloader.MainActivity
import com.vyz.downloader.R
import com.vyz.downloader.app
import com.vyz.downloader.data.DownloadTask
import com.vyz.downloader.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Foreground service yang memproses antrian download (2 file sekaligus). */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIF_ID, progressNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (!running) {
            running = true
            scope.launch {
                val done = AtomicInteger(0)
                val failed = AtomicInteger(0)
                try {
                    pump(done, failed)
                } finally {
                    running = false
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    if (done.get() + failed.get() > 0) notifyDone(done.get(), failed.get())
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private suspend fun pump(done: AtomicInteger, failed: AtomicInteger) {
        val store = app.store
        do {
            coroutineScope {
                repeat(2) {
                    launch {
                        while (true) {
                            val task = store.claimNext() ?: break
                            runTask(task, done, failed)
                        }
                    }
                }
            }
        } while (store.hasQueued())
    }

    private fun runTask(task: DownloadTask, done: AtomicInteger, failed: AtomicInteger) {
        val store = app.store
        try {
            val uri = DownloadEngine.download(
                ctx = applicationContext,
                task = task,
                settings = app.settings,
                isCancelled = { store.isCancelled(task.id) },
                onProgress = { p ->
                    store.setProgress(task.id, p)
                    updateProgressNotification()
                },
            )
            store.finish(task.id, uri)
            done.incrementAndGet()
        } catch (e: CancelledException) {
            store.fail(task.id, "Dibatalkan")
            failed.incrementAndGet()
        } catch (e: Exception) {
            store.fail(task.id, e.message ?: "Gagal mengunduh")
            failed.incrementAndGet()
        } finally {
            store.clearCancelled(task.id)
            updateProgressNotification()
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(): Notification {
        val active = app.store.tasks.value.filter { it.status == Status.DOWNLOADING }
        val queued = app.store.tasks.value.count { it.status == Status.QUEUED }
        val known = active.filter { it.progress >= 0 }
        val avg = if (known.isEmpty()) 0 else known.sumOf { it.progress } / known.size
        val text = buildString {
            append("Mengunduh ${active.size} file")
            if (queued > 0) append(", $queued antri")
        }
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloader")
            .setContentText(text)
            .setProgress(100, avg, known.isEmpty())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private var lastNotify = 0L

    private fun updateProgressNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 700) return
        lastNotify = now
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, progressNotification())
    }

    private fun notifyDone(ok: Int, bad: Int) {
        val text = buildString {
            if (ok > 0) append("$ok file selesai")
            if (bad > 0) {
                if (isNotEmpty()) append(", ")
                append("$bad gagal")
            }
        }
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloader")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_ID, n)
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val DONE_ID = 1002

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        }
    }
}
