package com.lotterynet.pro.core.finance

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONArray
import org.json.JSONObject

class RemoteOperationalReportRepository(
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(SupabaseConfig.URL, SupabaseConfig.KEY),
    private val bearerTokenProvider: () -> String?,
    private val bearerTokenRefresher: () -> String?,
) {
    fun getReport(
        session: ActiveSession,
        filter: OperationalReportActorFilter,
        preset: FinancePeriodPreset,
        range: FinanceResolvedRange,
    ): OperationalReportViewState {
        val payload = buildReportPayload(session, filter, range)
        val response = invokeReport(session, payload)
        if (!response.optBoolean("ok", false)) {
            throw IllegalStateException(response.optString("message").ifBlank { "No se pudo cargar reporte servidor." })
        }
        return response.toOperationalReportViewState(
            filter = filter,
            preset = preset,
            range = range,
        )
    }

    private fun invokeReport(session: ActiveSession, payload: JSONObject): JSONObject {
        val slug = when (session.role) {
            UserRole.CASHIER -> "get-cashier-report"
            UserRole.SUPERVISOR -> "get-supervisor-report"
            else -> "get-admin-report"
        }
        return runCatching {
            edgeClient.invokeAuthenticated(slug, payload, bearerTokenProvider())
        }.getOrElse {
            edgeClient.invokeAuthenticated(slug, payload, bearerTokenRefresher())
        }
    }
}

private fun buildReportPayload(
    session: ActiveSession,
    filter: OperationalReportActorFilter,
    range: FinanceResolvedRange,
): JSONObject {
    val actorKey = session.username.ifBlank { session.userId }
    return JSONObject().apply {
        put("actorKey", actorKey)
        put("from", range.fromDayKey)
        put("to", range.toDayKey)
        when (session.role) {
            UserRole.CASHIER -> {
                put("cashierKey", session.username.ifBlank { session.userId })
                session.adminUser?.takeIf { it.isNotBlank() }?.let { put("adminKey", it) }
                session.adminId?.takeIf { it.isNotBlank() }?.let { put("adminId", it) }
            }
            UserRole.SUPERVISOR -> {
                put("supervisorKey", session.username.ifBlank { session.userId })
                session.adminUser?.takeIf { it.isNotBlank() }?.let { put("adminKey", it) }
                session.adminId?.takeIf { it.isNotBlank() }?.let { put("adminId", it) }
            }
            else -> {
                put("adminKey", session.username.ifBlank { session.userId })
            }
        }
        when (filter) {
            OperationalReportActorFilter.All -> Unit
            OperationalReportActorFilter.Admin -> put("adminKey", session.username.ifBlank { session.userId })
            is OperationalReportActorFilter.Supervisor -> put("supervisorKey", filter.supervisorKey)
            is OperationalReportActorFilter.Cashier -> put("cashierKey", filter.actorKey)
        }
    }
}

private fun JSONObject.toOperationalReportViewState(
    filter: OperationalReportActorFilter,
    preset: FinancePeriodPreset,
    range: FinanceResolvedRange,
): OperationalReportViewState {
    val summary = optJSONObject("summary").toFinanceSummary()
    val cashiers = optJSONArray("cashiers").orEmpty()
    return OperationalReportViewState(
        periodLabel = range.label,
        filter = filter,
        syncStatus = OperationalReportSyncStatus.UPDATED,
        summary = summary,
        trend = listOf(OperationalReportTrendPoint(range.label, summary)),
        actorRows = cashiers.mapJsonObjects { item ->
            val key = item.optString("cashier_key").ifBlank { "sin-cajero" }
            val label = item.optString("cashier_label")
                .ifBlank { item.optString("displayName") }
                .ifBlank { key }
            FinanceActorPeriodRow(
                actorKey = key,
                actorDisplay = label,
                summary = item.toCashierFinanceSummary(),
            )
        },
    )
}

private fun JSONObject?.toFinanceSummary(): FinanceSummary {
    if (this == null) return FinanceSummary()
    val ventas = optDouble("totalVendido", 0.0)
    val recargas = optDouble("totalRecargas", optDouble("recargas", 0.0))
    val premiosPagados = optDouble("totalPagado", 0.0)
    val premiosPendientes = optDouble("totalPendiente", 0.0)
    val comision = optDouble("comision", 0.0)
    val supervisorComision = optDouble("supervisorComision", 0.0)
    val anuladosMonto = optDouble("totalAnulado", 0.0)
    val invalidosMonto = optDouble("totalInvalidado", 0.0)
    return FinanceSummary(
        ventas = ventas,
        ticketsCount = optInt("tickets", 0),
        anuladosMonto = anuladosMonto,
        invalidosMonto = invalidosMonto,
        fueraDeFinanzaMonto = anuladosMonto + invalidosMonto,
        premiosPagados = premiosPagados,
        premiosPendientes = premiosPendientes,
        recargas = recargas,
        comision = comision,
        supervisorComision = supervisorComision,
        cajaDisponible = ventas + recargas - premiosPagados - premiosPendientes - comision - supervisorComision,
        avgTicket = if (optInt("tickets", 0) > 0) ventas / optInt("tickets", 0) else 0.0,
    )
}

private fun JSONObject.toCashierFinanceSummary(): FinanceSummary {
    val ventas = optDouble("vendido", 0.0)
    val recargas = optDouble("recargas", optDouble("totalRecargas", 0.0))
    val premiosPagados = optDouble("pagado", 0.0)
    val premiosPendientes = optDouble("pendiente", 0.0)
    val premios = optDouble("premios", premiosPagados + premiosPendientes)
    val pending = if (has("pendiente")) premiosPendientes else kotlin.math.max(premios - premiosPagados, 0.0)
    val comision = optDouble("comision", 0.0)
    val anuladosMonto = optDouble("anulado", 0.0)
    return FinanceSummary(
        ventas = ventas,
        ticketsCount = optInt("tickets", 0),
        anuladosMonto = anuladosMonto,
        fueraDeFinanzaMonto = anuladosMonto,
        premiosPagados = premiosPagados,
        premiosPendientes = pending,
        recargas = recargas,
        comision = comision,
        cajaDisponible = ventas + recargas - premiosPagados - pending - comision,
        avgTicket = if (optInt("tickets", 0) > 0) ventas / optInt("tickets", 0) else 0.0,
    )
}

private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

private inline fun <T> JSONArray.mapJsonObjects(transform: (JSONObject) -> T): List<T> {
    val rows = ArrayList<T>(length())
    for (index in 0 until length()) {
        optJSONObject(index)?.let { rows += transform(it) }
    }
    return rows
}
