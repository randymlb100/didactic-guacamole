package com.lotterynet.pro.core.calendar

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.ClockSource
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.TrustedClockSnapshot
import com.lotterynet.pro.core.repository.TrustedClockRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LotteryAvailabilityResolverContractsTest {

    private val catalog = StaticLotteryCatalogRepository()
    private val resolver = LotteryAvailabilityResolver(
        trustedClockRepository = FixedTrustedClockRepository(),
        holidayCalendarRepository = StaticHolidayCalendarRepository(
            dominicanLotteryIds = catalog.getCalendarRule().dominicanLotteryIds,
            americanLotteryIds = catalog.getCalendarRule().americanLotteryIds,
        ),
        calendarRule = catalog.getCalendarRule(),
    )

    @Test
    fun `dominican lotteries remain open on observed labor day when draws exist`() {
        val nacional = catalog.getLotteryById("13") ?: error("Missing Nacional")

        assertFalse(
            resolver.isRealNoDrawToday(
                lottery = nacional,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-04-27T16:00:00Z"),
            ),
        )
    }

    @Test
    fun `dominican lotteries stay disabled on good friday`() {
        val nacional = catalog.getLotteryById("13") ?: error("Missing Nacional")

        assertTrue(
            resolver.isRealNoDrawToday(
                lottery = nacional,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-04-03T16:00:00Z"),
            ),
        )
    }

    @Test
    fun `pick lotteries without sunday draw are calendar closed on sunday`() {
        val texasPick4Night = catalog.getLotteryById("US-P4-TX-DAILY-4-NIGHT") ?: error("Missing Texas Daily 4 Night")
        val arkansasPick3Midday = catalog.getLotteryById("US-P3-AR-CASH-3-MIDDAY") ?: error("Missing Arkansas Cash 3 Midday")
        val sundayUtcMs = utcMillis("2026-05-10T16:00:00Z")

        val closedIds = resolver.getRealNoDrawLotteryIds(
            lotteries = listOf(texasPick4Night, arkansasPick3Midday),
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = sundayUtcMs,
        )

        assertTrue(texasPick4Night.id in closedIds)
        assertTrue(arkansasPick3Midday.id in closedIds)
        assertTrue(
            resolver.isRealNoDrawToday(
                lottery = texasPick4Night,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = sundayUtcMs,
            ),
        )
    }

    @Test
    fun `pick lotteries with sunday draw remain available on sunday`() {
        val newYorkNumbersMidday = catalog.getLotteryById("US-P3-NY-NUMBERS-MIDDAY") ?: error("Missing New York Numbers Midday")
        val arkansasCash3Evening = catalog.getLotteryById("US-P3-AR-CASH-3-EVENING") ?: error("Missing Arkansas Cash 3 Evening")
        val southCarolinaPick4Evening = catalog.getLotteryById("US-P4-SC-PICK-4-EVENING") ?: error("Missing South Carolina Pick 4 Evening")
        val sundayUtcMs = utcMillis("2026-05-10T16:00:00Z")

        assertFalse(
            resolver.isRealNoDrawToday(
                lottery = newYorkNumbersMidday,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = sundayUtcMs,
            ),
        )
        assertFalse(
            resolver.isRealNoDrawToday(
                lottery = arkansasCash3Evening,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = sundayUtcMs,
            ),
        )
        assertFalse(
            resolver.isRealNoDrawToday(
                lottery = southCarolinaPick4Evening,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = sundayUtcMs,
            ),
        )
    }

    @Test
    fun `classic lotteries are not affected by pick sunday closures`() {
        val primeraDia = catalog.getLotteryById("1") ?: error("Missing La Primera Dia")

        assertFalse(
            resolver.isRealNoDrawToday(
                lottery = primeraDia,
                operationTerritory = LotteryTerritory.RD,
                nowUtcMs = utcMillis("2026-05-10T16:00:00Z"),
            ),
        )
    }

    @Test
    fun `memorial day does not calendar close usa daily lotteries`() {
        val memorialDayUtcMs = utcMillis("2026-05-25T12:00:00Z")
        val dailyUsLotteryIds = listOf(
            "8", // New York Tarde
            "17", // Florida Noche
            "44", // Georgia Dia
            "19", // NJ Pick 3 Dia
            "20", // NJ Pick 3 Noche
            "US-P3-NY-NUMBERS-MIDDAY",
            "US-P4-NY-WIN-4-MIDDAY",
            "US-P3-FL-PICK-3-MIDDAY",
            "US-P4-FL-PICK-4-MIDDAY",
            "US-P3-GA-PICK-3-MIDDAY",
            "US-P4-GA-CASH-4-MIDDAY",
        )
        val lotteries = dailyUsLotteryIds.map { id -> catalog.getLotteryById(id) ?: error("Missing lottery $id") }

        val closedIds = resolver.getRealNoDrawLotteryIds(
            lotteries = lotteries,
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = memorialDayUtcMs,
        )

        assertTrue(closedIds.isEmpty())
    }

    @Test
    fun `christmas is the only usa holiday that calendar closes usa lottery sales`() {
        val christmasUtcMs = utcMillis("2026-12-25T12:00:00Z")
        val newJerseyPick3Day = catalog.getLotteryById("19") ?: error("Missing NJ Pick 3 Dia")
        val floridaDay = catalog.getLotteryById("6") ?: error("Missing Florida Dia")
        val newYorkTarde = catalog.getLotteryById("8") ?: error("Missing New York Tarde")

        val closedIds = resolver.getRealNoDrawLotteryIds(
            lotteries = listOf(newJerseyPick3Day, floridaDay, newYorkTarde),
            operationTerritory = LotteryTerritory.RD,
            nowUtcMs = christmasUtcMs,
        )

        assertTrue(newJerseyPick3Day.id in closedIds)
        assertTrue(floridaDay.id in closedIds)
        assertTrue(newYorkTarde.id in closedIds)
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
