package com.lotterynet.pro.core.sales

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.remote.SupabaseEdgeException
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class BackendTicketPlay(
    val playType: String,
    val number: String,
    val amount: Double,
    val potentialPayout: Double = 0.0,
    val lotteryId: String? = null,
    val lotteryName: String? = null,
    val secondaryLotteryId: String? = null,
    val secondaryLotteryName: String? = null,
)

data class BackendTicketRequest(
    val clientRequestId: String,
    val localTicketId: String? = null,
    val adminKey: String,
    val adminId: String? = null,
    val actorKey: String? = null,
    val actorId: String? = null,
    val actorRole: String? = null,
    val cashierKey: String,
    val cashierId: String? = null,
    val sorteoId: String? = null,
    val drawDate: String,
    val dayKey: String? = null,
    val lotteryName: String? = null,
    val lotteryEndpoint: String? = null,
    val phoneTimeIso: String? = null,
    val plays: List<BackendTicketPlay>,
)

data class BackendTicketActionRequest(
    val actorKey: String,
    val adminKey: String? = null,
    val ownerKey: String? = null,
    val cashierKey: String? = null,
    val ticketId: String? = null,
    val localTicketId: String? = null,
    val clientRequestId: String? = null,
    val action: String = "void",
    val returnLimit: Boolean = false,
)

data class BackendReportRequest(
    val actorKey: String,
    val adminKey: String? = null,
    val cashierKey: String? = null,
    val supervisorKey: String? = null,
    val from: String,
    val to: String,
)

class SupabaseTicketBackendException(
    val userMessage: String,
    technicalMessage: String,
) : IllegalStateException(technicalMessage)

internal fun isSupabaseTicketBackendTimeout(message: String?): Boolean {
    val normalized = message.orEmpty().lowercase()
    return normalized.contains("statement timeout") ||
        normalized.contains("canceling statement due to") ||
        normalized.contains("read timed out") ||
        normalized.contains("timeout")
}

internal fun presentSupabaseTicketBackendMessage(message: String?): String {
    val clean = message.orEmpty().trim()
    if (isSupabaseTicketBackendTimeout(clean)) {
        return "El servidor tardo demasiado validando la venta. No se guardo el ticket. Intenta de nuevo."
    }
    val normalized = clean.lowercase(Locale.US)
    if (normalized.contains("sorteo cerrado") || normalized.contains("lottery_closed_next_or_delete")) {
        return "Sorteo cerrado. Pasala al siguiente sorteo o borra esa jugada."
    }
    return clean.takeIf { it.isNotBlank() }
        ?: "No se pudo validar la venta en el servidor."
}

internal fun ticketBackendUserMessage(error: Throwable): String {
    return (error as? SupabaseTicketBackendException)?.userMessage
        ?: (error as? SupabaseEdgeException)?.userMessage
        ?: presentSupabaseTicketBackendMessage(error.message)
}

internal fun shouldReportSupabaseTicketBackendFailure(error: Throwable): Boolean {
    if (error is SupabaseTicketBackendException && isSupabaseTicketBackendTimeout(error.message)) return false
    if (error is SupabaseEdgeException) return false
    return !isSupabaseTicketBackendTimeout(error.message)
}

internal fun serverTicketPlayType(playType: String): String {
    return playType.trim().uppercase(Locale.US).ifBlank { playType }
}

internal fun serverPickPlayTypeAlias(playType: String): String? {
    return when (playType.trim().uppercase(Locale.US)) {
        "P3" -> "PICK3_STRAIGHT"
        "P3BOX" -> "PICK3_BOX"
        "P4" -> "PICK4_STRAIGHT"
        "P4BOX" -> "PICK4_BOX"
        else -> null
    }
}

internal fun serverPickGame(playType: String): String? {
    return when (playType.trim().uppercase(Locale.US)) {
        "P3", "P3BOX" -> "PICK3"
        "P4", "P4BOX" -> "PICK4"
        else -> null
    }
}

internal fun serverPickMode(playType: String): String? {
    return when (playType.trim().uppercase(Locale.US)) {
        "P3", "P4" -> "STRAIGHT"
        "P3BOX", "P4BOX" -> "BOX"
        else -> null
    }
}

internal fun serverUuidOrNull(value: String?): String? {
    val clean = value.orEmpty().trim()
    return clean.takeIf {
        it.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
    }
}

class SupabaseTicketBackendClient(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) {
    fun createTicket(request: BackendTicketRequest, bearerToken: String? = null): JSONObject {
        return edgeClient.invokeAuthenticated(createTicketFunctionSlug(), buildCreateTicketPayload(request), bearerToken)
    }

    fun voidTicket(request: BackendTicketActionRequest, bearerToken: String? = null): JSONObject {
        return edgeClient.invokeAuthenticated(ticketActionFunctionSlug(), buildTicketActionPayload(request), bearerToken)
    }

    fun payTicket(request: BackendTicketActionRequest, bearerToken: String? = null): JSONObject {
        return edgeClient.invokeAuthenticated(payTicketFunctionSlug(), buildTicketActionPayload(request.copy(action = "pay")), bearerToken)
    }

    fun deleteTicket(request: BackendTicketActionRequest, bearerToken: String? = null): JSONObject {
        return edgeClient.invokeAuthenticated(ticketActionFunctionSlug(), buildTicketActionPayload(request.copy(action = "delete")), bearerToken)
    }

    fun getReport(request: BackendReportRequest, bearerToken: String? = null): JSONObject {
        return edgeClient.invokeAuthenticated(reportFunctionSlug(request), buildReportPayload(request), bearerToken)
    }

    internal fun createTicketFunctionSlug(): String = "create-ticket-v2"

    internal fun payTicketFunctionSlug(): String = "pay-ticket"

    internal fun ticketActionFunctionSlug(): String = "void-ticket"

    internal fun reportFunctionSlug(request: BackendReportRequest): String {
        return if (!request.cashierKey.isNullOrBlank()) {
            "get-cashier-report"
        } else if (!request.supervisorKey.isNullOrBlank()) {
            "get-supervisor-report"
        } else {
            "get-admin-report"
        }
    }

    internal fun buildCreateTicketPayload(request: BackendTicketRequest): JSONObject {
        return JSONObject().apply {
            put("clientRequestId", request.clientRequestId)
            request.localTicketId?.let { put("localTicketId", it) }
            put("adminKey", request.adminKey)
            request.adminId?.let { put("adminId", it) }
            request.actorKey?.let { put("actorKey", it) }
            request.actorId?.let { put("actorId", it) }
            request.actorRole?.let { put("actorRole", it) }
            put("cashierKey", request.cashierKey)
            request.cashierId?.let { put("cashierId", it) }
            serverUuidOrNull(request.sorteoId)?.let { put("sorteoId", it) }
            put("drawDate", request.drawDate)
            request.dayKey?.let { put("dayKey", it) }
            request.lotteryName?.let { put("lotteryName", it) }
            request.lotteryEndpoint?.let { put("lotteryEndpoint", it) }
            request.phoneTimeIso?.let { put("phoneTime", it) }
            put(
                "plays",
                JSONArray().apply {
                    request.plays.forEach { play ->
                        val localPlayType = serverTicketPlayType(play.playType)
                        val serverPlayType = serverPickPlayTypeAlias(play.playType) ?: localPlayType
                        put(
                            JSONObject()
                                .put("playType", serverPlayType)
                                .put("number", play.number)
                                .put("amount", play.amount)
                                .put("potentialPayout", play.potentialPayout)
                                .apply {
                                    if (serverPlayType != localPlayType) {
                                        put("localPlayType", localPlayType)
                                    }
                                    serverPickPlayTypeAlias(play.playType)?.let { put("serverPlayType", it) }
                                    serverPickGame(play.playType)?.let { put("pickGame", it) }
                                    serverPickMode(play.playType)?.let { put("pickMode", it) }
                                    play.lotteryId?.let { put("lotteryId", it) }
                                    play.lotteryName?.let { put("lotteryName", it) }
                                    play.secondaryLotteryId?.let { put("secondaryLotteryId", it) }
                                    play.secondaryLotteryName?.let { put("secondaryLotteryName", it) }
                                },
                        )
                    }
                },
            )
        }
    }

    internal fun buildTicketActionPayload(request: BackendTicketActionRequest): JSONObject {
        return JSONObject().apply {
            put("actorKey", request.actorKey)
            request.adminKey?.let { put("adminKey", it) }
            request.ownerKey?.let { put("ownerKey", it) }
            request.cashierKey?.let { put("cashierKey", it) }
            request.ticketId?.let { put("ticketId", it) }
            request.localTicketId?.let { put("localTicketId", it) }
            request.clientRequestId?.let { put("clientRequestId", it) }
            put("action", request.action)
            if (request.returnLimit) put("returnLimit", true)
        }
    }

    internal fun buildReportPayload(request: BackendReportRequest): JSONObject {
        return JSONObject().apply {
            put("actorKey", request.actorKey)
            request.adminKey?.let { put("adminKey", it) }
            request.cashierKey?.let { put("cashierKey", it) }
            request.supervisorKey?.let { put("supervisorKey", it) }
            put("from", request.from)
            put("to", request.to)
        }
    }

    internal fun createTicketEndpointPath(): String = edgeClient.functionPath(createTicketFunctionSlug())

    internal fun payTicketEndpointPath(): String = edgeClient.functionPath(payTicketFunctionSlug())

    internal fun ticketActionEndpointPath(): String = edgeClient.functionPath(ticketActionFunctionSlug())

    internal fun reportEndpointPath(request: BackendReportRequest): String = edgeClient.functionPath(reportFunctionSlug(request))
}
