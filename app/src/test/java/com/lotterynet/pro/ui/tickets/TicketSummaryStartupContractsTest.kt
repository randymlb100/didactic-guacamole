package com.lotterynet.pro.ui.tickets

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class TicketSummaryStartupContractsTest {
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
        assertTrue(TICKET_SUMMARY_STARTUP_SYNC_DELAY_MS <= 500L)
        assertTrue(TICKET_SUMMARY_RESUME_SYNC_DELAY_MS >= 1_000L)
        assertTrue(TICKET_SUMMARY_POLL_MS >= 60_000L)
        assertTrue(TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS >= 120_000L)
        assertTrue(TICKET_SUMMARY_FOREGROUND_CATCH_UP_THROTTLE_MS >= 45_000L)
        assertEquals(TICKET_SUMMARY_POLL_MS, resolveTicketSummaryPollIntervalMs(realtimeEnabled = false))
        assertEquals(TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS, resolveTicketSummaryPollIntervalMs(realtimeEnabled = true))
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
