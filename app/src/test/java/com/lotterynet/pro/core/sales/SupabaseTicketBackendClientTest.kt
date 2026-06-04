package com.lotterynet.pro.core.sales

import com.lotterynet.pro.core.remote.SupabaseEdgeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTicketBackendClientTest {
    @Test
    fun `buildCreateTicketPayload sends server ticket validation fields`() {
        val client = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        )
        val payload = client.buildCreateTicketPayload(
            BackendTicketRequest(
                clientRequestId = "ticket-123456",
                localTicketId = "native-123456",
                adminKey = "admin01",
                adminId = "ADM-1",
                actorKey = "admin01",
                actorId = "ADM-1",
                cashierKey = "cajero01",
                cashierId = "CAJ-1",
                drawDate = "2026-05-06",
                dayKey = "2026-05-06",
                lotteryName = "Quiniela Leidsa",
                phoneTimeIso = "2026-05-06T18:00:00Z",
                plays = listOf(
                    BackendTicketPlay("QUINIELA", "15", 10.0, 700.0, lotteryId = "leidsa", lotteryName = "Quiniela Leidsa"),
                    BackendTicketPlay("PALE", "14/15", 5.0, 5000.0, lotteryId = "leidsa", lotteryName = "Quiniela Leidsa"),
                ),
            ),
        )

        assertEquals("ticket-123456", payload.getString("clientRequestId"))
        assertEquals("native-123456", payload.getString("localTicketId"))
        assertEquals("admin01", payload.getString("adminKey"))
        assertEquals("ADM-1", payload.getString("adminId"))
        assertEquals("admin01", payload.getString("actorKey"))
        assertEquals("ADM-1", payload.getString("actorId"))
        assertEquals("cajero01", payload.getString("cashierKey"))
        assertEquals("CAJ-1", payload.getString("cashierId"))
        assertEquals("2026-05-06", payload.getString("drawDate"))
        assertEquals("2026-05-06", payload.getString("dayKey"))
        assertEquals("2026-05-06T18:00:00Z", payload.getString("phoneTime"))
        assertEquals("QUINIELA", payload.getJSONArray("plays").getJSONObject(0).getString("playType"))
        assertEquals(10.0, payload.getJSONArray("plays").getJSONObject(0).getDouble("amount"), 0.001)
        assertEquals("leidsa", payload.getJSONArray("plays").getJSONObject(0).getString("lotteryId"))
        assertEquals("14/15", payload.getJSONArray("plays").getJSONObject(1).getString("number"))
    }

    @Test
    fun `buildCreateTicketPayload rejects empty ticket plays`() {
        val client = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        )

        val error = runCatching {
            client.buildCreateTicketPayload(
                BackendTicketRequest(
                    clientRequestId = "empty-ticket",
                    adminKey = "admin01",
                    actorKey = "admin01",
                    cashierKey = "cajero01",
                    drawDate = "2026-05-29",
                    plays = emptyList(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("No hay jugadas"))
    }

    @Test
    fun `buildCreateTicketPayload rejects blank play number`() {
        val client = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        )

        val error = runCatching {
            client.buildCreateTicketPayload(
                BackendTicketRequest(
                    clientRequestId = "blank-play",
                    adminKey = "admin01",
                    actorKey = "admin01",
                    cashierKey = "cajero01",
                    drawDate = "2026-05-29",
                    plays = listOf(BackendTicketPlay("Q", "", 10.0)),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("incompleta"))
    }

    @Test
    fun `buildCreateTicketPayload rejects play without lottery identity`() {
        val client = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        )

        val error = runCatching {
            client.buildCreateTicketPayload(
                BackendTicketRequest(
                    clientRequestId = "blank-lottery",
                    adminKey = "admin01",
                    actorKey = "admin01",
                    cashierKey = "cajero01",
                    drawDate = "2026-05-29",
                    plays = listOf(BackendTicketPlay("Q", "25", 10.0, lotteryId = "", lotteryName = "")),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("incompleta"))
    }

    @Test
    fun `buildCreateTicketPayload sends server pick codes and keeps local mode metadata`() {
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildCreateTicketPayload(
            BackendTicketRequest(
                clientRequestId = "ticket-pick",
                adminKey = "admin01",
                actorKey = "admin01",
                cashierKey = "cajero01",
                drawDate = "2026-05-08",
                plays = listOf(
                    BackendTicketPlay("P3", "358", 10.0, lotteryId = "pick3", lotteryName = "Pick 3 Dia"),
                    BackendTicketPlay("P3BOX", "358", 10.0, lotteryId = "pick3", lotteryName = "Pick 3 Dia"),
                    BackendTicketPlay("P4", "3581", 10.0, lotteryId = "pick4", lotteryName = "Pick 4 Dia"),
                    BackendTicketPlay("P4BOX", "3581", 10.0, lotteryId = "pick4", lotteryName = "Pick 4 Dia"),
                ),
            ),
        )

        val plays = payload.getJSONArray("plays")
        assertEquals("PICK3_STRAIGHT", plays.getJSONObject(0).getString("playType"))
        assertEquals("PICK3_BOX", plays.getJSONObject(1).getString("playType"))
        assertEquals("PICK4_STRAIGHT", plays.getJSONObject(2).getString("playType"))
        assertEquals("PICK4_BOX", plays.getJSONObject(3).getString("playType"))
        assertEquals("P3", plays.getJSONObject(0).getString("localPlayType"))
        assertEquals("P3BOX", plays.getJSONObject(1).getString("localPlayType"))
        assertEquals("P4", plays.getJSONObject(2).getString("localPlayType"))
        assertEquals("P4BOX", plays.getJSONObject(3).getString("localPlayType"))
        assertEquals("PICK3_STRAIGHT", plays.getJSONObject(0).getString("serverPlayType"))
        assertEquals("PICK3_BOX", plays.getJSONObject(1).getString("serverPlayType"))
        assertEquals("PICK4_STRAIGHT", plays.getJSONObject(2).getString("serverPlayType"))
        assertEquals("PICK4_BOX", plays.getJSONObject(3).getString("serverPlayType"))
        assertEquals("PICK3", plays.getJSONObject(0).getString("pickGame"))
        assertEquals("STRAIGHT", plays.getJSONObject(0).getString("pickMode"))
        assertEquals("BOX", plays.getJSONObject(1).getString("pickMode"))
        assertEquals("P3", plays.getJSONObject(0).getString("localPlayType"))
    }

    @Test
    fun `buildCreateTicketPayload preserves pick decimal cents amount`() {
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildCreateTicketPayload(
            BackendTicketRequest(
                clientRequestId = "ticket-pick-cents",
                adminKey = "admin01",
                actorKey = "admin01",
                cashierKey = "cajero01",
                drawDate = "2026-05-08",
                plays = listOf(
                    BackendTicketPlay("P3", "358", 0.50, lotteryId = "pick3", lotteryName = "Pick 3 Dia"),
                ),
            ),
        )

        val play = payload.getJSONArray("plays").getJSONObject(0)
        assertEquals("PICK3_STRAIGHT", play.getString("playType"))
        assertEquals(0.50, play.getDouble("amount"), 0.001)
    }

    @Test
    fun `buildCreateTicketPayload omits non uuid lottery id as sorteo id`() {
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildCreateTicketPayload(
            BackendTicketRequest(
                clientRequestId = "ticket-legacy-lottery",
                adminKey = "admin01",
                actorKey = "admin01",
                cashierKey = "cajero01",
                sorteoId = "LEIDSA",
                drawDate = "2026-05-18",
                plays = listOf(
                    BackendTicketPlay("QUINIELA", "15", 10.0, lotteryId = "LEIDSA", lotteryName = "Leidsa"),
                ),
            ),
        )

        assertTrue(payload.isNull("sorteoId"))
        assertEquals("LEIDSA", payload.getJSONArray("plays").getJSONObject(0).getString("lotteryId"))
    }

    @Test
    fun `buildCreateTicketPayload keeps uuid sorteo id`() {
        val sorteoId = "123e4567-e89b-12d3-a456-426614174000"
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildCreateTicketPayload(
            BackendTicketRequest(
                clientRequestId = "ticket-sorteo",
                adminKey = "admin01",
                actorKey = "admin01",
                cashierKey = "cajero01",
                sorteoId = sorteoId,
                drawDate = "2026-05-18",
                plays = listOf(BackendTicketPlay("QUINIELA", "15", 10.0, lotteryId = sorteoId, lotteryName = "Leidsa")),
            ),
        )

        assertEquals(sorteoId, payload.getString("sorteoId"))
    }

    @Test
    fun `buildCreateTicketPayload sends pick number without display suffix`() {
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildCreateTicketPayload(
            BackendTicketRequest(
                clientRequestId = "ticket-pick-852",
                adminKey = "admin01",
                actorKey = "admin01",
                cashierKey = "cajero01",
                drawDate = "2026-05-10",
                plays = listOf(
                    BackendTicketPlay("P3", "852", 5.0, lotteryId = "US-P3-TX-PICK-3-DAY", lotteryName = "Texas Pick 3 Day"),
                ),
            ),
        )

        val play = payload.getJSONArray("plays").getJSONObject(0)

        assertEquals("PICK3_STRAIGHT", play.getString("playType"))
        assertEquals("852", play.getString("number"))
        assertEquals("P3", play.getString("localPlayType"))
        assertEquals("PICK3_STRAIGHT", play.getString("serverPlayType"))
        assertEquals("STRAIGHT", play.getString("pickMode"))
    }

    @Test
    fun `build ticket action payload supports server delete`() {
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildTicketActionPayload(
            BackendTicketActionRequest(
                actorKey = "admin01",
                adminKey = "admin01",
                clientRequestId = "native-123",
                action = "delete",
                returnLimit = true,
            ),
        )

        assertEquals("admin01", payload.getString("actorKey"))
        assertEquals("native-123", payload.getString("clientRequestId"))
        assertEquals("delete", payload.getString("action"))
        assertEquals(true, payload.getBoolean("returnLimit"))
    }

    @Test
    fun `build ticket action payload supports server pay`() {
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildTicketActionPayload(
            BackendTicketActionRequest(
                actorKey = "cajero01",
                adminKey = "admin01",
                cashierKey = "cajero01",
                localTicketId = "native-123",
                clientRequestId = "native-123",
                action = "pay",
            ),
        )

        assertEquals("cajero01", payload.getString("actorKey"))
        assertEquals("admin01", payload.getString("adminKey"))
        assertEquals("cajero01", payload.getString("cashierKey"))
        assertEquals("native-123", payload.getString("localTicketId"))
        assertEquals("native-123", payload.getString("clientRequestId"))
        assertEquals("pay", payload.getString("action"))
    }

    @Test
    fun `build report payload sends backend filters`() {
        val payload = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        ).buildReportPayload(
            BackendReportRequest(
                actorKey = "super01",
                adminKey = "admin01",
                supervisorKey = "super01",
                from = "2026-05-01",
                to = "2026-05-06",
            ),
        )

        assertEquals("super01", payload.getString("actorKey"))
        assertEquals("admin01", payload.getString("adminKey"))
        assertEquals("super01", payload.getString("supervisorKey"))
        assertEquals("2026-05-06", payload.getString("to"))
    }

    @Test
    fun `ticket backend routes critical actions through edge functions`() {
        val client = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        )

        assertEquals("functions/v1/create-ticket-v2", client.createTicketEndpointPath())
        assertEquals("functions/v1/pay-ticket", client.payTicketEndpointPath())
        assertEquals("functions/v1/void-ticket", client.ticketActionEndpointPath())
    }

    @Test
    fun `report backend routes by requested scope through edge functions`() {
        val client = SupabaseTicketBackendClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        )

        assertEquals(
            "functions/v1/get-admin-report",
            client.reportEndpointPath(
                BackendReportRequest(
                    actorKey = "admin01",
                    adminKey = "admin01",
                    from = "2026-05-01",
                    to = "2026-05-06",
                ),
            ),
        )
        assertEquals(
            "functions/v1/get-cashier-report",
            client.reportEndpointPath(
                BackendReportRequest(
                    actorKey = "cajero01",
                    adminKey = "admin01",
                    cashierKey = "cajero01",
                    from = "2026-05-01",
                    to = "2026-05-06",
                ),
            ),
        )
        assertEquals(
            "functions/v1/get-supervisor-report",
            client.reportEndpointPath(
                BackendReportRequest(
                    actorKey = "super01",
                    adminKey = "admin01",
                    supervisorKey = "super01",
                    from = "2026-05-01",
                    to = "2026-05-06",
                ),
            ),
        )
    }

    @Test
    fun `statement timeout is presented as safe sales message`() {
        val message = presentSupabaseTicketBackendMessage("canceling statement due to statement timeout")

        assertEquals(
            "El servidor tardo validando. Si intentas de nuevo, se reutiliza la misma venta para no duplicarla.",
            message,
        )
    }

    @Test
    fun `statement timeout is not reported as handled crash`() {
        val error = SupabaseTicketBackendException(
            userMessage = presentSupabaseTicketBackendMessage("statement due to statement timeout"),
            technicalMessage = "statement due to statement timeout",
        )

        assertFalse(shouldReportSupabaseTicketBackendFailure(error))
    }

    @Test
    fun `ticket backend validation rejection is shown without recording crash`() {
        val error = SupabaseEdgeException(
            userMessage = "Jugada inválida en línea 1",
            technicalMessage = "Jugada inválida en línea 1",
        )

        assertEquals("Jugada inválida en línea 1", ticketBackendUserMessage(error))
        assertFalse(shouldReportSupabaseTicketBackendFailure(error))
    }
}
