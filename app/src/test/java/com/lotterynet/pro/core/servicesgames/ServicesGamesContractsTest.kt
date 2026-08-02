package com.lotterynet.pro.core.servicesgames

import com.lotterynet.pro.core.model.UserRole
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesGamesContractsTest {
    @Test
    fun `module catalog intentionally excludes IPTV`() {
        assertEquals(setOf("services", "video_games"), ServicesGamesModule.entries.map { it.wireValue }.toSet())
        assertEquals(null, ServicesGamesModule.fromWire("iptv"))
    }

    @Test
    fun `master activation and individual admin cashier access are independent`() {
        val config = ServicesGamesFeatureConfig(
            module = ServicesGamesModule.SERVICES,
            enabled = true,
            allowedAdminKeys = setOf("admin-a"),
            allowedCashierKeys = setOf("cashier-one"),
            cashierAdminKeys = setOf("admin-a"),
        )

        assertTrue(config.canOpen(UserRole.MASTER, "master"))
        assertTrue(config.canOpen(UserRole.ADMIN, "admin-a"))
        assertFalse(config.canOpen(UserRole.ADMIN, "admin-b"))
        assertTrue(config.canOpen(UserRole.CASHIER, "cashier-one", "admin-b"))
        assertTrue(config.canOpen(UserRole.CASHIER, "cashier-two", "admin-a"))
        assertFalse(config.canOpen(UserRole.CASHIER, "cashier-two", "admin-b"))
    }

    @Test
    fun `cashier cannot access add funds because it is not a module permission`() {
        val config = ServicesGamesFeatureConfig(
            module = ServicesGamesModule.SERVICES,
            enabled = true,
            allowedCashierKeys = setOf("cashier-one"),
        )

        assertTrue(config.canOpen(UserRole.CASHIER, "cashier-one", "admin-a"))
        // Add-funds has no ServicesGames permission and remains an Admin-only flow.
        assertFalse(ServicesGamesTargetType.entries.any { it == ServicesGamesTargetType.CASHIER && it.wireValue == "add_funds" })
    }

    @Test
    fun `query and confirm payloads keep module and provider identity`() {
        val query = buildServicesGamesQueryPayload(
            ServicesGamesQueryRequest(
                clientRequestId = "req-1",
                module = ServicesGamesModule.VIDEO_GAMES,
                providerId = "provider-games",
                productId = "free-fire-100",
                adminKey = "admin-a",
                cashierKey = "cashier-one",
                customerInput = JSONObject().put("playerId", "player-1"),
            ),
        )
        val confirm = buildServicesGamesConfirmPayload(
            ServicesGamesConfirmRequest(
                clientRequestId = "req-1",
                module = ServicesGamesModule.VIDEO_GAMES,
                providerId = "provider-games",
                productId = "free-fire-100",
                adminKey = "admin-a",
                cashierKey = "cashier-one",
                quotedPrice = 100.0,
                customerInput = JSONObject().put("playerId", "player-1"),
            ),
        )

        assertEquals("query", query.getString("action"))
        assertEquals("confirm", confirm.getString("action"))
        assertEquals("video_games", confirm.getString("module"))
        assertEquals("provider-games", confirm.getString("providerId"))
        assertEquals("free-fire-100", confirm.getString("productId"))
    }
}
