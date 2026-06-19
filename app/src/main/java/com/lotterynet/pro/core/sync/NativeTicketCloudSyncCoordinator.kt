package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.DeletedTicketRef
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.storage.LocalSalesRepository
import org.json.JSONArray
import org.json.JSONObject

private const val OWNER_SNAPSHOT_PUSH_LIMIT = 300

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
            val remoteSnapshot = remoteStore.fetchSnapshot(normalizedOwner)
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
            val remoteSnapshot = remoteStore.fetchSnapshot(normalizedOwner)
            val localDeletedIds = salesRepository.getDeletedTicketRefs()
                .filter { ref -> matchesDeletedOwner(ref, normalizedOwner) }
                .map { ref -> ref.id }
                .toSet()
            val missingRemoteDeletedIds = deletedIdsMissingFromRemote(localDeletedIds, remoteSnapshot.deletedIds)
            val shouldPushSnapshot = pendingTickets.isNotEmpty() || missingRemoteDeletedIds.isNotEmpty()
            val deletedIds = remoteSnapshot.deletedIds + localDeletedIds
            val remoteAndPending = reconcileAuthoritativeOwnerSnapshot(
                remoteTickets = remoteSnapshot.tickets,
                pendingTickets = pendingTickets,
                deletedIds = deletedIds,
            )
            val merged = reconcileMonotonicTickets(
                existing = salesRepository.getAllTickets()
                    .filter { ticket -> matchesOwner(ticket, normalizedOwner) },
                remote = remoteAndPending,
                deletedIds = deletedIds,
                completeScope = false,
            )
            persistMonotonicTicketReconciliation(
                salesRepository = salesRepository,
                reconciled = merged,
                deletedIds = deletedIds,
            )
            if (shouldPushSnapshot) {
                remoteStore.upsertSnapshot(normalizedOwner, trimOwnerSnapshotForPush(merged), deletedIds, banca)
            }
            queueRepository.removeByIds(pendingTickets.map { it.id } + deletedIds)
            NativeTicketCloudSyncResult(
                ok = true,
                message = if (shouldPushSnapshot) {
                    "Tickets subidos y conciliados con servidor."
                } else {
                    "Tickets cargados del servidor."
                },
                pushedCount = pendingTickets.size,
                pulledCount = remoteSnapshot.tickets.size,
            )
        }.getOrElse { error ->
            NativeTicketCloudSyncResult(false, error.message ?: "No se pudo sincronizar tickets.")
        }
    }

    override fun flushOwnerLocalSnapshot(ownerKey: String, banca: String?): NativeTicketCloudSyncResult {
        return runCatching {
            val normalizedOwner = ownerKey.trim()
            if (normalizedOwner.isBlank()) {
                return NativeTicketCloudSyncResult(false, "No hay banca/admin para subir tickets.")
            }
            val remoteSnapshot = remoteStore.fetchSnapshot(normalizedOwner)
            val globalDeletedIds = salesRepository.getDeletedTicketIds()
            val localTickets = salesRepository.getAllTickets()
                .filter { ticket -> matchesOwner(ticket, normalizedOwner) }
                .sortedByDescending { it.createdAtEpochMs }
            val scopedDeletedIds = salesRepository.getDeletedTicketRefs()
                .filter { ref -> matchesDeletedOwner(ref, normalizedOwner) }
                .map { ref -> ref.id }
                .toSet()
            val deletedIds = remoteSnapshot.deletedIds + globalDeletedIds + scopedDeletedIds
            val remoteTickets = filterServerVisibleTickets(localTickets, deletedIds)
            queueRepository.removeByIds(deletedIds)
            remoteStore.upsertSnapshot(normalizedOwner, trimOwnerSnapshotForPush(remoteTickets), deletedIds, banca)
            NativeTicketCloudSyncResult(
                ok = true,
                message = "Tickets del servidor actualizados.",
                pushedCount = remoteTickets.size,
                pulledCount = remoteSnapshot.tickets.size,
            )
        }.getOrElse { error ->
            NativeTicketCloudSyncResult(false, error.message ?: "No se pudo actualizar tickets del servidor.")
        }
    }

    private fun resolveOwnerKey(ticket: TicketRecord): String {
        return ticket.adminId?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
            ?: ticket.sellerId.orEmpty()
    }

    private fun matchesOwner(ticket: TicketRecord, ownerKey: String): Boolean {
        return matchesNativeTicketSyncOwner(ticket, ownerKey)
    }

    private fun matchesDeletedOwner(ref: DeletedTicketRef, ownerKey: String): Boolean {
        return ref.adminId.equals(ownerKey, ignoreCase = true) ||
            ref.adminUser.equals(ownerKey, ignoreCase = true) ||
            ref.sellerId.equals(ownerKey, ignoreCase = true)
    }

}

private fun trimOwnerSnapshotForPush(tickets: List<TicketRecord>): List<TicketRecord> {
    return tickets
        .sortedByDescending { it.createdAtEpochMs }
        .take(OWNER_SNAPSHOT_PUSH_LIMIT)
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
