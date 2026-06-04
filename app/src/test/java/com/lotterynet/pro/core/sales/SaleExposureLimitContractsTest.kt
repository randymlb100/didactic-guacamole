package com.lotterynet.pro.core.sales

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.SaleStagedRow
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class SaleExposureLimitContractsTest {

    @Test
    fun `quiniela limit uses exact number from 00 to 99`() {
        assertEquals(SaleExposureLimitBucket("Q", "15"), resolveSaleExposureLimitBucket("Q", "15"))
    }

    @Test
    fun `combination limits use exact combination across all lotteries`() {
        assertEquals(SaleExposureLimitBucket("P", "1520"), resolveSaleExposureLimitBucket("P", "15-20"))
        assertEquals(SaleExposureLimitBucket("T", "152030"), resolveSaleExposureLimitBucket("T", "15-20-30"))
        assertEquals(SaleExposureLimitBucket("SP", "1520"), resolveSaleExposureLimitBucket("SP", "15/20"))
    }

    @Test
    fun `pick limits use exact straight number and sorted box combination`() {
        assertEquals(SaleExposureLimitBucket("P3", "986"), resolveSaleExposureLimitBucket("P3", "986"))
        assertEquals(SaleExposureLimitBucket("P3BOX", "689"), resolveSaleExposureLimitBucket("P3BOX", "986"))
        assertEquals(SaleExposureLimitBucket("P3BOX", "689"), resolveSaleExposureLimitBucket("P3BOX", "689"))
        assertEquals(SaleExposureLimitBucket("P4", "9861"), resolveSaleExposureLimitBucket("P4", "9861"))
        assertEquals(SaleExposureLimitBucket("P4BOX", "1689"), resolveSaleExposureLimitBucket("P4BOX", "9861"))
        assertEquals(SaleExposureLimitBucket("P4BOX", "1689"), resolveSaleExposureLimitBucket("P4BOX", "1698"))
    }

    @Test
    fun `cashier limits map formats to separate compact fields`() {
        val limits = CashierLimits(
            q = 10000.0,
            pale = 500.0,
            superPale = 450.0,
            t = 75.0,
            pick3Straight = 300.0,
            pick3Box = 250.0,
            pick4Straight = 200.0,
            pick4Box = 150.0,
        )

        assertEquals(10000.0, limits.typeLimitFor("Q"), 0.001)
        assertEquals(500.0, limits.typeLimitFor("P"), 0.001)
        assertEquals(450.0, limits.typeLimitFor("SP"), 0.001)
        assertEquals(75.0, limits.typeLimitFor("T"), 0.001)
        assertEquals(300.0, limits.typeLimitFor("P3"), 0.001)
        assertEquals(250.0, limits.typeLimitFor("P3BOX"), 0.001)
        assertEquals(200.0, limits.typeLimitFor("P4"), 0.001)
        assertEquals(150.0, limits.typeLimitFor("P4BOX"), 0.001)
    }

    @Test
    fun `admin limit decoding defaults to no cap until user override exists`() {
        val payload = """{"defaults":{"daySale":1000,"q":50},"byUser":{"admin1":{"daySale":0,"q":25}}}"""

        val adminWithoutOverride = decodeCashierLimitsForSession(
            payload,
            ActiveSession(role = UserRole.ADMIN, userId = "admin-id", username = "other-admin"),
        )
        val adminWithOverride = decodeCashierLimitsForSession(
            payload,
            ActiveSession(role = UserRole.ADMIN, userId = "admin-id", username = "admin1"),
        )
        val cashierWithoutOverride = decodeCashierLimitsForSession(
            payload,
            ActiveSession(role = UserRole.CASHIER, userId = "cashier-id", username = "cajero1", adminId = "admin-id"),
        )

        assertEquals(0.0, adminWithoutOverride.daySale, 0.001)
        assertEquals(0.0, adminWithoutOverride.typeLimitFor("Q"), 0.001)
        assertEquals(0.0, adminWithOverride.daySale, 0.001)
        assertEquals(25.0, adminWithOverride.typeLimitFor("Q"), 0.001)
        assertEquals(1000.0, cashierWithoutOverride.daySale, 0.001)
        assertEquals(50.0, cashierWithoutOverride.typeLimitFor("Q"), 0.001)
    }

    @Test
    fun `admin self limits override cashier defaults without leaking to cashier defaults`() {
        val payload = """{"defaults":{"daySale":1000,"q":50,"sp":500},"adminSelf":{"sp":75},"byUser":{"cajero1":{"q":25}}}"""

        val adminLimits = decodeCashierLimitsForSession(
            payload,
            ActiveSession(role = UserRole.ADMIN, userId = "admin-id", username = "admin1"),
        )
        val cashierLimits = decodeCashierLimitsForSession(
            payload,
            ActiveSession(role = UserRole.CASHIER, userId = "cashier-id", username = "cajero1", adminId = "admin-id"),
        )

        assertEquals(0.0, adminLimits.daySale, 0.001)
        assertEquals(0.0, adminLimits.typeLimitFor("Q"), 0.001)
        assertEquals(75.0, adminLimits.typeLimitFor("SP"), 0.001)
        assertEquals(1000.0, cashierLimits.daySale, 0.001)
        assertEquals(25.0, cashierLimits.typeLimitFor("Q"), 0.001)
        assertEquals(500.0, cashierLimits.typeLimitFor("SP"), 0.001)
    }

    @Test
    fun `quiniela exposure is scoped by owner cashier and number across all lotteries`() {
        val bucket = resolveSaleExposureLimitBucket("Q", "06")
        val tickets = listOf(
            ticket(
                id = "a",
                adminId = "admin-1",
                sellerId = "cashier-1",
                plays = listOf(play(number = "06", playType = "Q", amount = 100.0, lotteryId = "lot-a")),
            ),
            ticket(
                id = "b",
                adminId = "admin-1",
                sellerUser = "caja01",
                plays = listOf(play(number = "06", playType = "Q", amount = 150.0, lotteryId = "lot-b")),
            ),
            ticket(
                id = "other-cashier",
                adminId = "admin-1",
                sellerId = "cashier-2",
                plays = listOf(play(number = "06", playType = "Q", amount = 600.0, lotteryId = "lot-b")),
            ),
            ticket(
                id = "other-owner",
                adminId = "admin-2",
                sellerId = "cashier-1",
                plays = listOf(play(number = "06", playType = "Q", amount = 900.0, lotteryId = "lot-a")),
            ),
            ticket(
                id = "voided",
                adminId = "admin-1",
                sellerId = "cashier-1",
                status = "voided",
                plays = listOf(play(number = "06", playType = "Q", amount = 500.0, lotteryId = "lot-c")),
            ),
        )

        assertEquals(
            250.0,
            calculateGlobalLimitExposure(tickets, "admin-1", bucket, setOf("cashier-1", "caja01")),
            0.001,
        )
    }

    @Test
    fun `combination exposure is global by owner and exact combination not by lottery`() {
        val paleBucket = resolveSaleExposureLimitBucket("P", "12-34")
        val tripletaBucket = resolveSaleExposureLimitBucket("T", "12-34-56")
        val superPaleBucket = resolveSaleExposureLimitBucket("SP", "12-34")
        val tickets = listOf(
            ticket(
                id = "pale-a",
                adminId = "admin-1",
                plays = listOf(play(number = "12-34", playType = "P", amount = 200.0, lotteryId = "lot-a")),
            ),
            ticket(
                id = "pale-same-combination-other-lottery",
                adminId = "admin-1",
                plays = listOf(play(number = "1234", playType = "P", amount = 125.0, lotteryId = "lot-b")),
            ),
            ticket(
                id = "pale-other-combination",
                adminId = "admin-1",
                plays = listOf(play(number = "99-00", playType = "P", amount = 500.0, lotteryId = "lot-b")),
            ),
            ticket(
                id = "tripleta",
                adminId = "admin-1",
                plays = listOf(play(number = "12-34-56", playType = "T", amount = 75.0, lotteryId = "lot-c")),
            ),
            ticket(
                id = "superpale",
                adminId = "admin-1",
                plays = listOf(play(number = "12/34", playType = "SP", amount = 40.0, lotteryId = "lot-c")),
            ),
        )

        assertEquals(325.0, calculateGlobalLimitExposure(tickets, "admin-1", paleBucket), 0.001)
        assertEquals(75.0, calculateGlobalLimitExposure(tickets, "admin-1", tripletaBucket), 0.001)
        assertEquals(40.0, calculateGlobalLimitExposure(tickets, "admin-1", superPaleBucket), 0.001)
    }

    @Test
    fun `staged exposure is global and ignores lottery for the same bucket`() {
        val bucket = resolveSaleExposureLimitBucket("Q", "06")
        val staged = listOf(
            staged(number = "06", playType = "Q", amount = 100.0, lotteryId = "lot-a"),
            staged(number = "06", playType = "Q", amount = 50.0, lotteryId = "lot-b"),
            staged(number = "07", playType = "Q", amount = 500.0, lotteryId = "lot-a"),
        )

        assertEquals(150.0, calculateGlobalStagedExposure(staged, bucket), 0.001)
    }

    @Test
    fun `cashier remaining rows are grouped by limit bucket and scoped to cashier`() {
        val staged = listOf(
            staged(number = "256", playType = "P3BOX", amount = 25.0, lotteryId = "p3-a"),
            staged(number = "652", playType = "P3BOX", amount = 10.0, lotteryId = "p3-b"),
        )
        val tickets = listOf(
            ticket(
                id = "same-cashier",
                adminId = "admin-1",
                sellerId = "cashier-1",
                plays = listOf(play(number = "526", playType = "P3BOX", amount = 40.0, lotteryId = "p3-old")),
            ),
            ticket(
                id = "other-cashier",
                adminId = "admin-1",
                sellerId = "cashier-2",
                plays = listOf(play(number = "256", playType = "P3BOX", amount = 90.0, lotteryId = "p3-old")),
            ),
        )

        val rows = resolveSaleLimitRemainingRows(
            role = UserRole.CASHIER,
            stagedRows = staged,
            tickets = tickets,
            ownerKey = "admin-1",
            cashierKeys = setOf("cashier-1"),
            limits = CashierLimits(pick3Box = 100.0),
        )

        assertEquals(1, rows.size)
        assertEquals("P3BOX", rows.first().playType)
        assertEquals("256", rows.first().number)
        assertEquals(40.0, rows.first().sold, 0.001)
        assertEquals(35.0, rows.first().pending, 0.001)
        assertEquals(25.0, rows.first().remaining, 0.001)
    }

    @Test
    fun `admin remaining rows show when admin has self limit`() {
        val tickets = listOf(
            ticket(
                id = "same-admin",
                adminId = "admin-1",
                sellerId = "admin-1",
                plays = listOf(play(number = "526", playType = "P3BOX", amount = 40.0, lotteryId = "p3-old")),
            ).copy(role = UserRole.ADMIN),
            ticket(
                id = "cashier-sale",
                adminId = "admin-1",
                sellerId = "cashier-1",
                plays = listOf(play(number = "256", playType = "P3BOX", amount = 90.0, lotteryId = "p3-old")),
            ).copy(role = UserRole.CASHIER),
        )

        val rows = resolveSaleLimitRemainingRows(
            role = UserRole.ADMIN,
            stagedRows = listOf(staged(number = "256", playType = "P3BOX", amount = 25.0, lotteryId = "p3-a")),
            tickets = tickets,
            ownerKey = "admin-1",
            cashierKeys = setOf("admin-1", "admin1"),
            limits = CashierLimits(pick3Box = 10.0),
        )

        assertEquals(1, rows.size)
        assertEquals(40.0, rows.first().sold, 0.001)
        assertEquals(0.0, rows.first().remaining, 0.001)
    }

    @Test
    fun `daily cashier sale total excludes deleted or voided tickets so limits are returned`() {
        val tickets = listOf(
            ticket(
                id = "active-sale",
                adminId = "admin-1",
                status = "active",
                plays = listOf(play(number = "06", playType = "Q", amount = 250.0, lotteryId = "lot-a")),
            ).copy(sellerUser = "cajero01", role = UserRole.CASHIER, total = 250.0),
            ticket(
                id = "deleted-sale",
                adminId = "admin-1",
                status = "voided",
                plays = listOf(play(number = "08", playType = "Q", amount = 1000.0, lotteryId = "lot-a")),
            ).copy(sellerUser = "cajero01", role = UserRole.CASHIER, total = 1000.0),
            ticket(
                id = "other-cashier",
                adminId = "admin-1",
                status = "active",
                plays = listOf(play(number = "10", playType = "Q", amount = 900.0, lotteryId = "lot-a")),
            ).copy(sellerUser = "cajero02", role = UserRole.CASHIER, total = 900.0),
        )

        assertEquals(250.0, calculateActorDailySaleTotal(tickets, "cajero01"), 0.001)
    }

    private fun ticket(
        id: String,
        adminId: String,
        status: String = "active",
        sellerId: String? = null,
        sellerUser: String? = null,
        plays: List<PlayItem>,
    ): TicketRecord {
        return TicketRecord(
            id = id,
            adminId = adminId,
            sellerId = sellerId,
            sellerUser = sellerUser,
            status = status,
            plays = plays,
        )
    }

    private fun play(number: String, playType: String, amount: Double, lotteryId: String): PlayItem {
        return PlayItem(number = number, playType = playType, amount = amount, lotteryId = lotteryId)
    }

    private fun staged(number: String, playType: String, amount: Double, lotteryId: String): SaleStagedRow {
        return SaleStagedRow(
            lotteryId = lotteryId,
            lotteryName = lotteryId,
            playType = playType,
            label = playType,
            number = number,
            displayNumber = number,
            amount = amount,
        )
    }
}
