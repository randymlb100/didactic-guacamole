package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.sync.mergeRechargesPreferImported
import org.json.JSONArray
import org.json.JSONObject

class LocalRechargeRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(RechargeStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getAvailableDayKeys(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith(RechargeStorageKeys.RECHARGES_PREFIX) }
            .map { it.removePrefix(RechargeStorageKeys.RECHARGES_PREFIX) }
            .sorted()
    }

    fun saveRecharge(record: RechargeRecord) {
        val dayKey = buildDayKeyFromEpoch(record.createdAtEpochMs)
        val rows = getRechargesForDay(dayKey).toMutableList()
        rows.add(record)
        saveDayRows(dayKey, rows)
    }

    fun getRechargesForDay(dayKey: String): List<RechargeRecord> {
        val raw = prefs.getString(RechargeStorageKeys.RECHARGES_PREFIX + dayKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(item.toRechargeRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getRechargesForActor(dayKey: String, actorKey: String): List<RechargeRecord> {
        val needle = actorKey.trim()
        if (needle.isBlank()) return emptyList()
        return getRechargesForDay(dayKey).filter { row ->
            row.userId == needle || row.userName == needle || row.adminId == needle || row.adminUser == needle
        }
    }

    fun getRechargesForOwner(ownerKey: String): List<RechargeRecord> {
        val needle = ownerKey.trim()
        if (needle.isBlank()) return emptyList()
        return getAvailableDayKeys()
            .flatMap(::getRechargesForDay)
            .filter { row ->
                row.adminId.equals(needle, ignoreCase = true) ||
                    row.adminUser.equals(needle, ignoreCase = true)
            }
    }

    fun replaceScopedImportedRecharges(
        ownerKey: String?,
        rows: List<RechargeRecord>,
    ) {
        val normalizedOwner = ownerKey?.trim().orEmpty()
        if (normalizedOwner.isBlank() && rows.isEmpty()) return
        val preserved = if (normalizedOwner.isBlank()) {
            getAvailableDayKeys().flatMap(::getRechargesForDay)
        } else {
            getAvailableDayKeys()
                .flatMap(::getRechargesForDay)
                .filterNot { record ->
                    record.adminId.equals(normalizedOwner, ignoreCase = true) ||
                        record.adminUser.equals(normalizedOwner, ignoreCase = true)
                }
        }
        val merged = mergeRechargesPreferImported(
            existing = preserved,
            imported = rows,
        )
        saveAllRows(merged)
    }

    private fun saveDayRows(dayKey: String, rows: List<RechargeRecord>) {
        prefs.edit {
            putString(
                RechargeStorageKeys.RECHARGES_PREFIX + dayKey,
                JSONArray(rows.map(::rechargeRecordToJson)).toString(),
            )
        }
    }

    private fun saveAllRows(rows: List<RechargeRecord>) {
        val grouped = rows.groupBy { buildDayKeyFromEpoch(it.createdAtEpochMs) }
        prefs.edit {
            prefs.all.keys
                .filter { it.startsWith(RechargeStorageKeys.RECHARGES_PREFIX) }
                .forEach(::remove)
            grouped.forEach { (dayKey, dayRows) ->
                putString(
                    RechargeStorageKeys.RECHARGES_PREFIX + dayKey,
                    JSONArray(dayRows.map(::rechargeRecordToJson)).toString(),
                )
            }
        }
    }

    private fun JSONObject.toRechargeRecord(): RechargeRecord {
        return rechargeJsonToRecord(this)
    }

    private fun buildDayKeyFromEpoch(epochMs: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(java.util.Date(epochMs))
    }
}

internal fun rechargeRecordToJson(record: RechargeRecord): JSONObject {
    return JSONObject().apply {
        put("id", record.id)
        put("providerId", record.providerId)
        put("providerName", record.providerName)
        put("phoneNumber", record.phoneNumber)
        put("amount", record.amount)
        put("productType", record.productType)
        put("status", record.status)
        put("providerReference", record.providerReference)
        put("userId", record.userId)
        put("userName", record.userName)
        put("adminId", record.adminId)
        put("adminUser", record.adminUser)
        put("createdAtEpochMs", record.createdAtEpochMs)
    }
}

internal fun rechargeJsonToRecord(json: JSONObject): RechargeRecord {
    return RechargeRecord(
        id = json.optString("id"),
        providerId = json.optString("providerId").takeIf { it.isNotBlank() },
        providerName = json.optString("providerName").takeIf { it.isNotBlank() },
        phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() },
        amount = json.optDouble("amount", 0.0),
        productType = json.optString("productType", "recarga").takeIf { it.isNotBlank() } ?: "recarga",
        status = json.optString("status", "pending").takeIf { it.isNotBlank() } ?: "pending",
        providerReference = json.optString("providerReference").takeIf { it.isNotBlank() },
        userId = json.optString("userId").takeIf { it.isNotBlank() },
        userName = json.optString("userName").takeIf { it.isNotBlank() },
        adminId = json.optString("adminId").takeIf { it.isNotBlank() },
        adminUser = json.optString("adminUser").takeIf { it.isNotBlank() },
        createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis()),
    )
}
