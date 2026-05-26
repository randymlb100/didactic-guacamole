package com.lotterynet.pro.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.update.ApkDownloadManager
import com.lotterynet.pro.core.update.ApkInstaller
import com.lotterynet.pro.core.update.InstallOpenResult
import com.lotterynet.pro.core.update.OtaDownloadStatus
import com.lotterynet.pro.core.update.OtaUpdateInfo
import com.lotterynet.pro.core.update.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val downloader = ApkDownloadManager(application)
    private val installer = ApkInstaller(application)
    private val repository = UpdateRepository(application)
    private val sessions = LocalSessionRepository(application)
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun bind(info: OtaUpdateInfo) {
        if (_state.value == UpdateUiState.Idle) {
            _state.value = UpdateUiState.Available(info)
        }
    }

    fun dismiss(info: OtaUpdateInfo, onDismissed: () -> Unit) {
        if (info.blocksCurrentBuild) return
        viewModelScope.launch {
            repository.logRemote(
                event = "dismissed",
                packageName = getApplication<Application>().packageName,
                session = sessions.getActiveSession(),
                targetVersionCode = info.versionCode,
                status = "dismissed",
            )
            onDismissed()
        }
    }

    fun startDownload(info: OtaUpdateInfo) {
        viewModelScope.launch {
            repository.logRemote(
                event = "download_started",
                packageName = getApplication<Application>().packageName,
                session = sessions.getActiveSession(),
                targetVersionCode = info.versionCode,
                status = "started",
            )
            runCatching {
                val downloadInfo = resolveDownloadInfo(
                    current = info,
                    refreshed = repository.checkForUpdate(
                        packageName = getApplication<Application>().packageName,
                        session = sessions.getActiveSession(),
                        forceNetwork = true,
                    ),
                )
                val apk = downloader.downloadApk(
                    update = downloadInfo,
                    onProgress = { progress ->
                        _state.value = UpdateUiState.Downloading(
                            info = downloadInfo,
                            percent = progress.percent,
                            speedLabel = formatDownloadSpeed(progress.speedBytesPerSecond),
                            status = progress.status,
                        )
                    },
                )
                repository.logRemote(
                    event = "download_completed",
                    packageName = getApplication<Application>().packageName,
                    session = sessions.getActiveSession(),
                    targetVersionCode = downloadInfo.versionCode,
                    status = "completed",
                )
                when (val result = installer.validateAndOpenInstaller(downloadInfo, apk)) {
                    InstallOpenResult.Opened -> {
                        repository.logRemote(
                            event = "install_opened",
                            packageName = getApplication<Application>().packageName,
                            session = sessions.getActiveSession(),
                            targetVersionCode = downloadInfo.versionCode,
                            status = "opened",
                        )
                        _state.value = UpdateUiState.ReadyToInstall(downloadInfo, "Instalador abierto.")
                    }
                    is InstallOpenResult.PermissionRequired -> {
                        _state.value = UpdateUiState.Error(downloadInfo, result.message, canRetry = true)
                    }
                    is InstallOpenResult.Blocked -> {
                        _state.value = UpdateUiState.Error(downloadInfo, result.message, canRetry = true)
                    }
                }
            }.onFailure { error ->
                val message = error.message ?: "No se pudo descargar la actualizacion."
                repository.logRemote(
                    event = "error",
                    packageName = getApplication<Application>().packageName,
                    session = sessions.getActiveSession(),
                    targetVersionCode = info.versionCode,
                    status = "error",
                    message = message,
                )
                _state.value = UpdateUiState.Error(info, message, canRetry = true)
            }
        }
    }

    fun resetToAvailable(info: OtaUpdateInfo) {
        _state.value = UpdateUiState.Available(info)
    }
}
