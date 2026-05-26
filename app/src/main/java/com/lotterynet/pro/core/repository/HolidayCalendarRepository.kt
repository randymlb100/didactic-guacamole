package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.LotteryTerritory

interface HolidayCalendarRepository {
    fun getObservedHolidayMap(year: Int, territory: LotteryTerritory): Map<String, String>
    fun getDynamicNoDrawLotteryIds(dateKey: String): Set<String>
    fun getHolidayReason(dateKey: String, lotteryId: String, territory: LotteryTerritory): String?
}
