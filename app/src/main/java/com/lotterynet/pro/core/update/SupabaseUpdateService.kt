package com.lotterynet.pro.core.update

import org.json.JSONArray
import org.json.JSONObject

class SupabaseUpdateService(
    private val service: SupabaseService = SupabaseService(),
) {
    fun checkUpdate(request: OtaCheckRequest): OtaUpdateInfo {
        val response = service.invoke(
            "ota-check-update",
            JSONObject().apply {
                put("currentVersionCode", request.currentVersionCode)
                put("currentVersionName", request.currentVersionName)
                put("packageName", request.packageName)
                put("role", request.role)
                put("userId", request.userId)
                put("username", request.username)
            },
        )
        return parseUpdateResponse(response)
    }

    fun logEvent(
        event: String,
        request: OtaCheckRequest,
        targetVersionCode: Int? = null,
        status: String? = null,
        message: String? = null,
    ) {
        runCatching {
            service.invoke(
                "ota-log-event",
                JSONObject().apply {
                    put("event", event)
                    put("currentVersionCode", request.currentVersionCode)
                    put("currentVersionName", request.currentVersionName)
                    put("packageName", request.packageName)
                    put("role", request.role)
                    put("userId", request.userId)
                    put("username", request.username)
                    put("targetVersionCode", targetVersionCode)
                    put("status", status)
                    put("message", message)
                },
            )
        }
    }
}

internal fun parseUpdateResponse(json: JSONObject): OtaUpdateInfo {
    val changelog = json.optJSONArray("changelog").toStringList()
    return OtaUpdateInfo(
        updateAvailable = json.optBoolean("updateAvailable", false),
        forceUpdate = json.optBoolean("forceUpdate", false),
        versionCode = json.optInt("versionCode", 0),
        versionName = json.optString("versionName"),
        minimumVersion = json.optInt("minimumVersion", 1),
        title = json.optString("title").ifBlank { "Nueva actualizacion" },
        changelog = changelog,
        apkUrl = json.optString("apkUrl"),
        apkSha256 = json.optString("apkSha256"),
        apkSizeBytes = json.optLong("apkSizeBytes", 0L),
        cacheTtlSeconds = json.optLong("cacheTtlSeconds", DEFAULT_OTA_CACHE_TTL_SECONDS),
        fetchedAtEpochMs = System.currentTimeMillis(),
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optString(index).trim().takeIf { it.isNotBlank() }
    }
}
