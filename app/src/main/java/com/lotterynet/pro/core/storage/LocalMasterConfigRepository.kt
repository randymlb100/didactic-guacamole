package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

data class ReloadlySettings(
    val clientId: String = "",
    val clientSecret: String = "",
    val enabled: Boolean = false,
)

class LocalMasterConfigRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getReloadlySettings(): ReloadlySettings {
        return ReloadlySettings(
            clientId = prefs.getString(KEY_RLDLY_CLIENT_ID, "").orEmpty(),
            clientSecret = prefs.getString(KEY_RLDLY_CLIENT_SECRET, "").orEmpty(),
            enabled = prefs.getBoolean(KEY_RLDLY_ENABLED, false),
        )
    }

    fun saveReloadlyClientId(value: String): ReloadlySettings {
        prefs.edit { putString(KEY_RLDLY_CLIENT_ID, value.trim()) }
        return getReloadlySettings()
    }

    fun saveReloadlyClientSecret(value: String): ReloadlySettings {
        prefs.edit { putString(KEY_RLDLY_CLIENT_SECRET, value.trim()) }
        return getReloadlySettings()
    }

    fun saveReloadlyEnabled(enabled: Boolean): ReloadlySettings {
        prefs.edit { putBoolean(KEY_RLDLY_ENABLED, enabled) }
        return getReloadlySettings()
    }

    fun cacheLegacyValue(key: String, payloadJson: String?) {
        val payload = payloadJson?.takeIf { it.isNotBlank() } ?: return
        when (key) {
            LEGACY_RLDLY_CLIENT_ID -> runCatching { JSONObject("{\"value\":$payload}").optString("value") }
                .onSuccess { saveReloadlyClientId(it) }

            LEGACY_RLDLY_CLIENT_SECRET -> runCatching { JSONObject("{\"value\":$payload}").optString("value") }
                .onSuccess { saveReloadlyClientSecret(it) }

            LEGACY_RLDLY_ENABLED -> runCatching { JSONObject("{\"value\":$payload}").optBoolean("value", false) }
                .onSuccess { saveReloadlyEnabled(it) }
        }
    }

    fun exportLegacyValue(key: String): String? {
        val settings = getReloadlySettings()
        return when (key) {
            LEGACY_RLDLY_CLIENT_ID -> JSONObject.quote(settings.clientId)
            LEGACY_RLDLY_CLIENT_SECRET -> JSONObject.quote(settings.clientSecret)
            LEGACY_RLDLY_ENABLED -> if (settings.enabled) "true" else "false"
            else -> null
        }
    }

    companion object {
        const val LEGACY_RLDLY_CLIENT_ID = "sys_rldly_client_id"
        const val LEGACY_RLDLY_CLIENT_SECRET = "sys_rldly_client_secret"
        const val LEGACY_RLDLY_ENABLED = "sys_rldly_enabled"

        private const val PREFS_NAME = "lotterynet_master_config_v1"
        private const val KEY_RLDLY_CLIENT_ID = "reloadly_client_id"
        private const val KEY_RLDLY_CLIENT_SECRET = "reloadly_client_secret"
        private const val KEY_RLDLY_ENABLED = "reloadly_enabled"
    }
}
