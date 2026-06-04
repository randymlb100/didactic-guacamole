package com.lotterynet.pro.core.sportsbook

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SportsbookSelection
import com.lotterynet.pro.core.model.SportsbookTicketDraft
import com.lotterynet.pro.core.model.SportsbookTicketLegRecord
import com.lotterynet.pro.core.model.SportsbookTicketRecord
import com.lotterynet.pro.core.model.SportsbookTicketSaleResult
import com.lotterynet.pro.core.model.SportsbookTicketSummary
import com.lotterynet.pro.core.model.SportsbookTicketStatus
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class SportsbookTicketRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) {
    fun createTicket(
        session: ActiveSession,
        draft: SportsbookTicketDraft,
        clientRequestId: String = UUID.randomUUID().toString(),
    ): SportsbookTicketSaleResult {
        val response = edgeClient.invokeAuthenticated(
            "create-sports-ticket",
            buildSportsbookTicketPayload(session, draft, clientRequestId),
            session.authAccessToken,
        )
        if (!response.optBoolean("ok", false)) {
            error(response.optString("message").ifBlank { "No se pudo vender el ticket deportivo." })
        }
        return parseSportsbookTicketSaleResult(response)
    }

    fun fetchTickets(
        session: ActiveSession,
        status: String = "",
        limit: Int = 50,
    ): SportsbookTicketSnapshot {
        val response = edgeClient.invokeAuthenticated(
            "get-sports-tickets",
            buildSportsbookTicketListPayload(session, status, limit),
            session.authAccessToken,
        )
        if (!response.optBoolean("ok", false)) {
            error(response.optString("message").ifBlank { "No se pudo leer tickets deportivos." })
        }
        return parseSportsbookTicketSnapshot(response)
    }

    fun payTicket(
        session: ActiveSession,
        ticketId: String,
    ): SportsbookTicketRecord {
        val response = edgeClient.invokeAuthenticated(
            "pay-sports-ticket",
            buildSportsbookPayTicketPayload(session, ticketId),
            session.authAccessToken,
        )
        if (!response.optBoolean("ok", false)) {
            error(response.optString("message").ifBlank { "No se pudo pagar el ticket deportivo." })
        }
        return parseSportsbookTicketRecord(response.optJSONObject("ticket") ?: JSONObject())
            ?: error("Servidor no devolvio el ticket deportivo pagado.")
    }

    fun settleTicket(
        session: ActiveSession,
        ticketId: String,
        nextStatus: SportsbookTicketStatus,
        reason: String = "",
    ): SportsbookTicketRecord {
        val response = edgeClient.invokeAuthenticated(
            "settle-sports-ticket",
            buildSportsbookSettlementPayload(session, ticketId, nextStatus, reason),
            session.authAccessToken,
        )
        if (!response.optBoolean("ok", false)) {
            error(response.optString("message").ifBlank { "No se pudo liquidar el ticket deportivo." })
        }
        return parseSportsbookTicketRecord(response.optJSONObject("ticket") ?: JSONObject())
            ?: error("Servidor no devolvio el ticket deportivo liquidado.")
    }
}

data class SportsbookTicketSnapshot(
    val tickets: List<SportsbookTicketRecord> = emptyList(),
    val summary: SportsbookTicketSummary = SportsbookTicketSummary(),
)

internal fun buildSportsbookTicketPayload(
    session: ActiveSession,
    draft: SportsbookTicketDraft,
    clientRequestId: String,
): JSONObject {
    val actorKey = sportsActorKey(session)
    val adminKey = sportsAdminKey(session)
    val cashierKey = if (session.role == UserRole.CASHIER) actorKey else ""
    return JSONObject()
        .put("actorRole", session.role.name.lowercase(Locale.US))
        .put("actorKey", actorKey)
        .put("ownerKey", adminKey.ifBlank { actorKey })
        .put("adminKey", adminKey.ifBlank { actorKey })
        .put("cashierKey", cashierKey.ifBlank { actorKey })
        .put("sellerUsername", session.username.ifBlank { actorKey })
        .put("bancaName", session.banca.orEmpty())
        .put("clientRequestId", clientRequestId)
        .put("stake", draft.stake)
        .put(
            "selections",
            JSONArray().apply {
                draft.selections.forEach { selection ->
                    put(JSONObject().put("oddsId", selection.oddsId))
                }
            },
        )
}

internal fun buildSportsbookTicketListPayload(
    session: ActiveSession,
    status: String,
    limit: Int,
): JSONObject {
    val actorKey = sportsActorKey(session)
    val adminKey = sportsAdminKey(session)
    val cashierKey = if (session.role == UserRole.CASHIER) actorKey else ""
    return JSONObject()
        .put("actorRole", session.role.name.lowercase(Locale.US))
        .put("actorKey", actorKey)
        .put("ownerKey", adminKey.ifBlank { actorKey })
        .put("adminKey", adminKey.ifBlank { actorKey })
        .put("cashierKey", cashierKey.ifBlank { actorKey })
        .put("status", status)
        .put("limit", limit.coerceIn(1, 100))
}

internal fun buildSportsbookPayTicketPayload(
    session: ActiveSession,
    ticketId: String,
): JSONObject {
    val actorKey = sportsActorKey(session)
    val adminKey = sportsAdminKey(session)
    val cashierKey = if (session.role == UserRole.CASHIER) actorKey else ""
    return JSONObject()
        .put("actorRole", session.role.name.lowercase(Locale.US))
        .put("actorKey", actorKey)
        .put("ownerKey", adminKey.ifBlank { actorKey })
        .put("adminKey", adminKey.ifBlank { actorKey })
        .put("cashierKey", cashierKey.ifBlank { actorKey })
        .put("ticketId", ticketId)
}

internal fun buildSportsbookSettlementPayload(
    session: ActiveSession,
    ticketId: String,
    nextStatus: SportsbookTicketStatus,
    reason: String = "",
): JSONObject {
    val actorKey = sportsActorKey(session)
    val adminKey = sportsAdminKey(session)
    val cashierKey = if (session.role == UserRole.CASHIER) actorKey else ""
    return JSONObject()
        .put("actorRole", session.role.name.lowercase(Locale.US))
        .put("actorKey", actorKey)
        .put("ownerKey", adminKey.ifBlank { actorKey })
        .put("adminKey", adminKey.ifBlank { actorKey })
        .put("cashierKey", cashierKey.ifBlank { actorKey })
        .put("ticketId", ticketId)
        .put("nextStatus", nextStatus.wireValue)
        .put("reason", reason)
}

internal fun sportsActorKey(session: ActiveSession): String {
    return session.userId.ifBlank { session.username }.trim()
}

internal fun sportsAdminKey(session: ActiveSession): String {
    return session.adminId
        ?.takeIf { it.isNotBlank() }
        ?: session.adminUser?.takeIf { it.isNotBlank() }
        ?: sportsActorKey(session)
}

internal fun selectionCanBeSold(selection: SportsbookSelection): Boolean {
    return selection.oddsId.isNotBlank() && selection.decimalOdds > 1.0
}

internal fun parseSportsbookTicketSaleResult(response: JSONObject): SportsbookTicketSaleResult {
    val ticket = response.optJSONObject("ticket") ?: JSONObject()
    val status = SportsbookTicketStatus.entries.firstOrNull {
        it.wireValue == ticket.optString("status")
    } ?: SportsbookTicketStatus.PENDING
    return SportsbookTicketSaleResult(
        ticketCode = ticket.optString("ticket_code").ifBlank { "SN-PENDIENTE" },
        status = status,
        stake = ticket.optDouble("stake", 0.0),
        decimalOdds = ticket.optDouble("decimal_odds", 0.0),
        potentialPayout = ticket.optDouble("potential_payout", 0.0),
        duplicate = response.optBoolean("duplicate", false),
    )
}

internal fun parseSportsbookTicketSnapshot(response: JSONObject): SportsbookTicketSnapshot {
    val ticketsArray = response.optJSONArray("tickets") ?: response.optJSONArray("data") ?: org.json.JSONArray()
    val tickets = buildList {
        for (index in 0 until ticketsArray.length()) {
            ticketsArray.optJSONObject(index)?.let(::parseSportsbookTicketRecord)?.let(::add)
        }
    }
    val summaryJson = response.optJSONObject("summary") ?: JSONObject()
    return SportsbookTicketSnapshot(
        tickets = tickets,
        summary = SportsbookTicketSummary(
            totalTickets = summaryJson.optInt("totalTickets", tickets.size),
            pendingTickets = summaryJson.optInt("pendingTickets", tickets.count { it.status == SportsbookTicketStatus.PENDING }),
            wonTickets = summaryJson.optInt("wonTickets", tickets.count { it.status == SportsbookTicketStatus.WON }),
            paidTickets = summaryJson.optInt("paidTickets", tickets.count { it.status == SportsbookTicketStatus.PAID }),
            totalStake = summaryJson.optDouble("totalStake", tickets.sumOf { it.stake }),
            pendingPayout = summaryJson.optDouble("pendingPayout", tickets.filter { it.status == SportsbookTicketStatus.PENDING }.sumOf { it.potentialPayout }),
            paidPayout = summaryJson.optDouble("paidPayout", tickets.filter { it.status == SportsbookTicketStatus.PAID }.sumOf { it.potentialPayout }),
        ),
    )
}

private fun parseSportsbookTicketRecord(json: JSONObject): SportsbookTicketRecord? {
    val ticketCode = json.optString("ticketCode").ifBlank { json.optString("ticket_code") }
    if (ticketCode.isBlank()) return null
    val status = sportsbookTicketStatus(json.optString("status"))
    val legsArray = json.optJSONArray("legs") ?: org.json.JSONArray()
    return SportsbookTicketRecord(
        id = json.optString("id"),
        ticketCode = ticketCode,
        sellerUsername = json.optString("sellerUsername").ifBlank { json.optString("seller_username") },
        bancaName = json.optString("bancaName").ifBlank { json.optString("banca_name") },
        ticketType = json.optString("ticketType").ifBlank { json.optString("ticket_type", "straight") },
        stake = json.optDouble("stake", 0.0),
        decimalOdds = json.optDouble("decimalOdds", json.optDouble("decimal_odds", 0.0)),
        potentialPayout = json.optDouble("potentialPayout", json.optDouble("potential_payout", 0.0)),
        status = status,
        soldAtEpochMs = parseSportsbookIsoEpochMs(json.optString("soldAt").ifBlank { json.optString("sold_at") }),
        legs = buildList {
            for (index in 0 until legsArray.length()) {
                val leg = legsArray.optJSONObject(index) ?: continue
                add(
                    SportsbookTicketLegRecord(
                        eventLabel = leg.optString("eventLabel").ifBlank { leg.optString("event_label") },
                        marketTitle = leg.optString("marketTitle").ifBlank { leg.optString("market_title") },
                        selectionLabel = leg.optString("selectionLabel").ifBlank { leg.optString("selection_label") },
                        decimalOdds = leg.optDouble("decimalOdds", leg.optDouble("decimal_odds", 0.0)),
                        status = sportsbookTicketStatus(leg.optString("status")),
                    ),
                )
            }
        },
    )
}

private fun sportsbookTicketStatus(raw: String): SportsbookTicketStatus {
    return SportsbookTicketStatus.entries.firstOrNull { it.wireValue == raw.trim().lowercase(Locale.US) }
        ?: SportsbookTicketStatus.PENDING
}

private fun parseSportsbookIsoEpochMs(raw: String): Long {
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
}
