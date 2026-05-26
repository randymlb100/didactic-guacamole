package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.finance.FinanceAlert
import com.lotterynet.pro.core.finance.FinanceAlertTone
import com.lotterynet.pro.core.finance.FinanceHistoryEntry
import com.lotterynet.pro.core.finance.FinanceSummary
import org.json.JSONArray
import org.json.JSONObject

class LocalFinanceHistoryRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(FinanceHistoryStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(): List<FinanceHistoryEntry> {
        val raw = prefs.getString(FinanceHistoryStorageKeys.HISTORY_KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    add(item.toEntry())
                }
            }.sortedByDescending { it.createdAtEpochMs }
        }.getOrDefault(emptyList())
    }

    fun saveEntry(entry: FinanceHistoryEntry) {
        val updated = (getHistory() + entry)
            .sortedByDescending { it.createdAtEpochMs }
            .take(120)
        prefs.edit {
            putString(
                FinanceHistoryStorageKeys.HISTORY_KEY,
                JSONArray().apply { updated.forEach { put(it.toJson()) } }.toString(),
            )
        }
    }

    private fun JSONObject.toEntry(): FinanceHistoryEntry {
        return FinanceHistoryEntry(
            id = optString("id"),
            createdAtEpochMs = optLong("createdAtEpochMs"),
            dayKey = optString("dayKey"),
            recordType = optString("recordType"),
            scopeType = optString("scopeType"),
            targetId = optString("targetId"),
            targetName = optString("targetName"),
            periodLabel = optString("periodLabel"),
            summary = optJSONObject("summary")?.toFinanceSummary() ?: FinanceSummary(),
            closeCash = optDouble("closeCash").takeUnless { isNull("closeCash") },
            closeDiff = optDouble("closeDiff").takeUnless { isNull("closeDiff") },
        )
    }

    private fun FinanceHistoryEntry.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("createdAtEpochMs", createdAtEpochMs)
            put("dayKey", dayKey)
            put("recordType", recordType)
            put("scopeType", scopeType)
            put("targetId", targetId)
            put("targetName", targetName)
            put("periodLabel", periodLabel)
            put("summary", summary.toJson())
            put("closeCash", closeCash ?: JSONObject.NULL)
            put("closeDiff", closeDiff ?: JSONObject.NULL)
        }
    }

    private fun FinanceSummary.toJson(): JSONObject {
        return JSONObject().apply {
            put("ventas", ventas)
            put("ticketsCount", ticketsCount)
            put("activos", activos)
            put("ganadores", ganadores)
            put("pagados", pagados)
            put("anuladosCount", anuladosCount)
            put("anuladosMonto", anuladosMonto)
            put("invalidosCount", invalidosCount)
            put("invalidosMonto", invalidosMonto)
            put("fueraDeFinanzaCount", fueraDeFinanzaCount)
            put("fueraDeFinanzaMonto", fueraDeFinanzaMonto)
            put("premiosPagados", premiosPagados)
            put("premiosPendientes", premiosPendientes)
            put("recargas", recargas)
            put("comision", comision)
            put("supervisorComision", supervisorComision)
            put("cajaDisponible", cajaDisponible)
            put("avgTicket", avgTicket)
            put(
                "alertas",
                JSONArray().apply {
                    alertas.forEach { alert ->
                        put(
                            JSONObject().apply {
                                put("label", alert.label)
                                put("text", alert.text)
                                put("tone", alert.tone.name)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun JSONObject.toFinanceSummary(): FinanceSummary {
        return FinanceSummary(
            ventas = optDouble("ventas"),
            ticketsCount = optInt("ticketsCount"),
            activos = optInt("activos"),
            ganadores = optInt("ganadores"),
            pagados = optInt("pagados"),
            anuladosCount = optInt("anuladosCount"),
            anuladosMonto = optDouble("anuladosMonto"),
            invalidosCount = optInt("invalidosCount"),
            invalidosMonto = optDouble("invalidosMonto"),
            fueraDeFinanzaCount = optInt("fueraDeFinanzaCount"),
            fueraDeFinanzaMonto = optDouble("fueraDeFinanzaMonto"),
            premiosPagados = optDouble("premiosPagados"),
            premiosPendientes = optDouble("premiosPendientes"),
            recargas = optDouble("recargas"),
            comision = optDouble("comision"),
            supervisorComision = optDouble("supervisorComision"),
            cajaDisponible = optDouble("cajaDisponible"),
            avgTicket = optDouble("avgTicket"),
            alertas = optJSONArray("alertas")?.let { alerts ->
                buildList {
                    for (index in 0 until alerts.length()) {
                        val item = alerts.optJSONObject(index) ?: continue
                        add(
                            FinanceAlert(
                                label = item.optString("label"),
                                text = item.optString("text"),
                                tone = runCatching { FinanceAlertTone.valueOf(item.optString("tone")) }.getOrDefault(FinanceAlertTone.NOTICE),
                            ),
                        )
                    }
                }
            } ?: emptyList(),
        )
    }
}
