package com.lotterynet.pro.core.calendar

import com.lotterynet.pro.core.model.LotteryTerritory
import java.util.TimeZone

object LotteryTimeZones {
    private const val RD_ZONE_ID = "America/Santo_Domingo"
    private const val USA_EASTERN_ZONE_ID = "America/New_York"

    fun zoneId(territory: LotteryTerritory): String {
        return when (territory) {
            LotteryTerritory.RD -> RD_ZONE_ID
            LotteryTerritory.USA -> USA_EASTERN_ZONE_ID
        }
    }

    fun offsetMinutes(territory: LotteryTerritory, utcMs: Long): Int {
        return TimeZone.getTimeZone(zoneId(territory)).getOffset(utcMs) / 60000
    }

    fun isDaylightSaving(territory: LotteryTerritory, utcMs: Long): Boolean {
        return TimeZone.getTimeZone(zoneId(territory)).inDaylightTime(java.util.Date(utcMs))
    }
}
