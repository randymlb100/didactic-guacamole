package com.lotterynet.pro.core.finance

import android.content.Context
import com.lotterynet.pro.core.sync.SyncFreshnessKey
import com.lotterynet.pro.core.sync.buildSyncFreshnessStorageKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores only responses that were successfully returned by the report endpoint.
 * Local ticket projections are deliberately not written here as an official report.
 */
class OperationalReportServerCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(key: SyncFreshnessKey, filter: OperationalReportActorFilter): OperationalReportViewState? {
        val raw = preferences.getString(storageKey(key), null) ?: return null
        return runCatching {
            JSONObject(raw).toViewState(filter)
        }.getOrNull()
    }

    fun write(key: SyncFreshnessKey, report: OperationalReportViewState) {
        val payload = JSONObject().apply {
            put("periodLabel", report.periodLabel)
            put("summary", report.summary.toJson())
            put("trend", JSONArray().apply {
                report.trend.forEach { point ->
                    put(JSONObject().apply {
                        put("label", point.label)
                        put("summary", point.summary.toJson())
                    })
                }
            })
            put("actorRows", JSONArray().apply {
                report.actorRows.forEach { row ->
                    put(JSONObject().apply {
                        put("actorKey", row.actorKey)
                        put("actorDisplay", row.actorDisplay)
                        put("summary", row.summary.toJson())
                    })
                }
            })
        }
        preferences.edit().putString(storageKey(key), payload.toString()).apply()
    }

    private fun storageKey(key: SyncFreshnessKey): String {
        return "report:${buildSyncFreshnessStorageKey(key)}"
    }

    companion object {
        private const val PREFERENCES = "operational_report_server_cache"
    }
}

private fun FinanceSummary.toJson(): JSONObject = JSONObject().apply {
    put("ventas", ventas)
    put("ticketsCount", ticketsCount)
    put("activos", activos)
    put("ganadores", ganadores)
    put("pagados", pagados)
    put("anuladosCount", anuladosCount)
    put("anuladosMonto", anuladosMonto)
    put("invalidosCount", invalidosCount)
    put("invalidosMonto", invalidosMonto)
    put("borradosCount", borradosCount)
    put("borradosMonto", borradosMonto)
    put("fueraDeFinanzaCount", fueraDeFinanzaCount)
    put("fueraDeFinanzaMonto", fueraDeFinanzaMonto)
    put("premiosPagados", premiosPagados)
    put("premiosPendientes", premiosPendientes)
    put("recargas", recargas)
    put("comision", comision)
    put("supervisorComision", supervisorComision)
    put("cajaDisponible", cajaDisponible)
    put("avgTicket", avgTicket)
}

private fun JSONObject.toFinanceSummary(): FinanceSummary = FinanceSummary(
    ventas = optDouble("ventas", 0.0),
    ticketsCount = optInt("ticketsCount", 0),
    activos = optInt("activos", 0),
    ganadores = optInt("ganadores", 0),
    pagados = optInt("pagados", 0),
    anuladosCount = optInt("anuladosCount", 0),
    anuladosMonto = optDouble("anuladosMonto", 0.0),
    invalidosCount = optInt("invalidosCount", 0),
    invalidosMonto = optDouble("invalidosMonto", 0.0),
    borradosCount = optInt("borradosCount", 0),
    borradosMonto = optDouble("borradosMonto", 0.0),
    fueraDeFinanzaCount = optInt("fueraDeFinanzaCount", 0),
    fueraDeFinanzaMonto = optDouble("fueraDeFinanzaMonto", 0.0),
    premiosPagados = optDouble("premiosPagados", 0.0),
    premiosPendientes = optDouble("premiosPendientes", 0.0),
    recargas = optDouble("recargas", 0.0),
    comision = optDouble("comision", 0.0),
    supervisorComision = optDouble("supervisorComision", 0.0),
    cajaDisponible = optDouble("cajaDisponible", 0.0),
    avgTicket = optDouble("avgTicket", 0.0),
)

private fun JSONObject.toViewState(filter: OperationalReportActorFilter): OperationalReportViewState {
    val trendJson = optJSONArray("trend") ?: JSONArray()
    val actorRowsJson = optJSONArray("actorRows") ?: JSONArray()
    return OperationalReportViewState(
        periodLabel = optString("periodLabel"),
        filter = filter,
        syncStatus = OperationalReportSyncStatus.CACHED_COPY,
        summary = optJSONObject("summary")?.toFinanceSummary() ?: FinanceSummary(),
        trend = buildList {
            for (index in 0 until trendJson.length()) {
                trendJson.optJSONObject(index)?.let { point ->
                    add(
                        OperationalReportTrendPoint(
                            label = point.optString("label"),
                            summary = point.optJSONObject("summary")?.toFinanceSummary() ?: FinanceSummary(),
                        ),
                    )
                }
            }
        },
        actorRows = buildList {
            for (index in 0 until actorRowsJson.length()) {
                actorRowsJson.optJSONObject(index)?.let { row ->
                    add(
                        FinanceActorPeriodRow(
                            actorKey = row.optString("actorKey"),
                            actorDisplay = row.optString("actorDisplay"),
                            summary = row.optJSONObject("summary")?.toFinanceSummary() ?: FinanceSummary(),
                        ),
                    )
                }
            }
        },
    )
}
