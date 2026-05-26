package com.lotterynet.pro.core.calendar

import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.repository.HolidayCalendarRepository
import java.util.Calendar
import java.util.TimeZone

class StaticHolidayCalendarRepository(
    private val dominicanLotteryIds: Set<String>,
    private val americanLotteryIds: Set<String>,
) : HolidayCalendarRepository {
    private val haitiBoletLotteryIds = setOf("27", "28", "40", "41", "42", "43")

    override fun getObservedHolidayMap(year: Int, territory: LotteryTerritory): Map<String, String> {
        return when (territory) {
            LotteryTerritory.RD -> getDominicanObservedHolidayDateMap(year)
            LotteryTerritory.USA -> getUsObservedHolidayDateMap(year)
        }
    }

    override fun getDynamicNoDrawLotteryIds(dateKey: String): Set<String> {
        val year = dateKey.substringBefore('-').toIntOrNull() ?: return emptySet()
        val easter = getEasterSundayUtc(year)
        return buildSet {
            when (dateKey) {
                shiftUtcDateKey(easter, -2),
                shiftUtcDateKey(easter, -3) -> addAll(dominicanLotteryIds)
            }
            if (isHaitiFlagAndUniversityDay(dateKey)) {
                addAll(haitiBoletLotteryIds)
            }
        }
    }

    override fun getHolidayReason(dateKey: String, lotteryId: String, territory: LotteryTerritory): String? {
        val year = dateKey.substringBefore('-').toIntOrNull() ?: return null
        if (lotteryId in haitiBoletLotteryIds && isHaitiFlagAndUniversityDay(dateKey)) {
            return "Feriado Haiti"
        }
        return when (territory) {
            LotteryTerritory.RD -> {
                if (lotteryId in getDynamicNoDrawLotteryIds(dateKey)) {
                    "Feriado RD"
                } else null
            }
            LotteryTerritory.USA -> {
                if (lotteryId in americanLotteryIds && getUsObservedLotteryNoDrawDateMap(year).containsKey(dateKey)) {
                    "Feriado USA"
                } else null
            }
        }
    }

    private fun isHaitiFlagAndUniversityDay(dateKey: String): Boolean {
        return dateKey.endsWith("-05-18")
    }

    private fun getUsObservedHolidayDateMap(year: Int): Map<String, String> {
        return buildMap {
            put(getObservedFixedHolidayUtcDateKey(year, 0, 1), "new_year")
            put(getNthWeekdayUtcDateKey(year, 0, Calendar.MONDAY, 3), "mlk_day")
            put(getNthWeekdayUtcDateKey(year, 1, Calendar.MONDAY, 3), "presidents_day")
            put(getLastWeekdayUtcDateKey(year, 4, Calendar.MONDAY), "memorial_day")
            put(getObservedFixedHolidayUtcDateKey(year, 5, 19), "juneteenth")
            put(getObservedFixedHolidayUtcDateKey(year, 6, 4), "independence_day")
            put(getNthWeekdayUtcDateKey(year, 8, Calendar.MONDAY, 1), "labor_day")
            put(getNthWeekdayUtcDateKey(year, 9, Calendar.MONDAY, 2), "columbus_day")
            put(getObservedFixedHolidayUtcDateKey(year, 10, 11), "veterans_day")
            put(getNthWeekdayUtcDateKey(year, 10, Calendar.THURSDAY, 4), "thanksgiving")
            put(getObservedFixedHolidayUtcDateKey(year, 11, 25), "christmas")
        }
    }

    private fun getUsObservedLotteryNoDrawDateMap(year: Int): Map<String, String> {
        return mapOf(getObservedFixedHolidayUtcDateKey(year, 11, 25) to "christmas")
    }

    private fun getDominicanObservedHolidayDateMap(year: Int): Map<String, String> {
        val out = linkedMapOf<String, String>()
        out[rdObserved(year, 0, 1)] = "ano_nuevo"
        out[moveToNearestMonday(year, 0, 6)] = "santos_reyes"
        out[rdObserved(year, 0, 21)] = "altagracia"
        out[moveToNearestMonday(year, 0, 26)] = "duarte"
        out[rdObserved(year, 1, 27)] = "independencia"
        out[moveToNearestMonday(year, 4, 1)] = "dia_del_trabajo"
        out[shiftUtcDateKey(getEasterSundayUtc(year), 60)] = "corpus_christi"
        out[rdObserved(year, 7, 16)] = "restauracion"
        out[rdObserved(year, 8, 24)] = "las_mercedes"
        out[moveToNearestMonday(year, 10, 6)] = "constitucion"
        out[rdObserved(year, 11, 25)] = "navidad"
        return out
    }

    private fun getEasterSundayUtc(year: Int): Calendar {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return utcCalendar(year, month - 1, day)
    }

    private fun getObservedFixedHolidayUtcDateKey(year: Int, month: Int, day: Int): String {
        val calendar = utcCalendar(year, month, day)
        when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> calendar.add(Calendar.DAY_OF_MONTH, -1)
            Calendar.SUNDAY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return toUtcDateKey(calendar)
    }

    private fun rdObserved(year: Int, month: Int, day: Int): String {
        val calendar = utcCalendar(year, month, day)
        when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> calendar.add(Calendar.DAY_OF_MONTH, -1)
            Calendar.SUNDAY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return toUtcDateKey(calendar)
    }

    private fun moveToNearestMonday(year: Int, month: Int, day: Int): String {
        val calendar = utcCalendar(year, month, day)
        when (val weekday = calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY -> {
                calendar.add(Calendar.DAY_OF_MONTH, -(weekday - Calendar.MONDAY))
            }
            Calendar.SATURDAY, Calendar.SUNDAY -> {
                calendar.add(Calendar.DAY_OF_MONTH, 8 - weekday)
            }
        }
        return toUtcDateKey(calendar)
    }

    private fun getNthWeekdayUtcDateKey(year: Int, month: Int, weekday: Int, nth: Int): String {
        val calendar = utcCalendar(year, month, 1)
        while (calendar.get(Calendar.DAY_OF_WEEK) != weekday) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        calendar.add(Calendar.DAY_OF_MONTH, (nth - 1) * 7)
        return toUtcDateKey(calendar)
    }

    private fun getLastWeekdayUtcDateKey(year: Int, month: Int, weekday: Int): String {
        val calendar = utcCalendar(year, month + 1, 0)
        while (calendar.get(Calendar.DAY_OF_WEEK) != weekday) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        return toUtcDateKey(calendar)
    }

    private fun shiftUtcDateKey(calendar: Calendar, deltaDays: Int): String {
        val copy = calendar.clone() as Calendar
        copy.add(Calendar.DAY_OF_MONTH, deltaDays)
        return toUtcDateKey(copy)
    }

    private fun utcCalendar(year: Int, month: Int, day: Int): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }
    }

    private fun toUtcDateKey(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "$year-$month-$day"
    }
}
