package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.master.MasterConfigRemoteStore
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import org.json.JSONObject

class CashierLimitCloudSyncCoordinator(
    private val repository: LocalCashierSalesLimitRepository,
    private val remoteStore: MasterConfigRemoteStore = SupabaseMasterConfigRemoteStore(),
) {
    fun pushOwner(ownerId: String?): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            remoteStore.upsertJsonValue(cashierLimitRemoteKey(key), repository.exportPayload(key))
            true
        }.getOrDefault(false)
    }

    fun pushDefaultLimitsServiceFirst(ownerId: String?, limits: CashierSalesLimitInputs): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        val payload = repository.buildPayloadWithDefaultLimits(key, limits)
        return runCatching {
            remoteStore.upsertJsonValue(cashierLimitRemoteKey(key), payload)
            repository.cachePayload(key, payload)
            true
        }.getOrDefault(false)
    }

    fun pushDefaultLimitsForUsersServiceFirst(
        ownerId: String?,
        limits: CashierSalesLimitInputs,
        usernames: Collection<String>,
    ): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        val payload = repository.buildPayloadWithDefaultLimitsForUsers(key, limits, usernames)
        return runCatching {
            remoteStore.upsertJsonValue(cashierLimitRemoteKey(key), payload)
            repository.cachePayload(key, payload)
            true
        }.getOrDefault(false)
    }

    fun pushUserLimitsServiceFirst(ownerId: String?, username: String?, limits: CashierSalesLimitInputs): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        val payload = repository.buildPayloadWithUserLimits(key, username, limits)
        if (payload.isBlank()) return false
        return runCatching {
            remoteStore.upsertJsonValue(cashierLimitRemoteKey(key), payload)
            repository.cachePayload(key, payload)
            true
        }.getOrDefault(false)
    }

    fun pullOwner(ownerId: String?): Boolean {
        val key = ownerId?.takeIf { it.isNotBlank() } ?: return false
        val payload = runCatching { remoteStore.fetchValue(cashierLimitRemoteKey(key)) }.getOrNull()
            ?: return false
        repository.cachePayload(key, payload.toRawJsonString())
        return true
    }
}

fun cashierLimitRemoteKey(ownerId: String): String = "cashier_limits:${ownerId.trim()}"

private fun Any.toRawJsonString(): String {
    return when (this) {
        is JSONObject -> toString()
        is String -> this
        else -> toString()
    }
}
