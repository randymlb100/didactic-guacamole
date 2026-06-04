package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOperationalSyncContractsTest {

    @Test
    fun `cashier operational owner resolves to admin id`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-1",
            username = "cajero1",
            adminId = "admin-1",
            adminUser = "admin",
            banca = "Banca Central",
        )

        assertEquals("admin-1", resolveOperationalOwnerKey(session))
    }

    @Test
    fun `admin operational owner falls back to own user id`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca Central",
        )

        assertEquals("admin-1", resolveOperationalOwnerKey(session))
    }

    @Test
    fun `admin operational owner keys include id and username aliases`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "auth-user-id",
            username = "nicola01",
            adminId = "ADM-C5FFB0",
            adminUser = "nicola01",
            banca = "Banca Central",
        )

        assertEquals(listOf("ADM-C5FFB0", "auth-user-id", "nicola01"), resolveOperationalOwnerKeys(session))
    }

    @Test
    fun `remote polling hydrates only when stamp changes unless forced`() {
        assertFalse(shouldHydrateOperationalRemote(lastRemoteUpdatedAt = "2026-04-24T10:00:00Z", remoteUpdatedAt = "2026-04-24T10:00:00Z", force = false))
        assertTrue(shouldHydrateOperationalRemote(lastRemoteUpdatedAt = "2026-04-24T10:00:00Z", remoteUpdatedAt = "2026-04-24T10:01:00Z", force = false))
        assertTrue(shouldHydrateOperationalRemote(lastRemoteUpdatedAt = "2026-04-24T10:00:00Z", remoteUpdatedAt = "2026-04-24T10:00:00Z", force = true))
    }

    @Test
    fun `sync governor coalesces repeated owner hydrates inside minimum window`() {
        var nowMs = 1_000L
        val governor = SyncGovernor(nowEpochMs = { nowMs })

        val firstPermit = governor.tryStartOwnerHydrate("ADMIN-1", force = false)
        assertNotNull(firstPermit)
        governor.finishOwnerHydrate(firstPermit!!)

        nowMs = 10_000L
        assertNull(governor.tryStartOwnerHydrate("admin-1", force = false))

        nowMs = 32_000L
        assertNotNull(governor.tryStartOwnerHydrate("admin-1", force = false))
    }

    @Test
    fun `sync governor lets forced hydrates bypass recent window but not in flight owner`() {
        var nowMs = 1_000L
        val governor = SyncGovernor(nowEpochMs = { nowMs })

        val firstPermit = governor.tryStartOwnerHydrate("admin-2", force = false)
        assertNotNull(firstPermit)
        assertNull(governor.tryStartOwnerHydrate("admin-2", force = true))
        governor.finishOwnerHydrate(firstPermit!!)

        nowMs = 2_000L
        assertNotNull(governor.tryStartOwnerHydrate("admin-2", force = true))
    }

    @Test
    fun `coordinator reports synchronized state after ticket hydrate`() {
        val tickets = FakeTicketGateway(
            result = NativeTicketCloudSyncResult(
                ok = true,
                message = "Tickets sincronizados con servidor.",
                pushedCount = 1,
                pulledCount = 2,
            ),
        )
        val stamps = FakeRemoteStampStore(remoteUpdatedAt = "2026-04-24T10:01:00Z")
        val coordinator = NativeOperationalSyncCoordinator(
            ticketGateway = tickets,
            remoteStampStore = stamps,
            nowEpochMs = { 1234L },
        )
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca Central",
        )

        val state = coordinator.syncTicketsForSession(
            session = session,
            lastRemoteUpdatedAt = "2026-04-24T10:00:00Z",
        )

        assertTrue(state.ok)
        assertEquals(NativeOperationalSyncStatus.SYNCED, state.status)
        assertEquals("admin-1", state.ownerKey)
        assertEquals("2026-04-24T10:01:00Z", state.remoteUpdatedAt)
        assertEquals(2, state.pushedCount)
        assertEquals(4, state.pulledCount)
        assertEquals(2, tickets.hydrateCalls)
    }

    @Test
    fun `coordinator uses same governor for manual and realtime refresh bursts`() {
        var nowMs = 1_000L
        val tickets = FakeTicketGateway()
        val stamps = MutableRemoteStampStore(remoteUpdatedAt = "2026-05-18T20:00:00Z")
        val coordinator = NativeOperationalSyncCoordinator(
            ticketGateway = tickets,
            remoteStampStore = stamps,
            nowEpochMs = { nowMs },
            syncGovernor = SyncGovernor(nowEpochMs = { nowMs }),
        )
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca Central",
        )

        val first = coordinator.syncTicketsForSession(
            session = session,
            lastRemoteUpdatedAt = "2026-05-18T19:59:00Z",
        )
        stamps.remoteUpdatedAt = "2026-05-18T20:00:10Z"
        nowMs = 5_000L
        val second = coordinator.refreshOwnerFromRealtime("admin-1", "Banca Central")
        nowMs = 6_000L
        val third = coordinator.syncTicketsForSession(
            session = session,
            lastRemoteUpdatedAt = "2026-05-18T20:00:00Z",
        )

        assertEquals(NativeOperationalSyncStatus.SYNCED, first.status)
        assertEquals(NativeOperationalSyncStatus.UP_TO_DATE, second.status)
        assertEquals(NativeOperationalSyncStatus.UP_TO_DATE, third.status)
        assertEquals("Sync ya reciente.", second.message)
        assertEquals("Sync ya reciente.", third.message)
        assertEquals(2, tickets.hydrateCalls)
    }

    @Test
    fun `realtime refresh hydrates when no recent owner sync was recorded`() {
        val tickets = FakeTicketGateway()
        val stamps = FakeRemoteStampStore(remoteUpdatedAt = "2026-05-18T20:00:00Z")
        val coordinator = NativeOperationalSyncCoordinator(
            ticketGateway = tickets,
            remoteStampStore = stamps,
            nowEpochMs = { 1_000L },
            syncGovernor = SyncGovernor(nowEpochMs = { 1_000L }),
        )

        val state = coordinator.refreshOwnerFromRealtime("admin-1", "Banca Central")

        assertEquals(NativeOperationalSyncStatus.SYNCED, state.status)
        assertEquals(1, tickets.hydrateCalls)
    }

    @Test
    fun `coordinator skips unchanged remote stamp without forcing hydrate`() {
        val tickets = FakeTicketGateway()
        val coordinator = NativeOperationalSyncCoordinator(
            ticketGateway = tickets,
            remoteStampStore = FakeRemoteStampStore(remoteUpdatedAt = "same"),
            nowEpochMs = { 1234L },
        )
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
        )

        val state = coordinator.syncTicketsForSession(
            session = session,
            lastRemoteUpdatedAt = "same",
        )

        assertTrue(state.ok)
        assertEquals(NativeOperationalSyncStatus.UP_TO_DATE, state.status)
        assertEquals(0, tickets.hydrateCalls)
    }

    @Test
    fun `flush ticket reports pending when supabase fails after local save`() {
        val tickets = FakeTicketGateway(
            result = NativeTicketCloudSyncResult(
                ok = false,
                message = "Sin conexión",
            ),
        )
        val coordinator = NativeOperationalSyncCoordinator(
            ticketGateway = tickets,
            remoteStampStore = FakeRemoteStampStore(remoteUpdatedAt = null),
            nowEpochMs = { 1234L },
        )

        val state = coordinator.flushTicket(
            ticket = com.lotterynet.pro.core.model.TicketRecord(
                id = "native-1",
                sellerId = "cashier-1",
                adminId = "admin-1",
                total = 50.0,
            ),
            banca = "Banca Central",
        )

        assertFalse(state.ok)
        assertEquals(NativeOperationalSyncStatus.PENDING, state.status)
        assertEquals("admin-1", state.ownerKey)
        assertEquals("Pendiente de sync: Sin conexión", state.message)
        assertEquals(1, tickets.enqueueAndFlushCalls)
    }

    @Test
    fun `coordinator flushes local owner snapshot for server deletion`() {
        val tickets = FakeTicketGateway(
            result = NativeTicketCloudSyncResult(
                ok = true,
                message = "Snapshot sincronizado",
                pushedCount = 3,
            ),
        )
        val coordinator = NativeOperationalSyncCoordinator(
            ticketGateway = tickets,
            remoteStampStore = FakeRemoteStampStore(remoteUpdatedAt = "2026-04-24T10:02:00Z"),
            nowEpochMs = { 1234L },
        )

        val state = coordinator.flushOwnerLocalSnapshot("admin-1", "Banca Central")

        assertTrue(state.ok)
        assertEquals(NativeOperationalSyncStatus.SYNCED, state.status)
        assertEquals("admin-1", state.ownerKey)
        assertEquals(3, state.pushedCount)
        assertEquals(1, tickets.flushOwnerLocalSnapshotCalls)
    }

    @Test
    fun `authoritative owner snapshot keeps remote and pending but drops stale local-only tickets`() {
        val remoteTicket = TicketRecord(id = "server-ticket", total = 1.0)
        val pendingTicket = TicketRecord(id = "pending-ticket", total = 2.0)
        val deletedTicket = TicketRecord(id = "deleted-ticket", total = 3.0)

        val merged = reconcileAuthoritativeOwnerSnapshot(
            remoteTickets = listOf(remoteTicket, deletedTicket),
            pendingTickets = listOf(pendingTicket),
            deletedIds = setOf("deleted-ticket"),
        )

        val mergedIds = merged.map { it.id }.toSet()
        assertEquals(setOf("pending-ticket", "server-ticket"), mergedIds)
        assertFalse(mergedIds.contains("deleted-ticket"))
    }

    private class FakeTicketGateway(
        private val result: NativeTicketCloudSyncResult = NativeTicketCloudSyncResult(true, "ok"),
    ) : TicketCloudSyncGateway {
        var hydrateCalls = 0
        var enqueueAndFlushCalls = 0
        var flushOwnerLocalSnapshotCalls = 0
        val hydratedOwners = mutableListOf<String>()

        override fun enqueueAndFlush(ticket: com.lotterynet.pro.core.model.TicketRecord, banca: String?): NativeTicketCloudSyncResult {
            enqueueAndFlushCalls += 1
            return result
        }

        override fun hydrateOwner(ownerKey: String, banca: String?): NativeTicketCloudSyncResult {
            hydrateCalls += 1
            hydratedOwners += ownerKey
            return result
        }

        override fun flushOwner(ownerKey: String, banca: String?): NativeTicketCloudSyncResult = result

        override fun flushOwnerLocalSnapshot(ownerKey: String, banca: String?): NativeTicketCloudSyncResult {
            flushOwnerLocalSnapshotCalls += 1
            return result
        }
    }

    private class FakeRemoteStampStore(
        private val remoteUpdatedAt: String?,
    ) : TicketRemoteStampStore {
        override fun fetchUpdatedAt(ownerKey: String): String? = remoteUpdatedAt
    }

    private class MutableRemoteStampStore(
        var remoteUpdatedAt: String?,
    ) : TicketRemoteStampStore {
        override fun fetchUpdatedAt(ownerKey: String): String? = remoteUpdatedAt
    }
}
