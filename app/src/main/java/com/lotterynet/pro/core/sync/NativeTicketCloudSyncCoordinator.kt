package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.DeletedTicketRef
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.storage.LocalSalesRepository
import org.json.JSONArray
import org.json.JSONObject

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
            val pendingResult = flushOwner(normalizedOwner, banca)
            NativeTicketCloudSyncResult(
                ok = pendingResult.ok,
                message = if (pendingResult.ok) "Tickets sincronizados con servidor." else pendingResult.message,
                pushedCount = pendingResult.pushedCount,
                pulledCount = pendingResult.pulledCount,
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
            val localTickets = salesRepository.getAllTickets()
                .filter { ticket -> matchesNativeTicketSyncOwner(ticket, normalizedOwner) }
            val localDeletedIds = salesRepository.getDeletedTicketRefs()
                .filter { ref -> matchesDeletedOwner(ref, normalizedOwner) }
                .map { ref -> ref.id }
                .toSet()
            val deletedIds = remoteSnapshot.deletedIds + localDeletedIds
            val merged = filterServerVisibleTickets(
                tickets = mergeTicketsPreferImported(
                    existing = mergeTicketsPreferImported(
                        filterServerVisibleTickets(remoteSnapshot.tickets, deletedIds),
                        filterServerVisibleTickets(localTickets, deletedIds),
                    ),
                    imported = pendingTickets,
                ),
                deletedIds = deletedIds,
            )
            salesRepository.replaceScopedImportedTickets(normalizedOwner, merged)
            remoteStore.upsertSnapshot(normalizedOwner, merged, deletedIds, banca)
            queueRepository.removeByIds(pendingTickets.map { it.id } + deletedIds)
            NativeTicketCloudSyncResult(
                ok = true,
                message = "Tickets subidos y conciliados con servidor.",
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
            remoteStore.upsertSnapshot(normalizedOwner, remoteTickets, deletedIds, banca)
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
