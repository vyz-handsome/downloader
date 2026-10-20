package com.vyz.downloader.data

import android.content.Context

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var saveMode: SaveMode
        get() = runCatching { SaveMode.valueOf(sp.getString("saveMode", "DOWNLOAD")!!) }
            .getOrDefault(SaveMode.DOWNLOAD)
        set(v) = sp.edit().putString("saveMode", v.name).apply()

    var customUri: String?
        get() = sp.getString("customUri", null)
        set(v) = sp.edit().putString("customUri", v).apply()

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit().putBoolean("setupDone", v).apply()
}
