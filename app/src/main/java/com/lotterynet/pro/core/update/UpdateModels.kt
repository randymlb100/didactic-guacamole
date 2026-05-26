package com.lotterynet.pro.core.update

import com.lotterynet.pro.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

data class OtaUpdateInfo(
    val updateAvailable: Boolean,
    val forceUpdate: Boolean = false,
    val versionCode: Int = 0,
    val versionName: String = "",
    val minimumVersion: Int = 1,
    val title: String = "Nueva actualizacion",
    val changelog: List<String> = emptyList(),
    val apkUrl: String = "",
    val apkSha256: String = "",
    val apkSizeBytes: Long = 0L,
    val cacheTtlSeconds: Long = DEFAULT_OTA_CACHE_TTL_SECONDS,
    val fetchedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val blocksCurrentBuild: Boolean
        get() = forceUpdate || minimumVersion > BuildConfig.VERSION_CODE

    val shouldInstall: Boolean
        get() = updateAvailable && (versionCode > BuildConfig.VERSION_CODE || blocksCurrentBuild)

    fun toJson(): JSONObject = JSONObject().apply {
        put("updateAvailable", updateAvailable)
        put("forceUpdate", forceUpdate)
        put("versionCode", versionCode)
        put("versionName", versionName)
        put("minimumVersion", minimumVersion)
        put("title", title)
        put("changelog", JSONArray(changelog))
        put("apkUrl", apkUrl)
        put("apkSha256", apkSha256)
        put("apkSizeBytes", apkSizeBytes)
        put("cacheTtlSeconds", cacheTtlSeconds)
        put("fetchedAtEpochMs", fetchedAtEpochMs)
    }

    companion object {
        fun fromJson(json: JSONObject): OtaUpdateInfo {
            val lines = json.optJSONArray("changelog")
                ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
                ?: emptyList()
            return OtaUpdateInfo(
                updateAvailable = json.optBoolean("updateAvailable", false),
                forceUpdate = json.optBoolean("forceUpdate", false),
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName"),
                minimumVersion = json.optInt("minimumVersion", 1),
                title = json.optString("title").ifBlank { "Nueva actualizacion" },
                changelog = lines,
                apkUrl = json.optString("apkUrl"),
                apkSha256 = json.optString("apkSha256"),
                apkSizeBytes = json.optLong("apkSizeBytes", 0L),
                cacheTtlSeconds = json.optLong("cacheTtlSeconds", DEFAULT_OTA_CACHE_TTL_SECONDS),
                fetchedAtEpochMs = json.optLong("fetchedAtEpochMs", System.currentTimeMillis()),
            )
        }
    }
}

data class OtaCheckRequest(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val packageName: String,
    val role: String?,
    val userId: String?,
    val username: String?,
)

data class OtaDownloadProgress(
    val percent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
    val status: OtaDownloadStatus,
    val message: String? = null,
)

enum class OtaDownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

sealed class OtaCheckResult {
    data class Success(val info: OtaUpdateInfo, val fromCache: Boolean = false) : OtaCheckResult()
    data class Offline(val cachedInfo: OtaUpdateInfo?, val message: String) : OtaCheckResult()
    data class Error(val cachedInfo: OtaUpdateInfo?, val message: String) : OtaCheckResult()
}

const val DEFAULT_OTA_CACHE_TTL_SECONDS: Long = 21_600L
