package com.lotterynet.pro.core.update

import android.content.Context
import com.lotterynet.pro.BuildConfig
import com.lotterynet.pro.core.model.ActiveSession
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateRepository(
    context: Context,
    private val remote: SupabaseUpdateService = SupabaseUpdateService(),
    private val cache: UpdateCacheRepository = UpdateCacheRepository(context),
    private val logs: UpdateLogRepository = UpdateLogRepository(context),
) {
    suspend fun checkForUpdate(
        packageName: String,
        session: ActiveSession?,
        forceNetwork: Boolean = false,
    ): OtaCheckResult = withContext(Dispatchers.IO) {
        val cached = cache.getCachedUpdate()
        if (!forceNetwork && cached?.shouldInstall == true && cached.blocksCurrentBuild) {
            return@withContext OtaCheckResult.Success(cached, fromCache = true)
        }
        if (!forceNetwork && cache.shouldSkipNetworkCheck()) {
            return@withContext OtaCheckResult.Success(cached ?: OtaUpdateInfo(updateAvailable = false), fromCache = true)
        }

        val request = OtaCheckRequest(
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
            packageName = packageName,
            role = session?.role?.name?.lowercase(),
            userId = session?.userId,
            username = session?.username,
        )
        runCatching {
            val info = remote.checkUpdate(request)
            cache.markChecked()
            if (info.updateAvailable) cache.saveCachedUpdate(info) else cache.clearCachedUpdate()
            logs.append("check", if (info.updateAvailable) "Update disponible" else "Sin update", info.versionCode.takeIf { it > 0 })
            OtaCheckResult.Success(info)
        }.getOrElse { error ->
            val message = otaUserMessage(error)
            logs.append("error", message)
            if (error is UnknownHostException || error is SocketTimeoutException || error is IOException) {
                OtaCheckResult.Offline(cached, message)
            } else {
                OtaCheckResult.Error(cached, message)
            }
        }
    }

    suspend fun logRemote(
        event: String,
        packageName: String,
        session: ActiveSession?,
        targetVersionCode: Int?,
        status: String? = null,
        message: String? = null,
    ) = withContext(Dispatchers.IO) {
        val request = OtaCheckRequest(
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
            packageName = packageName,
            role = session?.role?.name?.lowercase(),
            userId = session?.userId,
            username = session?.username,
        )
        logs.append(event, message, targetVersionCode)
        remote.logEvent(event, request, targetVersionCode, status, message)
    }
}

internal fun otaUserMessage(error: Throwable): String {
    return when (error) {
        is UnknownHostException -> "Sin internet. Se usara la ultima configuracion disponible."
        is SocketTimeoutException -> "Internet lento. No se pudo revisar la actualizacion ahora."
        is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "Servidor OTA no disponible."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "No se pudo revisar actualizacion."
    }
}
