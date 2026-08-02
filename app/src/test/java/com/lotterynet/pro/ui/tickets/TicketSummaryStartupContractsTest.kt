package com.lotterynet.pro.ui.tickets

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.sync.TicketRefreshGovernor
import com.lotterynet.pro.core.sync.resolveOperationalRealtimeOwnerKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class TicketSummaryStartupContractsTest {
    @Test
    fun `ticket cashier filter shows edited names but stays in cashier order`() {
        val options = buildCashierOptions(
            listOf(
                UserAccount(id = "CAJ-03", user = "bancay03", role = UserRole.CASHIER, displayName = "Ana"),
                UserAccount(id = "CAJ-01", user = "bancay01", role = UserRole.CASHIER, displayName = "Zoe"),
                UserAccount(id = "CAJ-02", user = "bancay02", role = UserRole.CASHIER, displayName = "Carlos"),
            ),
        )

        assertEquals(listOf("", "CAJ-01", "CAJ-02", "CAJ-03"), options.map { it.value })
        assertEquals(listOf("Todos los cajeros", "Zoe", "Carlos", "Ana"), options.map { it.label })
    }

    @Test
    fun `ticket summary first frame uses local ticket data only`() {
        val plan = resolveTicketSummaryStartupPlan()

        assertTrue(plan.firstFrameWork.contains(TicketSummaryStartupWork.LOAD_SESSION))
        assertTrue(plan.firstFrameWork.contains(TicketSummaryStartupWork.LOAD_LOCAL_TICKETS))
        assertTrue(plan.firstFrameWork.contains(TicketSummaryStartupWork.LOAD_LOCAL_CASHIERS))
        assertFalse(plan.firstFrameWork.contains(TicketSummaryStartupWork.HYDRATE_REMOTE_TICKETS))
        assertFalse(plan.firstFrameWork.contains(TicketSummaryStartupWork.FLUSH_SYNC_QUEUE))
        assertFalse(plan.firstFrameWork.contains(TicketSummaryStartupWork.RENDER_TICKET_BITMAP))
    }

    @Test
    fun `ticket summary hydrates server after first frame`() {
        val plan = resolveTicketSummaryStartupPlan()

        assertTrue(plan.afterFirstFrameWork.contains(TicketSummaryStartupWork.HYDRATE_REMOTE_TICKETS))
        assertTrue(plan.afterFirstFrameWork.contains(TicketSummaryStartupWork.FLUSH_SYNC_QUEUE))
    }

    @Test
    fun `ticket summary shows local tickets first and keeps automatic refresh light`() {
        assertTrue(TICKET_SUMMARY_STARTUP_SYNC_DELAY_MS <= 150L)
        assertTrue(TICKET_SUMMARY_RESUME_SYNC_DELAY_MS <= 300L)
        assertTrue(TICKET_SUMMARY_POLL_MS >= 300_000L)
        assertTrue(TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS >= 60_000L)
        assertTrue(TICKET_SUMMARY_FOREGROUND_CATCH_UP_THROTTLE_MS >= 20_000L)
        assertEquals(TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS, resolveTicketSummaryPollIntervalMs(realtimeEnabled = false))
        assertEquals(
            TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS,
            resolveTicketSummaryPollIntervalMs(realtimeEnabled = true, realtimeConnected = false),
        )
        assertEquals(
            TICKET_SUMMARY_POLL_MS,
            resolveTicketSummaryPollIntervalMs(realtimeEnabled = true, realtimeConnected = true),
        )
        assertTrue(shouldStartTicketSummaryFallbackPoll(realtimeEnabled = false))
        assertFalse(shouldStartTicketSummaryFallbackPoll(realtimeEnabled = true))
        assertFalse(shouldForceTicketSummaryLivePoll())
    }

    @Test
    fun `ticket summary loads today before full archive so new sales appear immediately`() {
        val plan = resolveTicketSummaryLocalLoadPlan(nowEpochMs = 1_779_710_400_000L)

        assertEquals("2026-05-25", plan.firstFrameDayKey)
        assertTrue(plan.loadSingleDayFirst)
        assertTrue(plan.loadFullArchiveAfterFirstFrame)
    }

    @Test
    fun `ticket summary exposes a compact manual refresh action`() {
        val action = resolveTicketSummaryRefreshAction()

        assertEquals("Refrescar", action.label)
        assertTrue(action.compact)
        assertTrue(action.forceRemoteSync)
    }

    @Test
    fun `ticket summary clean install uses full server hydration before delta`() {
        assertTrue(
            shouldUseFullTicketHydrationForAutomaticCatchUp(
                hasTodayTickets = false,
                lastRemoteUpdatedAt = null,
                lastDeltaCursor = null,
            ),
        )
        assertFalse(
            shouldUseFullTicketHydrationForAutomaticCatchUp(
                hasTodayTickets = true,
                lastRemoteUpdatedAt = null,
                lastDeltaCursor = null,
            ),
        )
        assertFalse(
            shouldUseFullTicketHydrationForAutomaticCatchUp(
                hasTodayTickets = false,
                lastRemoteUpdatedAt = "2026-06-06T10:00:00Z",
                lastDeltaCursor = null,
            ),
        )
    }

    @Test
    fun `ticket summary automatic sync only rehydrates visible tickets when forced or manual`() {
        assertFalse(
            shouldHydrateVisibleTicketsAfterOperationalSync(
                showRefreshing = false,
                shouldForce = false,
            ),
        )
        assertTrue(
            shouldHydrateVisibleTicketsAfterOperationalSync(
                showRefreshing = true,
                shouldForce = false,
            ),
        )
        assertTrue(
            shouldHydrateVisibleTicketsAfterOperationalSync(
                showRefreshing = false,
                shouldForce = true,
            ),
        )
    }

    @Test
    fun `ticket summary background remote refresh is deduped but force still runs`() {
        val governor = TicketRefreshGovernor(requestCooldownMs = 10_000L)

        assertFalse(
            shouldSkipTicketSummaryRemoteRefresh(
                governor = governor,
                ownerKey = "ADM-C5FFB0",
                requestType = "period-hydrate:TODAY:05",
                authScope = "ADMIN",
                force = false,
                nowEpochMs = 1_000L,
            ),
        )
        assertTrue(
            shouldSkipTicketSummaryRemoteRefresh(
                governor = governor,
                ownerKey = "adm-c5ffb0",
                requestType = "period-hydrate:TODAY:05",
                authScope = "admin",
                force = false,
                nowEpochMs = 4_000L,
            ),
        )
        assertFalse(
            shouldSkipTicketSummaryRemoteRefresh(
                governor = governor,
                ownerKey = "ADM-C5FFB0",
                requestType = "period-hydrate:TODAY:05",
                authScope = "ADMIN",
                force = true,
                nowEpochMs = 4_000L,
            ),
        )
    }

    @Test
    fun `ticket summary flushes pending queue before hydration when allowed`() {
        assertTrue(shouldFlushPendingTicketsBeforeHydration(allowPendingFlush = true, pendingSyncCount = 1))
        assertFalse(shouldFlushPendingTicketsBeforeHydration(allowPendingFlush = false, pendingSyncCount = 1))
        assertFalse(shouldFlushPendingTicketsBeforeHydration(allowPendingFlush = true, pendingSyncCount = 0))
    }

    @Test
    fun `ticket summary continues pending flush only while queue count decreases`() {
        assertTrue(
            shouldContinuePendingTicketFlush(
                passIndex = 0,
                pendingSyncCount = 3,
                previousPendingSyncCount = null,
                maxPasses = 5,
            ),
        )
        assertTrue(
            shouldContinuePendingTicketFlush(
                passIndex = 1,
                pendingSyncCount = 2,
                previousPendingSyncCount = 3,
                maxPasses = 5,
            ),
        )
        assertFalse(
            shouldContinuePendingTicketFlush(
                passIndex = 2,
                pendingSyncCount = 2,
                previousPendingSyncCount = 2,
                maxPasses = 5,
            ),
        )
        assertFalse(
            shouldContinuePendingTicketFlush(
                passIndex = 5,
                pendingSyncCount = 1,
                previousPendingSyncCount = 2,
                maxPasses = 5,
            ),
        )
    }

    @Test
    fun `ticket summary refresh ui shows active server work`() {
        val ui = resolveTicketSummaryRefreshUi(isRefreshing = true, syncMessage = "Consultando servidor...")

        assertEquals("Refrescando", ui.buttonLabel)
        assertEquals("Refrescando servidor...", ui.statusLabel)
        assertFalse(ui.buttonEnabled)
        assertTrue(ui.showProgress)
        assertTrue(ui.showStatus)
    }

    @Test
    fun `ticket summary refresh ui keeps success silent and reports errors`() {
        val success = resolveTicketSummaryRefreshUi(isRefreshing = false, syncMessage = "Tickets sincronizados con servidor.")
        val error = resolveTicketSummaryRefreshUi(isRefreshing = false, syncMessage = "Pendiente de sync: Sin conexión")

        assertEquals("Refrescar", success.buttonLabel)
        assertEquals("", success.statusLabel)
        assertTrue(success.buttonEnabled)
        assertFalse(success.showProgress)
        assertFalse(success.showStatus)
        assertEquals("Error", error.statusLabel)
        assertTrue(error.buttonEnabled)
        assertFalse(error.showProgress)
        assertTrue(error.showStatus)
    }

    @Test
    fun `ticket summary pending banner only appears during active pending refresh`() {
        assertFalse(shouldShowTicketSummarySyncBanner(pendingSyncCount = 1, isRefreshing = false))
        assertFalse(shouldShowTicketSummarySyncBanner(pendingSyncCount = 0, isRefreshing = true))
        assertTrue(shouldShowTicketSummarySyncBanner(pendingSyncCount = 1, isRefreshing = true))
    }

    @Test
    fun `ticket summary clears stale pending message when queue is empty`() {
        assertEquals(
            "Tickets sincronizados con servidor.",
            resolveTicketSummarySyncMessage(
                pendingSyncCount = 0,
                currentMessage = "Ticket guardado en el celular. 1 ticket(s) esperando servidor.",
            ),
        )
        assertEquals(
            "Pendiente de sync: Sin conexión",
            resolveTicketSummarySyncMessage(
                pendingSyncCount = 1,
                currentMessage = "Pendiente de sync: Sin conexión",
            ),
        )
    }

    @Test
    fun `ticket summary pending sync count is scoped to active session`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            adminId = "ADM-1",
            adminUser = "ramonc3",
        )
        val pending = listOf(
            JSONObject("""{"id":"mine-1","adminId":"ADM-1"}"""),
            JSONObject("""{"id":"mine-2","adminUser":"ramonc3"}"""),
            JSONObject("""{"id":"other","adminId":"ADM-2"}"""),
        )

        assertEquals(2, countPendingTicketSyncForSession(pending, session))
    }

    @Test
    fun `ticket summary realtime subscriptions use the canonical owner only`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "auth-user-id",
            username = "nicola01",
            adminId = "ADM-163C38",
            adminUser = "nicola01",
        )

        assertEquals(listOf("ADM-163C38"), resolveOperationalRealtimeOwnerKeys(session))
    }

    @Test
    fun `ticket summary foreground catch-up refreshes when remote ticket stamp changes`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            adminId = "ADM-1",
            adminUser = "ramonc3",
        )
        val input = resolveTicketSummaryForegroundCatchUpInput(
            session = session,
            tickets = listOf(TicketRecord(id = "T-1", drawDateKey = "2026-06-02")),
            lastRemoteUpdatedAt = "2026-06-02T10:00:00Z",
            remoteUpdatedAt = "2026-06-02T10:05:00Z",
            realtimeConfigured = true,
            hasRealtimeSubscription = true,
            nowEpochMs = 1_780_376_400_000L,
        )

        assertEquals("ADM-1", input.ownerKey)
        assertEquals("2026-06-02", input.dateKey)
        assertTrue(input.hasLocalTickets)
        assertTrue(input.ticketStampChanged)
        assertTrue(input.realtimeConnected)
    }

    @Test
    fun `ticket summary foreground catch-up reconnects realtime without forcing ticket download`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            adminId = "ADM-1",
            adminUser = "ramonc3",
        )
        val input = resolveTicketSummaryForegroundCatchUpInput(
            session = session,
            tickets = listOf(TicketRecord(id = "T-1", drawDateKey = "2026-06-02")),
            lastRemoteUpdatedAt = "2026-06-02T10:00:00Z",
            remoteUpdatedAt = "2026-06-02T10:00:00Z",
            realtimeConfigured = true,
            hasRealtimeSubscription = false,
            nowEpochMs = 1_780_376_400_000L,
        )

        assertFalse(input.ticketStampChanged)
        assertFalse(input.realtimeConnected)
    }
}
