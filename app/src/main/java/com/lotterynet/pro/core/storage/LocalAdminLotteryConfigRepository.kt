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
                blockedSalePlays = decodeBlockedSalePlays(
                    prefs.getString(AdminLotteryStorageKeys.SYSTEM_BLOCKED_PLAYS_KEY, null),
                ),
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
            if (normalized.blockedSalePlays.isEmpty()) {
                remove(AdminLotteryStorageKeys.SYSTEM_BLOCKED_PLAYS_KEY)
            } else {
                putString(
                    AdminLotteryStorageKeys.SYSTEM_BLOCKED_PLAYS_KEY,
                    encodeBlockedSalePlaysArray(normalized.blockedSalePlays).toString(),
                )
            }
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

data class AdminBlockedSalePlay(
    val playType: String,
    val number: String,
)

data class AdminSystemModeConfig(
    val posLiteEnabled: Boolean = false,
    val lotteryModeEnabled: Boolean = true,
    val pickModeEnabled: Boolean = false,
    val cashierPickEnabled: Boolean = false,
    val cashierModeEnabled: Boolean = false,
    val cashierLotteryModeEnabled: Boolean = true,
    val cashierPickModeEnabled: Boolean = false,
    val blockedSalePlays: Set<AdminBlockedSalePlay> = emptySet(),
) {
    val pickAndLotteryEnabled: Boolean
        get() = lotteryModeEnabled && pickModeEnabled

    val cashierPickAndLotteryEnabled: Boolean
        get() = cashierLotteryModeEnabled && cashierPickModeEnabled
}

fun normalizeAdminSystemModeConfig(config: AdminSystemModeConfig): AdminSystemModeConfig {
    var normalized = config.copy(blockedSalePlays = normalizeBlockedSalePlays(config.blockedSalePlays))
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

fun normalizeBlockedSalePlays(plays: Set<AdminBlockedSalePlay>): Set<AdminBlockedSalePlay> {
    return plays.mapNotNull { normalizeBlockedSalePlay(it.playType, it.number) }
        .sortedWith(compareBy<AdminBlockedSalePlay> { it.playType }.thenBy { it.number })
        .toSet()
}

fun normalizeBlockedSalePlay(playType: String, number: String): AdminBlockedSalePlay? {
    val type = salePlayExactTypeKey(playType) ?: return null
    val digits = number.filter(Char::isDigit)
    val normalizedNumber = when (type) {
        "Q" -> digits.takeIf { it.length == 2 }
        "P" -> digits.takeIf { it.length == 4 }
        "SP" -> digits.takeIf { it.length == 4 }?.let { "${it.take(2)}-${it.drop(2)}" }
        "T" -> digits.takeIf { it.length == 6 }
        "P3", "P3BOX" -> digits.takeIf { it.length == 3 }
        "P4", "P4BOX" -> digits.takeIf { it.length == 4 }
        else -> null
    } ?: return null
    return AdminBlockedSalePlay(type, normalizedNumber)
}

fun salePlayExactTypeKey(playType: String): String? {
    return when (playType.trim().uppercase(Locale.US)) {
        "Q", "QUINIELA" -> "Q"
        "P", "PALE", "PALÉ" -> "P"
        "SP", "SUPERPALE", "SUPER_PALE", "SUPER PALÉ", "SUPER PALE" -> "SP"
        "T", "TRIPLETA" -> "T"
        "P3", "PICK3", "PICK_3", "PICK 3", "P3S", "P3STRAIGHT" -> "P3"
        "P3BOX", "PICK3BOX", "PICK_3_BOX", "PICK 3 BOX" -> "P3BOX"
        "P4", "PICK4", "PICK_4", "PICK 4", "P4S", "P4STRAIGHT" -> "P4"
        "P4BOX", "PICK4BOX", "PICK_4_BOX", "PICK 4 BOX" -> "P4BOX"
        else -> null
    }
}

fun salePlayTypeBlockLabel(playType: String): String {
    return when (salePlayExactTypeKey(playType) ?: playType.trim().uppercase(Locale.US)) {
        "Q" -> "Quiniela"
        "P" -> "Palé"
        "SP" -> "Super Palé"
        "T" -> "Tripleta"
        "P3" -> "Pick 3 Straight"
        "P3BOX" -> "Pick 3 Box"
        "P4" -> "Pick 4 Straight"
        "P4BOX" -> "Pick 4 Box"
        else -> "Jugada"
    }
}

fun blockedSalePlayLabel(play: AdminBlockedSalePlay): String {
    val displayNumber = if (play.playType == "SP") {
        play.number.replace("-", "/")
    } else {
        play.number
    }
    return "${salePlayTypeBlockLabel(play.playType)} $displayNumber"
}

fun isSalePlayBlocked(playType: String, number: String, config: AdminSystemModeConfig): Boolean {
    val play = normalizeBlockedSalePlay(playType, number) ?: return false
    return play in normalizeAdminSystemModeConfig(config).blockedSalePlays
}

fun firstBlockedSalePlayLabel(plays: Iterable<AdminBlockedSalePlay>, config: AdminSystemModeConfig): String? {
    return plays.firstOrNull { isSalePlayBlocked(it.playType, it.number, config) }?.let(::blockedSalePlayLabel)
}

private fun encodeBlockedSalePlaysArray(plays: Set<AdminBlockedSalePlay>): JSONArray {
    return JSONArray().apply {
        normalizeBlockedSalePlays(plays).forEach { play ->
            put(JSONObject().apply {
                put("playType", play.playType)
                put("number", play.number)
            })
        }
    }
}

private fun decodeBlockedSalePlays(raw: String?): Set<AdminBlockedSalePlay> {
    raw ?: return emptySet()
    return runCatching {
        val array = JSONArray(raw)
        buildSet {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                when (item) {
                    is JSONObject -> normalizeBlockedSalePlay(
                        item.optString("playType"),
                        item.optString("number"),
                    )?.let(::add)
                    else -> {
                        // Ignore old type-wide entries; sale blocking is now exact-number only.
                    }
                }
            }
        }
    }.map(::normalizeBlockedSalePlays).getOrDefault(emptySet())
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
        put("configured", true)
        put("posLiteEnabled", normalized.posLiteEnabled)
        put("lotteryModeEnabled", normalized.lotteryModeEnabled)
        put("pickModeEnabled", normalized.pickModeEnabled)
        put("cashierPickEnabled", normalized.cashierPickEnabled)
        put("cashierModeEnabled", normalized.cashierModeEnabled)
        put("cashierLotteryModeEnabled", normalized.cashierLotteryModeEnabled)
        put("cashierPickModeEnabled", normalized.cashierPickModeEnabled)
        put("blockedSalePlays", encodeBlockedSalePlaysArray(normalized.blockedSalePlays))
        put("updatedAt", System.currentTimeMillis())
    }.toString()
}

fun decodeAdminSystemModeConfig(raw: String?): AdminSystemModeConfig {
    if (raw.isNullOrBlank()) return AdminSystemModeConfig()
    return runCatching {
        val json = JSONObject(raw)
        val configured = json.optBoolean("configured", false)
        val decoded = normalizeAdminSystemModeConfig(
            AdminSystemModeConfig(
                posLiteEnabled = json.optBoolean("posLiteEnabled", false),
                lotteryModeEnabled = json.optBoolean("lotteryModeEnabled", true),
                pickModeEnabled = json.optBoolean("pickModeEnabled", false),
                cashierPickEnabled = json.optBoolean("cashierPickEnabled", false),
                cashierModeEnabled = json.optBoolean("cashierModeEnabled", json.optBoolean("cashierPickEnabled", false)),
                cashierLotteryModeEnabled = json.optBoolean("cashierLotteryModeEnabled", true),
                cashierPickModeEnabled = json.optBoolean("cashierPickModeEnabled", json.optBoolean("cashierPickEnabled", false)),
                blockedSalePlays = decodeBlockedSalePlays(json.optJSONArray("blockedSalePlays")?.toString()),
            ),
        )
        if (!configured && !decoded.lotteryModeEnabled) {
            AdminSystemModeConfig()
        } else {
            decoded
        }
    }.getOrDefault(AdminSystemModeConfig())
}
