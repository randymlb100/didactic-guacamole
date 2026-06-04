package com.lotterynet.pro.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole

class AdminLotteryConfigContractsTest {

    @Test
    fun `manual lottery close only applies to the same operation date`() {
        assertTrue(isManualLotteryCloseDateActive(savedDate = "2026-04-27", today = "2026-04-27", permanent = false))
        assertFalse(isManualLotteryCloseDateActive(savedDate = "2026-04-26", today = "2026-04-27", permanent = false))
        assertFalse(isManualLotteryCloseDateActive(savedDate = null, today = "2026-04-27", permanent = false))
    }

    @Test
    fun `permanent manual lottery close stays active after operation date changes`() {
        assertTrue(isManualLotteryCloseDateActive(savedDate = "2026-04-26", today = "2026-04-27", permanent = true))
    }

    @Test
    fun `manual lottery close serializes stable keys for supabase`() {
        val payload = encodeManualDisabledLotteryConfig(
            ids = setOf("US-P3-FL-PICK-3-MIDDAY", "1"),
            date = "2026-05-19",
            permanent = true,
        )
        val decoded = decodeManualDisabledLotteryConfig(payload)

        assertEquals(setOf("1", "US-P3-FL-PICK-3-MIDDAY"), decoded.ids)
        assertEquals("2026-05-19", decoded.date)
        assertTrue(decoded.permanent)
        assertTrue(payload.contains("\"ids\""))
        assertTrue(payload.contains("\"permanent\":true"))
    }

    @Test
    fun `system mode config defaults to lottery only without future sale mode`() {
        val config = AdminSystemModeConfig()

        assertFalse(config.posLiteEnabled)
        assertTrue(config.lotteryModeEnabled)
        assertFalse(config.pickModeEnabled)
        assertFalse(config.cashierPickEnabled)
        assertFalse(config.cashierModeEnabled)
        assertTrue(config.cashierLotteryModeEnabled)
        assertFalse(config.cashierPickModeEnabled)
        assertFalse(config.pickAndLotteryEnabled)
    }

    @Test
    fun `system mode config never saves both result modes off`() {
        val config = normalizeAdminSystemModeConfig(
            AdminSystemModeConfig(
                lotteryModeEnabled = false,
                pickModeEnabled = false,
            ),
        )

        assertTrue(config.lotteryModeEnabled)
        assertFalse(config.pickModeEnabled)
    }

    @Test
    fun `system mode config serializes stable keys for supabase`() {
        val payload = encodeAdminSystemModeConfig(
            AdminSystemModeConfig(
                posLiteEnabled = true,
                lotteryModeEnabled = true,
                pickModeEnabled = true,
            ),
        )

        assertTrue(payload.contains("\"posLiteEnabled\":true"))
        assertTrue(payload.contains("\"configured\":true"))
        assertFalse(payload.contains("futureSaleEnabled"))
        assertTrue(payload.contains("\"lotteryModeEnabled\":true"))
        assertTrue(payload.contains("\"pickModeEnabled\":true"))
        assertTrue(payload.contains("\"cashierPickEnabled\":false"))
        assertTrue(payload.contains("\"cashierModeEnabled\":false"))
        assertTrue(payload.contains("\"cashierLotteryModeEnabled\":true"))
        assertTrue(payload.contains("\"cashierPickModeEnabled\":false"))
        assertTrue(payload.contains("\"blockedSalePlays\":[]"))
        assertEquals(
            AdminSystemModeConfig(posLiteEnabled = true, lotteryModeEnabled = true, pickModeEnabled = true),
            decodeAdminSystemModeConfig(payload),
        )
    }

    @Test
    fun `legacy remote pick only mode falls back to lottery until admin saves explicitly`() {
        val decoded = decodeAdminSystemModeConfig(
            """{"lotteryModeEnabled":false,"pickModeEnabled":true,"cashierModeEnabled":false}""",
        )

        assertTrue(decoded.lotteryModeEnabled)
        assertFalse(decoded.pickModeEnabled)
    }

    @Test
    fun `explicit remote pick only mode is respected after admin saves`() {
        val decoded = decodeAdminSystemModeConfig(
            """{"configured":true,"lotteryModeEnabled":false,"pickModeEnabled":true,"cashierModeEnabled":false}""",
        )

        assertFalse(decoded.lotteryModeEnabled)
        assertTrue(decoded.pickModeEnabled)
    }

    @Test
    fun `system mode config serializes blocked sale plays by exact number`() {
        val payload = encodeAdminSystemModeConfig(
            AdminSystemModeConfig(
                blockedSalePlays = setOf(
                    AdminBlockedSalePlay(playType = "Q", number = "03"),
                    AdminBlockedSalePlay(playType = "P3BOX", number = "852"),
                ),
            ),
        )
        val decoded = decodeAdminSystemModeConfig(payload)

        assertEquals(
            setOf(
                AdminBlockedSalePlay(playType = "P3BOX", number = "852"),
                AdminBlockedSalePlay(playType = "Q", number = "03"),
            ),
            decoded.blockedSalePlays,
        )
        assertTrue(isSalePlayBlocked("Q", "03", decoded))
        assertFalse(isSalePlayBlocked("Q", "04", decoded))
        assertTrue(isSalePlayBlocked("P3BOX", "852", decoded))
        assertFalse(isSalePlayBlocked("P3", "852", decoded))
        assertEquals("Quiniela 03", firstBlockedSalePlayLabel(listOf(AdminBlockedSalePlay("Q", "03")), decoded))
    }

    @Test
    fun `cashier cannot see pick unless admin explicitly allows it`() {
        val config = AdminSystemModeConfig(
            lotteryModeEnabled = true,
            pickModeEnabled = true,
            cashierPickEnabled = false,
        )

        val cashier = effectiveAdminSystemModeConfigForRole(config, UserRole.CASHIER)
        val admin = effectiveAdminSystemModeConfigForRole(config, UserRole.ADMIN)

        assertTrue(cashier.lotteryModeEnabled)
        assertFalse(cashier.pickModeEnabled)
        assertTrue(admin.lotteryModeEnabled)
        assertTrue(admin.pickModeEnabled)
    }

    @Test
    fun `cashier can see pick when admin enables cashier pick`() {
        val config = AdminSystemModeConfig(
            lotteryModeEnabled = false,
            pickModeEnabled = true,
            cashierModeEnabled = true,
            cashierLotteryModeEnabled = false,
            cashierPickModeEnabled = true,
        )

        val cashier = effectiveAdminSystemModeConfigForRole(config, UserRole.CASHIER)

        assertFalse(cashier.lotteryModeEnabled)
        assertTrue(cashier.pickModeEnabled)
    }

    @Test
    fun `cashier mode can use the same three sale modes without changing admin mode`() {
        val config = AdminSystemModeConfig(
            lotteryModeEnabled = true,
            pickModeEnabled = false,
            cashierModeEnabled = true,
            cashierLotteryModeEnabled = true,
            cashierPickModeEnabled = true,
        )

        val cashier = effectiveAdminSystemModeConfigForRole(config, UserRole.CASHIER)
        val admin = effectiveAdminSystemModeConfigForRole(config, UserRole.ADMIN)

        assertTrue(cashier.lotteryModeEnabled)
        assertTrue(cashier.pickModeEnabled)
        assertTrue(admin.lotteryModeEnabled)
        assertFalse(admin.pickModeEnabled)
    }

    @Test
    fun `cashier mode override can keep old pos on lottery only`() {
        val config = AdminSystemModeConfig(
            lotteryModeEnabled = true,
            pickModeEnabled = true,
            cashierModeEnabled = true,
            cashierLotteryModeEnabled = true,
            cashierPickModeEnabled = true,
        )
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "CAJ-1",
            username = "cajero1",
            adminId = "ADM-1",
        )
        val accounts = listOf(
            UserAccount(
                id = "CAJ-1",
                user = "cajero1",
                role = UserRole.CASHIER,
                systemModeOverride = "lottery",
            ),
        )

        val effective = effectiveSystemModeConfigForSession(config, session, accounts)

        assertTrue(effective.lotteryModeEnabled)
        assertFalse(effective.pickModeEnabled)
    }

    @Test
    fun `cashier without override defaults to lottery only`() {
        val config = AdminSystemModeConfig(
            lotteryModeEnabled = true,
            pickModeEnabled = false,
            cashierModeEnabled = true,
            cashierLotteryModeEnabled = false,
            cashierPickModeEnabled = true,
        )
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "CAJ-2",
            username = "cajero2",
            adminId = "ADM-1",
        )

        val effective = effectiveSystemModeConfigForSession(config, session, emptyList())

        assertTrue(effective.lotteryModeEnabled)
        assertFalse(effective.pickModeEnabled)
    }
}
