package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import org.json.JSONObject
import java.util.Locale

data class CashierSalesLimitInputs(
    val daySale: Double = 10000.0,
    val payout: Double = 0.0,
    val quiniela: Double = 10000.0,
    val pale: Double = 500.0,
    val superPale: Double = 500.0,
    val tripleta: Double = 75.0,
    val pick3Straight: Double = 500.0,
    val pick3Box: Double = 500.0,
    val pick4Straight: Double = 500.0,
    val pick4Box: Double = 500.0,
)

fun encodeCashierSalesLimitInputs(limits: CashierSalesLimitInputs): String =
    JSONObject().apply { put("defaults", limits.toJson()) }.toString()

fun decodeCashierSalesLimitInputs(payload: String?): CashierSalesLimitInputs {
    val defaults = payload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.optJSONObject("defaults")
        ?: return CashierSalesLimitInputs()
    return decodeCashierSalesLimitInputs(defaults)
}

fun decodeCashierPoolLimitInputs(payload: String?): CashierSalesLimitInputs {
    val root = payload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: return CashierSalesLimitInputs()
    val pool = root.optJSONObject("pool") ?: return CashierSalesLimitInputs(
        daySale = 0.0,
        payout = 0.0,
        quiniela = 0.0,
        pale = 0.0,
        superPale = 0.0,
        tripleta = 0.0,
        pick3Straight = 0.0,
        pick3Box = 0.0,
        pick4Straight = 0.0,
        pick4Box = 0.0,
    )
    return decodeCashierSalesLimitInputs(pool)
}

fun decodeCashierSalesLimitInputs(json: JSONObject?): CashierSalesLimitInputs {
    json ?: return CashierSalesLimitInputs()
    return CashierSalesLimitInputs(
        daySale = json.optDouble("daySale", 10000.0).coerceAtLeast(0.0),
        payout = json.optDouble("payout", 0.0).coerceAtLeast(0.0),
        quiniela = json.optDouble("q", 10000.0).coerceAtLeast(0.0),
        pale = json.optDouble("pale", json.optDouble("p", 500.0)).coerceAtLeast(0.0),
        superPale = json.optDouble("sp", json.optDouble("p", 500.0)).coerceAtLeast(0.0),
        tripleta = json.optDouble("t", 75.0).coerceAtLeast(0.0),
        pick3Straight = json.optDouble("p3", json.optDouble("p", 500.0)).coerceAtLeast(0.0),
        pick3Box = json.optDouble("p3box", json.optDouble("p", 500.0)).coerceAtLeast(0.0),
        pick4Straight = json.optDouble("p4", json.optDouble("p", 500.0)).coerceAtLeast(0.0),
        pick4Box = json.optDouble("p4box", json.optDouble("p", 500.0)).coerceAtLeast(0.0),
    )
}

fun decodeCashierUserSalesLimitInputs(payload: String?, username: String?): CashierSalesLimitInputs? {
    val userKey = username?.takeIf { it.isNotBlank() } ?: return null
    return decodeCashierUserSalesLimitInputs(payload, listOf(userKey))
}

fun decodeCashierUserSalesLimitInputs(payload: String?, account: UserAccount?): CashierSalesLimitInputs? {
    return decodeCashierUserSalesLimitInputs(payload, resolveCashierLimitLookupKeys(account))
}

fun decodeCashierUserSalesLimitInputs(payload: String?, userKeys: Collection<String>): CashierSalesLimitInputs? {
    val cleanKeys = userKeys
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (cleanKeys.isEmpty()) return null
    val root = payload?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val byUser = root.optJSONObject("byUser") ?: return null
    cleanKeys.forEach { key ->
        byUser.optJSONObject(key)?.let { return decodeCashierSalesLimitInputs(it) }
    }
    val normalizedKeys = cleanKeys.map { it.lowercase(Locale.US) }.toSet()
    val iterator = byUser.keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        if (key.trim().lowercase(Locale.US) in normalizedKeys) {
            byUser.optJSONObject(key)?.let { return decodeCashierSalesLimitInputs(it) }
        }
    }
    return null
}

internal fun resolveCashierLimitLookupKeys(account: UserAccount?): List<String> {
    if (account == null) return emptyList()
    return listOfNotNull(
        account.id.takeIf { it.isNotBlank() },
        account.user.takeIf { it.isNotBlank() },
        account.displayName?.takeIf { it.isNotBlank() },
        account.authUserId?.takeIf { it.isNotBlank() },
        account.adminId?.takeIf { it.isNotBlank() },
        account.adminUser?.takeIf { it.isNotBlank() },
        account.ownerName?.takeIf { it.isNotBlank() },
        account.banca?.takeIf { it.isNotBlank() },
        account.cashierPrefix?.takeIf { it.isNotBlank() },
        account.recargasRapidasUsername?.takeIf { it.isNotBlank() },
    ).distinct()
}

internal fun resolveCashierLimitLookupKeys(session: ActiveSession?): List<String> {
    if (session == null) return emptyList()
    return listOfNotNull(
        session.username.takeIf { it.isNotBlank() },
        session.userId.takeIf { it.isNotBlank() },
        session.adminId?.takeIf { it.isNotBlank() },
        session.adminUser?.takeIf { it.isNotBlank() },
        session.authUserId?.takeIf { it.isNotBlank() },
    ).distinct()
}

fun buildCashierLimitPayloadWithDefault(
    currentPayload: String?,
    limits: CashierSalesLimitInputs,
): String {
    val current = currentPayload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject()
    current.put("defaults", limits.toJson())
    return current.toString()
}

fun buildCashierLimitPayloadWithPool(
    currentPayload: String?,
    limits: CashierSalesLimitInputs,
): String {
    val current = currentPayload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject()
    current.put("pool", limits.toJson())
    return current.toString()
}

fun buildCashierLimitPayloadWithDefaultForUsers(
    currentPayload: String?,
    limits: CashierSalesLimitInputs,
    usernames: Collection<String>,
): String {
    val current = currentPayload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject()
    current.put("defaults", limits.toJson())
    val cleanUsers = usernames
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (cleanUsers.isNotEmpty()) {
        val byUser = current.optJSONObject("byUser") ?: JSONObject().also { current.put("byUser", it) }
        cleanUsers.forEach { username ->
            byUser.put(username, limits.toJson())
        }
    }
    return current.toString()
}

fun buildCashierLimitPayloadWithUser(
    currentPayload: String?,
    username: String?,
    limits: CashierSalesLimitInputs,
): String {
    val userKey = username?.takeIf { it.isNotBlank() } ?: return currentPayload.orEmpty()
    val current = currentPayload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject().apply { put("defaults", CashierSalesLimitInputs().toJson()) }
    val byUser = current.optJSONObject("byUser") ?: JSONObject().also { current.put("byUser", it) }
    byUser.put(userKey, limits.toJson())
    return current.toString()
}

fun buildCashierLimitPayloadWithAdminSelf(
    currentPayload: String?,
    limits: CashierSalesLimitInputs,
): String {
    val current = currentPayload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject().apply { put("defaults", CashierSalesLimitInputs().toJson()) }
    current.put("adminSelf", limits.toJson())
    return current.toString()
}

fun resolveAdminSelfLimitsAreEmpty(payload: String?): Boolean {
    val adminSelf = payload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.optJSONObject("adminSelf")
        ?: return true
    return listOf("daySale", "payout", "q", "pale", "sp", "t", "p3", "p3box", "p4", "p4box")
        .all { key -> adminSelf.optDouble(key, 0.0) <= 0.0 }
}

private fun CashierSalesLimitInputs.toJson(): JSONObject {
    return JSONObject().apply {
        put("daySale", daySale.coerceAtLeast(0.0))
        put("payout", payout.coerceAtLeast(0.0))
        put("q", quiniela.coerceAtLeast(0.0))
        put("pale", pale.coerceAtLeast(0.0))
        put("sp", superPale.coerceAtLeast(0.0))
        put("t", tripleta.coerceAtLeast(0.0))
        put("p3", pick3Straight.coerceAtLeast(0.0))
        put("p3box", pick3Box.coerceAtLeast(0.0))
        put("p4", pick4Straight.coerceAtLeast(0.0))
        put("p4box", pick4Box.coerceAtLeast(0.0))
    }
}

class LocalCashierSalesLimitRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(SalesStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getDefaultLimits(ownerId: String?): CashierSalesLimitInputs {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return CashierSalesLimitInputs()
        return decodeCashierSalesLimitInputs(prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null))
    }

    fun saveDefaultLimits(ownerId: String?, limits: CashierSalesLimitInputs) {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return
        val storageKey = SalesStorageKeys.CASHIER_LIMITS_PREFIX + key
        val payload = buildCashierLimitPayloadWithDefault(
            currentPayload = prefs.getString(storageKey, null),
            limits = limits,
        )
        prefs.edit { putString(storageKey, payload) }
    }

    fun getPoolLimits(ownerId: String?): CashierSalesLimitInputs {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return CashierSalesLimitInputs()
        return decodeCashierPoolLimitInputs(prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null))
    }

    fun savePoolLimits(ownerId: String?, limits: CashierSalesLimitInputs) {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return
        val storageKey = SalesStorageKeys.CASHIER_LIMITS_PREFIX + key
        val payload = buildCashierLimitPayloadWithPool(
            currentPayload = prefs.getString(storageKey, null),
            limits = limits,
        )
        prefs.edit { putString(storageKey, payload) }
    }

    fun exportPayload(ownerId: String?): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierSalesLimitInputs(CashierSalesLimitInputs())
        return prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null)
            ?: encodeCashierSalesLimitInputs(CashierSalesLimitInputs())
    }

    fun buildPayloadWithDefaultLimits(ownerId: String?, limits: CashierSalesLimitInputs): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierSalesLimitInputs(limits)
        return buildCashierLimitPayloadWithDefault(
            currentPayload = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null),
            limits = limits,
        )
    }

    fun buildPayloadWithPoolLimits(ownerId: String?, limits: CashierSalesLimitInputs): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierSalesLimitInputs(limits)
        return buildCashierLimitPayloadWithPool(
            currentPayload = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null),
            limits = limits,
        )
    }

    fun buildPayloadWithDefaultLimitsForUsers(
        ownerId: String?,
        limits: CashierSalesLimitInputs,
        usernames: Collection<String>,
    ): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierSalesLimitInputs(limits)
        return buildCashierLimitPayloadWithDefaultForUsers(
            currentPayload = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null),
            limits = limits,
            usernames = usernames,
        )
    }

    fun buildPayloadWithUserLimits(ownerId: String?, username: String?, limits: CashierSalesLimitInputs): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierSalesLimitInputs(CashierSalesLimitInputs())
        return buildCashierLimitPayloadWithUser(
            currentPayload = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null),
            username = username,
            limits = limits,
        )
    }

    fun buildPayloadWithAdminSelfLimits(ownerId: String?, limits: CashierSalesLimitInputs): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierSalesLimitInputs(CashierSalesLimitInputs())
        return buildCashierLimitPayloadWithAdminSelf(
            currentPayload = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, null),
            limits = limits,
        )
    }

    fun cachePayload(ownerId: String?, payload: String) {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return
        if (payload.isBlank()) return
        prefs.edit { putString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + key, payload) }
    }

    fun getUserLimits(ownerId: String?, username: String?): CashierSalesLimitInputs {
        val ownerKey = ownerId?.takeIf { it.isNotBlank() } ?: return CashierSalesLimitInputs()
        val userKey = username?.takeIf { it.isNotBlank() } ?: return getDefaultLimits(ownerKey)
        val raw = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerKey, null)
        val root = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return getDefaultLimits(ownerKey)
        val defaults = decodeCashierSalesLimitInputs(root.optJSONObject("defaults"))
        val row = root.optJSONObject("byUser")?.optJSONObject(userKey) ?: return defaults
        return decodeCashierSalesLimitInputs(row)
    }

    fun getUserLimits(ownerId: String?, account: UserAccount): CashierSalesLimitInputs {
        val ownerKey = ownerId?.takeIf { it.isNotBlank() } ?: return CashierSalesLimitInputs()
        val raw = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerKey, null)
        val root = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return getDefaultLimits(ownerKey)
        val defaults = decodeCashierSalesLimitInputs(root.optJSONObject("defaults"))
        val byUser = root.optJSONObject("byUser") ?: return defaults
        val aliasKeys = resolveCashierLimitLookupKeys(account)
        aliasKeys.forEach { key ->
            byUser.optJSONObject(key)?.let { return decodeCashierSalesLimitInputs(it) }
        }
        val normalizedKeys = aliasKeys.map { it.lowercase(Locale.US) }.toSet()
        val iterator = byUser.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key.trim().lowercase(Locale.US) in normalizedKeys) {
                byUser.optJSONObject(key)?.let { return decodeCashierSalesLimitInputs(it) }
            }
        }
        return defaults
    }

    fun getUserLimits(ownerId: String?, session: ActiveSession): CashierSalesLimitInputs {
        val ownerKey = ownerId?.takeIf { it.isNotBlank() } ?: return CashierSalesLimitInputs()
        val raw = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerKey, null)
        val root = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return getDefaultLimits(ownerKey)
        val defaults = decodeCashierSalesLimitInputs(root.optJSONObject("defaults"))
        val byUser = root.optJSONObject("byUser") ?: return defaults
        val aliasKeys = resolveCashierLimitLookupKeys(session)
        aliasKeys.forEach { key ->
            byUser.optJSONObject(key)?.let { return decodeCashierSalesLimitInputs(it) }
        }
        val normalizedKeys = aliasKeys.map { it.lowercase(Locale.US) }.toSet()
        val iterator = byUser.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key.trim().lowercase(Locale.US) in normalizedKeys) {
                byUser.optJSONObject(key)?.let { return decodeCashierSalesLimitInputs(it) }
            }
        }
        return defaults
    }

    fun getUserLimitsOrNull(ownerId: String?, username: String?): CashierSalesLimitInputs? {
        val ownerKey = ownerId?.takeIf { it.isNotBlank() } ?: return null
        return decodeCashierUserSalesLimitInputs(
            payload = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerKey, null),
            username = username,
        )
    }

    fun getAdminSelfLimits(ownerId: String?): CashierSalesLimitInputs? {
        val ownerKey = ownerId?.takeIf { it.isNotBlank() } ?: return null
        val payload = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerKey, null)
        if (resolveAdminSelfLimitsAreEmpty(payload)) return null
        val root = payload?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return decodeCashierSalesLimitInputs(root.optJSONObject("adminSelf"))
    }

    fun saveUserLimits(ownerId: String?, username: String?, limits: CashierSalesLimitInputs) {
        val ownerKey = ownerId?.takeIf { it.isNotBlank() } ?: return
        val storageKey = SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerKey
        val payload = buildPayloadWithUserLimits(ownerKey, username, limits)
        if (payload.isBlank()) return
        prefs.edit { putString(storageKey, payload) }
    }

    fun saveAdminSelfLimits(ownerId: String?, limits: CashierSalesLimitInputs) {
        val ownerKey = ownerId?.takeIf { it.isNotBlank() } ?: return
        val storageKey = SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerKey
        prefs.edit { putString(storageKey, buildPayloadWithAdminSelfLimits(ownerKey, limits)) }
    }
}
