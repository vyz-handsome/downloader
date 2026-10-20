package com.vyz.downloader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Antrian + riwayat download. Urutan di list = urutan dibuat (terlama dulu). */
class TaskStore(ctx: Context) {
    private val file = File(ctx.filesDir, "tasks.json")
    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    fun add(list: List<DownloadTask>) {
        _tasks.value = _tasks.value + list
        persist()
    }

    @Synchronized
    fun claimNext(): DownloadTask? {
        val next = _tasks.value.firstOrNull { it.status == Status.QUEUED } ?: return null
        val claimed = next.copy(status = Status.DOWNLOADING, progress = 0, error = null)
        replace(claimed)
        persist()
        return claimed
    }

    @Synchronized
    fun hasQueued(): Boolean = _tasks.value.any { it.status == Status.QUEUED }

    @Synchronized
    fun setProgress(id: String, progress: Int) {
        _tasks.value = _tasks.value.map { if (it.id == id) it.copy(progress = progress) else it }
    }

    @Synchronized
    fun finish(id: String, uri: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(status = Status.DONE, progress = 100, savedUri = uri, error = null) else it
        }
        persist()
    }

    @Synchronized
    fun fail(id: String, message: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(status = Status.FAILED, error = message) else it
        }
        persist()
    }

    @Synchronized
    fun cancel(id: String) {
        val t = _tasks.value.firstOrNull { it.id == id } ?: return
        if (t.status == Status.DOWNLOADING) {
            cancelled.add(id)
        } else if (t.status == Status.QUEUED) {
            fail(id, "Dibatalkan")
        }
    }

    fun isCancelled(id: String): Boolean = cancelled.contains(id)

    fun clearCancelled(id: String) {
        cancelled.remove(id)
    }

    @Synchronized
    fun retry(id: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id && it.status == Status.FAILED) it.copy(status = Status.QUEUED, progress = 0, error = null) else it
        }
        persist()
    }

    @Synchronized
    fun remove(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id && it.status != Status.DOWNLOADING }
        persist()
    }

    @Synchronized
    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.status == Status.QUEUED || it.status == Status.DOWNLOADING }
        persist()
    }

    private fun replace(t: DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == t.id) t else it }
    }

    private fun persist() {
        runCatching {
            val arr = JSONArray()
            _tasks.value.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id).put("title", t.title).put("thumbnail", t.thumbnail)
                        .put("url", t.url).put("type", t.type).put("quality", t.quality)
                        .put("platform", t.platform).put("status", t.status.name)
                        .put("progress", t.progress).put("savedUri", t.savedUri ?: JSONObject.NULL)
                        .put("error", t.error ?: JSONObject.NULL).put("createdAt", t.createdAt)
                )
            }
            file.writeText(arr.toString())
        }
    }

    private fun load(): List<DownloadTask> = runCatching {
        if (!file.exists()) return@runCatching emptyList<DownloadTask>()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            var status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.FAILED)
            var error = if (o.isNull("error")) null else o.getString("error")
            if (status == Status.DOWNLOADING) {
                status = Status.FAILED
                error = "Terputus"
            }
            DownloadTask(
                id = o.getString("id"),
                title = o.optString("title"),
                thumbnail = o.optString("thumbnail"),
                url = o.getString("url"),
                type = o.optString("type", "video"),
                quality = o.optString("quality"),
                platform = o.optString("platform"),
                status = status,
                progress = o.optInt("progress", 0),
                savedUri = if (o.isNull("savedUri")) null else o.getString("savedUri"),
                error = error,
                createdAt = o.optLong("createdAt"),
            )
        }
    }.getOrDefault(emptyList())
}
