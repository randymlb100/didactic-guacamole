package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.storage.LocalSalesRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

private const val RECENT_AUTHORITATIVE_TICKET_LIMIT = 1000

data class NativeTicketCloudSyncResult(
    val ok: Boolean,
    val message: String,
    val pushedCount: Int = 0,
    val pulledCount: Int = 0,
)

class NativeTicketCloudSyncCoordinator(
    private val salesRepository: LocalSalesRepository,
    private val queueRepository: NativeTicketSyncQueueRepository,
    private val remoteStore: NativeTicketRemoteStore = NativeTicketRemoteStore(),
) : TicketCloudSyncGateway {
    override fun enqueueAndFlush(ticket: TicketRecord, banca: String?): NativeTicketCloudSyncResult {
        queueRepository.enqueue(ticketRecordToWebCompatibleJson(ticket, banca))
        return flushOwner(resolveOwnerKey(ticket), banca)
    }

    override fun hydrateOwner(ownerKey: String, banca: String?): NativeTicketCloudSyncResult {
        return runCatching {
            val normalizedOwner = ownerKey.trim()
            if (normalizedOwner.isBlank()) {
                return NativeTicketCloudSyncResult(false, "No hay banca/admin para sincronizar tickets.")
            }
            val remoteSnapshot = fetchRecentAuthoritativeSnapshot(normalizedOwner)
            val existingTickets = salesRepository.getAllTickets()
                .filter { ticket -> matchesOwner(ticket, normalizedOwner) }
            val visibleTickets = reconcileMonotonicTickets(
                existing = existingTickets,
                remote = remoteSnapshot.tickets,
                deletedIds = remoteSnapshot.deletedIds,
                completeScope = false,
            )
            if (remoteSnapshot.tickets.isEmpty() &&
                remoteSnapshot.deletedIds.isEmpty() &&
                existingTickets.isNotEmpty()
            ) {
                return NativeTicketCloudSyncResult(
                    ok = true,
                    message = "Servidor sin tickets nuevos; se mantiene cache local.",
                    pushedCount = 0,
                    pulledCount = 0,
                )
            }
            persistMonotonicTicketReconciliation(
                salesRepository = salesRepository,
                reconciled = visibleTickets,
                remote = remoteSnapshot.tickets,
                deletedIds = remoteSnapshot.deletedIds,
            )
            NativeTicketCloudSyncResult(
                ok = true,
                message = "Tickets cargados del servidor.",
                pushedCount = 0,
                pulledCount = visibleTickets.size,
            )
        }.getOrElse { error ->
            NativeTicketCloudSyncResult(false, error.message ?: "No se pudo cargar tickets del servidor.")
        }
    }

    override fun flushOwner(ownerKey: String, banca: String?): NativeTicketCloudSyncResult {
        return runCatching {
            val normalizedOwner = ownerKey.trim()
            if (normalizedOwner.isBlank()) {
                return NativeTicketCloudSyncResult(false, "No hay banca/admin para subir tickets.")
            }
            val pendingJson = queueRepository.peekAll()
                .filter { json -> matchesNativeTicketSyncOwner(json, normalizedOwner) }
            val pendingTickets = parseWebTicketsPayload(JSONArray(pendingJson).toString())
            val remoteSnapshot = fetchRecentAuthoritativeSnapshot(normalizedOwner)
            val deletedIds = remoteSnapshot.deletedIds
            val merged = reconcileMonotonicTickets(
                existing = salesRepository.getAllTickets()
                    .filter { ticket -> matchesOwner(ticket, normalizedOwner) },
                remote = remoteSnapshot.tickets,
                deletedIds = deletedIds,
                completeScope = false,
            )
            persistMonotonicTicketReconciliation(
                salesRepository = salesRepository,
                reconciled = merged,
                remote = remoteSnapshot.tickets,
                deletedIds = deletedIds,
            )
            val confirmedPendingTickets = pendingTickets.filter { pending ->
                remoteSnapshot.tickets.any { remote -> sameTicketIdentity(pending, remote) }
            }
            queueRepository.removeByIds(confirmedPendingTickets.map { it.id } + deletedIds)
            val allPendingConfirmed = confirmedPendingTickets.size == pendingTickets.size
            NativeTicketCloudSyncResult(
                ok = allPendingConfirmed,
                message = if (allPendingConfirmed) {
                    "Tickets confirmados con el servidor."
                } else {
                    "Venta oficial guardada; confirmacion de lista pendiente."
                },
                pushedCount = confirmedPendingTickets.size,
                pulledCount = remoteSnapshot.tickets.size,
            )
        }.getOrElse { error ->
            NativeTicketCloudSyncResult(false, error.message ?: "No se pudo sincronizar tickets.")
        }
    }

    override fun flushOwnerLocalSnapshot(ownerKey: String, banca: String?): NativeTicketCloudSyncResult {
        return flushOwner(ownerKey, banca)
    }

    private fun resolveOwnerKey(ticket: TicketRecord): String {
        return ticket.adminId?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
            ?: ticket.sellerId.orEmpty()
    }

    private fun matchesOwner(ticket: TicketRecord, ownerKey: String): Boolean {
        return matchesNativeTicketSyncOwner(ticket, ownerKey)
    }

    private fun fetchRecentAuthoritativeSnapshot(ownerKey: String): NativeTicketRemoteSnapshot {
        val (fromDate, toDate) = recentAuthoritativeDayRange()
        return remoteStore.fetchSnapshot(
            ownerKey = ownerKey,
            fromDate = fromDate,
            toDate = toDate,
            limit = RECENT_AUTHORITATIVE_TICKET_LIMIT,
        )
    }

}

private fun sameTicketIdentity(left: TicketRecord, right: TicketRecord): Boolean {
    val leftKeys = setOf(left.id, left.serial.orEmpty())
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() }
        .toSet()
    val rightKeys = setOf(right.id, right.serial.orEmpty())
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() }
        .toSet()
    return leftKeys.any(rightKeys::contains)
}

private fun recentAuthoritativeDayRange(): Pair<String, String> {
    val zone = TimeZone.getTimeZone("America/Santo_Domingo")
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
    val today = Calendar.getInstance(zone)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return format.format(yesterday.time) to format.format(today.time)
}

internal fun reconcileAuthoritativeOwnerSnapshot(
    remoteTickets: List<TicketRecord>,
    pendingTickets: List<TicketRecord>,
    deletedIds: Set<String>,
): List<TicketRecord> {
    return filterServerVisibleTickets(
        tickets = mergeTicketsPreferImported(
            existing = filterServerVisibleTickets(remoteTickets, deletedIds),
            imported = filterServerVisibleTickets(pendingTickets, deletedIds),
        ),
        deletedIds = deletedIds,
    )
}

internal fun deletedIdsMissingFromRemote(localDeletedIds: Set<String>, remoteDeletedIds: Set<String>): Set<String> {
    val remote = remoteDeletedIds
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    return localDeletedIds
        .filter { id -> id.trim().isNotBlank() && id.trim().lowercase() !in remote }
        .toSet()
}

internal fun matchesNativeTicketSyncOwner(ticket: TicketRecord, ownerKey: String): Boolean {
    val normalizedOwner = ownerKey.trim()
    if (normalizedOwner.isBlank()) return false
    return ticket.adminId.equals(normalizedOwner, ignoreCase = true) ||
        ticket.adminUser.equals(normalizedOwner, ignoreCase = true) ||
        ticket.sellerId.equals(normalizedOwner, ignoreCase = true) ||
        ticket.sellerUser.equals(normalizedOwner, ignoreCase = true)
}

internal fun matchesNativeTicketSyncOwner(json: JSONObject, ownerKey: String): Boolean {
    val normalizedOwner = ownerKey.trim()
    if (normalizedOwner.isBlank()) return false
    return listOf("adminId", "adminUser", "vendedorId", "vendedorNombre", "cajeroId")
        .any { key -> json.optString(key).equals(normalizedOwner, ignoreCase = true) }
}
