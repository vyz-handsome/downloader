package com.vyz.downloader.data

data class MediaItem(
    val url: String,
    val type: String,
    val quality: String,
    val title: String,
)

data class ResolveResult(
    val platform: String,
    val scraper: String,
    val title: String,
    val thumbnail: String,
    val type: String,
    val items: List<MediaItem>,
)

enum class SaveMode { DOWNLOAD, GALLERY, CUSTOM }

enum class Status { QUEUED, DOWNLOADING, DONE, FAILED }

data class DownloadTask(
    val id: String,
    val title: String,
    val thumbnail: String,
    val url: String,
    val type: String,
    val quality: String,
    val platform: String,
    val status: Status,
    val progress: Int, // 0..100, -1 = tidak diketahui
    val savedUri: String?,
    val error: String?,
    val createdAt: Long,
)
