package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.storage.LocalSalesRepository
import java.util.Locale

internal fun reconcileMonotonicTickets(
    existing: List<TicketRecord>,
    remote: List<TicketRecord>,
    deletedIds: Set<String>,
    completeScope: Boolean,
): List<TicketRecord> {
    val normalizedDeletedIds = deletedIds
        .mapNotNull(::normalizedTicketId)
        .toSet()
    val baseline = if (completeScope) emptyList() else existing
    return mergeTicketsPreferImported(
        existing = baseline,
        imported = remote,
    ).filterNot { ticket ->
        normalizedTicketId(ticket.id) in normalizedDeletedIds ||
            isExplicitRemoteTicketTombstone(ticket.status)
    }
}

internal fun persistMonotonicTicketReconciliation(
    salesRepository: LocalSalesRepository,
    reconciled: List<TicketRecord>,
    remote: List<TicketRecord>,
    deletedIds: Set<String>,
) {
    val normalizedDeletedIds = authoritativeTicketTombstoneIds(
        remote = remote,
        deletedIds = deletedIds,
    )
    if (normalizedDeletedIds.isNotEmpty()) {
        salesRepository.getAllTickets()
            .filter { ticket -> normalizedTicketId(ticket.id) in normalizedDeletedIds }
            .forEach(salesRepository::deleteTicket)
    }
    salesRepository.mergeImportedTickets(reconciled)
}

private fun authoritativeTicketTombstoneIds(
    remote: List<TicketRecord>,
    deletedIds: Set<String>,
): Set<String> {
    return buildSet {
        deletedIds.mapNotNullTo(this, ::normalizedTicketId)
        remote.asSequence()
            .filter { ticket -> isExplicitRemoteTicketTombstone(ticket.status) }
            .mapNotNullTo(this) { ticket -> normalizedTicketId(ticket.id) }
    }
}

private fun normalizedTicketId(value: String?): String? {
    return value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.US)
}

private fun isExplicitRemoteTicketTombstone(status: String): Boolean {
    return status.trim().lowercase(Locale.US) in setOf(
        "deleted",
        "borrado",
        "removed",
    )
}
