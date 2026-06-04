package com.lotterynet.pro.core.sportsbook

import com.lotterynet.pro.core.model.SportsbookMarketKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsbookBoardRemoteStoreTest {
    @Test
    fun `parse board groups event markets and odds`() {
        val snapshot = parseSportsbookBoardSnapshot(
            """
            {
              "fetchedAt": "2026-05-30T16:00:00Z",
              "source": "cache",
              "games": [
                {
                  "event": {
                    "id": "event-1",
                    "sportKey": "baseball_mlb",
                    "sportTitle": "MLB",
                    "leagueTitle": "MLB",
                    "homeTeam": "Yankees",
                    "awayTeam": "Red Sox",
                    "commenceTime": "2026-05-30T23:05:00Z",
                    "status": "open"
                  },
                  "markets": [
                    {"id": "market-1", "eventId": "event-1", "marketKey": "moneyline", "marketTitle": "Moneyline", "status": "open"}
                  ],
                  "odds": [
                    {"id": "odd-1", "marketId": "market-1", "selectionKey": "home", "selectionLabel": "Yankees", "decimalOdds": 1.85, "status": "open"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, snapshot.games.size)
        assertEquals(1, snapshot.openGames)
        assertEquals("Yankees", snapshot.games.first().event.homeTeam)
        assertEquals(SportsbookMarketKey.MONEYLINE, snapshot.games.first().markets.first().key)
        assertEquals(1.85, snapshot.games.first().odds.first().decimalOdds, 0.0)
        assertTrue(snapshot.fetchedAtEpochMs > 0L)
    }
}
