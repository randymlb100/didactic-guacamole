package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.SportsbookFeatureConfig
import com.lotterynet.pro.core.model.SportsbookMarketKey
import com.lotterynet.pro.core.model.UserRole
import org.json.JSONArray
import org.json.JSONObject

data class ReloadlySettings(
    val clientId: String = "",
    val clientSecret: String = "",
    val enabled: Boolean = false,
)

data class MasterSportsbookSettings(
    val enabled: Boolean = false,
    val adminEnabled: Boolean = false,
    val supervisorEnabled: Boolean = false,
    val cashierEnabled: Boolean = false,
    val allowedActorKeys: Set<String> = emptySet(),
    val cashierAdminKeys: Set<String> = emptySet(),
    val enabledMarkets: Set<SportsbookMarketKey> = setOf(
        SportsbookMarketKey.MONEYLINE,
        SportsbookMarketKey.RUNLINE,
        SportsbookMarketKey.SPREAD,
        SportsbookMarketKey.TOTAL,
    ),
    val updatedAtEpochMs: Long = 0L,
    val updatedBy: String = "",
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

    fun getSportsbookSettings(): MasterSportsbookSettings {
        return decodeMasterSportsbookSettings(prefs.getString(KEY_SPORTSBOOK_SETTINGS, null))
    }

    fun saveSportsbookSettings(settings: MasterSportsbookSettings): MasterSportsbookSettings {
        val normalized = normalizeMasterSportsbookSettings(settings)
        prefs.edit { putString(KEY_SPORTSBOOK_SETTINGS, encodeMasterSportsbookSettings(normalized)) }
        return normalized
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

            SPORTSBOOK_REMOTE_KEY -> runCatching { decodeMasterSportsbookSettings(payload) }
                .onSuccess { saveSportsbookSettings(it) }
        }
    }

    fun exportLegacyValue(key: String): String? {
        val settings = getReloadlySettings()
        return when (key) {
            LEGACY_RLDLY_CLIENT_ID -> JSONObject.quote(settings.clientId)
            LEGACY_RLDLY_CLIENT_SECRET -> JSONObject.quote(settings.clientSecret)
            LEGACY_RLDLY_ENABLED -> if (settings.enabled) "true" else "false"
            SPORTSBOOK_REMOTE_KEY -> encodeMasterSportsbookSettings(getSportsbookSettings())
            else -> null
        }
    }

    companion object {
        const val LEGACY_RLDLY_CLIENT_ID = "sys_rldly_client_id"
        const val LEGACY_RLDLY_CLIENT_SECRET = "sys_rldly_client_secret"
        const val LEGACY_RLDLY_ENABLED = "sys_rldly_enabled"
        const val SPORTSBOOK_REMOTE_KEY = "sportsbook:global"

        private const val PREFS_NAME = "lotterynet_master_config_v1"
        private const val KEY_RLDLY_CLIENT_ID = "reloadly_client_id"
        private const val KEY_RLDLY_CLIENT_SECRET = "reloadly_client_secret"
        private const val KEY_RLDLY_ENABLED = "reloadly_enabled"
        private const val KEY_SPORTSBOOK_SETTINGS = "sportsbook_settings"
    }
}

fun sportsbookRemoteKey(): String = LocalMasterConfigRepository.SPORTSBOOK_REMOTE_KEY

fun normalizeMasterSportsbookSettings(settings: MasterSportsbookSettings): MasterSportsbookSettings {
    return settings.copy(
        enabledMarkets = settings.enabledMarkets.ifEmpty {
            setOf(
                SportsbookMarketKey.MONEYLINE,
                SportsbookMarketKey.RUNLINE,
                SportsbookMarketKey.SPREAD,
                SportsbookMarketKey.TOTAL,
            )
        },
        updatedBy = settings.updatedBy.trim(),
        allowedActorKeys = settings.allowedActorKeys.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
        cashierAdminKeys = settings.cashierAdminKeys.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
    )
}

fun MasterSportsbookSettings.toFeatureConfig(): SportsbookFeatureConfig {
    val roles = buildSet {
        if (adminEnabled) add(UserRole.ADMIN)
        if (supervisorEnabled) add(UserRole.SUPERVISOR)
        if (cashierEnabled) add(UserRole.CASHIER)
    }
    return SportsbookFeatureConfig(
        enabled = enabled,
        allowedRoles = roles,
        allowedActorKeys = allowedActorKeys,
        cashierAdminKeys = cashierAdminKeys,
        enabledMarkets = enabledMarkets,
    )
}

fun encodeMasterSportsbookSettings(settings: MasterSportsbookSettings): String {
    val normalized = normalizeMasterSportsbookSettings(settings)
    return JSONObject().apply {
        put("configured", true)
        put("enabled", normalized.enabled)
        put("adminEnabled", normalized.adminEnabled)
        put("supervisorEnabled", normalized.supervisorEnabled)
        put("cashierEnabled", normalized.cashierEnabled)
        put("allowedActorKeys", JSONArray().apply {
            normalized.allowedActorKeys.sorted().forEach { put(it) }
        })
        put("cashierAdminKeys", JSONArray().apply {
            normalized.cashierAdminKeys.sorted().forEach { put(it) }
        })
        put("enabledMarkets", JSONArray().apply {
            normalized.enabledMarkets.forEach { put(it.wireValue) }
        })
        put("updatedAt", normalized.updatedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis())
        put("updatedBy", normalized.updatedBy)
    }.toString()
}

fun decodeMasterSportsbookSettings(raw: String?): MasterSportsbookSettings {
    if (raw.isNullOrBlank()) return MasterSportsbookSettings()
    return runCatching {
        val json = JSONObject(raw)
        val configured = json.optBoolean("configured", false)
        val decodedMarkets = buildSet {
            val array = json.optJSONArray("enabledMarkets")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val rawMarket = array.optString(index)
                    SportsbookMarketKey.entries.firstOrNull { it.wireValue == rawMarket }?.let(::add)
                }
            }
        }
        if (!configured) {
            MasterSportsbookSettings()
        } else {
            normalizeMasterSportsbookSettings(
                MasterSportsbookSettings(
                    enabled = json.optBoolean("enabled", false),
                    adminEnabled = json.optBoolean("adminEnabled", false),
                    supervisorEnabled = json.optBoolean("supervisorEnabled", false),
                    cashierEnabled = json.optBoolean("cashierEnabled", false),
                    allowedActorKeys = json.optStringSet("allowedActorKeys"),
                    cashierAdminKeys = json.optStringSet("cashierAdminKeys"),
                    enabledMarkets = decodedMarkets,
                    updatedAtEpochMs = json.optLong("updatedAt", 0L),
                    updatedBy = json.optString("updatedBy"),
                ),
            )
        }
    }.getOrDefault(MasterSportsbookSettings())
}

private fun JSONObject.optStringSet(key: String): Set<String> {
    val array = optJSONArray(key) ?: return emptySet()
    return buildSet {
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
