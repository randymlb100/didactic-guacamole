package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord

enum class NativeOperationalSyncStatus {
    SYNCED,
    UP_TO_DATE,
    PENDING,
    OFFLINE,
    ERROR,
}

data class NativeOperationalSyncState(
    val ok: Boolean,
    val status: NativeOperationalSyncStatus,
    val ownerKey: String,
    val message: String,
    val pushedCount: Int = 0,
    val pulledCount: Int = 0,
    val remoteUpdatedAt: String? = null,
    val refreshedAtEpochMs: Long = System.currentTimeMillis(),
)

interface TicketCloudSyncGateway {
    fun enqueueAndFlush(ticket: TicketRecord, banca: String? = null): NativeTicketCloudSyncResult
    fun hydrateOwner(ownerKey: String, banca: String? = null): NativeTicketCloudSyncResult
    fun flushOwner(ownerKey: String, banca: String? = null): NativeTicketCloudSyncResult
    fun flushOwnerLocalSnapshot(ownerKey: String, banca: String? = null): NativeTicketCloudSyncResult
}

interface TicketRemoteStampStore {
    fun fetchUpdatedAt(ownerKey: String): String?
}

class NativeOperationalSyncCoordinator(
    private val ticketGateway: TicketCloudSyncGateway,
    private val remoteStampStore: TicketRemoteStampStore = NativeTicketRemoteStore(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val syncGovernor: SyncGovernor = SyncGovernor.shared,
) {
    fun refreshOwnerFromRealtime(ownerKey: String, banca: String? = null): NativeOperationalSyncState {
        return syncTicketsForOwner(ownerKey, banca, force = false)
    }

    fun syncTicketsForSession(
        session: ActiveSession,
        lastRemoteUpdatedAt: String? = null,
        force: Boolean = false,
    ): NativeOperationalSyncState {
        val ownerKeys = resolveOperationalOwnerKeys(session)
        if (ownerKeys.isEmpty()) {
            return NativeOperationalSyncState(
                ok = false,
                status = NativeOperationalSyncStatus.ERROR,
                ownerKey = "",
                message = "No hay admin/banca para sincronizar.",
                refreshedAtEpochMs = nowEpochMs(),
            )
        }
        val states = mutableListOf<NativeOperationalSyncState>()
        ownerKeys.forEach { ownerKey ->
            states += syncOwnerWithStampGate(
                ownerKey = ownerKey,
                banca = session.banca,
                lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                force = force,
            )
        }
        if (states.isNotEmpty()) {
            val primary = states.first()
            val synced = states.any { it.status == NativeOperationalSyncStatus.SYNCED }
            val failed = states.firstOrNull { !it.ok }
            return NativeOperationalSyncState(
                ok = failed == null,
                status = failed?.status ?: if (synced) NativeOperationalSyncStatus.SYNCED else NativeOperationalSyncStatus.UP_TO_DATE,
                ownerKey = ownerKeys.first(),
                message = failed?.message ?: if (synced) "Tickets sincronizados con servidor." else primary.message,
                pushedCount = states.sumOf { it.pushedCount },
                pulledCount = states.sumOf { it.pulledCount },
                remoteUpdatedAt = states.mapNotNull { it.remoteUpdatedAt }.lastOrNull(),
                refreshedAtEpochMs = nowEpochMs(),
            )
        }
        return NativeOperationalSyncState(
            ok = true,
            status = NativeOperationalSyncStatus.UP_TO_DATE,
            ownerKey = ownerKeys.first(),
            message = "Datos al dia.",
            refreshedAtEpochMs = nowEpochMs(),
        )
    }

    fun flushTicket(ticket: TicketRecord, banca: String? = null): NativeOperationalSyncState {
        val ownerKey = ticket.adminId?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
            ?: ticket.sellerId.orEmpty()
        val result = ticketGateway.enqueueAndFlush(ticket, banca)
        return result.toOperationalState(
            ownerKey = ownerKey,
            remoteUpdatedAt = runCatching { remoteStampStore.fetchUpdatedAt(ownerKey) }.getOrNull(),
            refreshedAtEpochMs = nowEpochMs(),
        )
    }

    fun flushOwner(ownerKey: String, banca: String? = null): NativeOperationalSyncState {
        val result = ticketGateway.flushOwner(ownerKey, banca)
        return result.toOperationalState(
            ownerKey = ownerKey,
            remoteUpdatedAt = runCatching { remoteStampStore.fetchUpdatedAt(ownerKey) }.getOrNull(),
            refreshedAtEpochMs = nowEpochMs(),
        )
    }

    fun flushOwnerLocalSnapshot(ownerKey: String, banca: String? = null): NativeOperationalSyncState {
        val result = ticketGateway.flushOwnerLocalSnapshot(ownerKey, banca)
        return result.toOperationalState(
            ownerKey = ownerKey,
            remoteUpdatedAt = runCatching { remoteStampStore.fetchUpdatedAt(ownerKey) }.getOrNull(),
            refreshedAtEpochMs = nowEpochMs(),
        )
    }

    private fun syncTicketsForOwner(
        ownerKey: String,
        banca: String? = null,
        force: Boolean = false,
    ): NativeOperationalSyncState {
        val normalizedOwner = ownerKey.trim()
        if (normalizedOwner.isBlank()) {
            return NativeOperationalSyncState(
                ok = false,
                status = NativeOperationalSyncStatus.ERROR,
                ownerKey = "",
                message = "No hay admin/banca para sincronizar.",
                refreshedAtEpochMs = nowEpochMs(),
            )
        }
        return syncOwnerWithStampGate(
            ownerKey = normalizedOwner,
            banca = banca,
            lastRemoteUpdatedAt = null,
            force = force,
        )
    }

    private fun syncOwnerWithStampGate(
        ownerKey: String,
        banca: String?,
        lastRemoteUpdatedAt: String?,
        force: Boolean,
    ): NativeOperationalSyncState {
        val normalizedOwner = ownerKey.trim()
        val permit = syncGovernor.tryStartOwnerHydrate(normalizedOwner, force)
        if (permit == null) {
            return NativeOperationalSyncState(
                ok = true,
                status = NativeOperationalSyncStatus.UP_TO_DATE,
                ownerKey = normalizedOwner,
                message = "Sync ya reciente.",
                remoteUpdatedAt = lastRemoteUpdatedAt,
                refreshedAtEpochMs = nowEpochMs(),
            )
        }
        return try {
            val remoteStamp = runCatching { remoteStampStore.fetchUpdatedAt(normalizedOwner) }.getOrNull()
            if (!shouldHydrateOperationalRemote(lastRemoteUpdatedAt, remoteStamp, force)) {
                NativeOperationalSyncState(
                    ok = true,
                    status = NativeOperationalSyncStatus.UP_TO_DATE,
                    ownerKey = normalizedOwner,
                    message = "Datos al dia.",
                    remoteUpdatedAt = remoteStamp ?: lastRemoteUpdatedAt,
                    refreshedAtEpochMs = nowEpochMs(),
                )
            } else {
                val result = ticketGateway.hydrateOwner(normalizedOwner, banca)
                result.toOperationalState(
                    ownerKey = normalizedOwner,
                    remoteUpdatedAt = remoteStamp,
                    refreshedAtEpochMs = nowEpochMs(),
                )
            }
        } finally {
            syncGovernor.finishOwnerHydrate(permit, nowEpochMs())
        }
    }
}

fun resolveOperationalOwnerKey(session: ActiveSession?): String {
    return resolveOperationalOwnerKeys(session).firstOrNull().orEmpty()
}

fun resolveOperationalOwnerKeys(session: ActiveSession?): List<String> {
    if (session == null) return emptyList()
    return listOf(
        session.adminId,
        session.userId,
        session.adminUser,
        session.username,
    )
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotBlank() } }
        .distinctBy { it.lowercase() }
}

fun shouldHydrateOperationalRemote(
    lastRemoteUpdatedAt: String?,
    remoteUpdatedAt: String?,
    force: Boolean,
): Boolean {
    if (force) return true
    if (remoteUpdatedAt.isNullOrBlank()) return true
    return !remoteUpdatedAt.equals(lastRemoteUpdatedAt.orEmpty(), ignoreCase = true)
}

private fun NativeTicketCloudSyncResult.toOperationalState(
    ownerKey: String,
    remoteUpdatedAt: String?,
    refreshedAtEpochMs: Long,
): NativeOperationalSyncState {
    return NativeOperationalSyncState(
        ok = ok,
        status = if (ok) NativeOperationalSyncStatus.SYNCED else NativeOperationalSyncStatus.PENDING,
        ownerKey = ownerKey,
        message = if (ok) message else "Pendiente de sync: $message",
        pushedCount = pushedCount,
        pulledCount = pulledCount,
        remoteUpdatedAt = remoteUpdatedAt,
        refreshedAtEpochMs = refreshedAtEpochMs,
    )
}
