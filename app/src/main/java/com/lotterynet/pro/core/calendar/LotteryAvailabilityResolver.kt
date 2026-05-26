package com.lotterynet.pro.core.calendar

import com.lotterynet.pro.core.model.LotteryCalendarRule
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.repository.HolidayCalendarRepository
import com.lotterynet.pro.core.repository.TrustedClockRepository
import java.util.Calendar
import java.util.TimeZone

class LotteryAvailabilityResolver(
    private val trustedClockRepository: TrustedClockRepository,
    private val holidayCalendarRepository: HolidayCalendarRepository,
    private val calendarRule: LotteryCalendarRule,
) {
    fun getRealNoDrawLotteryIds(
        lotteries: List<LotteryCatalogItem>,
        operationTerritory: LotteryTerritory,
        nowUtcMs: Long = trustedClockRepository.getTrustedUtcMs(),
    ): Set<String> {
        return lotteries.filter { isRealNoDrawToday(it, operationTerritory, nowUtcMs) }.mapTo(linkedSetOf()) { it.id }
    }

    fun isRealNoDrawToday(
        lottery: LotteryCatalogItem,
        operationTerritory: LotteryTerritory,
        nowUtcMs: Long = trustedClockRepository.getTrustedUtcMs(),
    ): Boolean {
        return getRealNoDrawReason(lottery, operationTerritory, nowUtcMs) != null
    }

    fun getRealNoDrawReason(
        lottery: LotteryCatalogItem,
        operationTerritory: LotteryTerritory,
        nowUtcMs: Long = trustedClockRepository.getTrustedUtcMs(),
    ): String? {
        val calendar = calendarForTerritory(operationTerritory, nowUtcMs)
        val dateKey = toDateKey(calendar)
        val weekdayIndex = toLegacyWeekdayIndex(calendar)
        val lotteryId = lottery.id

        if (dateKey in calendarRule.noDrawAllDates) return "Sin sorteo hoy"
        if (lotteryId in calendarRule.noDrawDatesByLottery[dateKey].orEmpty()) return "Sin sorteo hoy"
        if (dateKey in calendarRule.holidayAllDisabledDates) return "Feriado"
        if (lotteryId in calendarRule.holidayDisabledDates[dateKey].orEmpty()) return "Feriado"
        if (lotteryId in calendarRule.dayDisabledByWeekday[weekdayIndex].orEmpty()) return "Sin sorteo hoy"

        if (lotteryId in holidayCalendarRepository.getDynamicNoDrawLotteryIds(dateKey)) {
            return holidayCalendarRepository.getHolidayReason(dateKey, lotteryId, lottery.territory) ?: "Sin sorteo hoy"
        }

        return holidayCalendarRepository.getHolidayReason(dateKey, lotteryId, lottery.territory)
    }

    private fun calendarForTerritory(territory: LotteryTerritory, nowUtcMs: Long): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone(trustedClockRepository.getOperationTimeZone(territory))).apply {
            timeInMillis = nowUtcMs
        }
    }

    private fun toDateKey(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private fun toLegacyWeekdayIndex(calendar: Calendar): Int {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 0
        }
    }
}
