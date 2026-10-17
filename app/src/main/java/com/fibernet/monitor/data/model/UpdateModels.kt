package com.fibernet.monitor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubAsset(
    @SerialName("name") val name: String = "",
    @SerialName("size") val size: Long = 0L,
    @SerialName("browser_download_url") val downloadUrl: String = ""
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList()
)

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersion: String = "",
    val currentVersion: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val apkSize: Long = 0L,
    val isLocalFileReady: Boolean = false,
    val localFilePath: String? = null
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateDownloadState()
    object UpToDate : UpdateDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val updateInfo: AppUpdateInfo, val apkPath: String) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}
