package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.DeletedUserRef
import com.lotterynet.pro.core.model.DeletedUsersState
import com.lotterynet.pro.core.model.UserAccount
import org.json.JSONArray
import org.json.JSONObject

class LocalUsersDeletedRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(UsersStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(): DeletedUsersState {
        val raw = prefs.getString(UsersStorageKeys.USERS_DELETED_KEY, null) ?: return DeletedUsersState()
        return runCatching {
            val root = JSONObject(raw)
            DeletedUsersState(
                admins = root.optJSONArray("admins").toRefs(),
                cashiers = root.optJSONArray("cajeros").toRefs(),
            )
        }.getOrDefault(DeletedUsersState())
    }

    fun saveState(state: DeletedUsersState): DeletedUsersState {
        val normalized = DeletedUsersState(
            admins = state.admins.filterMeaningful(),
            cashiers = state.cashiers.filterMeaningful(),
        )
        prefs.edit {
            putString(
                UsersStorageKeys.USERS_DELETED_KEY,
                JSONObject().apply {
                    put("admins", JSONArray().apply { normalized.admins.forEach { put(it.toJson()) } })
                    put("cajeros", JSONArray().apply { normalized.cashiers.forEach { put(it.toJson()) } })
                }.toString()
            )
        }
        return normalized
    }

    fun exportStateJson(): String {
        val state = getState()
        return JSONObject().apply {
            put("admins", JSONArray().apply { state.admins.forEach { put(it.toJson()) } })
            put("cajeros", JSONArray().apply { state.cashiers.forEach { put(it.toJson()) } })
        }.toString()
    }

    fun cacheRawPayload(payload: String) {
        parseRawPayload(payload)?.let { saveState(it) }
    }

    fun parseRawPayload(payload: String): DeletedUsersState? {
        return runCatching { JSONObject(payload) }.getOrNull()?.let { root ->
            DeletedUsersState(
                admins = root.optJSONArray("admins").toRefs(),
                cashiers = root.optJSONArray("cajeros").toRefs(),
            )
        }
    }

    fun merge(local: DeletedUsersState, remote: DeletedUsersState): DeletedUsersState {
        val adminMap = linkedMapOf<String, DeletedUserRef>()
        val cashierMap = linkedMapOf<String, DeletedUserRef>()
        (local.admins + remote.admins).forEach { ref ->
            val key = ref.adminKey() ?: return@forEach
            val prev = adminMap[key]
            if (prev == null || ref.deletedAtEpochMs >= prev.deletedAtEpochMs) adminMap[key] = ref
        }
        (local.cashiers + remote.cashiers).forEach { ref ->
            val key = ref.cashierKey() ?: return@forEach
            val prev = cashierMap[key]
            if (prev == null || ref.deletedAtEpochMs >= prev.deletedAtEpochMs) cashierMap[key] = ref
        }
        return DeletedUsersState(
            admins = adminMap.values.toList(),
            cashiers = cashierMap.values.toList(),
        )
    }

    fun rememberDeletedAdmin(admin: UserAccount, cashiers: List<UserAccount>): DeletedUsersState {
        val deletedAt = System.currentTimeMillis()
        val current = getState()
        return saveState(
            merge(
                current,
                DeletedUsersState(
                    admins = listOf(
                        DeletedUserRef(
                            id = admin.id,
                            user = admin.user,
                            banca = admin.banca,
                            deletedAtEpochMs = deletedAt,
                        )
                    ),
                    cashiers = cashiers.map { cashier ->
                        DeletedUserRef(
                            id = cashier.id,
                            user = cashier.user,
                            adminId = admin.id,
                            adminUser = admin.user,
                            banca = admin.banca,
                            deletedAtEpochMs = deletedAt,
                        )
                    },
                )
            )
        )
    }

    fun applyDeletedUsers(payloadJson: String, deletedState: DeletedUsersState = getState()): String =
        applyDeletedUsersToPayload(payloadJson, deletedState)

    fun mergeUsersPayloadPreferNewest(localPayloadJson: String, remotePayloadJson: String): String {
        val local = runCatching { JSONObject(localPayloadJson) }.getOrDefault(JSONObject())
        val remote = runCatching { JSONObject(remotePayloadJson) }.getOrDefault(JSONObject())
        return JSONObject().apply {
            put("admins", mergeArray(local.optJSONArray("admins"), remote.optJSONArray("admins"), isCashier = false))
            put("cajeros", mergeArray(local.optJSONArray("cajeros"), remote.optJSONArray("cajeros"), isCashier = true))
        }.toString()
    }

    private fun mergeArray(local: JSONArray?, remote: JSONArray?, isCashier: Boolean): JSONArray {
        val map = linkedMapOf<String, JSONObject>()
        fun ingest(source: JSONArray?) {
            for (index in 0 until (source?.length() ?: 0)) {
                val item = source?.optJSONObject(index) ?: continue
                val key = if (isCashier) item.cashierKey() else item.adminKey()
                if (key == null) continue
                val prev = map[key]
                if (prev == null || item.rankTimestamp() >= prev.rankTimestamp()) {
                    map[key] = item
                }
            }
        }
        ingest(local)
        ingest(remote)
        return JSONArray().apply { map.values.forEach { put(it) } }
    }

    private fun JSONArray?.toRefs(): List<DeletedUserRef> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    DeletedUserRef(
                        id = item.optString("id").takeIf { it.isNotBlank() },
                        user = item.optString("user").takeIf { it.isNotBlank() },
                        adminId = item.optString("adminId").takeIf { it.isNotBlank() },
                        adminUser = item.optString("adminUser").takeIf { it.isNotBlank() },
                        banca = item.optString("banca").takeIf { it.isNotBlank() },
                        deletedAtEpochMs = item.optLong("deletedAt").takeIf { it > 0L } ?: 0L,
                    )
                )
            }
        }
    }

    private fun List<DeletedUserRef>.filterMeaningful(): List<DeletedUserRef> {
        return filter { ref ->
            !ref.id.isNullOrBlank() || !ref.user.isNullOrBlank() || !ref.banca.isNullOrBlank()
        }
    }

    private fun DeletedUserRef.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("user", user)
            put("adminId", adminId)
            put("adminUser", adminUser)
            put("banca", banca)
            put("deletedAt", deletedAtEpochMs)
        }
    }

    private fun DeletedUserRef.adminKey(): String? {
        return id.normalizedKey()
            ?: user.normalizedKey()
            ?: banca.normalizedLooseText()?.let { "b:$it" }
    }

    private fun DeletedUserRef.cashierKey(): String? {
        return user.normalizedKey()?.let { "u:$it" }
            ?: id.normalizedKey()?.let { "i:$it" }
    }

    private fun JSONObject.adminKey(): String? {
        return optString("id").normalizedKey()
            ?: optString("user").normalizedKey()
            ?: optString("banca").normalizedLooseText()?.let { "b:$it" }
    }

    private fun JSONObject.cashierKey(): String? {
        return optString("user").normalizedKey()?.let { "u:$it" }
            ?: optString("id").normalizedKey()?.let { "i:$it" }
    }

    private fun JSONObject.rankTimestamp(): Long {
        return maxOf(
            optLong("credChangedAt", 0L),
            optLong("lastSeenAt", 0L),
            optLong("updatedAt", 0L),
            optLong("createdAt", 0L),
        )
    }

    private fun String?.normalizedKey(): String? {
        val value = this?.trim()?.lowercase().orEmpty()
        return value.ifBlank { null }
    }

    private fun String?.normalizedLooseText(): String? {
        val value = this.orEmpty()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        return value.ifBlank { null }
    }
}

internal fun applyDeletedUsersToPayload(payloadJson: String, deletedState: DeletedUsersState): String {
    val root = runCatching { JSONObject(payloadJson) }.getOrDefault(JSONObject())

    val admins = JSONArray()
    val rawAdmins = root.optJSONArray("admins")
    for (index in 0 until (rawAdmins?.length() ?: 0)) {
        val item = rawAdmins?.optJSONObject(index) ?: continue
        if (deletedState.admins.any { ref -> ref.matchesAdmin(item) && ref.appliesTo(item) }) {
            continue
        }
        admins.put(item)
    }

    val cashiers = JSONArray()
    val rawCashiers = root.optJSONArray("cajeros")
    for (index in 0 until (rawCashiers?.length() ?: 0)) {
        val item = rawCashiers?.optJSONObject(index) ?: continue
        val cashierDeleted = deletedState.cashiers.any { ref -> ref.matchesCashier(item) && ref.appliesTo(item) }
        val adminDeleted = deletedState.admins.any { ref -> ref.matchesCashierOwner(item) && ref.appliesTo(item) }
        if (cashierDeleted || adminDeleted) {
            continue
        }
        cashiers.put(item)
    }

    return JSONObject().apply {
        put("admins", admins)
        put("cajeros", cashiers)
    }.toString()
}

private fun DeletedUserRef.appliesTo(item: JSONObject): Boolean {
    val itemTimestamp = item.rankTimestamp()
    return itemTimestamp <= 0L || deletedAtEpochMs >= itemTimestamp
}

private fun DeletedUserRef.matchesAdmin(item: JSONObject): Boolean {
    val itemId = item.optString("id").normalizedKey()
    val itemUser = item.optString("user").normalizedKey()
    val itemBanca = item.optString("banca").normalizedLooseText()
    return (!id.isNullOrBlank() && id.normalizedKey() == itemId) ||
        (!user.isNullOrBlank() && user.normalizedKey() == itemUser) ||
        (!banca.isNullOrBlank() && banca.normalizedLooseText() == itemBanca)
}

private fun DeletedUserRef.matchesCashier(item: JSONObject): Boolean {
    val itemId = item.optString("id").normalizedKey()
    val itemUser = item.optString("user").normalizedKey()
    return (!id.isNullOrBlank() && id.normalizedKey() == itemId) ||
        (!user.isNullOrBlank() && user.normalizedKey() == itemUser)
}

private fun DeletedUserRef.matchesCashierOwner(item: JSONObject): Boolean {
    val itemAdminId = item.optString("adminId").normalizedKey()
    val itemAdminUser = item.optString("adminUser").normalizedKey()
    val itemBanca = item.optString("banca").normalizedLooseText()
    return (!id.isNullOrBlank() && id.normalizedKey() == itemAdminId) ||
        (!user.isNullOrBlank() && user.normalizedKey() == itemAdminUser) ||
        (!banca.isNullOrBlank() && banca.normalizedLooseText() == itemBanca)
}

private fun DeletedUserRef.cashierKey(): String? {
    return user.normalizedKey()?.let { "u:$it" }
        ?: id.normalizedKey()?.let { "i:$it" }
}

private fun String?.normalizedKey(): String? {
    val value = this?.trim()?.lowercase().orEmpty()
    return value.ifBlank { null }
}

private fun String?.normalizedLooseText(): String? {
    val value = this.orEmpty()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    return value.ifBlank { null }
}

private fun JSONObject.cashierKey(): String? {
    return optString("user").normalizedKey()?.let { "u:$it" }
        ?: optString("id").normalizedKey()?.let { "i:$it" }
}

private fun JSONObject.rankTimestamp(): Long {
    return maxOf(
        optLong("credChangedAt", 0L),
        optLong("lastSeenAt", 0L),
        optLong("updatedAt", 0L),
        optLong("createdAt", 0L),
    )
}
