package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

data class ManualDisabledLotteryConfig(
    val ids: Set<String> = emptySet(),
    val date: String? = null,
    val permanent: Boolean = false,
)

class LocalAdminLotteryConfigRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(AdminLotteryStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getManualDisabledLotteryIds(): Set<String> {
        val config = getManualDisabledLotteryConfig()
        if (!isManualLotteryCloseDateActive(config.date, todayOperationDateKey(), config.permanent)) {
            clearManualDisabledLotteryIds()
            return emptySet()
        }
        return config.ids
    }

    fun getManualDisabledLotteryConfig(): ManualDisabledLotteryConfig {
        val raw = prefs.getString(AdminLotteryStorageKeys.DISABLED_IDS_KEY, null)
        val ids = decodeManualDisabledLotteryIds(raw)
        return ManualDisabledLotteryConfig(
            ids = ids,
            date = prefs.getString(AdminLotteryStorageKeys.DISABLED_DATE_KEY, null),
            permanent = prefs.getBoolean(AdminLotteryStorageKeys.DISABLED_PERMANENT_KEY, false),
        )
    }

    fun setLotteryDisabled(lotteryId: String, disabled: Boolean): Set<String> {
        return setLotteryDisabled(lotteryId, disabled, permanent = false)
    }

    fun setLotteryDisabled(lotteryId: String, disabled: Boolean, permanent: Boolean): Set<String> {
        val updated = getManualDisabledLotteryIds().toMutableSet().apply {
            if (disabled) add(lotteryId) else remove(lotteryId)
        }
        saveManualDisabledLotteryIds(updated, permanent)
        return updated
    }

    fun clearManualDisabledLotteryIds(): Set<String> {
        saveManualDisabledLotteryIds(emptySet())
        return emptySet()
    }

    fun saveManualDisabledLotteryIds(ids: Set<String>) {
        saveManualDisabledLotteryIds(ids, permanent = prefs.getBoolean(AdminLotteryStorageKeys.DISABLED_PERMANENT_KEY, false))
    }

    fun saveManualDisabledLotteryIds(ids: Set<String>, permanent: Boolean) {
        saveManualDisabledLotteryConfig(
            ManualDisabledLotteryConfig(
                ids = ids,
                date = todayOperationDateKey(),
                permanent = permanent,
            ),
        )
    }

    fun saveManualDisabledLotteryConfig(config: ManualDisabledLotteryConfig) {
        val normalized = config.ids.map(String::trim).filter(String::isNotEmpty).distinct().sorted()
        prefs.edit {
            if (normalized.isEmpty()) {
                remove(AdminLotteryStorageKeys.DISABLED_IDS_KEY)
                remove(AdminLotteryStorageKeys.DISABLED_DATE_KEY)
                remove(AdminLotteryStorageKeys.DISABLED_PERMANENT_KEY)
            } else {
                putString(AdminLotteryStorageKeys.DISABLED_IDS_KEY, JSONArray(normalized).toString())
                putString(AdminLotteryStorageKeys.DISABLED_DATE_KEY, config.date ?: todayOperationDateKey())
                putBoolean(AdminLotteryStorageKeys.DISABLED_PERMANENT_KEY, config.permanent)
            }
        }
    }

    fun exportManualDisabledLotteryConfig(): String {
        val config = getManualDisabledLotteryConfig()
        return encodeManualDisabledLotteryConfig(
            ids = config.ids,
            date = config.date ?: todayOperationDateKey(),
            permanent = config.permanent,
        )
    }

    fun cacheManualDisabledLotteryConfig(rawPayload: String?): Set<String> {
        val config = decodeManualDisabledLotteryConfig(rawPayload)
        if (!isManualLotteryCloseDateActive(config.date, todayOperationDateKey(), config.permanent)) {
            clearManualDisabledLotteryIds()
            return emptySet()
        }
        saveManualDisabledLotteryConfig(config)
        return getManualDisabledLotteryIds()
    }

    fun getSystemModeConfig(): AdminSystemModeConfig {
        return normalizeAdminSystemModeConfig(
            AdminSystemModeConfig(
                posLiteEnabled = prefs.getBoolean(AdminLotteryStorageKeys.SYSTEM_POS_LITE_KEY, false),
                lotteryModeEnabled = prefs.getBoolean(AdminLotteryStorageKeys.SYSTEM_LOTTERY_MODE_KEY, true),
                pickModeEnabled = prefs.getBoolean(AdminLotteryStorageKeys.SYSTEM_PICK_MODE_KEY, false),
                cashierPickEnabled = prefs.getBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_PICK_KEY, false),
                cashierModeEnabled = prefs.getBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_MODE_KEY, false),
                cashierLotteryModeEnabled = prefs.getBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_LOTTERY_MODE_KEY, true),
                cashierPickModeEnabled = prefs.getBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_PICK_MODE_KEY, false),
            ),
        )
    }

    fun saveSystemModeConfig(config: AdminSystemModeConfig): AdminSystemModeConfig {
        val normalized = normalizeAdminSystemModeConfig(config)
        prefs.edit {
            putBoolean(AdminLotteryStorageKeys.SYSTEM_POS_LITE_KEY, normalized.posLiteEnabled)
            remove(AdminLotteryStorageKeys.SYSTEM_FUTURE_SALE_KEY)
            putBoolean(AdminLotteryStorageKeys.SYSTEM_LOTTERY_MODE_KEY, normalized.lotteryModeEnabled)
            putBoolean(AdminLotteryStorageKeys.SYSTEM_PICK_MODE_KEY, normalized.pickModeEnabled)
            putBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_PICK_KEY, normalized.cashierPickEnabled)
            putBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_MODE_KEY, normalized.cashierModeEnabled)
            putBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_LOTTERY_MODE_KEY, normalized.cashierLotteryModeEnabled)
            putBoolean(AdminLotteryStorageKeys.SYSTEM_CASHIER_PICK_MODE_KEY, normalized.cashierPickModeEnabled)
        }
        return normalized
    }

    private fun todayOperationDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
    }
}

private fun decodeManualDisabledLotteryIds(raw: String?): Set<String> {
    raw ?: return emptySet()
    return runCatching {
        val array = JSONArray(raw)
        buildSet {
            for (index in 0 until array.length()) {
                array.opt(index)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }.getOrDefault(emptySet())
}

internal fun isManualLotteryCloseDateActive(savedDate: String?, today: String, permanent: Boolean = false): Boolean {
    return permanent || (savedDate != null && savedDate == today)
}

fun manualDisabledLotteriesRemoteKey(ownerKey: String): String = "manual_disabled_lotteries:${ownerKey.trim()}"

fun encodeManualDisabledLotteryConfig(
    ids: Set<String>,
    date: String?,
    permanent: Boolean,
): String {
    val normalized = ids.map(String::trim).filter(String::isNotEmpty).distinct().sorted()
    return JSONObject().apply {
        put("ids", JSONArray(normalized))
        put("date", date.orEmpty())
        put("permanent", permanent)
        put("updatedAt", System.currentTimeMillis())
    }.toString()
}

fun decodeManualDisabledLotteryConfig(raw: String?): ManualDisabledLotteryConfig {
    if (raw.isNullOrBlank()) return ManualDisabledLotteryConfig()
    return runCatching {
        val json = JSONObject(raw)
        val idsArray = json.optJSONArray("ids") ?: JSONArray()
        val ids = buildSet {
            for (index in 0 until idsArray.length()) {
                idsArray.opt(index)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
        ManualDisabledLotteryConfig(
            ids = ids,
            date = json.optString("date").ifBlank { null },
            permanent = json.optBoolean("permanent", false),
        )
    }.getOrDefault(ManualDisabledLotteryConfig())
}

data class AdminSystemModeConfig(
    val posLiteEnabled: Boolean = false,
    val lotteryModeEnabled: Boolean = true,
    val pickModeEnabled: Boolean = false,
    val cashierPickEnabled: Boolean = false,
    val cashierModeEnabled: Boolean = false,
    val cashierLotteryModeEnabled: Boolean = true,
    val cashierPickModeEnabled: Boolean = false,
) {
    val pickAndLotteryEnabled: Boolean
        get() = lotteryModeEnabled && pickModeEnabled

    val cashierPickAndLotteryEnabled: Boolean
        get() = cashierLotteryModeEnabled && cashierPickModeEnabled
}

fun normalizeAdminSystemModeConfig(config: AdminSystemModeConfig): AdminSystemModeConfig {
    var normalized = config
    if (!normalized.lotteryModeEnabled && !normalized.pickModeEnabled) {
        normalized = normalized.copy(lotteryModeEnabled = true)
    }
    if (!normalized.cashierLotteryModeEnabled && !normalized.cashierPickModeEnabled) {
        normalized = normalized.copy(cashierLotteryModeEnabled = true)
    }
    return if (!normalized.cashierModeEnabled) {
        normalized.copy(
            cashierPickEnabled = false,
            cashierLotteryModeEnabled = true,
            cashierPickModeEnabled = false,
        )
    } else {
        normalized.copy(cashierPickEnabled = normalized.cashierPickModeEnabled)
    }
}

fun effectiveAdminSystemModeConfigForRole(
    config: AdminSystemModeConfig,
    role: UserRole,
): AdminSystemModeConfig {
    val normalized = normalizeAdminSystemModeConfig(config)
    return if (role == UserRole.CASHIER) {
        if (normalized.cashierModeEnabled) {
            normalized.copy(
                lotteryModeEnabled = normalized.cashierLotteryModeEnabled,
                pickModeEnabled = normalized.cashierPickModeEnabled,
            )
        } else {
            normalized.copy(lotteryModeEnabled = true, pickModeEnabled = false)
        }
    } else {
        normalized
    }
}

fun normalizeCashierSystemModeOverride(raw: String?): String? {
    return when (raw?.trim()?.lowercase(Locale.US).orEmpty()) {
        "lottery", "loteria", "solo_loteria", "solo-loteria" -> "lottery"
        "pick", "solo_pick", "solo-pick" -> "pick"
        "both", "ambos", "loteria_pick", "lottery_pick" -> "both"
        "inherit", "admin", "default", "" -> "lottery"
        else -> "lottery"
    }
}

fun cashierSystemModeOverrideLabel(raw: String?): String {
    return when (normalizeCashierSystemModeOverride(raw)) {
        "lottery" -> "Solo Lotería"
        "pick" -> "Solo Pick"
        "both" -> "Lotería + Pick"
        else -> "Solo Lotería"
    }
}

fun applyCashierSystemModeOverride(
    config: AdminSystemModeConfig,
    override: String?,
): AdminSystemModeConfig {
    val normalized = normalizeAdminSystemModeConfig(config)
    return when (normalizeCashierSystemModeOverride(override)) {
        "lottery" -> normalized.copy(lotteryModeEnabled = true, pickModeEnabled = false)
        "pick" -> normalized.copy(lotteryModeEnabled = false, pickModeEnabled = true)
        "both" -> normalized.copy(lotteryModeEnabled = true, pickModeEnabled = true)
        else -> effectiveAdminSystemModeConfigForRole(normalized, UserRole.CASHIER)
    }
}

fun effectiveSystemModeConfigForSession(
    config: AdminSystemModeConfig,
    session: ActiveSession,
    accounts: List<UserAccount>,
): AdminSystemModeConfig {
    if (session.role != UserRole.CASHIER) {
        return effectiveAdminSystemModeConfigForRole(config, session.role)
    }
    val cashier = accounts.firstOrNull { account ->
        account.role == UserRole.CASHIER &&
            (account.id.equals(session.userId, ignoreCase = true) || account.user.equals(session.username, ignoreCase = true))
    }
    return applyCashierSystemModeOverride(config, cashier?.systemModeOverride)
}

fun systemModeRemoteKey(ownerKey: String): String = "system_modes:${ownerKey.trim()}"

fun encodeAdminSystemModeConfig(config: AdminSystemModeConfig): String {
    val normalized = normalizeAdminSystemModeConfig(config)
    return JSONObject().apply {
        put("posLiteEnabled", normalized.posLiteEnabled)
        put("lotteryModeEnabled", normalized.lotteryModeEnabled)
        put("pickModeEnabled", normalized.pickModeEnabled)
        put("cashierPickEnabled", normalized.cashierPickEnabled)
        put("cashierModeEnabled", normalized.cashierModeEnabled)
        put("cashierLotteryModeEnabled", normalized.cashierLotteryModeEnabled)
        put("cashierPickModeEnabled", normalized.cashierPickModeEnabled)
        put("updatedAt", System.currentTimeMillis())
    }.toString()
}

fun decodeAdminSystemModeConfig(raw: String?): AdminSystemModeConfig {
    if (raw.isNullOrBlank()) return AdminSystemModeConfig()
    return runCatching {
        val json = JSONObject(raw)
        normalizeAdminSystemModeConfig(
            AdminSystemModeConfig(
                posLiteEnabled = json.optBoolean("posLiteEnabled", false),
                lotteryModeEnabled = json.optBoolean("lotteryModeEnabled", true),
                pickModeEnabled = json.optBoolean("pickModeEnabled", false),
                cashierPickEnabled = json.optBoolean("cashierPickEnabled", false),
                cashierModeEnabled = json.optBoolean("cashierModeEnabled", json.optBoolean("cashierPickEnabled", false)),
                cashierLotteryModeEnabled = json.optBoolean("cashierLotteryModeEnabled", true),
                cashierPickModeEnabled = json.optBoolean("cashierPickModeEnabled", json.optBoolean("cashierPickEnabled", false)),
            ),
        )
    }.getOrDefault(AdminSystemModeConfig())
}
