package com.lotterynet.pro.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lotterynet.pro.BuildConfig
import java.io.File
import java.security.MessageDigest

class ApkInstaller(
    private val context: Context,
) {
    fun validateAndOpenInstaller(update: OtaUpdateInfo, apkFile: File): InstallOpenResult {
        val validation = validateApk(update, apkFile)
        if (validation != InstallValidationResult.Valid) {
            return InstallOpenResult.Blocked(validation.message)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return InstallOpenResult.PermissionRequired("Permite instalar actualizaciones de LotteryNet Pro y vuelve a tocar instalar.")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return InstallOpenResult.Opened
    }

    fun validateApk(update: OtaUpdateInfo, apkFile: File): InstallValidationResult {
        if (!update.apkUrl.startsWith("https://", ignoreCase = true)) {
            return InstallValidationResult.Invalid("URL de APK no segura.")
        }
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return InstallValidationResult.Invalid("APK no descargado.")
        }
        if (!isValidSha256(update.apkSha256)) {
            return InstallValidationResult.Invalid("Hash SHA-256 de APK invalido.")
        }
        val actualHash = apkFile.sha256Hex()
        if (!actualHash.equals(update.apkSha256, ignoreCase = true)) {
            return InstallValidationResult.Invalid("APK corrupta. Descarga de nuevo.")
        }
        val archiveInfo = archivePackageInfo(apkFile)
            ?: return InstallValidationResult.Invalid("APK no valida.")
        if (archiveInfo.packageName != context.packageName) {
            return InstallValidationResult.Invalid("APK no pertenece a LotteryNet Pro.")
        }
        if (archiveInfo.versionCodeCompat() <= BuildConfig.VERSION_CODE) {
            return InstallValidationResult.Invalid("No se permite instalar una version anterior.")
        }
        val current = currentPackageInfo()
            ?: return InstallValidationResult.Invalid("No se pudo validar firma instalada.")
        if (!sameSigner(current, archiveInfo)) {
            return InstallValidationResult.Invalid("La firma del APK no coincide.")
        }
        return InstallValidationResult.Valid
    }

    private fun currentPackageInfo(): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()
    }

    private fun archivePackageInfo(apkFile: File): PackageInfo? {
        return runCatching {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)?.apply {
                applicationInfo?.sourceDir = apkFile.absolutePath
                applicationInfo?.publicSourceDir = apkFile.absolutePath
            }
        }.getOrNull()
    }
}

sealed class InstallValidationResult(val message: String) {
    data object Valid : InstallValidationResult("")
    data class Invalid(private val reason: String) : InstallValidationResult(reason)
}

sealed class InstallOpenResult {
    data object Opened : InstallOpenResult()
    data class PermissionRequired(val message: String) : InstallOpenResult()
    data class Blocked(val message: String) : InstallOpenResult()
}

internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun isValidSha256(value: String): Boolean = value.matches(Regex("^[A-Fa-f0-9]{64}$"))

internal fun PackageInfo.versionCodeCompat(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
}

private fun sameSigner(current: PackageInfo, candidate: PackageInfo): Boolean {
    val currentHashes = current.signatureHashes()
    val candidateHashes = candidate.signatureHashes()
    return currentHashes.isNotEmpty() && currentHashes == candidateHashes
}

private fun PackageInfo.signatureHashes(): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.apkContentsSigners?.toList().orEmpty()
    } else {
        @Suppress("DEPRECATION")
        signatures?.toList().orEmpty()
    }
    return signatures.map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    }.toSet()
}
