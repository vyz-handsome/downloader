package com.vyz.downloader.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.vyz.downloader.data.DownloadTask
import com.vyz.downloader.data.SaveMode
import com.vyz.downloader.data.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class CancelledException : Exception("Dibatalkan")

private class Dest(
    val uri: Uri,
    val out: OutputStream,
    val finish: () -> Unit,
    val abort: () -> Unit,
)

object DownloadEngine {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** Mengunduh satu file dan menyimpannya sesuai pengaturan. Return Uri hasil (string). */
    fun download(
        ctx: Context,
        task: DownloadTask,
        settings: Settings,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ): String {
        val req = Request.Builder().url(task.url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Respons kosong")
            val total = body.contentLength()
            val mime = pickMime(resp.header("Content-Type"), task)
            val name = headerFilename(resp.header("Content-Disposition"))
                ?.takeIf { task.type.equals("file", ignoreCase = true) }
                ?: buildName(task, mime)
            val dest = openDestination(ctx, settings, name, mime)
            try {
                dest.out.use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPct = -2
                    body.byteStream().use { input ->
                        while (true) {
                            if (isCancelled()) throw CancelledException()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            val pct = if (total > 0) ((read * 100) / total).toInt() else -1
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
                dest.finish()
                return dest.uri.toString()
            } catch (e: Exception) {
                dest.abort()
                throw e
            }
        }
    }

    private fun pickMime(contentType: String?, task: DownloadTask): String {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (ct != null && (ct.startsWith("video/") || ct.startsWith("audio/") || ct.startsWith("image/"))) return ct
        val ext = task.url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (ext.length in 2..4) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return when (task.type.lowercase()) {
            "audio" -> "audio/mpeg"
            "image", "photo" -> "image/jpeg"
            "file" -> "application/octet-stream"
            else -> "video/mp4"
        }
    }

    private fun buildName(task: DownloadTask, mime: String): String {
        val clean = { s: String -> s.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), " ").replace(Regex("\\s+"), " ").trim() }
        var base = clean(task.title).take(80)
        if (base.isBlank()) base = "download"
        val q = clean(task.quality).take(20)
        if (q.isNotBlank()) base = "$base ($q)"
        val urlExt = task.url.substringBefore('?').substringAfterLast('.', "").lowercase()
        val ext = if (mime == "application/octet-stream") {
            if (urlExt.length in 2..5 && urlExt.all { it.isLetterOrDigit() }) urlExt else "bin"
        } else {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        }
        return "$base.$ext"
    }

    private fun headerFilename(cd: String?): String? {
        if (cd == null) return null
        val m = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd) ?: return null
        val raw = runCatching { java.net.URLDecoder.decode(m.groupValues[1].trim(), "UTF-8") }.getOrNull() ?: return null
        val clean = raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), " ").trim()
        return clean.ifBlank { null }
    }

    private fun openDestination(ctx: Context, settings: Settings, name: String, mime: String): Dest {
        return when (settings.saveMode) {
            SaveMode.DOWNLOAD ->
                mediaStore(ctx, MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/Downloader", name, mime)

            SaveMode.GALLERY -> when {
                mime.startsWith("video/") -> mediaStore(
                    ctx, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "Movies/Downloader", name, mime,
                )

                mime.startsWith("image/") -> mediaStore(
                    ctx, MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "Pictures/Downloader", name, mime,
                )

                mime.startsWith("audio/") -> mediaStore(
                    ctx, MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "Music/Downloader", name, mime,
                )

                else -> mediaStore(ctx, MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/Downloader", name, mime)
            }

            SaveMode.CUSTOM -> {
                val tree = settings.customUri ?: throw IOException("Folder tujuan belum dipilih")
                val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(tree))
                    ?: throw IOException("Folder tujuan tidak bisa dibuka")
                val file = dir.createFile(mime, name) ?: throw IOException("Gagal membuat file di folder tujuan")
                val out = ctx.contentResolver.openOutputStream(file.uri) ?: throw IOException("Gagal menulis file")
                Dest(file.uri, out, {}, { runCatching { file.delete() } })
            }
        }
    }

    private fun mediaStore(ctx: Context, collection: Uri, relPath: String, name: String, mime: String): Dest {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IOException("Gagal membuat file")
        val out = resolver.openOutputStream(uri) ?: throw IOException("Gagal menulis file")
        return Dest(
            uri, out,
            finish = {
                val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, v, null, null)
            },
            abort = { runCatching { resolver.delete(uri, null, null) } },
        )
    }
}
