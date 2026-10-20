package com.vyz.downloader.server

import com.vyz.downloader.data.MediaItem
import com.vyz.downloader.data.ResolveResult
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ResolveOutcome {
    data class Success(val result: ResolveResult) : ResolveOutcome()
    data class Failure(val message: String, val tried: List<String>) : ResolveOutcome()
}

/** Klien HTTP ke server lokal di Termux (127.0.0.1:8787). Semua fungsi memblokir, panggil dari IO. */
object ServerClient {
    private const val BASE = "http://127.0.0.1:8787"

    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build()

    fun ping(): Boolean = try {
        http.newCall(Request.Builder().url("$BASE/ping").build()).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }

    fun stop() {
        try {
            http.newCall(Request.Builder().url("$BASE/stop").post("".toRequestBody()).build()).execute().close()
        } catch (_: Exception) {
        }
    }

    fun resolve(link: String): ResolveOutcome {
        return try {
            val url = "$BASE/resolve".toHttpUrl().newBuilder().addQueryParameter("url", link).build()
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val o = JSONObject(body)
                if (!o.optBoolean("ok", false)) {
                    val tried = mutableListOf<String>()
                    o.optJSONArray("tried")?.let { a -> for (i in 0 until a.length()) tried.add(a.getString(i)) }
                    return ResolveOutcome.Failure(o.optString("message", "Gagal memproses link."), tried)
                }
                val r = o.getJSONObject("result")
                val arr = r.getJSONArray("items")
                val items = (0 until arr.length()).map { i ->
                    val it = arr.getJSONObject(i)
                    MediaItem(
                        url = it.getString("url"),
                        type = it.optString("type", "video"),
                        quality = it.optString("quality"),
                        title = it.optString("title"),
                    )
                }
                ResolveOutcome.Success(
                    ResolveResult(
                        platform = o.optString("platform"),
                        scraper = o.optString("scraper"),
                        title = r.optString("title"),
                        thumbnail = r.optString("thumbnail"),
                        type = r.optString("type", "video"),
                        items = items,
                    )
                )
            }
        } catch (e: Exception) {
            ResolveOutcome.Failure("Server tidak merespons: ${e.message ?: "kesalahan jaringan"}", emptyList())
        }
    }
}
