package com.lotterynet.pro.core.update

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ApkDownloadManager(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun downloadApk(
        update: OtaUpdateInfo,
        onProgress: (OtaDownloadProgress) -> Unit,
        retryAttempts: Int = 2,
    ): File {
        var lastError: Throwable? = null
        repeat(retryAttempts + 1) { attempt ->
            runCatching {
                return executeDownload(update, onProgress, attempt)
            }.onFailure { error ->
                lastError = error
                onProgress(
                    OtaDownloadProgress(
                        percent = 0,
                        bytesDownloaded = 0L,
                        totalBytes = update.apkSizeBytes,
                        speedBytesPerSecond = 0L,
                        status = OtaDownloadStatus.FAILED,
                        message = error.message,
                    ),
                )
                delay(800L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("No se pudo descargar la actualizacion.")
    }

    private suspend fun executeDownload(
        update: OtaUpdateInfo,
        onProgress: (OtaDownloadProgress) -> Unit,
        attempt: Int,
    ): File {
        require(update.apkUrl.startsWith("https://", ignoreCase = true)) {
            "URL de APK no segura."
        }
        val targetFile = otaTargetFile(update, attempt)
        if (targetFile.exists()) targetFile.delete()
        targetFile.parentFile?.mkdirs()

        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("LotteryNet Pro ${update.versionName}")
            .setDescription("Descargando actualizacion segura")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationUri(Uri.fromFile(targetFile))
        val downloadId = downloadManager.enqueue(request)
        onProgress(OtaDownloadProgress(0, 0, update.apkSizeBytes, 0, OtaDownloadStatus.QUEUED))

        var lastBytes = 0L
        var lastTimestamp = System.currentTimeMillis()
        val queuedAt = lastTimestamp
        while (true) {
            delay(500L)
            val snapshot = query(downloadId)
            val now = System.currentTimeMillis()
            val elapsedSeconds = ((now - lastTimestamp).coerceAtLeast(1L)) / 1000.0
            val speed = ((snapshot.bytesDownloaded - lastBytes) / elapsedSeconds).toLong().coerceAtLeast(0L)
            lastBytes = snapshot.bytesDownloaded
            lastTimestamp = now
            onProgress(snapshot.copy(speedBytesPerSecond = speed))
            when (snapshot.status) {
                OtaDownloadStatus.COMPLETED -> return targetFile
                OtaDownloadStatus.FAILED -> {
                    downloadManager.remove(downloadId)
                    throw IllegalStateException(snapshot.message ?: "Descarga fallida.")
                }
                OtaDownloadStatus.QUEUED -> {
                    if (isQueuedDownloadStale(snapshot.status, snapshot.bytesDownloaded, now - queuedAt)) {
                        downloadManager.remove(downloadId)
                        return downloadDirectly(update, targetFile, onProgress)
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun downloadDirectly(
        update: OtaUpdateInfo,
        targetFile: File,
        onProgress: (OtaDownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (targetFile.exists()) targetFile.delete()
        targetFile.parentFile?.mkdirs()
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Servidor OTA rechazo la descarga (${response.code}).")
            }
            val body = response.body ?: throw IOException("APK sin contenido.")
            val total = body.contentLength().takeIf { it > 0L } ?: update.apkSizeBytes
            var downloaded = 0L
            var lastBytes = 0L
            var lastTimestamp = System.currentTimeMillis()
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastTimestamp >= 500L) {
                            val elapsedSeconds = ((now - lastTimestamp).coerceAtLeast(1L)) / 1000.0
                            val speed = ((downloaded - lastBytes) / elapsedSeconds).toLong().coerceAtLeast(0L)
                            val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                            onProgress(
                                OtaDownloadProgress(
                                    percent = percent,
                                    bytesDownloaded = downloaded,
                                    totalBytes = total,
                                    speedBytesPerSecond = speed,
                                    status = OtaDownloadStatus.DOWNLOADING,
                                ),
                            )
                            lastBytes = downloaded
                            lastTimestamp = now
                        }
                    }
                }
            }
            val finalTotal = total.takeIf { it > 0L } ?: downloaded
            onProgress(
                OtaDownloadProgress(
                    percent = 100,
                    bytesDownloaded = downloaded,
                    totalBytes = finalTotal,
                    speedBytesPerSecond = 0L,
                    status = OtaDownloadStatus.COMPLETED,
                ),
            )
        }
        targetFile
    }

    private fun query(downloadId: Long): OtaDownloadProgress {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return OtaDownloadProgress(0, 0, 0, 0, OtaDownloadStatus.FAILED, "No se pudo consultar descarga.")
        cursor.use {
            if (!it.moveToFirst()) {
                return OtaDownloadProgress(0, 0, 0, 0, OtaDownloadStatus.FAILED, "Descarga no encontrada.")
            }
            val statusCode = it.long(DownloadManager.COLUMN_STATUS)
            val downloaded = it.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR).coerceAtLeast(0L)
            val total = it.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).coerceAtLeast(0L)
            val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
            return OtaDownloadProgress(
                percent = percent,
                bytesDownloaded = downloaded,
                totalBytes = total,
                speedBytesPerSecond = 0L,
                status = when (statusCode.toInt()) {
                    DownloadManager.STATUS_SUCCESSFUL -> OtaDownloadStatus.COMPLETED
                    DownloadManager.STATUS_FAILED -> OtaDownloadStatus.FAILED
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_PENDING -> OtaDownloadStatus.QUEUED
                    else -> OtaDownloadStatus.DOWNLOADING
                },
                message = if (statusCode.toInt() == DownloadManager.STATUS_FAILED) {
                    "No se pudo descargar el APK."
                } else {
                    null
                },
            )
        }
    }

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndex(column)
        return if (index >= 0) getLong(index) else 0L
    }

    private fun otaTargetFile(update: OtaUpdateInfo, attempt: Int): File {
        val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.cacheDir, "ota_apks")
        val suffix = if (attempt == 0) "" else "-retry$attempt"
        return File(dir, "lotterynet-${update.versionCode}$suffix.apk")
    }
}

internal const val OTA_QUEUE_STALL_TIMEOUT_MS: Long = 20_000L

internal fun isQueuedDownloadStale(
    status: OtaDownloadStatus,
    bytesDownloaded: Long,
    queuedForMs: Long,
): Boolean {
    return status == OtaDownloadStatus.QUEUED &&
        bytesDownloaded <= 0L &&
        queuedForMs >= OTA_QUEUE_STALL_TIMEOUT_MS
}
