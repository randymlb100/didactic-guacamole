package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.ClockSource
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.TrustedClockSnapshot

interface TrustedClockRepository {
    fun getTrustedUtcMs(): Long
    fun getSnapshot(territory: LotteryTerritory = LotteryTerritory.RD): TrustedClockSnapshot
    fun syncFromUtc(utcMs: Long, source: ClockSource): Boolean
    fun getOperationTimeZone(territory: LotteryTerritory): String
}
