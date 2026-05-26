package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.master.MasterConfigRemoteStore
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.storage.LocalCashierPrizePayoutRepository
import org.json.JSONObject

class CashierPrizePayoutCloudSyncCoordinator(
    private val repository: LocalCashierPrizePayoutRepository,
    private val remoteStore: MasterConfigRemoteStore = SupabaseMasterConfigRemoteStore(),
) {
    fun pushDefaultPayoutServiceFirst(ownerId: String?, config: PrizeTableConfig): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        val payload = repository.buildPayloadWithDefaultPayout(key, config)
        return runCatching {
            remoteStore.upsertJsonValue(cashierPrizePayoutRemoteKey(key), payload)
            repository.cachePayload(key, payload)
            true
        }.getOrDefault(false)
    }

    fun pushUserPayoutServiceFirst(ownerId: String?, username: String?, config: PrizeTableConfig): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        val payload = repository.buildPayloadWithUserPayout(key, username, config)
        if (payload.isBlank()) return false
        return runCatching {
            remoteStore.upsertJsonValue(cashierPrizePayoutRemoteKey(key), payload)
            repository.cachePayload(key, payload)
            true
        }.getOrDefault(false)
    }

    fun pullOwner(ownerId: String?): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        val payload = runCatching { remoteStore.fetchValue(cashierPrizePayoutRemoteKey(key)) }.getOrNull()
            ?: return false
        repository.cachePayload(key, payload.toRawJsonString())
        return true
    }
}

fun cashierPrizePayoutRemoteKey(ownerId: String): String = "cashier_prize_payouts:${ownerId.trim()}"

private fun Any.toRawJsonString(): String {
    return when (this) {
        is JSONObject -> toString()
        is String -> this
        else -> toString()
    }
}
