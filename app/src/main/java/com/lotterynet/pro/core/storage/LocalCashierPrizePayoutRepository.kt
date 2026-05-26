package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.normalizedPrizeTableConfig
import org.json.JSONObject

fun encodeCashierPrizePayout(config: PrizeTableConfig): String =
    JSONObject().apply { put("defaults", config.normalizedPrizeTableConfig().toCashierPrizeJson()) }.toString()

fun decodeCashierPrizeDefaultPayout(
    payload: String?,
    fallback: PrizeTableConfig = PrizeTableConfig(),
): PrizeTableConfig {
    val defaults = payload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.optJSONObject("defaults")
        ?: return fallback
    return decodeCashierPrizePayout(defaults, fallback)
}

fun decodeCashierPrizeUserPayout(
    payload: String?,
    username: String?,
    fallback: PrizeTableConfig = PrizeTableConfig(),
): PrizeTableConfig {
    val userKey = username?.takeIf { it.isNotBlank() } ?: return decodeCashierPrizeDefaultPayout(payload, fallback)
    val root = payload?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return fallback
    val defaults = decodeCashierPrizePayout(root.optJSONObject("defaults"), fallback)
    val user = root.optJSONObject("byUser")?.optJSONObject(userKey) ?: return defaults
    return decodeCashierPrizePayout(user, defaults)
}

fun buildCashierPrizePayoutPayloadWithDefault(
    currentPayload: String?,
    config: PrizeTableConfig,
): String {
    val current = currentPayload?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
    current.put("defaults", config.normalizedPrizeTableConfig().toCashierPrizeJson())
    return current.toString()
}

fun buildCashierPrizePayoutPayloadWithUser(
    currentPayload: String?,
    username: String?,
    config: PrizeTableConfig,
): String {
    val userKey = username?.takeIf { it.isNotBlank() } ?: return currentPayload.orEmpty()
    val current = currentPayload
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: JSONObject().apply { put("defaults", PrizeTableConfig().toCashierPrizeJson()) }
    val byUser = current.optJSONObject("byUser") ?: JSONObject().also { current.put("byUser", it) }
    byUser.put(userKey, config.normalizedPrizeTableConfig().toCashierPrizeJson())
    return current.toString()
}

class LocalCashierPrizePayoutRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(SalesStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val defaultPrizeConfig = PrizeTableConfig().normalizedPrizeTableConfig()

    fun getDefaultPayout(ownerId: String?): PrizeTableConfig {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return defaultPrizeConfig
        return decodeCashierPrizeDefaultPayout(
            payload = prefs.getString(SalesStorageKeys.CASHIER_PRIZE_PAYOUTS_PREFIX + key, null),
            fallback = defaultPrizeConfig,
        )
    }

    fun getUserPayout(ownerId: String?, username: String?): PrizeTableConfig {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return defaultPrizeConfig
        return decodeCashierPrizeUserPayout(
            payload = prefs.getString(SalesStorageKeys.CASHIER_PRIZE_PAYOUTS_PREFIX + key, null),
            username = username,
            fallback = defaultPrizeConfig,
        )
    }

    fun resolveForTicket(ownerId: String?, sellerUser: String?): PrizeTableConfig =
        getUserPayout(ownerId, sellerUser)

    fun exportPayload(ownerId: String?): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierPrizePayout(defaultPrizeConfig)
        return prefs.getString(SalesStorageKeys.CASHIER_PRIZE_PAYOUTS_PREFIX + key, null)
            ?: encodeCashierPrizePayout(defaultPrizeConfig)
    }

    fun buildPayloadWithDefaultPayout(ownerId: String?, config: PrizeTableConfig): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierPrizePayout(config)
        return buildCashierPrizePayoutPayloadWithDefault(
            currentPayload = prefs.getString(SalesStorageKeys.CASHIER_PRIZE_PAYOUTS_PREFIX + key, null),
            config = config,
        )
    }

    fun buildPayloadWithUserPayout(ownerId: String?, username: String?, config: PrizeTableConfig): String {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return encodeCashierPrizePayout(defaultPrizeConfig)
        return buildCashierPrizePayoutPayloadWithUser(
            currentPayload = prefs.getString(SalesStorageKeys.CASHIER_PRIZE_PAYOUTS_PREFIX + key, null),
            username = username,
            config = config,
        )
    }

    fun cachePayload(ownerId: String?, payload: String) {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return
        if (payload.isBlank()) return
        prefs.edit { putString(SalesStorageKeys.CASHIER_PRIZE_PAYOUTS_PREFIX + key, payload) }
    }
}

private fun decodeCashierPrizePayout(json: JSONObject?, fallback: PrizeTableConfig): PrizeTableConfig {
    val safeFallback = fallback.normalizedPrizeTableConfig()
    json ?: return safeFallback
    return PrizeTableConfig(
        q1 = json.optInt("q1", safeFallback.q1).positiveOr(safeFallback.q1),
        q2 = json.optInt("q2", safeFallback.q2).positiveOr(safeFallback.q2),
        q3 = json.optInt("q3", safeFallback.q3).positiveOr(safeFallback.q3),
        pale = json.optInt("pale", safeFallback.pale).positiveOr(safeFallback.pale),
        pale12 = json.optInt("pale12", json.optInt("p12", safeFallback.pale12)).positiveOr(safeFallback.pale12),
        pale13 = json.optInt("pale13", json.optInt("p13", safeFallback.pale13)).positiveOr(safeFallback.pale13),
        pale23 = json.optInt("pale23", json.optInt("p23", safeFallback.pale23)).positiveOr(safeFallback.pale23),
        tripleta = json.optInt("tripleta", safeFallback.tripleta).positiveOr(safeFallback.tripleta),
        tripleta3 = json.optInt("tripleta3", json.optInt("t3", safeFallback.tripleta3)).positiveOr(safeFallback.tripleta3),
        tripleta2 = json.optInt("tripleta2", json.optInt("t2", safeFallback.tripleta2)).positiveOr(safeFallback.tripleta2),
        superPale = json.optInt("sp", safeFallback.superPale).positiveOr(safeFallback.superPale),
        pick3Straight = json.optInt("p3", safeFallback.pick3Straight).positiveOr(safeFallback.pick3Straight),
        pick3Box3 = json.optInt("p3box3", safeFallback.pick3Box3).positiveOr(safeFallback.pick3Box3),
        pick3Box6 = json.optInt("p3box6", safeFallback.pick3Box6).positiveOr(safeFallback.pick3Box6),
        pick4Straight = json.optInt("p4", safeFallback.pick4Straight).positiveOr(safeFallback.pick4Straight),
        pick4Box4 = json.optInt("p4box4", safeFallback.pick4Box4).positiveOr(safeFallback.pick4Box4),
        pick4Box6 = json.optInt("p4box6", safeFallback.pick4Box6).positiveOr(safeFallback.pick4Box6),
        pick4Box12 = json.optInt("p4box12", safeFallback.pick4Box12).positiveOr(safeFallback.pick4Box12),
        pick4Box24 = json.optInt("p4box24", safeFallback.pick4Box24).positiveOr(safeFallback.pick4Box24),
        pick3BackPair = json.optInt("p3b", safeFallback.pick3BackPair).positiveOr(safeFallback.pick3BackPair),
        pick4BackPair = json.optInt("p4b", safeFallback.pick4BackPair).positiveOr(safeFallback.pick4BackPair),
    ).normalizedPrizeTableConfig()
}

private fun PrizeTableConfig.toCashierPrizeJson(): JSONObject =
    JSONObject().apply {
        put("q1", q1.coerceAtLeast(0))
        put("q2", q2.coerceAtLeast(0))
        put("q3", q3.coerceAtLeast(0))
        put("pale", pale.coerceAtLeast(0))
        put("pale12", pale12.coerceAtLeast(0))
        put("pale13", pale13.coerceAtLeast(0))
        put("pale23", pale23.coerceAtLeast(0))
        put("tripleta", tripleta.coerceAtLeast(0))
        put("tripleta3", tripleta3.coerceAtLeast(0))
        put("tripleta2", tripleta2.coerceAtLeast(0))
        put("sp", superPale.coerceAtLeast(0))
        put("p3", pick3Straight.coerceAtLeast(0))
        put("p3box3", pick3Box3.coerceAtLeast(0))
        put("p3box6", pick3Box6.coerceAtLeast(0))
        put("p4", pick4Straight.coerceAtLeast(0))
        put("p4box4", pick4Box4.coerceAtLeast(0))
        put("p4box6", pick4Box6.coerceAtLeast(0))
        put("p4box12", pick4Box12.coerceAtLeast(0))
        put("p4box24", pick4Box24.coerceAtLeast(0))
        put("p3b", pick3BackPair.coerceAtLeast(0))
        put("p4b", pick4BackPair.coerceAtLeast(0))
    }

private fun Int.positiveOr(fallback: Int): Int = takeIf { it > 0 } ?: fallback
