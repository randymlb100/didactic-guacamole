package com.lotterynet.pro.core.calendar

import com.lotterynet.pro.core.model.CloseState
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryCloseDecision
import com.lotterynet.pro.core.model.LotterySchedule
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.repository.HolidayCalendarRepository
import com.lotterynet.pro.core.repository.TrustedClockRepository
import java.util.Calendar
import java.util.TimeZone

class LotteryClosePolicy(
    private val trustedClockRepository: TrustedClockRepository,
    private val holidayCalendarRepository: HolidayCalendarRepository,
) {
    fun resolveCloseDecision(
        lottery: LotteryCatalogItem,
        operationTerritory: LotteryTerritory,
        manualClosedLotteryIds: Set<String> = emptySet(),
        calendarClosedLotteryIds: Set<String> = emptySet(),
        publishedResultLotteryIds: Set<String> = emptySet(),
        allowAdminAfterCloseGrace: Boolean = false,
        nowUtcMs: Long = trustedClockRepository.getTrustedUtcMs(),
    ): LotteryCloseDecision {
        val sourceTerritory = lottery.territory
        val sourceCalendar = calendarForLottery(lottery, nowUtcMs)
        val operationCalendar = calendarForTerritory(operationTerritory, nowUtcMs)
        val dateKey = toDateKey(operationCalendar)

        if (lottery.id in calendarClosedLotteryIds) {
            return closed(lottery, "Sin sorteo hoy", nowUtcMs, operationTerritory)
        }
        if (lottery.id in manualClosedLotteryIds) {
            return closed(lottery, "Cerrada por la banca", nowUtcMs, operationTerritory)
        }

        val holidayReason = holidayCalendarRepository.getHolidayReason(dateKey, lottery.id, sourceTerritory)
        if (lottery.id in holidayCalendarRepository.getDynamicNoDrawLotteryIds(dateKey) || holidayReason != null) {
            return closed(lottery, holidayReason ?: "Sin sorteo hoy", nowUtcMs, operationTerritory)
        }

        val schedule = resolveSchedule(lottery, sourceCalendar, nowUtcMs, operationTerritory)
        val drawMinutes = parseClockMinutes(schedule.drawTime)
        val effectiveSchedule = schedule.copy(
            closeTime = if (lottery.usesExplicitCloseTime) {
                formatClock24(parseClockMinutes(schedule.closeTime))
            } else {
                formatClock24(drawMinutes - PRE_DRAW_CLOSE_MINUTES)
            },
        )
        val closeMinutes = parseClockMinutes(effectiveSchedule.closeTime)
        val nowMinutes = operationCalendar.get(Calendar.HOUR_OF_DAY) * 60 + operationCalendar.get(Calendar.MINUTE)
        val diff = closeMinutes - nowMinutes
        val adminGraceRemainingMinutes = closeMinutes + ADMIN_AFTER_CLOSE_GRACE_MINUTES - nowMinutes
        val canUseAdminGrace = canUseAdminAfterCloseGrace(
            lottery = lottery,
            allowAdminAfterCloseGrace = allowAdminAfterCloseGrace,
            closeDiffMinutes = diff,
            adminGraceRemainingMinutes = adminGraceRemainingMinutes,
        )

        if (lottery.id in publishedResultLotteryIds) {
            return if (canUseAdminGrace) {
                LotteryCloseDecision(
                    isClosed = false,
                    reason = "Resultado publicado",
                    drawTime = effectiveSchedule.drawTime,
                    closeTime = effectiveSchedule.closeTime,
                    state = CloseState.DANGER,
                )
            } else {
                closed(lottery, "Resultado publicado", nowUtcMs, operationTerritory, effectiveSchedule)
            }
        }

        return when {
            canUseAdminGrace -> LotteryCloseDecision(
                isClosed = false,
                reason = "Admin extra $adminGraceRemainingMinutes min",
                drawTime = effectiveSchedule.drawTime,
                closeTime = effectiveSchedule.closeTime,
                state = CloseState.DANGER,
            )
            diff <= 0 -> closed(lottery, "Esperando resultado", nowUtcMs, operationTerritory, effectiveSchedule)
            diff <= 20 -> LotteryCloseDecision(
                isClosed = false,
                reason = "$diff min restantes",
                drawTime = effectiveSchedule.drawTime,
                closeTime = effectiveSchedule.closeTime,
                state = CloseState.DANGER,
            )
            diff <= 60 -> LotteryCloseDecision(
                isClosed = false,
                reason = "$diff min restantes",
                drawTime = effectiveSchedule.drawTime,
                closeTime = effectiveSchedule.closeTime,
                state = CloseState.WARNING,
            )
            else -> LotteryCloseDecision(
                isClosed = false,
                reason = "$diff min restantes",
                drawTime = effectiveSchedule.drawTime,
                closeTime = effectiveSchedule.closeTime,
                state = CloseState.OPEN,
            )
        }
    }

    private fun canUseAdminAfterCloseGrace(
        lottery: LotteryCatalogItem,
        allowAdminAfterCloseGrace: Boolean,
        closeDiffMinutes: Int,
        adminGraceRemainingMinutes: Int,
    ): Boolean {
        return allowAdminAfterCloseGrace &&
            closeDiffMinutes <= 0 &&
            adminGraceRemainingMinutes >= 0 &&
            lottery.playCapabilities.let {
                !it.supportsStraight &&
                    !it.supportsBox &&
                    (it.supportsQuiniela || it.supportsPale || it.supportsTripleta || it.supportsSuperPale)
            }
    }

    private fun closed(
        lottery: LotteryCatalogItem,
        reason: String,
        nowUtcMs: Long,
        operationTerritory: LotteryTerritory,
        schedule: LotterySchedule? = null,
    ): LotteryCloseDecision {
        val resolved = schedule ?: resolveSchedule(
            lottery = lottery,
            sourceCalendar = calendarForLottery(lottery, nowUtcMs),
            nowUtcMs = nowUtcMs,
            operationTerritory = operationTerritory,
        )
        return LotteryCloseDecision(
            isClosed = true,
            reason = reason,
            drawTime = resolved.drawTime,
            closeTime = resolved.closeTime,
            state = CloseState.CLOSED,
        )
    }

    private fun resolveSchedule(
        lottery: LotteryCatalogItem,
        sourceCalendar: Calendar,
        nowUtcMs: Long,
        operationTerritory: LotteryTerritory,
    ): LotterySchedule {
        val sourceWeekday = sourceCalendar.get(Calendar.DAY_OF_WEEK)
        var schedule = LotterySchedule(
            drawTime = lottery.baseDrawTime,
            closeTime = lottery.baseCloseTime,
        )
        if (sourceWeekday == Calendar.SUNDAY && lottery.sundayOverride != null) {
            schedule = lottery.sundayOverride
        }
        val usesUsStandardDisplayOverride = lottery.territory == LotteryTerritory.USA &&
            !isEasternDaylightSaving(nowUtcMs) &&
            lottery.standardTimeOverride != null &&
            operationTerritory != lottery.territory
        if (usesUsStandardDisplayOverride) {
            return lottery.standardTimeOverride
        }
        return if (lottery.territory == operationTerritory && lottery.timeZoneId == null) {
            schedule
        } else {
            shiftSchedule(schedule, lottery, operationTerritory, nowUtcMs)
        }
    }

    private fun shiftSchedule(
        schedule: LotterySchedule,
        lottery: LotteryCatalogItem,
        toTerritory: LotteryTerritory,
        nowUtcMs: Long,
    ): LotterySchedule {
        return LotterySchedule(
            drawTime = shiftClock(schedule.drawTime, lottery, toTerritory, nowUtcMs, forDisplay = true),
            closeTime = shiftClock(schedule.closeTime, lottery, toTerritory, nowUtcMs, forDisplay = false),
        )
    }

    private fun shiftClock(
        value: String,
        lottery: LotteryCatalogItem,
        toTerritory: LotteryTerritory,
        nowUtcMs: Long,
        forDisplay: Boolean,
    ): String {
        val minutes = parseClockMinutes(value)
        val delta = zoneOffsetMinutes(toTerritory, nowUtcMs) - zoneOffsetMinutes(lottery, nowUtcMs)
        val shifted = ((minutes + delta) % (24 * 60) + (24 * 60)) % (24 * 60)
        return if (forDisplay) formatClock12(shifted) else formatClock24(shifted)
    }

    private fun zoneOffsetMinutes(lottery: LotteryCatalogItem, nowUtcMs: Long): Int {
        return lottery.timeZoneId
            ?.let { zoneOffsetMinutesForTimeZone(it, nowUtcMs) }
            ?: zoneOffsetMinutes(lottery.territory, nowUtcMs)
    }

    private fun zoneOffsetMinutes(territory: LotteryTerritory, nowUtcMs: Long): Int {
        return LotteryTimeZones.offsetMinutes(territory, nowUtcMs)
    }

    private fun zoneOffsetMinutesForTimeZone(timeZoneId: String, nowUtcMs: Long): Int {
        return TimeZone.getTimeZone(timeZoneId).getOffset(nowUtcMs) / 60_000
    }

    private fun calendarForLottery(lottery: LotteryCatalogItem, nowUtcMs: Long): Calendar {
        val zone = lottery.timeZoneId ?: trustedClockRepository.getOperationTimeZone(lottery.territory)
        return Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            timeInMillis = nowUtcMs
        }
    }


    private fun calendarForTerritory(territory: LotteryTerritory, nowUtcMs: Long): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone(trustedClockRepository.getOperationTimeZone(territory))).apply {
            timeInMillis = nowUtcMs
        }
    }

    private fun isEasternDaylightSaving(nowUtcMs: Long): Boolean {
        return LotteryTimeZones.isDaylightSaving(LotteryTerritory.USA, nowUtcMs)
    }

    private fun parseClockMinutes(raw: String): Int {
        val text = raw.trim().uppercase()
        val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""").find(text) ?: return 23 * 60 + 59
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val meridiem = match.groupValues.getOrNull(3).orEmpty()
        if (meridiem == "AM" && hour == 12) hour = 0
        if (meridiem == "PM" && hour < 12) hour += 12
        return hour * 60 + minute
    }

    private fun formatClock24(minutes: Int): String {
        val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        val hour = (normalized / 60).toString().padStart(2, '0')
        val minute = (normalized % 60).toString().padStart(2, '0')
        return "$hour:$minute"
    }

    private fun formatClock12(minutes: Int): String {
        val rawHour = minutes / 60
        val minute = (minutes % 60).toString().padStart(2, '0')
        val suffix = if (rawHour >= 12) "PM" else "AM"
        val hour12 = when (val normalized = rawHour % 12) {
            0 -> 12
            else -> normalized
        }
        return "$hour12:$minute $suffix"
    }

    private fun toDateKey(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private companion object {
        const val PRE_DRAW_CLOSE_MINUTES = 5
        const val ADMIN_AFTER_CLOSE_GRACE_MINUTES = 10
    }
}
