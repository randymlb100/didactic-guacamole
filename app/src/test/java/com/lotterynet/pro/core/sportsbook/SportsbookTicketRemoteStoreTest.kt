package com.lotterynet.pro.core.sportsbook

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SportsbookMarketKey
import com.lotterynet.pro.core.model.SportsbookSelection
import com.lotterynet.pro.core.model.SportsbookTicketDraft
import com.lotterynet.pro.core.model.SportsbookTicketLegRecord
import com.lotterynet.pro.core.model.SportsbookTicketRecord
import com.lotterynet.pro.core.model.SportsbookTicketStatus
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.printing.ThermalTicketRenderer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SportsbookTicketRemoteStoreTest {
    @Test
    fun `payload sends admin and cashier scope without lottery tables`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-1",
            username = "bancay01",
            adminId = "admin-1",
            adminUser = "ramonc3",
            banca = "1-Banca",
            authAccessToken = "eyJ.test.jwt",
        )
        val draft = SportsbookTicketDraft(
            selections = listOf(selection("odds-1")),
            stake = 50.0,
        )

        val payload = buildSportsbookTicketPayload(session, draft, "req-1")

        assertEquals("cashier", payload.getString("actorRole"))
        assertEquals("cashier-1", payload.getString("actorKey"))
        assertEquals("admin-1", payload.getString("adminKey"))
        assertEquals("cashier-1", payload.getString("cashierKey"))
        assertEquals("req-1", payload.getString("clientRequestId"))
        assertEquals("odds-1", payload.getJSONArray("selections").getJSONObject(0).getString("oddsId"))
    }

    @Test
    fun `sale result parses duplicate protection response`() {
        val result = parseSportsbookTicketSaleResult(
            JSONObject(
                """
                {
                  "ok": true,
                  "duplicate": true,
                  "ticket": {
                    "ticket_code": "SN-1234",
                    "status": "pending",
                    "stake": 50,
                    "decimal_odds": 1.9,
                    "potential_payout": 95
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("SN-1234", result.ticketCode)
        assertEquals(SportsbookTicketStatus.PENDING, result.status)
        assertEquals(95.0, result.potentialPayout, 0.0)
        assertEquals(true, result.duplicate)
    }

    @Test
    fun `ticket list payload and parser keep sports finance separated`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            authAccessToken = "eyJ.test.jwt",
        )
        val payload = buildSportsbookTicketListPayload(session, "pending", 25)
        val snapshot = parseSportsbookTicketSnapshot(
            JSONObject(
                """
                {
                  "ok": true,
                  "tickets": [
                    {
                      "id": "ticket-1",
                      "ticketCode": "SN-1234",
                      "sellerUsername": "bancay01",
                      "bancaName": "1-Banca",
                      "ticketType": "straight",
                      "stake": 100,
                      "decimalOdds": 1.9,
                      "potentialPayout": 190,
                      "status": "pending",
                      "soldAt": "2026-05-31T12:00:00Z",
                      "legs": [
                        {
                          "eventLabel": "Away @ Home",
                          "marketTitle": "Ganador",
                          "selectionLabel": "Home",
                          "decimalOdds": 1.9,
                          "status": "pending"
                        }
                      ]
                    }
                  ],
                  "summary": {
                    "totalTickets": 1,
                    "pendingTickets": 1,
                    "totalStake": 100,
                    "pendingPayout": 190
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("admin", payload.getString("actorRole"))
        assertEquals("pending", payload.getString("status"))
        assertEquals("SN-1234", snapshot.tickets.single().ticketCode)
        assertEquals("Away @ Home", snapshot.tickets.single().legs.single().eventLabel)
        assertEquals(100.0, snapshot.summary.totalStake, 0.0)
        assertEquals(190.0, snapshot.summary.pendingPayout, 0.0)
    }

    @Test
    fun `pay ticket payload keeps sports ticket scope explicit`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            authAccessToken = "eyJ.test.jwt",
        )

        val payload = buildSportsbookPayTicketPayload(session, "ticket-1")

        assertEquals("admin", payload.getString("actorRole"))
        assertEquals("admin-1", payload.getString("actorKey"))
        assertEquals("admin-1", payload.getString("ownerKey"))
        assertEquals("ticket-1", payload.getString("ticketId"))
    }

    @Test
    fun `settlement payload only accepts terminal sports result states`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            authAccessToken = "eyJ.test.jwt",
        )

        val payload = buildSportsbookSettlementPayload(
            session = session,
            ticketId = "ticket-1",
            nextStatus = SportsbookTicketStatus.WON,
            reason = "Resultado confirmado",
        )

        assertEquals("admin", payload.getString("actorRole"))
        assertEquals("admin-1", payload.getString("actorKey"))
        assertEquals("ticket-1", payload.getString("ticketId"))
        assertEquals("won", payload.getString("nextStatus"))
        assertEquals("Resultado confirmado", payload.getString("reason"))
    }

    @Test
    fun `sportsbook thermal ticket shows selections stake and possible payout`() {
        val text = ThermalTicketRenderer().renderSportsbookTicket(
            ticket = sportsbookTicket(),
            bancaName = "1-Banca",
            prefs = ThermalPrinterPrefs(),
        )

        assert(text.contains("DEPORTE"))
        assert(text.contains("SN-1234"))
        assert(text.contains("AWAY @ HOME"))
        assert(text.contains("PAGO POSIBLE"))
        assert(text.contains("190"))
    }

    private fun selection(oddsId: String): SportsbookSelection {
        return SportsbookSelection(
            oddsId = oddsId,
            eventId = "event-1",
            market = SportsbookMarketKey.MONEYLINE,
            selectionKey = "home",
            selectionLabel = "Home",
            decimalOdds = 1.9,
            oddsLockedAtEpochMs = 1L,
        )
    }

    private fun sportsbookTicket(): SportsbookTicketRecord {
        return SportsbookTicketRecord(
            id = "ticket-1",
            ticketCode = "SN-1234",
            sellerUsername = "bancay01",
            bancaName = "1-Banca",
            ticketType = "straight",
            stake = 100.0,
            decimalOdds = 1.9,
            potentialPayout = 190.0,
            status = SportsbookTicketStatus.PENDING,
            soldAtEpochMs = 1_780_000_000_000L,
            legs = listOf(
                SportsbookTicketLegRecord(
                    eventLabel = "Away @ Home",
                    marketTitle = "Ganador",
                    selectionLabel = "Home",
                    decimalOdds = 1.9,
                    status = SportsbookTicketStatus.PENDING,
                ),
            ),
        )
    }
}
