package com.lotterynet.pro.ui.tickets

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketCollectionContractsTest {

    @Test
    fun `ticket collections collapse filters by default on phone modes`() {
        val tight = resolveTicketCollectionLayout(LotteryNetWindowMode.POS_TIGHT)
        val phone = resolveTicketCollectionLayout(LotteryNetWindowMode.POS)

        assertTrue(tight.filtersCollapsedByDefault)
        assertTrue(phone.filtersCollapsedByDefault)
        assertTrue(tight.useCompactRows)
        assertTrue(tight.inlinePrimaryNumbers)
        assertTrue(tight.minTouchTargetDp >= 44)
        assertTrue(tight.listSpacingDp < phone.listSpacingDp)
        assertTrue(tight.filterRowSpacingDp <= 4)
        assertTrue(phone.headerPaddingVerticalDp <= 7)
        assertTrue(tight.rowPaddingVerticalDp <= 5)
        assertTrue(tight.collapseSecondarySummaryFilters)
        assertTrue(phone.collapseSecondarySummaryFilters)
    }

    @Test
    fun `ticket collections keep wider layouts expanded by default`() {
        val wide = resolveTicketCollectionLayout(LotteryNetWindowMode.WIDE)

        assertFalse(wide.filtersCollapsedByDefault)
        assertFalse(wide.collapseSecondarySummaryFilters)
        assertEquals(3, wide.metricColumns)
        assertTrue(wide.listSpacingDp >= 6)
        assertTrue(wide.headerPaddingVerticalDp >= 8)
    }

    @Test
    fun `ticket detail rows group plays under the same ticket`() {
        val ticket = TicketRecord(
            id = "ticket-1",
            serial = "SER-1",
            plays = listOf(
                PlayItem(number = "69", playType = "Q", amount = 69.0, lotteryName = "Anguila Tarde"),
                PlayItem(number = "022334", playType = "T", amount = 1090.0, lotteryName = "Anguila Tarde"),
            ),
            total = 1159.0,
        )

        val groups = groupTicketDetailRows(buildTicketDetailRows(listOf(ticket)))

        assertEquals(1, groups.size)
        assertEquals("ticket-1", groups.first().ticket.id)
        assertEquals(2, groups.first().plays.size)
        assertEquals(1090.0, groups.first().plays.last().amount, 0.001)
    }

    @Test
    fun `admin ticket directory includes cashier tickets from same banca`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca Central",
        )
        val cashier = UserAccount(
            id = "cashier-1",
            user = "cajero1",
            role = UserRole.CASHIER,
            adminId = "admin-1",
            banca = "Banca Central",
        )
        val cashierTicket = TicketRecord(
            id = "ticket-cajero",
            sellerId = "cashier-1",
            sellerUser = "cajero1",
            role = UserRole.CASHIER,
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
            total = 20.0,
        )
        val otherTicket = cashierTicket.copy(id = "ticket-otra-banca", sellerId = "cashier-2", sellerUser = "otro")

        val directory = buildTicketDirectory(
            session = session,
            allTickets = listOf(cashierTicket, otherTicket),
            allCashiers = listOf(cashier),
        )

        assertEquals(listOf("ticket-cajero"), directory.tickets.map { it.id })
        assertEquals(listOf("cashier-1"), directory.cashierOptions.map { it.id })
    }

    @Test
    fun `admin delegated sale appears in selected cashier summary`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca Central",
        )
        val cashier = UserAccount(
            id = "cashier-1",
            user = "cajero1",
            role = UserRole.CASHIER,
            adminId = "admin-1",
            banca = "Banca Central",
        )
        val delegatedTicket = TicketRecord(
            id = "ticket-delegado",
            sellerId = "cashier-1",
            sellerUser = "cajero1",
            adminId = "admin-1",
            adminUser = "admin",
            role = UserRole.CASHIER,
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
            total = 20.0,
        )
        val directory = buildTicketDirectory(session, listOf(delegatedTicket), listOf(cashier))

        val filtered = filterSummaryTickets(
            directory = directory,
            statusBucket = TicketStatusBucket.ALL.id,
            lotteryName = "",
            ownerScope = TicketOwnerScope.CASHIER,
            cashierKey = "cashier-1",
            query = "",
            fromDateTime = "",
            toDateTime = "",
        )

        assertEquals(listOf("ticket-delegado"), filtered.map { it.id })
    }

    @Test
    fun `ticket owner label resolves cashier display name from server id`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca Central",
        )
        val cashier = UserAccount(
            id = "srv-cajero-01",
            user = "cajero01",
            role = UserRole.CASHIER,
            displayName = "Caja Norte",
            adminId = "admin-1",
            banca = "Banca Central",
        )
        val ticket = TicketRecord(
            id = "ticket-cajero",
            sellerId = "srv-cajero-01",
            sellerUser = null,
            role = UserRole.CASHIER,
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
            total = 20.0,
        )
        val directory = buildTicketDirectory(session, listOf(ticket), listOf(cashier))

        assertEquals("Caja Norte", ticketOwnerLabel(ticket, directory.actorLabelsByKey))
    }

    @Test
    fun `admin owner filter includes tickets keyed by session admin id`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "device-admin",
            username = "admin-device",
            adminId = "admin-1",
            adminUser = "admin",
            banca = "Banca Central",
        )
        val adminTicket = TicketRecord(
            id = "ticket-admin",
            sellerId = "admin-1",
            sellerUser = "admin",
            adminId = "admin-1",
            adminUser = "admin",
            role = UserRole.ADMIN,
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
            total = 20.0,
        )

        val directory = buildTicketDirectory(session, listOf(adminTicket), emptyList())
        val filtered = filterSummaryTickets(
            directory = directory,
            statusBucket = TicketStatusBucket.ALL.id,
            lotteryName = "",
            ownerScope = TicketOwnerScope.ADMIN,
            cashierKey = "",
            query = "",
            fromDateTime = "",
            toDateTime = "",
        )

        assertEquals(listOf("ticket-admin"), filtered.map { it.id })
    }

    @Test
    fun `ticket summary can start prefiltered for cashier`() {
        val filters = resolveTicketSummaryInitialFilters(
            ownerScopeRaw = "CASHIER",
            cashierKeyRaw = "cashier-1",
        )

        assertEquals(TicketOwnerScope.CASHIER, filters.ownerScope)
        assertEquals("cashier-1", filters.cashierKey)
    }

    @Test
    fun `cashier lookup filters out admin tickets before query and mode filters`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-1",
            username = "cajero1",
            adminId = "admin-1",
            adminUser = "admin1",
            banca = "Banca Central",
        )
        val ownTicket = TicketRecord(
            id = "own-winner",
            sellerId = "cashier-1",
            sellerUser = "cajero1",
            adminId = "admin-1",
            adminUser = "admin1",
            role = UserRole.CASHIER,
            status = "winner",
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0, lotteryName = "Anguila")),
        )
        val adminTicket = ownTicket.copy(
            id = "admin-winner",
            sellerId = "admin-1",
            sellerUser = "admin1",
            role = UserRole.ADMIN,
        )

        val visible = filterLookupTicketsForSession(
            session = session,
            tickets = listOf(adminTicket, ownTicket),
            cashiers = emptyList(),
            mode = LookupMode.PAY,
            query = "",
        )

        assertEquals(listOf("own-winner"), visible.map { it.id })
    }

    @Test
    fun `duplicate lookup hides deleted and nulled tickets`() {
        val session = ActiveSession(role = UserRole.ADMIN, userId = "admin-1", username = "admin")
        val activeTicket = TicketRecord(
            id = "active-ticket",
            sellerId = "admin-1",
            sellerUser = "admin",
            role = UserRole.ADMIN,
            status = "active",
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
        )
        val deletedTicket = activeTicket.copy(id = "deleted-ticket")
        val nulledTicket = activeTicket.copy(id = "nulled-ticket", status = "nulled")

        val visible = filterLookupTicketsForSession(
            session = session,
            tickets = listOf(activeTicket, deletedTicket, nulledTicket),
            cashiers = emptyList(),
            mode = LookupMode.DUPLICATE,
            query = "",
            deletedTicketIds = setOf("deleted-ticket"),
        )

        assertEquals(listOf("active-ticket"), visible.map { it.id })
    }

    @Test
    fun `duplicate lookup follows cashier lottery mode`() {
        val session = ActiveSession(role = UserRole.ADMIN, userId = "admin-1", username = "admin")
        val lotteryTicket = TicketRecord(
            id = "lottery-ticket",
            sellerId = "admin-1",
            sellerUser = "admin",
            role = UserRole.ADMIN,
            status = "active",
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0, lotteryId = "anguila")),
        )
        val pickTicket = lotteryTicket.copy(
            id = "pick-ticket",
            plays = listOf(PlayItem(number = "123", playType = "P3STRAIGHT", amount = 5.0, lotteryId = "US-P3-NJ-DAY")),
        )

        val visible = filterLookupTicketsForSession(
            session = session,
            tickets = listOf(lotteryTicket, pickTicket),
            cashiers = emptyList(),
            mode = LookupMode.DUPLICATE,
            query = "",
            systemModeConfig = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = false),
        )

        assertEquals(listOf("lottery-ticket"), visible.map { it.id })
    }

    @Test
    fun `duplicate lookup follows cashier pick mode`() {
        val session = ActiveSession(role = UserRole.ADMIN, userId = "admin-1", username = "admin")
        val lotteryTicket = TicketRecord(
            id = "lottery-ticket",
            sellerId = "admin-1",
            sellerUser = "admin",
            role = UserRole.ADMIN,
            status = "active",
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0, lotteryId = "anguila")),
        )
        val pickTicket = lotteryTicket.copy(
            id = "pick-ticket",
            plays = listOf(PlayItem(number = "1234", playType = "P4BOX", amount = 5.0, lotteryId = "US-P4-NJ-EVENING")),
        )

        val visible = filterLookupTicketsForSession(
            session = session,
            tickets = listOf(lotteryTicket, pickTicket),
            cashiers = emptyList(),
            mode = LookupMode.DUPLICATE,
            query = "",
            systemModeConfig = AdminSystemModeConfig(lotteryModeEnabled = false, pickModeEnabled = true),
        )

        assertEquals(listOf("pick-ticket"), visible.map { it.id })
    }

    @Test
    fun `server deleted status is treated as nulled and cannot be repeated`() {
        val deletedTicket = TicketRecord(id = "deleted-ticket", status = "deleted")

        assertEquals(TicketStatusBucket.NULLED.id, ticketStatusBucket(deletedTicket.status))
        assertTrue(ticketExcludedFromTotals(deletedTicket))
        assertFalse(canRepeatTicket(deletedTicket))
    }

    @Test
    fun `summary hides deleted server tickets but keeps voided tickets visible`() {
        val session = ActiveSession(role = UserRole.ADMIN, userId = "admin-1", username = "admin")
        val activeTicket = TicketRecord(
            id = "active-ticket",
            sellerId = "admin-1",
            sellerUser = "admin",
            role = UserRole.ADMIN,
            status = "active",
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
            total = 20.0,
        )
        val voidedTicket = activeTicket.copy(id = "voided-ticket", status = "voided")
        val deletedTicket = activeTicket.copy(id = "deleted-ticket", status = "deleted")

        val directory = buildTicketDirectory(
            session = session,
            allTickets = listOf(activeTicket, voidedTicket, deletedTicket),
            allCashiers = emptyList(),
        )
        val visible = filterSummaryTickets(
            directory = directory,
            statusBucket = TicketStatusBucket.ALL.id,
            lotteryName = "",
            ownerScope = TicketOwnerScope.ALL,
            cashierKey = "",
            query = "",
            fromDateTime = "",
            toDateTime = "",
        )

        val visibleIds = visible.map { it.id }
        assertEquals(2, visibleIds.size)
        assertTrue("voided-ticket" in visibleIds)
        assertTrue("active-ticket" in visibleIds)
        assertFalse("deleted-ticket" in visibleIds)
    }

    @Test
    fun `pay lookup includes tickets with detected prize even before winner status`() {
        val session = ActiveSession(role = UserRole.ADMIN, userId = "admin-1", username = "admin")
        val prizeTicket = TicketRecord(
            id = "prize-ticket",
            sellerId = "admin-1",
            sellerUser = "admin",
            role = UserRole.ADMIN,
            status = "active",
            totalPrize = 500.0,
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
        )
        val activeTicket = prizeTicket.copy(id = "active-ticket", totalPrize = 0.0)

        val visible = filterLookupTicketsForSession(
            session = session,
            tickets = listOf(activeTicket, prizeTicket),
            cashiers = emptyList(),
            mode = LookupMode.PAY,
            query = "",
        )

        assertEquals(listOf("prize-ticket"), visible.map { it.id })
    }

    @Test
    fun `summary winner filter includes tickets with detected prize before status changes`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "CAJ-6",
            username = "bancay06",
            adminId = "ADM-1",
            adminUser = "nicola01",
            banca = "Banca juan",
        )
        val prizeTicket = TicketRecord(
            id = "LN-E8E25B-E01626",
            status = "active",
            sellerId = "CAJ-6",
            sellerUser = "bancay06",
            adminId = "ADM-1",
            adminUser = "nicola01",
            role = UserRole.CASHIER,
            total = 139.0,
            totalPrize = 139.0,
        )
        val directory = buildTicketDirectory(session, listOf(prizeTicket), emptyList())

        val visible = filterSummaryTickets(
            directory = directory,
            statusBucket = TicketStatusBucket.WINNER.id,
            lotteryName = "",
            ownerScope = TicketOwnerScope.ALL,
            cashierKey = "",
            query = "",
            fromDateTime = "",
            toDateTime = "",
        )

        assertEquals(listOf("LN-E8E25B-E01626"), visible.map { it.id })
    }

    @Test
    fun `summary primary action is cobrar for unpaid winners`() {
        val winner = TicketRecord(id = "winner", status = "winner", totalPrize = 139.0)
        val paid = winner.copy(id = "paid", status = "paid")
        val active = winner.copy(id = "active", status = "active", totalPrize = 0.0)

        assertEquals("Cobrar", resolveTicketSummaryPrimaryAction(winner).label)
        assertEquals("pagar", resolveTicketSummaryPrimaryAction(winner).mode)
        assertEquals("buscar", resolveTicketSummaryPrimaryAction(active).mode)
        assertEquals("buscar", resolveTicketSummaryPrimaryAction(paid).mode)
    }

    @Test
    fun `summary open guard rejects stale deleted tickets`() {
        val ticket = TicketRecord(
            id = "ticket-borrado",
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0)),
        )

        val resolution = resolveTicketOpenRequest(
            requestedTicket = ticket,
            currentTickets = emptyList(),
            deletedTicketIds = setOf("ticket-borrado"),
        )

        assertFalse(resolution.canOpen)
        assertEquals("Ese ticket ya no existe o fue borrado.", resolution.message)
    }

    @Test
    fun `summary lottery dropdown can include full catalog not only sold tickets`() {
        val ticket = TicketRecord(
            id = "ticket-1",
            plays = listOf(PlayItem(number = "22", playType = "Q", amount = 20.0, lotteryName = "Anguila")),
        )
        val catalog = listOf(
            LotteryCatalogItem("ang", "Anguila", "RD", "10:00 AM", "9:55 AM", "#111111"),
            LotteryCatalogItem("nac", "Lotería Nacional", "RD", "8:00 PM", "7:55 PM", "#222222"),
        )

        val options = buildLotteryOptions(listOf(ticket), catalog)

        assertEquals(listOf("", "Anguila", "Lotería Nacional"), options.map { it.value })
    }

    @Test
    fun `summary period filters include operational ranges and month selector uses calendar months`() {
        assertEquals(
            listOf("today", "yesterday", "week", "quinza", "month", "all"),
            TicketSummaryPeriod.entries.map { it.id },
        )
        assertEquals(
            listOf("Hoy", "Ayer", "Semana", "Quincena", "Mes", "Todo"),
            TicketSummaryPeriod.entries.map { it.label },
        )
        assertEquals(12, buildTicketMonthOptions().size)
        assertEquals("Enero", buildTicketMonthOptions().first().label)
    }
}
