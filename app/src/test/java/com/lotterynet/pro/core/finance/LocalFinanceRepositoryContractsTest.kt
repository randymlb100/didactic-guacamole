package com.lotterynet.pro.core.finance

import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.model.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFinanceRepositoryContractsTest {

    @Test
    fun `week preset resolves rolling seven day range`() {
        val range = resolveFinanceRange(
            preset = FinancePeriodPreset.WEEK,
            anchorDayKey = "2026-04-22",
        )

        assertEquals("2026-04-16", range.fromDayKey)
        assertEquals("2026-04-22", range.toDayKey)
    }

    @Test
    fun `week preset on sunday still includes previous friday sales`() {
        val range = resolveFinanceRange(
            preset = FinancePeriodPreset.WEEK,
            anchorDayKey = "2026-05-03",
        )

        assertEquals("2026-04-27", range.fromDayKey)
        assertEquals("2026-05-03", range.toDayKey)
    }

    @Test
    fun `quincena preset resolves first half of month`() {
        val range = resolveFinanceRange(
            preset = FinancePeriodPreset.QUINCENA,
            anchorDayKey = "2026-04-10",
        )

        assertEquals("2026-04-01", range.fromDayKey)
        assertEquals("2026-04-15", range.toDayKey)
    }

    @Test
    fun `quincena preset resolves second half of month`() {
        val range = resolveFinanceRange(
            preset = FinancePeriodPreset.QUINCENA,
            anchorDayKey = "2026-04-22",
        )

        assertEquals("2026-04-16", range.fromDayKey)
        assertEquals("2026-04-30", range.toDayKey)
    }

    @Test
    fun `calendar preset normalizes inverted range`() {
        val range = resolveFinanceRange(
            preset = FinancePeriodPreset.CALENDAR,
            anchorDayKey = "2026-04-22",
            fromDayKey = "2026-04-30",
            toDayKey = "2026-04-01",
        )

        assertEquals("2026-04-01", range.fromDayKey)
        assertEquals("2026-04-30", range.toDayKey)
    }

    @Test
    fun `invalid anchor day still resolves non empty quincena range`() {
        val range = resolveFinanceRange(
            preset = FinancePeriodPreset.QUINCENA,
            anchorDayKey = "fecha-invalida",
        )

        assertTrue(range.fromDayKey.isNotBlank())
        assertTrue(range.toDayKey.isNotBlank())
    }

    @Test
    fun `bank scope matches by admin id`() {
        assertTrue(
            matchesBankScopeRecord(
                adminId = "ADM-1",
                adminUser = "owner-a",
                bancaName = "Banca A",
                recordAdminId = "ADM-1",
                recordAdminUser = "other-user",
                recordAdminBanca = "Banca B",
            ),
        )
    }

    @Test
    fun `bank scope falls back to banca name when admin fields differ`() {
        assertTrue(
            matchesBankScopeRecord(
                adminId = "ADM-1",
                adminUser = "owner-a",
                bancaName = "Banca A",
                recordAdminId = "ADM-2",
                recordAdminUser = "owner-b",
                recordAdminBanca = "Banca A",
            ),
        )
    }

    @Test
    fun `bank scope rejects records from another banca`() {
        assertFalse(
            matchesBankScopeRecord(
                adminId = "ADM-1",
                adminUser = "owner-a",
                bancaName = "Banca A",
                recordAdminId = "ADM-2",
                recordAdminUser = "owner-b",
                recordAdminBanca = "Banca B",
            ),
        )
    }

    @Test
    fun `empty bank scope represents master all banks`() {
        assertTrue(
            matchesBankScopeRecord(
                adminId = null,
                adminUser = null,
                bancaName = null,
                recordAdminId = "ADM-2",
                recordAdminUser = "owner-b",
                recordAdminBanca = "Banca B",
            ),
        )
    }

    @Test
    fun `detected prize is treated as pending winner in finance`() {
        assertTrue(isFinancePendingWinner(TicketRecord(id = "winner", status = "active", totalPrize = 500.0)))
        assertFalse(isFinancePendingWinner(TicketRecord(id = "paid", status = "paid", totalPrize = 500.0)))
        assertFalse(isFinancePendingWinner(TicketRecord(id = "voided", status = "voided", totalPrize = 500.0)))
    }

    @Test
    fun `spanish ticket statuses affect finance buckets`() {
        assertTrue(isFinancePendingWinner(TicketRecord(id = "ganador", status = "ganador", totalPrize = 500.0)))
        assertFalse(isFinancePendingWinner(TicketRecord(id = "pagado", status = "pagado", totalPrize = 500.0)))
        assertTrue(isFinanceCancelledStatus(TicketRecord(id = "anulado", status = "anulado", totalPrize = 500.0)))
        assertTrue(isFinanceCancelledStatus(TicketRecord(id = "borrado", status = "deleted", totalPrize = 500.0)))
        assertFalse(isFinancePendingWinner(TicketRecord(id = "borrado", status = "deleted", totalPrize = 500.0)))
    }

    @Test
    fun `pending winner is reserved from cashbox before payout`() {
        val summary = FinanceSummary(
            ventas = 1000.0,
            premiosPagados = 0.0,
            premiosPendientes = 350.0,
            cajaDisponible = 550.0,
        )

        assertEquals(550.0, summary.netoProyectado, 0.0)
    }

    @Test
    fun `legacy pale prize amount is normalized for finance totals`() {
        assertEquals(5_000.0, normalizeLegacyPrizeAmount(500_000.0, 5.0), 0.0)
        assertEquals(5_000.0, normalizeLegacyPrizeAmount(5_000.0, 5.0), 0.0)
    }

    @Test
    fun `admin actor report does not include cashier tickets owned by same admin`() {
        val adminTicket = TicketRecord(
            id = "admin-sale",
            sellerId = "ADM-1",
            sellerUser = "admin01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.ADMIN,
        )
        val cashierTicket = TicketRecord(
            id = "cashier-sale",
            sellerId = "CAJ-1",
            sellerUser = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
        )

        assertTrue(matchesFinanceActorTicket(adminTicket, "ADM-1", "admin01"))
        assertFalse(matchesFinanceActorTicket(cashierTicket, "ADM-1", "admin01"))
    }

    @Test
    fun `cashier actor report only includes selected cashier operations`() {
        val selectedCashier = TicketRecord(
            id = "cashier-1-sale",
            sellerId = "CAJ-1",
            sellerUser = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
        )
        val otherCashier = TicketRecord(
            id = "cashier-2-sale",
            sellerId = "CAJ-2",
            sellerUser = "cajero02",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
        )

        assertTrue(matchesFinanceActorTicket(selectedCashier, "CAJ-1", "cajero01"))
        assertFalse(matchesFinanceActorTicket(otherCashier, "CAJ-1", "cajero01"))
    }

    @Test
    fun `cashier finance rows include legacy username tickets with paid and pending prizes`() {
        val cashier = UserAccount(
            id = "CAJ-1",
            user = "cajero01",
            role = UserRole.CASHIER,
            displayName = "Caja 01",
            adminId = "ADM-1",
            adminUser = "admin01",
        )
        val paidByUser = TicketRecord(
            id = "paid-by-user",
            sellerId = "cajero01",
            sellerUser = "cajero01",
            adminId = "admin01",
            adminUser = "admin01",
            role = UserRole.CASHIER,
            status = "paid",
            total = 20.0,
            totalPrize = 500.0,
        )
        val pendingById = paidByUser.copy(
            id = "pending-by-id",
            sellerId = "CAJ-1",
            status = "winner",
            totalPrize = 300.0,
        )
        val otherCashier = paidByUser.copy(id = "other", sellerId = "CAJ-2", sellerUser = "cajero02")

        val rows = filterFinanceActorTickets(
            rows = listOf(paidByUser, pendingById, otherCashier),
            actorKey = cashier.id,
            actorDisplay = cashier.displayName,
            actorAccount = cashier,
        )
        val summary = summarizeFinanceRows(rows, commissionRateResolver = { 0.0 })

        assertEquals(listOf("paid-by-user", "pending-by-id"), rows.map { it.id })
        assertEquals(1, summary.pagados)
        assertEquals(1, summary.ganadores)
        assertEquals(500.0, summary.premiosPagados, 0.0)
        assertEquals(300.0, summary.premiosPendientes, 0.0)
        assertEquals(-760.0, summary.cajaDisponible, 0.0)
    }

    @Test
    fun `admin actor report does not include cashier recharges owned by same admin`() {
        val adminRecharge = RechargeRecord(
            id = "admin-recarga",
            userId = "ADM-1",
            userName = "admin01",
            adminId = "ADM-1",
            adminUser = "admin01",
        )
        val cashierRecharge = RechargeRecord(
            id = "cashier-recarga",
            userId = "CAJ-1",
            userName = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
        )

        assertTrue(matchesFinanceActorRecharge(adminRecharge, "ADM-1", "admin01"))
        assertFalse(matchesFinanceActorRecharge(cashierRecharge, "ADM-1", "admin01"))
    }

    @Test
    fun `recharges enter finance as clean cash without commission`() {
        val summary = summarizeFinanceRows(
            rows = listOf(TicketRecord(id = "sale-1", total = 1_000.0, status = "active")),
            rechargeRows = listOf(RechargeRecord(id = "recarga-1", amount = 10_000.0)),
            deletedRows = emptyList(),
            commissionRateResolver = { 0.05 },
        )

        assertEquals(10_000.0, summary.recargas, 0.0)
        assertEquals(50.0, summary.comision, 0.0)
        assertEquals(10_950.0, summary.cajaDisponible, 0.0)
    }

    @Test
    fun `finance keeps decimal cents from pick sales and commission`() {
        val summary = summarizeFinanceRows(
            rows = listOf(TicketRecord(id = "pick-sale", total = 0.50, status = "active")),
            rechargeRows = emptyList(),
            deletedRows = emptyList(),
            commissionRateResolver = { 0.05 },
        )

        assertEquals(0.50, summary.ventas, 0.001)
        assertEquals(0.025, summary.comision, 0.001)
        assertEquals(0.475, summary.cajaDisponible, 0.001)
    }

    @Test
    fun `paid winner remains deducted when recharge enters same cashbox`() {
        val summary = summarizeFinanceRows(
            rows = listOf(
                TicketRecord(id = "paid-winner", total = 20.0, totalPrize = 181.0, status = "paid"),
            ),
            rechargeRows = listOf(RechargeRecord(id = "recarga-1", amount = 130.0)),
            deletedRows = emptyList(),
            commissionRateResolver = { 0.0 },
        )

        assertEquals(181.0, summary.premiosPagados, 0.0)
        assertEquals(-31.0, summary.cajaDisponible, 0.0)
    }

    @Test
    fun `pending winner is deducted before cashier presses pay`() {
        val summary = summarizeFinanceRows(
            rows = listOf(
                TicketRecord(id = "pending-winner", total = 20.0, totalPrize = 181.0, status = "ganador"),
            ),
            rechargeRows = listOf(RechargeRecord(id = "recarga-1", amount = 130.0)),
            deletedRows = emptyList(),
            commissionRateResolver = { 0.0 },
        )

        assertEquals(181.0, summary.premiosPendientes, 0.0)
        assertEquals(-31.0, summary.cajaDisponible, 0.0)
        assertEquals(-31.0, summary.netoProyectado, 0.0)
    }

    @Test
    fun `spanish paid winner remains deducted from finance`() {
        val summary = summarizeFinanceRows(
            rows = listOf(
                TicketRecord(id = "paid-winner", total = 20.0, totalPrize = 181.0, status = "cobrado"),
            ),
            rechargeRows = listOf(RechargeRecord(id = "recarga-1", amount = 130.0)),
            deletedRows = emptyList(),
            commissionRateResolver = { 0.0 },
        )

        assertEquals(1, summary.pagados)
        assertEquals(181.0, summary.premiosPagados, 0.0)
        assertEquals(-31.0, summary.cajaDisponible, 0.0)
    }

    @Test
    fun `paid winner without confirmed prize does not count sale total as payout`() {
        val summary = summarizeFinanceRows(
            rows = listOf(
                TicketRecord(id = "ticket-ln-e8e25b", total = 139.0, totalPrize = 0.0, status = "paid"),
            ),
            rechargeRows = emptyList(),
            deletedRows = emptyList(),
            commissionRateResolver = { 0.0 },
        )

        assertEquals(139.0, summary.ventas, 0.0)
        assertEquals(1, summary.pagados)
        assertEquals(0.0, summary.premiosPagados, 0.0)
        assertEquals(139.0, summary.cajaDisponible, 0.0)
    }

    @Test
    fun `supervised bank scope accepts only assigned cashiers`() {
        val scope = FinanceScope(
            type = FinanceScopeType.BANK,
            adminId = "ADM-1",
            adminUser = "admin01",
            allowedActorKeys = setOf("caj-1", "cajero01"),
            actorDisplay = "Supervisor sup01",
        )
        val assigned = UserAccount(
            id = "CAJ-1",
            user = "cajero01",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            adminUser = "admin01",
        )
        val unassigned = assigned.copy(id = "CAJ-2", user = "cajero02")

        assertTrue(matchesFinanceCashierScope(assigned, scope))
        assertFalse(matchesFinanceCashierScope(unassigned, scope))
    }

    @Test
    fun `supervisor commission is calculated from positive group benefit only`() {
        val positive = FinanceSummary(
            ventas = 1_000.0,
            comision = 100.0,
            premiosPagados = 200.0,
            premiosPendientes = 0.0,
            cajaDisponible = 700.0,
        )
        val negative = FinanceSummary(
            ventas = 1_000.0,
            comision = 100.0,
            premiosPagados = 1_200.0,
            premiosPendientes = 0.0,
            cajaDisponible = -300.0,
        )

        assertEquals(70.0, calculateSupervisorCommission(positive, 0.10), 0.001)
        assertEquals(0.0, calculateSupervisorCommission(negative, 0.10), 0.001)
    }
}
