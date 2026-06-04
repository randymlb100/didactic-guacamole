package com.lotterynet.pro.core.model

import com.lotterynet.pro.core.storage.MasterSportsbookSettings
import com.lotterynet.pro.core.storage.decodeMasterSportsbookSettings
import com.lotterynet.pro.core.storage.encodeMasterSportsbookSettings
import com.lotterynet.pro.core.storage.toFeatureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsbookModelsContractsTest {
    @Test
    fun `sportsbook feature is hidden by default`() {
        val config = SportsbookFeatureConfig()

        assertFalse(config.canOpen(UserRole.ADMIN, "adm-1"))
        assertFalse(config.canOpen(UserRole.CASHIER, "cashier-1"))
    }

    @Test
    fun `enabled feature can be restricted by role and actor`() {
        val config = SportsbookFeatureConfig(
            enabled = true,
            allowedRoles = setOf(UserRole.ADMIN),
            allowedActorKeys = setOf("adm-1"),
        )

        assertTrue(config.canOpen(UserRole.ADMIN, "adm-1"))
        assertFalse(config.canOpen(UserRole.ADMIN, "adm-2"))
        assertFalse(config.canOpen(UserRole.CASHIER, "adm-1"))
    }

    @Test
    fun `enabled feature still requires a selected account scope`() {
        val config = SportsbookFeatureConfig(
            enabled = true,
            allowedRoles = setOf(UserRole.ADMIN, UserRole.CASHIER),
        )

        assertFalse(config.canOpen(UserRole.ADMIN, "adm-1"))
        assertFalse(config.canOpen(UserRole.CASHIER, "cashier-1", "adm-1"))
    }

    @Test
    fun `cashier can be opened by own key or selected admin group`() {
        val config = SportsbookFeatureConfig(
            enabled = true,
            allowedRoles = setOf(UserRole.CASHIER),
            allowedActorKeys = setOf("cashier-1"),
            cashierAdminKeys = setOf("adm-1"),
        )

        assertTrue(config.canOpen(UserRole.CASHIER, "cashier-1", "adm-2"))
        assertTrue(config.canOpen(UserRole.CASHIER, "cashier-2", "adm-1"))
        assertFalse(config.canOpen(UserRole.CASHIER, "cashier-2", "adm-2"))
        assertFalse(config.canOpen(UserRole.ADMIN, "adm-1"))
    }

    @Test
    fun `empty market set means all initial markets are available`() {
        val config = SportsbookFeatureConfig(enabled = true)

        assertTrue(config.isMarketEnabled(SportsbookMarketKey.MONEYLINE))
        assertTrue(config.isMarketEnabled(SportsbookMarketKey.TOTAL))
    }

    @Test
    fun `parlay combined odds multiply decimal odds`() {
        val selections = listOf(
            SportsbookSelection(
                eventId = "event-1",
                market = SportsbookMarketKey.MONEYLINE,
                selectionKey = "home",
                selectionLabel = "Yankees",
                decimalOdds = 1.82,
                oddsLockedAtEpochMs = 1L,
            ),
            SportsbookSelection(
                eventId = "event-2",
                market = SportsbookMarketKey.TOTAL,
                selectionKey = "over",
                selectionLabel = "Alta 8.5",
                decimalOdds = 1.90,
                oddsLockedAtEpochMs = 1L,
            ),
        )

        assertEquals(3.458, calculateSportsbookCombinedDecimalOdds(selections), 0.0001)
        assertEquals(345.8, calculateSportsbookPotentialPayout(100.0, 3.458), 0.0001)
    }

    @Test
    fun `invalid payout inputs return zero`() {
        assertEquals(0.0, calculateSportsbookPotentialPayout(0.0, 1.82), 0.0)
        assertEquals(0.0, calculateSportsbookPotentialPayout(100.0, 1.0), 0.0)
    }

    @Test
    fun `master sportsbook settings encode and decode role access`() {
        val settings = MasterSportsbookSettings(
            enabled = true,
            adminEnabled = true,
            cashierEnabled = true,
            allowedActorKeys = setOf("admin-1"),
            cashierAdminKeys = setOf("admin-1"),
            enabledMarkets = setOf(SportsbookMarketKey.MONEYLINE, SportsbookMarketKey.TOTAL),
            updatedAtEpochMs = 42L,
            updatedBy = "master",
        )

        val decoded = decodeMasterSportsbookSettings(encodeMasterSportsbookSettings(settings))
        val featureConfig = decoded.toFeatureConfig()

        assertTrue(featureConfig.canOpen(UserRole.ADMIN, "admin-1"))
        assertTrue(featureConfig.canOpen(UserRole.CASHIER, "cashier-1", "admin-1"))
        assertFalse(featureConfig.canOpen(UserRole.CASHIER, "cashier-1", "admin-2"))
        assertFalse(featureConfig.canOpen(UserRole.SUPERVISOR, "supervisor-1"))
        assertTrue(featureConfig.isMarketEnabled(SportsbookMarketKey.MONEYLINE))
        assertFalse(featureConfig.isMarketEnabled(SportsbookMarketKey.RUNLINE))
    }
}
