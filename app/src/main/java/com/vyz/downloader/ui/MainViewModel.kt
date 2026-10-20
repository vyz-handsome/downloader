package com.vyz.downloader.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vyz.downloader.app
import com.vyz.downloader.data.DownloadTask
import com.vyz.downloader.data.ResolveResult
import com.vyz.downloader.data.SaveMode
import com.vyz.downloader.data.Status
import com.vyz.downloader.download.DownloadService
import com.vyz.downloader.server.ResolveOutcome
import com.vyz.downloader.server.ServerClient
import com.vyz.downloader.server.ServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Tab { HOME, QUEUE, SETTINGS }

private val LINK_REGEX = Regex("https?://\\S+")
private val KNOWN_HOSTS = listOf(
    "tiktok.com", "instagram.com", "instagr.am", "threads.net", "threads.com", "facebook.com", "fb.watch", "fb.com",
    "youtube.com", "youtu.be", "twitter.com", "x.com", "pinterest.", "pin.it", "spotify.com", "spotify.link",
    "soundcloud.com", "music.apple.com", "bandcamp.com", "bilibili.com", "bilibili.tv", "b23.tv", "douyin.com",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app

    var tab by mutableStateOf(Tab.HOME)
    var link by mutableStateOf("")
    var resolving by mutableStateOf(false)
    var resolveError by mutableStateOf<String?>(null)
    var resolveTried by mutableStateOf<List<String>>(emptyList())
    var resolved by mutableStateOf<ResolveResult?>(null)
    var selected by mutableStateOf(setOf<Int>())
    var clipboardHint by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    var saveMode by mutableStateOf(app.settings.saveMode)
        private set
    var customUri by mutableStateOf(app.settings.customUri)
        private set
    var setupDone by mutableStateOf(app.settings.setupDone)
        private set
    var setupBusy by mutableStateOf(false)
        private set
    var setupMessage by mutableStateOf<String?>(null)
        private set

    val serverState = app.server.state
    val tasks = app.store.tasks

    private var lastClip: String? = null

    fun termuxInstalled() = app.server.termuxInstalled()
    fun runCommandGranted() = app.server.runCommandGranted()

    // ---------- server ----------

    fun checkServer() {
        if (!setupDone) return
        viewModelScope.launch { app.server.ensure() }
    }

    fun finishSetup() {
        viewModelScope.launch {
            setupBusy = true
            setupMessage = null
            val ok = app.server.ensure()
            if (ok) {
                app.settings.setupDone = true
                setupDone = true
            } else {
                setupMessage = when {
                    !app.server.termuxInstalled() -> "Termux belum terpasang."
                    !app.server.runCommandGranted() -> "Izin untuk menyalakan Termux belum diberikan. Tekan tombol izin di langkah 3."
                    else -> "Server belum merespons. Pastikan perintah di Termux sudah selesai tanpa error, lalu coba lagi."
                }
            }
            setupBusy = false
        }
    }

    fun reopenSetup() {
        app.settings.setupDone = false
        setupDone = false
        setupMessage = null
    }

    fun restartServer() {
        viewModelScope.launch { app.server.ensure() }
    }

    // ---------- resolve ----------

    fun resolve() {
        val url = LINK_REGEX.find(link)?.value
        if (url == null) {
            resolveError = "Tempel link yang diawali http:// atau https://"
            resolveTried = emptyList()
            return
        }
        link = url
        resolving = true
        resolveError = null
        resolveTried = emptyList()
        resolved = null
        viewModelScope.launch {
            if (app.server.state.value != ServerState.READY && !app.server.ensure()) {
                resolveError = "Server Termux belum menyala. Cek status server di Pengaturan."
                resolving = false
                return@launch
            }
            when (val out = withContext(Dispatchers.IO) { ServerClient.resolve(url) }) {
                is ResolveOutcome.Success -> {
                    resolved = out.result
                    selected = if (out.result.items.isNotEmpty()) setOf(0) else emptySet()
                }

                is ResolveOutcome.Failure -> {
                    resolveError = out.message
                    resolveTried = out.tried
                }
            }
            resolving = false
        }
    }

    fun toggle(i: Int) {
        selected = if (selected.contains(i)) selected - i else selected + i
    }

    fun selectAll() {
        val r = resolved ?: return
        selected = if (selected.size == r.items.size) emptySet() else r.items.indices.toSet()
    }

    fun downloadSelected() {
        val r = resolved ?: return
        if (selected.isEmpty()) return
        if (saveMode == SaveMode.CUSTOM && customUri == null) {
            message = "Pilih folder tujuan dulu di Pengaturan."
            return
        }
        val now = System.currentTimeMillis()
        val list = selected.sorted().map { i ->
            val it = r.items[i]
            DownloadTask(
                id = UUID.randomUUID().toString(),
                title = it.title.ifBlank { r.title },
                thumbnail = r.thumbnail,
                url = it.url,
                type = it.type,
                quality = it.quality,
                platform = r.platform,
                status = Status.QUEUED,
                progress = 0,
                savedUri = null,
                error = null,
                createdAt = now,
            )
        }
        app.store.add(list)
        DownloadService.start(getApplication())
        message = "${list.size} file masuk antrian"
        resolved = null
        link = ""
        selected = emptySet()
    }

    // ---------- tugas ----------

    fun cancelTask(id: String) = app.store.cancel(id)

    fun retryTask(id: String) {
        app.store.retry(id)
        DownloadService.start(getApplication())
    }

    fun removeTask(id: String) = app.store.remove(id)
    fun clearFinished() = app.store.clearFinished()

    // ---------- link masuk ----------

    fun handleShared(text: String?) {
        val url = text?.let { LINK_REGEX.find(it)?.value } ?: return
        tab = Tab.HOME
        link = url
        clipboardHint = false
        resolve()
    }

    fun checkClipboard(ctx: Context) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        val text = try {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
        } catch (e: Exception) {
            null
        } ?: return
        val url = LINK_REGEX.find(text)?.value ?: return
        if (url == lastClip) return
        if (KNOWN_HOSTS.none { url.contains(it, ignoreCase = true) }) return
        lastClip = url
        if (link.isBlank() && resolved == null && !resolving) {
            link = url
            clipboardHint = true
        }
    }

    fun pasteNow(ctx: Context) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
        if (!text.isNullOrBlank()) {
            link = text.trim()
            clipboardHint = false
        }
    }

    // ---------- pengaturan ----------

    fun updateSaveMode(mode: SaveMode) {
        app.settings.saveMode = mode
        saveMode = mode
    }

    fun updateCustomUri(uri: String) {
        app.settings.customUri = uri
        customUri = uri
        updateSaveMode(SaveMode.CUSTOM)
    }
}
