package com.vyz.downloader.server

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class ServerState { CHECKING, STARTING, READY, DOWN }

const val TERMUX_PACKAGE = "com.termux"
const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
const val TERMUX_FDROID_URL = "https://f-droid.org/packages/com.termux/"
const val SETUP_COMMAND =
    "curl -fsSL https://raw.githubusercontent.com/vyz-handsome/downloader/main/server/setup.sh | bash"

/** Mengelola server lokal di Termux: cek, nyalakan, tunggu siap. */
class ServerManager(private val ctx: Context) {
    private val _state = MutableStateFlow(ServerState.CHECKING)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    fun termuxInstalled(): Boolean = try {
        ctx.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun runCommandGranted(): Boolean =
        ContextCompat.checkSelfPermission(ctx, RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Pastikan server hidup. Return true kalau siap. */
    suspend fun ensure(): Boolean = withContext(Dispatchers.IO) {
        _state.value = ServerState.CHECKING
        if (ServerClient.ping()) {
            markReady()
            return@withContext true
        }
        _state.value = ServerState.STARTING
        if (!startViaTermux()) {
            _state.value = ServerState.DOWN
            return@withContext false
        }
        for (i in 0 until 45) {
            delay(1000)
            if (ServerClient.ping()) {
                markReady()
                return@withContext true
            }
        }
        _state.value = ServerState.DOWN
        false
    }

    fun markDown() {
        _state.value = ServerState.DOWN
    }

    private fun markReady() {
        _state.value = ServerState.READY
        KeeperService.start(ctx)
    }

    /** Minta Termux menjalankan start.sh lewat RUN_COMMAND (butuh allow-external-apps = true). */
    private fun startViaTermux(): Boolean {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/home/downloader-server/start.sh")
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home/downloader-server")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        return try {
            ctx.startService(intent)
            true
        } catch (e: Exception) {
            try {
                ContextCompat.startForegroundService(ctx, intent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
