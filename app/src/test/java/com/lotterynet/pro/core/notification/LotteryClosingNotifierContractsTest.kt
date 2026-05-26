package com.lotterynet.pro.core.notification

import com.lotterynet.pro.core.model.CloseState
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryCloseDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryClosingNotifierContractsTest {

    @Test
    fun `closing alerts group open lotteries with ten minutes or less`() {
        val alerts = buildLotteryClosingAlerts(
            lotteries = listOf(
                lottery("a", "La Primera"),
                lottery("b", "Florida Dia"),
                lottery("c", "Cerrada"),
                lottery("d", "Lejana"),
            ),
            decisionsByLotteryId = mapOf(
                "a" to decision("10 min restantes"),
                "b" to decision("4 min restantes"),
                "c" to decision("Cerrada", isClosed = true),
                "d" to decision("18 min restantes"),
            ),
        )

        assertEquals(listOf("b", "a"), alerts.map { it.lotteryId })
        assertEquals("Florida Dia", alerts.first().lotteryName)
        assertEquals(4, alerts.first().minutesRemaining)
    }

    @Test
    fun `closing notification text is compact when multiple lotteries close`() {
        val alerts = listOf(
            LotteryClosingAlert("b", "Florida Dia", 4),
            LotteryClosingAlert("a", "La Primera", 10),
        )

        assertEquals("2 loterias cerca de cerrar", lotteryClosingTitle(alerts))
        assertEquals("Florida Dia 4 min; La Primera 10 min", lotteryClosingMessage(alerts))
        assertEquals("b", topClosingLotteryId(alerts))
    }

    @Test
    fun `closing dedupe key changes only by day and lottery group`() {
        val alerts = listOf(
            LotteryClosingAlert("b", "Florida Dia", 4),
            LotteryClosingAlert("a", "La Primera", 10),
        )

        assertEquals("2026-05-06:a,b", lotteryClosingNotificationKey("2026-05-06", alerts))
        assertTrue(lotteryClosingNotificationKey("2026-05-06", emptyList()).isBlank())
    }

    private fun lottery(id: String, name: String) = LotteryCatalogItem(
        id = id,
        name = name,
        type = "dominicana",
        baseDrawTime = "14:00",
        baseCloseTime = "13:55",
        colorHex = "#111111",
    )

    private fun decision(reason: String, isClosed: Boolean = false) = LotteryCloseDecision(
        isClosed = isClosed,
        reason = reason,
        state = if (isClosed) CloseState.CLOSED else CloseState.DANGER,
    )
}
