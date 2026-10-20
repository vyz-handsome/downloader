package com.vyz.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.vyz.downloader.data.Settings
import com.vyz.downloader.data.TaskStore
import com.vyz.downloader.server.ServerManager

const val CHANNEL_PROGRESS = "progress"
const val CHANNEL_DONE = "done"

class App : Application() {
    lateinit var settings: Settings
        private set
    lateinit var store: TaskStore
        private set
    lateinit var server: ServerManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        store = TaskStore(this)
        server = ServerManager(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Progres download", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Download selesai", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

val Context.app: App get() = applicationContext as App
