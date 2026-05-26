package com.lotterynet.pro.ui.update

import com.lotterynet.pro.core.update.OtaDownloadStatus
import com.lotterynet.pro.core.update.OtaCheckResult
import com.lotterynet.pro.core.update.OtaUpdateInfo

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data class Available(val info: OtaUpdateInfo) : UpdateUiState()
    data class Downloading(
        val info: OtaUpdateInfo,
        val percent: Int,
        val speedLabel: String,
        val status: OtaDownloadStatus,
    ) : UpdateUiState()
    data class ReadyToInstall(val info: OtaUpdateInfo, val message: String) : UpdateUiState()
    data class Error(val info: OtaUpdateInfo, val message: String, val canRetry: Boolean) : UpdateUiState()
    data class Offline(val message: String) : UpdateUiState()
}

internal fun shouldShowLaterButton(info: OtaUpdateInfo): Boolean = !info.blocksCurrentBuild

internal fun formatApkSize(bytes: Long): String {
    if (bytes <= 0L) return "Tamano pendiente"
    val mb = bytes / 1024.0 / 1024.0
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

internal fun formatDownloadSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "Calculando velocidad"
    val kb = bytesPerSecond / 1024.0
    return if (kb >= 1024.0) {
        String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.0f KB/s", kb)
    }
}

internal fun resolveDownloadInfo(current: OtaUpdateInfo, refreshed: OtaCheckResult): OtaUpdateInfo {
    val fresh = (refreshed as? OtaCheckResult.Success)?.info ?: return current
    return if (fresh.shouldInstall && fresh.versionCode >= current.versionCode && fresh.apkUrl.isNotBlank()) {
        fresh
    } else {
        current
    }
}
