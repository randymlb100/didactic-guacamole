package com.lotterynet.pro.core.calendar

import com.lotterynet.pro.core.model.LotteryTerritory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LotteryTimeZonesTest {

    @Test
    fun `dominican time stays fixed while usa eastern changes for daylight saving`() {
        val winter = utcMillis("2026-01-15T17:00:00Z")
        val summer = utcMillis("2026-07-15T17:00:00Z")

        assertEquals(-240, LotteryTimeZones.offsetMinutes(LotteryTerritory.RD, winter))
        assertEquals(-240, LotteryTimeZones.offsetMinutes(LotteryTerritory.RD, summer))
        assertEquals(-300, LotteryTimeZones.offsetMinutes(LotteryTerritory.USA, winter))
        assertEquals(-240, LotteryTimeZones.offsetMinutes(LotteryTerritory.USA, summer))
    }

    @Test
    fun `territory zones use precise lottery operation ids`() {
        assertEquals("America/Santo_Domingo", LotteryTimeZones.zoneId(LotteryTerritory.RD))
        assertEquals("America/New_York", LotteryTimeZones.zoneId(LotteryTerritory.USA))
    }

    private fun utcMillis(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value)?.time ?: error("Invalid date $value")
    }
}
