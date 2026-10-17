package com.fibernet.monitor.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.fibernet.monitor.data.model.AppUpdateInfo
import com.fibernet.monitor.data.model.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AppUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/arbdevai/fibernet-monitor/releases/latest"
    }

    /**
     * Memeriksa rilis terbaru dari GitHub API
     */
    suspend fun checkForUpdate(context: Context, currentVersion: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FiberNet-Monitor-Android")
                .get()
                .build()

            val responseBody = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to check updates (HTTP ${resp.code})"))
                }
                resp.body?.string() ?: throw Exception("Empty release metadata response")
            }

            val release = json.decodeFromString<GitHubRelease>(responseBody)
            val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext Result.failure(Exception("No APK asset found in latest GitHub release (${release.tagName})"))

            val isNewer = isNewerVersion(remoteTag = release.tagName, currentVersion = currentVersion)
            val localApk = getLocalApkFile(context, release.tagName)
            val isLocalReady = localApk.exists() && localApk.length() > (1024 * 1024) &&
                    (apkAsset.size == 0L || localApk.length() == apkAsset.size)

            val info = AppUpdateInfo(
                isUpdateAvailable = isNewer,
                latestVersion = release.tagName,
                currentVersion = currentVersion,
                releaseNotes = release.body.ifBlank { "Latest release ${release.tagName} is available." },
                downloadUrl = apkAsset.downloadUrl,
                apkSize = apkAsset.size,
                isLocalFileReady = isLocalReady,
                localFilePath = if (isLocalReady) localApk.absolutePath else null
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Membandingkan semantic version (misal: v1.1.5 vs 1.1.4)
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Lokasi penyimpanan file APK lokal
     */
    fun getLocalApkFile(context: Context, versionTag: String): File {
        val cleanTag = versionTag.trim().removePrefix("v").removePrefix("V")
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads").apply { mkdirs() }
        return File(downloadsDir, "fibernet-v$cleanTag.apk")
    }

    /**
     * Unduh file APK dengan pencegahan Anti Double-Download dan pemantauan progres real-time
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getLocalApkFile(context, updateInfo.latestVersion)

        // 1. Anti Double-Download: Cek apakah file sudah pernah diunduh dan valid
        if (targetFile.exists() && targetFile.length() > (1024 * 1024)) {
            if (updateInfo.apkSize == 0L || targetFile.length() == updateInfo.apkSize) {
                onProgress(100, targetFile.length(), targetFile.length())
                return@withContext Result.success(targetFile)
            }
        }

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        try {
            val req = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "FiberNet-Monitor-Android")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Failed to download APK (HTTP ${resp.code})")
                val body = resp.body ?: throw Exception("Empty download response body")
                val totalLength = if (body.contentLength() > 0) body.contentLength() else updateInfo.apkSize

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgressReport = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val percent = if (totalLength > 0) {
                                ((totalRead * 100) / totalLength).toInt().coerceIn(0, 100)
                            } else 0

                            if (percent != lastProgressReport) {
                                lastProgressReport = percent
                                onProgress(percent, totalRead, totalLength)
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                onProgress(100, targetFile.length(), targetFile.length())
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Downloaded file is empty or missing."))
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Pemasangan mandiri menggunakan Android FileProvider
     */
    fun installApk(context: Context, apkFile: File): Result<Boolean> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("APK installation file does not exist."))
            }

            // Validasi izin REQUEST_INSTALL_PACKAGES pada Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return Result.failure(Exception("Please grant permission to install applications from this app."))
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
