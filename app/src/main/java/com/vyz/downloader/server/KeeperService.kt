package com.vyz.downloader.server

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.vyz.downloader.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mengirim detak ke server Termux tiap 5 detik selama app hidup.
 * Saat app dihapus dari recent -> server dimatikan. Kalau proses mati tiba-tiba,
 * detak berhenti dan server mati sendiri (watchdog di server.js).
 */
class KeeperService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive != true) {
            job = scope.launch {
                while (isActive) {
                    ServerClient.ping()
                    delay(5000)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        job?.cancel()
        val appCtx = applicationContext
        scope.launch {
            ServerClient.stop()
            appCtx.app.server.markDown()
            stopSelf()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, KeeperService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
