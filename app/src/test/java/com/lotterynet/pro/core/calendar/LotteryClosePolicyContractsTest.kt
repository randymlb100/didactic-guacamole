package com.lotterynet.pro.core.calendar

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.ClockSource
import com.lotterynet.pro.core.model.CloseState
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.TrustedClockSnapshot
import com.lotterynet.pro.core.repository.TrustedClockRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LotteryClosePolicyContractsTest {

    private val catalog = StaticLotteryCatalogRepository()
    private val policy = LotteryClosePolicy(
        trustedClockRepository = FixedTrustedClockRepository(),
        holidayCalendarRepository = StaticHolidayCalendarRepository(
            dominicanLotteryIds = catalog.getCalendarRule().dominicanLotteryIds,
            americanLotteryIds = catalog.getCalendarRule().americanLotteryIds,
        ),
    )

    @Test
    fun `haiti bolet 11 30 closes five minutes before draw`() {
        val lottery = catalog.getLotteryById("27") ?: error("Missing Haiti Bolet 11:30 AM")

        assertFalse(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-04-29T15:24:00Z"),
            ).isClosed,
        )
        assertTrue(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-04-29T15:25:00Z"),
            ).state == CloseState.CLOSED,
        )
    }

    @Test
    fun `haiti bolet 6 30 closes five minutes before draw`() {
        val lottery = catalog.getLotteryById("28") ?: error("Missing Haiti Bolet 6:30 PM")

        assertFalse(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-04-29T22:24:00Z"),
            ).isClosed,
        )
        assertTrue(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-04-29T22:25:00Z"),
            ).state == CloseState.CLOSED,
        )
    }

    @Test
    fun `haiti bolet has no draw on haiti flag and university day`() {
        val holidayRepository = StaticHolidayCalendarRepository(
            dominicanLotteryIds = catalog.getCalendarRule().dominicanLotteryIds,
            americanLotteryIds = catalog.getCalendarRule().americanLotteryIds,
        )

        val noDrawIds = holidayRepository.getDynamicNoDrawLotteryIds("2026-05-18")

        assertTrue("27" in noDrawIds)
        assertTrue("28" in noDrawIds)
        assertTrue("40" in noDrawIds)
        assertTrue("41" in noDrawIds)
        assertTrue("42" in noDrawIds)
        assertTrue("43" in noDrawIds)
        assertEquals("Feriado Haiti", holidayRepository.getHolidayReason("2026-05-18", "27", LotteryTerritory.RD))
    }

    @Test
    fun `new jersey midday closes five minutes before draw during us standard time`() {
        val lottery = catalog.getLotteryById("25") ?: error("Missing New Jersey AM")

        assertFalse(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-01-15T17:53:00Z"),
            ).isClosed,
        )
        assertTrue(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-01-15T17:54:00Z"),
            ).isClosed,
        )
    }

    @Test
    fun `new jersey midday closes five minutes before eastern draw time for usa operation during standard time`() {
        val lottery = catalog.getLotteryById("25") ?: error("Missing New Jersey AM")

        assertFalse(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.USA,
                nowUtcMs = utcMillis("2026-01-15T17:53:00Z"),
            ).isClosed,
        )
        assertTrue(
            policy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = LotteryTerritory.USA,
                nowUtcMs = utcMillis("2026-01-15T17:54:00Z"),
            ).isClosed,
        )
    }

    @Test
    fun `admin can sell classic lottery during ten minute grace`() {
        val lottery = catalog.getLotteryById("13") ?: error("Missing Lotería Nacional")

        val cashierDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-04-30T01:05:00Z"),
        )
        val adminDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-04-30T01:05:00Z"),
        )

        assertTrue(cashierDecision.isClosed)
        assertFalse(adminDecision.isClosed)
        assertEquals(CloseState.DANGER, adminDecision.state)
    }

    @Test
    fun `gana mas closes at configured weekday close time`() {
        val lottery = catalog.getLotteryById("9") ?: error("Missing Gana Más")

        val beforeClose = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-25T18:34:00Z"),
        )
        val atClose = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-25T18:35:00Z"),
        )

        assertFalse(beforeClose.isClosed)
        assertEquals("14:35", beforeClose.closeTime)
        assertTrue(atClose.isClosed)
        assertEquals("14:35", atClose.closeTime)
    }

    @Test
    fun `gana mas admin grace lasts ten minutes after configured close time`() {
        val lottery = catalog.getLotteryById("9") ?: error("Missing Gana Más")

        val cashierDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-25T18:36:00Z"),
        )
        val adminDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-05-25T18:36:00Z"),
        )
        val afterGraceDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-05-25T18:46:00Z"),
        )

        assertTrue(cashierDecision.isClosed)
        assertFalse(adminDecision.isClosed)
        assertTrue(afterGraceDecision.isClosed)
    }

    @Test
    fun `gana mas uses configured close time on sunday too`() {
        val lottery = catalog.getLotteryById("9") ?: error("Missing Gana Más")

        val decision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-24T18:34:00Z"),
        )
        val atClose = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-24T18:35:00Z"),
        )

        assertEquals("2:40 PM", decision.drawTime)
        assertEquals("14:35", decision.closeTime)
        assertFalse(decision.isClosed)
        assertTrue(atClose.isClosed)
    }

    @Test
    fun `loteka cashier closes at seven fifty five while admin keeps ten minute grace`() {
        val lottery = catalog.getLotteryById("12") ?: error("Missing Quiniela Loteka")

        val cashierDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-25T23:56:00Z"),
        )
        val adminDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-05-25T23:56:00Z"),
        )
        val afterGraceDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-05-26T00:06:00Z"),
        )

        assertTrue(cashierDecision.isClosed)
        assertEquals("19:55", cashierDecision.closeTime)
        assertFalse(adminDecision.isClosed)
        assertEquals(CloseState.DANGER, adminDecision.state)
        assertTrue(afterGraceDecision.isClosed)
    }

    @Test
    fun `admin can sell until ten minutes after draw time`() {
        val lottery = catalog.getLotteryById("12") ?: error("Missing Quiniela Loteka")

        val adminDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-04-29T23:59:00Z"),
        )
        val afterGraceDecision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-04-30T00:06:00Z"),
        )

        assertFalse(adminDecision.isClosed)
        assertEquals(CloseState.DANGER, adminDecision.state)
        assertTrue(afterGraceDecision.isClosed)
    }

    @Test
    fun `admin grace does not apply to pick lotteries`() {
        val lottery = catalog.getLotteryById("19") ?: error("Missing NJ Pick 3 Día")

        val decision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.USA,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-04-29T17:00:00Z"),
        )

        assertTrue(decision.isClosed)
    }

    @Test
    fun `admin grace does not apply to pick 4 lotteries`() {
        val lottery = catalog.getLotteryById("21") ?: error("Missing NJ Pick 4 Día")

        val decision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.USA,
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-04-29T17:00:00Z"),
        )

        assertTrue(decision.isClosed)
    }

    @Test
    fun `dynamic texas pick uses its local timezone before closing in rd`() {
        val lottery = catalog.getLotteryById("US-P4-TX-DAILY-4-NIGHT") ?: error("Missing Texas Daily 4 Night")

        val beforeClose = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-10T03:06:00Z"),
        )
        val atClose = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-10T03:07:00Z"),
        )

        assertFalse(beforeClose.isClosed)
        assertEquals("11:12 PM", beforeClose.drawTime)
        assertTrue(atClose.isClosed)
    }

    @Test
    fun `pick lottery without sunday draw closes as no draw today`() {
        val lottery = catalog.getLotteryById("US-P4-TX-DAILY-4-NIGHT") ?: error("Missing Texas Daily 4 Night")

        val decision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-10T16:00:00Z"),
            calendarClosedLotteryIds = setOf(lottery.id),
        )

        assertTrue(decision.isClosed)
        assertEquals("Sin sorteo hoy", decision.reason)
    }

    @Test
    fun `published result closes after admin grace expires`() {
        val lottery = catalog.getLotteryById("13") ?: error("Missing Lotería Nacional")

        val decision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            publishedResultLotteryIds = setOf(lottery.id),
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-04-30T01:11:00Z"),
        )

        assertTrue(decision.isClosed)
        assertEquals("Resultado publicado", decision.reason)
    }

    @Test
    fun `admin can keep classic lottery selectable during grace even when result exists`() {
        val lottery = catalog.getLotteryById("13") ?: error("Missing Lotería Nacional")

        val decision = policy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = LotteryTerritory.RD,
            publishedResultLotteryIds = setOf(lottery.id),
            allowAdminAfterCloseGrace = true,
            nowUtcMs = utcMillis("2026-04-30T01:05:00Z"),
        )

        assertFalse(decision.isClosed)
        assertEquals("Resultado publicado", decision.reason)
        assertEquals(CloseState.DANGER, decision.state)
    }

    @Test
    fun `nj pick night remains open after only day draw result is published`() {
        val pick3Night = catalog.getLotteryById("20") ?: error("Missing NJ Pick 3 Noche")
        val pick4Night = catalog.getLotteryById("22") ?: error("Missing NJ Pick 4 Noche")
        val publishedDayDrawIds = setOf("19", "21")

        val pick3Decision = policy.resolveCloseDecision(
            lottery = pick3Night,
            operationTerritory = LotteryTerritory.RD,
            publishedResultLotteryIds = publishedDayDrawIds,
            nowUtcMs = utcMillis("2026-05-23T00:17:00Z"),
        )
        val pick4Decision = policy.resolveCloseDecision(
            lottery = pick4Night,
            operationTerritory = LotteryTerritory.RD,
            publishedResultLotteryIds = publishedDayDrawIds,
            nowUtcMs = utcMillis("2026-05-23T00:17:00Z"),
        )

        assertFalse(pick3Decision.isClosed)
        assertFalse(pick4Decision.isClosed)
    }

    @Test
    fun `nj pick day remains open on sunday morning before midday draw`() {
        val pick3Day = catalog.getLotteryById("19") ?: error("Missing NJ Pick 3 Dia")
        val pick4Day = catalog.getLotteryById("21") ?: error("Missing NJ Pick 4 Dia")

        val pick3Decision = policy.resolveCloseDecision(
            lottery = pick3Day,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-24T10:00:00Z"),
        )
        val pick4Decision = policy.resolveCloseDecision(
            lottery = pick4Day,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = utcMillis("2026-05-24T10:00:00Z"),
        )

        assertFalse(pick3Decision.isClosed)
        assertFalse(pick4Decision.isClosed)
        assertEquals("12:59 PM", pick3Decision.drawTime)
        assertEquals("12:59 PM", pick4Decision.drawTime)
    }

    private fun utcMillis(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value)?.time ?: error("Invalid date $value")
    }

    private class FixedTrustedClockRepository : TrustedClockRepository {
        override fun getTrustedUtcMs(): Long = 0L
        override fun getSnapshot(territory: LotteryTerritory): TrustedClockSnapshot {
            return TrustedClockSnapshot(
                trustedUtcMs = getTrustedUtcMs(),
                operationTerritory = territory,
            )
        }

        override fun syncFromUtc(utcMs: Long, source: ClockSource): Boolean = true

        override fun getOperationTimeZone(territory: LotteryTerritory): String {
            return when (territory) {
                LotteryTerritory.RD -> "America/Santo_Domingo"
                LotteryTerritory.USA -> "America/New_York"
            }
        }
    }
}
