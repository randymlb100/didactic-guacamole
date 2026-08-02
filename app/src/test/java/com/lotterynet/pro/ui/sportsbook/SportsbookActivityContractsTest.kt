package com.lotterynet.pro.ui.sportsbook

import com.lotterynet.pro.core.model.SportsbookBoardGame
import com.lotterynet.pro.core.model.SportsbookEvent
import com.lotterynet.pro.core.model.SportsbookMarket
import com.lotterynet.pro.core.model.SportsbookMarketKey
import com.lotterynet.pro.core.model.SportsbookOdd
import com.lotterynet.pro.core.model.SportsbookTicketSaleResult
import com.lotterynet.pro.core.model.SportsbookSelection
import com.lotterynet.pro.core.sportsbook.SportsbookTicketSnapshot
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.sportsbook.parseSportsbookBoardSnapshot
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.MasterSportsbookSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsbookActivityContractsTest {
    @Test
    fun `master only sees sportsbook system controls`() {
        assertEquals(listOf("config"), resolveSportsbookTabIdsForRole(UserRole.MASTER))
        assertEquals("config", resolveSportsbookInitialTab(UserRole.MASTER))
    }

    @Test
    fun `sportsbook access reason explains global role individual and group states`() {
        val admin = UserAccount(id = "admin-1", user = "admin1", role = UserRole.ADMIN)
        val cashier = UserAccount(id = "cashier-1", user = "cashier1", role = UserRole.CASHIER, adminId = admin.id)
        val base = MasterSportsbookSettings(enabled = true, adminEnabled = true, cashierEnabled = true)

        assertEquals("Activo por rol", sportsbookAccessReason(base, UserRole.ADMIN, admin))
        assertEquals(
            "Activo individual",
            sportsbookAccessReason(base.copy(allowedActorKeys = setOf(admin.id)), UserRole.ADMIN, admin),
        )
        assertEquals(
            "Activo por grupo",
            sportsbookAccessReason(base.copy(cashierAdminKeys = setOf(admin.id)), UserRole.CASHIER, cashier, admin),
        )
        assertEquals(
            "Bloqueado: Deportes global apagado",
            sportsbookAccessReason(base.copy(enabled = false), UserRole.ADMIN, admin),
        )
    }

    @Test
    fun `business roles keep operational sportsbook tabs`() {
        val expectedOperational = listOf("juegos", "ticket", "cobros", "finanza", "reportes")

        assertEquals(expectedOperational + "control", resolveSportsbookTabIdsForRole(UserRole.ADMIN))
        assertEquals(expectedOperational, resolveSportsbookTabIdsForRole(UserRole.SUPERVISOR))
        assertEquals(expectedOperational, resolveSportsbookTabIdsForRole(UserRole.CASHIER))
    }

    @Test
    fun `master does not load operational sportsbook board`() {
        val settings = MasterSportsbookSettings(
            enabled = true,
            adminEnabled = true,
            supervisorEnabled = true,
            cashierEnabled = true,
        )

        assertFalse(canLoadSportsbookBoard(UserRole.MASTER, settings, "master"))
        assertTrue(canLoadSportsbookBoard(UserRole.ADMIN, settings, "admin-1"))
        assertTrue(canLoadSportsbookBoard(UserRole.CASHIER, settings, "cashier-1"))
    }

    @Test
    fun `master can target one admin without opening every cashier`() {
        val admin = com.lotterynet.pro.core.model.UserAccount(
            id = "ADM-C5FFB0",
            user = "podero02",
            role = UserRole.ADMIN,
        )
        val settings = MasterSportsbookSettings(
            enabled = true,
            adminEnabled = true,
            cashierEnabled = true,
        ).withAccountAccess(admin, true)

        assertTrue(canLoadSportsbookBoard(UserRole.ADMIN, settings, "ADM-C5FFB0"))
        assertFalse(canLoadSportsbookBoard(UserRole.CASHIER, settings, "CAJ-1", "ADM-C5FFB0"))
    }

    @Test
    fun `master can open all cashiers for selected admin separately`() {
        val admin = com.lotterynet.pro.core.model.UserAccount(
            id = "ADM-C5FFB0",
            user = "podero02",
            role = UserRole.ADMIN,
        )
        val settings = MasterSportsbookSettings(
            enabled = true,
            cashierEnabled = true,
        ).withCashierAdminAccess(admin, true)

        assertTrue(canLoadSportsbookBoard(UserRole.CASHIER, settings, "CAJ-1", "ADM-C5FFB0"))
        assertFalse(canLoadSportsbookBoard(UserRole.CASHIER, settings, "CAJ-2", "ADM-OTHER"))
    }

    @Test
    fun `sportsbook board filters by league and status`() {
        val openMlb = sportsbookGame(id = "1", league = "MLB", status = "open")
        val closedNba = sportsbookGame(id = "2", league = "NBA", status = "closed")
        val games = listOf(openMlb, closedNba)

        val leagues = buildSportsbookLeagueFilterOptions(games).map { it.label }

        assertEquals(listOf("Todas", "MLB", "NBA"), leagues)
        assertEquals(listOf(openMlb), filterSportsbookBoardGames(games, "MLB", "open"))
        assertEquals(listOf(closedNba), filterSportsbookBoardGames(games, "all", "closed"))
    }

    @Test
    fun `sportsbook board filters by sport without touching lottery filters`() {
        val mlb = sportsbookGame(id = "1", league = "MLB", status = "open").copy(
            event = sportsbookGame(id = "1", league = "MLB", status = "open").event.copy(sportTitle = "Baseball"),
        )
        val nba = sportsbookGame(id = "2", league = "NBA", status = "open").copy(
            event = sportsbookGame(id = "2", league = "NBA", status = "open").event.copy(sportTitle = "Basketball"),
        )

        assertEquals(listOf("Todas", "Baseball", "Basketball"), buildSportsbookSportFilterOptions(listOf(mlb, nba)).map { it.label })
        assertEquals(listOf(mlb), filterSportsbookBoardGames(listOf(mlb, nba), "all", "open", "Baseball"))
    }

    @Test
    fun `sportsbook exposes date and market filters`() {
        val game = sportsbookGame(id = "1", league = "MLB", status = "open")
        val market = SportsbookMarket("market-1", "1", SportsbookMarketKey.MONEYLINE, "Ganador", "open")
        val withMarket = game.copy(markets = listOf(market))

        assertEquals(listOf("Todas", "Hoy", "Mañana"), sportsbookDateFilterOptions().map { it.label })
        assertEquals(listOf("Todas", "Moneyline"), buildSportsbookMarketFilterOptions(listOf(withMarket)).map { it.label })
    }

    @Test
    fun `sportsbook board parses cached team logo urls`() {
        val snapshot = parseSportsbookBoardSnapshot(
            """
            {
              "games": [
                {
                  "event": {
                    "id": "game-1",
                    "sportKey": "baseball_mlb",
                    "sportTitle": "Baseball",
                    "leagueTitle": "MLB",
                    "homeTeam": "Boston Red Sox",
                    "awayTeam": "New York Yankees",
                    "homeTeamLogoUrl": "https://cdn.example.com/boston.png",
                    "awayTeamLogoUrl": "https://cdn.example.com/yankees.png",
                    "commenceTime": "2026-05-31T20:00:00Z",
                    "status": "open"
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val event = snapshot.games.single().event

        assertEquals("https://cdn.example.com/boston.png", event.homeTeamLogoUrl)
        assertEquals("https://cdn.example.com/yankees.png", event.awayTeamLogoUrl)
    }

    @Test
    fun `sportsbook selection keeps server odds id and replaces same market`() {
        val game = sportsbookGame(id = "game-1", league = "NBA", status = "open")
        val market = SportsbookMarket(
            id = "market-1",
            eventId = "game-1",
            key = SportsbookMarketKey.MONEYLINE,
            title = "Ganador",
        )
        val homeOdd = SportsbookOdd(
            id = "odd-home",
            marketId = "market-1",
            selectionKey = "home",
            selectionLabel = "Home",
            decimalOdds = 1.9,
            lastUpdatedEpochMs = 100L,
        )
        val awayOdd = homeOdd.copy(id = "odd-away", selectionKey = "away", selectionLabel = "Away")

        val homeSelection = buildSportsbookSelection(game, market, homeOdd)
        val awaySelection = buildSportsbookSelection(game, market, awayOdd)
        val next = toggleSportsbookSelection(listOf(homeSelection), awaySelection)

        assertEquals("odd-home", homeSelection.oddsId)
        assertEquals("Away game-1 @ Home game-1", homeSelection.eventLabel)
        assertEquals(listOf("odd-away"), next.map { it.oddsId })
    }

    @Test
    fun `newly sold sports ticket stays visible while remote list catches up`() {
        val sale = SportsbookTicketSaleResult(
            ticketCode = "SN-NEW",
            status = com.lotterynet.pro.core.model.SportsbookTicketStatus.PENDING,
            stake = 50.0,
            decimalOdds = 1.9,
            potentialPayout = 95.0,
        )
        val local = buildSportsbookTicketRecordFromSale(
            session = com.lotterynet.pro.core.model.ActiveSession(
                role = UserRole.CASHIER,
                userId = "cashier-1",
                username = "cajero",
            ),
            sale = sale,
            selections = listOf(
                SportsbookSelection(
                    oddsId = "odd-1",
                    eventId = "event-1",
                    market = SportsbookMarketKey.MONEYLINE,
                    selectionKey = "home",
                    selectionLabel = "Home",
                    decimalOdds = 1.9,
                    oddsLockedAtEpochMs = 1L,
                    eventLabel = "Away @ Home",
                    marketTitle = "Ganador",
                ),
            ),
        )

        val merged = mergeSportsbookTicketSnapshot(SportsbookTicketSnapshot(), local)

        assertEquals(listOf("SN-NEW"), merged.tickets.map { it.ticketCode })
        assertEquals(1, merged.summary.totalTickets)
        assertEquals(50.0, merged.summary.totalStake, 0.0)
    }

    private fun sportsbookGame(id: String, league: String, status: String): SportsbookBoardGame {
        return SportsbookBoardGame(
            event = SportsbookEvent(
                id = id,
                sportKey = "baseball_mlb",
                sportTitle = "Baseball",
                leagueTitle = league,
                homeTeam = "Home $id",
                awayTeam = "Away $id",
                commenceTimeEpochMs = 1L,
                status = status,
            ),
        )
    }
}
