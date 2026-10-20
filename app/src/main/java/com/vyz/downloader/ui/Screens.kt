package com.vyz.downloader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vyz.downloader.data.DownloadTask
import com.vyz.downloader.data.SaveMode
import com.vyz.downloader.data.Status
import com.vyz.downloader.server.RUN_COMMAND_PERMISSION
import com.vyz.downloader.server.SETUP_COMMAND
import com.vyz.downloader.server.ServerState
import com.vyz.downloader.server.TERMUX_FDROID_URL
import dev.chrisbanes.haze.HazeState

// ---------------------------------------------------------------- util

private fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Downloader", text))
}

@Composable
private fun Title(text: String, sub: String? = null) {
    val g = LocalGlass.current
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(text, color = g.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = g.subtext, fontSize = 14.sp)
    }
}

@Composable
private fun Label(text: String, bold: Boolean = false, size: Int = 14, sub: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    val g = LocalGlass.current
    Text(
        text,
        color = if (sub) g.subtext else g.text,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------- root

@Composable
fun AppRoot(vm: MainViewModel) {
    val g = LocalGlass.current
    val haze = remember { HazeState() }
    val ctx = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(haze)

        if (!vm.setupDone) {
            OnboardingScreen(vm, haze)
        } else {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (vm.tab) {
                    Tab.HOME -> HomeScreen(vm, haze)
                    Tab.QUEUE -> QueueScreen(vm, haze)
                    Tab.SETTINGS -> SettingsScreen(vm, haze)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                NavBar(vm, haze)
            }
            vm.message?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2500)
                    vm.message = null
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 24.dp, end = 24.dp)
                ) {
                    GlassCard(haze, shape = RoundedCornerShape(20.dp)) {
                        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) { Label(msg, bold = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBar(vm: MainViewModel, haze: HazeState) {
    val g = LocalGlass.current
    val active = vm.tasks.collectAsState().value.count { it.status == Status.QUEUED || it.status == Status.DOWNLOADING }
    GlassCard(haze, Modifier.fillMaxWidth(), shape = RoundedCornerShape(36.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem("Beranda", Icons.Rounded.Home, vm.tab == Tab.HOME) { vm.tab = Tab.HOME }
            NavItem(if (active > 0) "Antrian ($active)" else "Unduhan", Icons.Rounded.Download, vm.tab == Tab.QUEUE) { vm.tab = Tab.QUEUE }
            NavItem("Pengaturan", Icons.Rounded.Settings, vm.tab == Tab.SETTINGS) { vm.tab = Tab.SETTINGS }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    val bg = if (selected) g.accent.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier
            .clip(PillShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) g.text else g.subtext, modifier = Modifier.size(22.dp))
        if (selected) {
            Spacer(Modifier.size(6.dp))
            Text(label, color = g.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- onboarding

@Composable
private fun OnboardingScreen(vm: MainViewModel, haze: HazeState) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(vm.runCommandGranted()) }
    val installed = vm.termuxInstalled()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title("Siapkan server", "Downloader butuh server kecil yang jalan di Termux (sekali saja).")

        StepCard(haze, "1", "Pasang Termux", if (installed) "Termux sudah terpasang" else "Pakai versi F-Droid, bukan Play Store.", installed) {
            if (!installed) GlassButton("Unduh Termux (F-Droid)", haze, icon = Icons.Rounded.Download, primary = true) { openUrl(ctx, TERMUX_FDROID_URL) }
        }

        StepCard(haze, "2", "Jalankan perintah di Termux", "Salin, buka Termux, tempel, lalu tekan Enter. Tunggu sampai muncul tulisan SELESAI.", false) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)).padding(12.dp)
            ) {
                Text(SETUP_COMMAND, color = androidx.compose.ui.graphics.Color(0xFFB7F7C8), fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(if (copied) "Tersalin" else "Salin perintah", haze, icon = Icons.Rounded.ContentPaste, primary = true) {
                    copyText(ctx, SETUP_COMMAND); copied = true
                }
                GlassButton("Buka Termux", haze, enabled = installed) {
                    ctx.packageManager.getLaunchIntentForPackage("com.termux")?.let { ctx.startActivity(it) }
                }
            }
        }

        StepCard(haze, "3", "Izinkan app menyalakan Termux", if (granted) "Izin sudah diberikan" else "Supaya server menyala otomatis tiap app dibuka.", granted) {
            if (!granted) GlassButton("Beri izin", haze, enabled = installed, primary = true) { permLauncher.launch(RUN_COMMAND_PERMISSION) }
        }

        StepCard(haze, "4", "Cek server", "Setelah langkah 2 selesai, tekan tombol di bawah.", false) {
            if (vm.setupBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = g.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Label("Menyalakan dan mengecek server...", sub = true)
                }
            } else {
                GlassButton("Saya sudah menjalankan perintah", haze, icon = Icons.Rounded.CheckCircle, primary = true) { vm.finishSetup() }
            }
            vm.setupMessage?.let { Text(it, color = g.danger, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepCard(haze: HazeState, num: String, title: String, desc: String, done: Boolean, body: @Composable () -> Unit) {
    val g = LocalGlass.current
    GlassCard(haze, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (done) "✓" else num,
                    color = if (done) g.success else g.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(10.dp))
                Label(title, bold = true, size = 17)
            }
            Label(desc, sub = true)
            body()
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun HomeScreen(vm: MainViewModel, haze: HazeState) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    val server by vm.serverState.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Title("Downloader", "Tempel link, pilih kualitas, unduh.") }

        if (server != ServerState.READY) {
            item {
                GlassCard(haze, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (server == ServerState.DOWN) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = g.danger)
                        } else {
                            CircularProgressIndicator(Modifier.size(22.dp), color = g.accent, strokeWidth = 2.dp)
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Label(if (server == ServerState.DOWN) "Server belum menyala" else "Menyalakan server di Termux...", bold = true)
                            if (server == ServerState.DOWN) Label("Pastikan Termux terpasang dan setup sudah dijalankan.", sub = true, size = 12)
                        }
                        if (server == ServerState.DOWN) {
                            GlassButton("Coba lagi", haze) { vm.restartServer() }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(haze, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = vm.link,
                        onValueChange = { vm.link = it; vm.clipboardHint = false },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://...", color = g.subtext) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f),
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f),
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedTextColor = g.text,
                            unfocusedTextColor = g.text,
                            cursorColor = g.accent,
                        ),
                    )
                    if (vm.clipboardHint) Label("Link dari clipboard ditempel otomatis", sub = true, size = 12)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Tempel", haze, icon = Icons.Rounded.ContentPaste) { vm.pasteNow(ctx) }
                        GlassButton("Cek link", haze, icon = Icons.Rounded.Search, primary = true, enabled = !vm.resolving && vm.link.isNotBlank()) { vm.resolve() }
                    }
                }
            }
        }

        if (vm.resolving) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = g.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Label("Mengambil link download...", sub = true)
                }
            }
        }

        vm.resolveError?.let { err ->
            item {
                GlassCard(haze, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = g.danger)
                            Spacer(Modifier.size(10.dp))
                            Label(err, bold = true)
                        }
                        vm.resolveTried.forEach { Label(it, sub = true, size = 11) }
                    }
                }
            }
        }

        vm.resolved?.let { r ->
            item {
                GlassCard(haze, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r.thumbnail.isNotBlank()) {
                                AsyncImage(
                                    model = r.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                                )
                                Spacer(Modifier.size(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Label(r.title.ifBlank { "(tanpa judul)" }, bold = true, size = 16, maxLines = 3)
                                Label("${r.platform} · ${r.items.size} pilihan", sub = true, size = 12)
                            }
                        }
                        if (r.items.size > 1) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Label("Pilih kualitas / format", sub = true)
                                Text(
                                    if (vm.selected.size == r.items.size) "Batal pilih semua" else "Pilih semua",
                                    color = g.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { vm.selectAll() },
                                )
                            }
                        }
                        r.items.forEachIndexed { i, item ->
                            val on = vm.selected.contains(i)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .background(if (on) g.accent.copy(alpha = 0.30f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f))
                                    .clickable { vm.toggle(i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (on) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                    null, tint = if (on) g.text else g.subtext, modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Label(
                                        listOf(item.title, item.quality).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "File ${i + 1}" },
                                        bold = true, maxLines = 2,
                                    )
                                    Label(item.type.uppercase(), sub = true, size = 11)
                                }
                            }
                        }
                        GlassButton(
                            "Download (${vm.selected.size})", haze,
                            icon = Icons.Rounded.Download, primary = true, enabled = vm.selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { vm.downloadSelected() }
                    }
                }
            }
        }

        if (vm.resolved == null && !vm.resolving && vm.resolveError == null) {
            item {
                Label(
                    "Didukung: TikTok, Instagram, Threads, Facebook, YouTube, Twitter/X, Pinterest, Spotify, SoundCloud, Apple Music, Bandcamp, Bilibili, Douyin. Kamu juga bisa Bagikan link dari app lain langsung ke Downloader.",
                    sub = true, size = 12,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- queue

@Composable
private fun QueueScreen(vm: MainViewModel, haze: HazeState) {
    val g = LocalGlass.current
    val tasks by vm.tasks.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    val active = tasks.filter { it.status == Status.QUEUED || it.status == Status.DOWNLOADING }
    val history = tasks.filter { it.status == Status.DONE || it.status == Status.FAILED }.reversed()
    val list = if (showHistory) history else active

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Title("Unduhan") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton("Antrian (${active.size})", haze, primary = !showHistory) { showHistory = false }
                GlassButton("Riwayat (${history.size})", haze, primary = showHistory) { showHistory = true }
                if (showHistory && history.isNotEmpty()) {
                    GlassButton("Bersihkan", haze, icon = Icons.Rounded.DeleteOutline) { vm.clearFinished() }
                }
            }
        }
        if (list.isEmpty()) {
            item { Label(if (showHistory) "Belum ada riwayat." else "Tidak ada unduhan yang berjalan.", sub = true) }
        }
        items(list, key = { it.id }) { t -> TaskCard(t, vm, haze) }
    }
}

@Composable
private fun TaskCard(t: DownloadTask, vm: MainViewModel, haze: HazeState) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    GlassCard(haze, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = t.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Label(t.title.ifBlank { "(tanpa judul)" }, bold = true, maxLines = 2)
                    val sub = when (t.status) {
                        Status.QUEUED -> "Menunggu antrian"
                        Status.DOWNLOADING -> if (t.progress >= 0) "Mengunduh ${t.progress}%" else "Mengunduh..."
                        Status.DONE -> "Selesai"
                        Status.FAILED -> "Gagal: ${t.error ?: "tidak diketahui"}"
                    }
                    Label(listOf(t.platform, t.quality, sub).filter { it.isNotBlank() }.joinToString(" · "), sub = true, size = 12, maxLines = 2)
                }
            }
            if (t.status == Status.DOWNLOADING) {
                if (t.progress >= 0) {
                    LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = g.accent, trackColor = g.glassTint)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = g.accent, trackColor = g.glassTint)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (t.status) {
                    Status.QUEUED, Status.DOWNLOADING ->
                        GlassButton("Batal", haze, icon = Icons.Rounded.Close) { vm.cancelTask(t.id) }

                    Status.DONE -> {
                        GlassButton("Buka", haze, icon = Icons.Rounded.FolderOpen) {
                            t.savedUri?.let { u ->
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(u))
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                        GlassButton("Hapus", haze, icon = Icons.Rounded.DeleteOutline) { vm.removeTask(t.id) }
                    }

                    Status.FAILED -> {
                        GlassButton("Ulangi", haze, icon = Icons.Rounded.Refresh, primary = true) { vm.retryTask(t.id) }
                        GlassButton("Hapus", haze, icon = Icons.Rounded.DeleteOutline) { vm.removeTask(t.id) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsScreen(vm: MainViewModel, haze: HazeState) {
    val g = LocalGlass.current
    val ctx = LocalContext.current
    val server by vm.serverState.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.updateCustomUri(uri.toString())
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title("Pengaturan")

        GlassCard(haze, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Lokasi simpan", bold = true, size = 17)
                SaveRow("Folder Download", "Download/Downloader", vm.saveMode == SaveMode.DOWNLOAD) { vm.updateSaveMode(SaveMode.DOWNLOAD) }
                SaveRow("Galeri / Musik", "Video ke Movies, foto ke Pictures, audio ke Music", vm.saveMode == SaveMode.GALLERY) { vm.updateSaveMode(SaveMode.GALLERY) }
                SaveRow(
                    "Folder pilihan sendiri",
                    vm.customUri?.let { Uri.parse(it).lastPathSegment ?: it } ?: "Belum dipilih",
                    vm.saveMode == SaveMode.CUSTOM,
                ) { if (vm.customUri != null) vm.updateSaveMode(SaveMode.CUSTOM) else picker.launch(null) }
                GlassButton("Pilih folder", haze, icon = Icons.Rounded.FolderOpen) { picker.launch(null) }
            }
        }

        GlassCard(haze, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("Server (Termux)", bold = true, size = 17)
                Label(
                    when (server) {
                        ServerState.READY -> "Menyala. Akan berhenti otomatis saat app ditutup."
                        ServerState.STARTING -> "Sedang dinyalakan..."
                        ServerState.CHECKING -> "Mengecek..."
                        ServerState.DOWN -> "Mati"
                    },
                    sub = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Nyalakan ulang", haze, icon = Icons.Rounded.Refresh) { vm.restartServer() }
                    GlassButton("Panduan setup", haze) { vm.reopenSetup() }
                }
            }
        }

        Label("Downloader 1.0.0", sub = true, size = 12)
    }
}

@Composable
private fun SaveRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) g.accent.copy(alpha = 0.30f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
            null, tint = if (selected) g.text else g.subtext, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Label(title, bold = true)
            Label(sub, sub = true, size = 12, maxLines = 2)
        }
    }
}
