package com.lotterynet.pro.ui.finance

import com.lotterynet.pro.core.finance.FinancePeriodPreset
import com.lotterynet.pro.core.finance.FinancePeriodReport
import com.lotterynet.pro.core.finance.FinanceScope
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

internal data class FinancePeriodUiOption(
    val preset: FinancePeriodPreset,
    val label: String,
    val tone: ActionTone,
    val order: Int,
)

internal data class SafeFinanceReportLaunch(
    val report: FinancePeriodReport,
    val preset: FinancePeriodPreset,
    val anchorDay: String,
    val recovered: Boolean,
)

internal data class FinanceMonthUiOption(
    val label: String,
    val anchorDayKey: String,
    val fromDayKey: String,
    val toDayKey: String,
)

internal data class FinanceDateQuickOption(
    val label: String,
    val dayKey: String,
    val order: Int,
)

internal data class FinanceCalendarDayOption(
    val dayKey: String?,
    val dayNumber: Int?,
    val inMonth: Boolean,
    val selected: Boolean,
)

internal data class ManualFinanceRange(
    val fromDayKey: String,
    val toDayKey: String,
)

internal fun financePeriodOptions(): List<FinancePeriodUiOption> = listOf(
    FinancePeriodUiOption(FinancePeriodPreset.DAY, "Día", ActionTone.Primary, 0),
    FinancePeriodUiOption(FinancePeriodPreset.WEEK, "Semana", ActionTone.Success, 1),
    FinancePeriodUiOption(FinancePeriodPreset.QUINCENA, "Quincena", ActionTone.Warning, 2),
    FinancePeriodUiOption(FinancePeriodPreset.MONTH, "Mes", ActionTone.Primary, 3),
    FinancePeriodUiOption(FinancePeriodPreset.CALENDAR, "Calendario", ActionTone.Secondary, 4),
).sortedBy { it.order }

internal fun financeMonthOptions(
    anchorDayKey: String,
    monthsBack: Int = 12,
): List<FinanceMonthUiOption> {
    val anchor = runCatching { LocalDate.parse(anchorDayKey) }.getOrElse { LocalDate.now() }
    val baseMonth = YearMonth.from(anchor)
    val locale = Locale.forLanguageTag("es-DO")
    return (0 until monthsBack.coerceAtLeast(1)).map { offset ->
        val month = baseMonth.minusMonths(offset.toLong())
        val firstDay = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        val monthLabel = month.month.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { it.titlecase(locale) }
        FinanceMonthUiOption(
            label = "$monthLabel ${month.year}",
            anchorDayKey = lastDay.toString(),
            fromDayKey = firstDay.toString(),
            toDayKey = lastDay.toString(),
        )
    }
}

internal fun financePeriodOptionRows(windowMode: LotteryNetWindowMode): List<List<FinancePeriodUiOption>> {
    val options = financePeriodOptions()
    return when (windowMode) {
        LotteryNetWindowMode.POS,
        LotteryNetWindowMode.POS_TIGHT -> listOf(options.take(3), options.drop(3))
        LotteryNetWindowMode.TABLET,
        LotteryNetWindowMode.WIDE -> listOf(options)
    }.filter { it.isNotEmpty() }
}

internal fun financeDateQuickOptions(todayDayKey: String): List<FinanceDateQuickOption> {
    val today = runCatching { LocalDate.parse(todayDayKey) }.getOrElse { LocalDate.now() }
    return listOf(
        FinanceDateQuickOption("Hoy", today.toString(), 0),
        FinanceDateQuickOption("Ayer", today.minusDays(1).toString(), 1),
    )
}

internal fun resolveFinanceSelectedDateLabel(
    selectedDayKey: String,
    todayDayKey: String,
): String {
    val selected = runCatching { LocalDate.parse(selectedDayKey) }.getOrNull()
    val today = runCatching { LocalDate.parse(todayDayKey) }.getOrNull()
    return when {
        selected != null && today != null && selected == today -> "Hoy"
        selected != null && today != null && selected == today.minusDays(1) -> "Ayer"
        else -> selectedDayKey
    }
}

internal fun normalizeManualFinanceRange(
    fromDayKey: String,
    toDayKey: String,
): ManualFinanceRange {
    val from = runCatching { LocalDate.parse(fromDayKey) }.getOrElse { LocalDate.now() }
    val to = runCatching { LocalDate.parse(toDayKey) }.getOrElse { from }
    val start = minOf(from, to)
    val end = maxOf(from, to)
    return ManualFinanceRange(start.toString(), end.toString())
}

internal fun formatManualFinanceRangeLabel(
    fromDayKey: String,
    toDayKey: String,
): String {
    val range = normalizeManualFinanceRange(fromDayKey, toDayKey)
    val locale = Locale.forLanguageTag("es-DO")
    val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
    val from = LocalDate.parse(range.fromDayKey)
    val to = LocalDate.parse(range.toDayKey)
    return "${from.format(formatter).replace(".", "")} - ${to.format(formatter).replace(".", "")}"
}

internal fun formatFinanceScreenMoney(value: Double): String {
    val rounded = kotlin.math.round(value).toLong()
    val absolute = abs(rounded).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (rounded < 0) "-$absolute" else absolute
}

internal fun formatFinanceScreenSignedMoney(value: Double): String {
    val prefix = if (value >= 0.0) "+" else "-"
    return prefix + formatFinanceScreenMoney(abs(value))
}

internal fun resolveFinanceActionPeriodLabel(
    selectedDayKey: String,
    selectedPeriodLabel: String,
): String = selectedPeriodLabel.ifBlank { selectedDayKey }

internal fun resolveFinanceCalendarEntryPointCount(): Int = 1

internal fun financeCalendarMonthDays(
    anchorDayKey: String,
    selectedDayKey: String,
): List<FinanceCalendarDayOption> {
    val anchor = runCatching { LocalDate.parse(anchorDayKey) }.getOrElse { LocalDate.now() }
    val selected = runCatching { LocalDate.parse(selectedDayKey) }.getOrNull()
    val month = YearMonth.from(anchor)
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val dayCells = (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        FinanceCalendarDayOption(
            dayKey = date.toString(),
            dayNumber = day,
            inMonth = true,
            selected = selected == date,
        )
    }
    val cells = List(leadingBlanks) {
        FinanceCalendarDayOption(dayKey = null, dayNumber = null, inMonth = false, selected = false)
    } + dayCells
    val trailing = (7 - (cells.size % 7)).let { if (it == 7) 0 else it }
    return cells + List(trailing) {
        FinanceCalendarDayOption(dayKey = null, dayNumber = null, inMonth = false, selected = false)
    }
}

internal fun resolveRequestedFinancePreset(
    rawPreset: String?,
    role: UserRole,
): FinancePeriodPreset {
    val requested = rawPreset?.let { runCatching { FinancePeriodPreset.valueOf(it) }.getOrNull() }
    return requested ?: if (role == UserRole.ADMIN) FinancePeriodPreset.WEEK else FinancePeriodPreset.DAY
}

internal fun buildSafeFinanceReportLaunch(
    repository: LocalFinanceRepository,
    scope: FinanceScope,
    role: UserRole,
    latestDayKey: String,
    requestedPresetRaw: String?,
    requestedAnchorDay: String?,
    fromDay: String?,
    toDay: String?,
): SafeFinanceReportLaunch {
    val initialPreset = resolveRequestedFinancePreset(requestedPresetRaw, role)
    val safeAnchorDay = requestedAnchorDay?.takeIf { it.isNotBlank() } ?: latestDayKey
    val primary = runCatching {
        repository.getScopedPeriodReport(
            scope = scope,
            preset = initialPreset,
            anchorDayKey = safeAnchorDay,
            fromDayKey = fromDay,
            toDayKey = toDay,
        )
    }.getOrNull()
    if (primary != null) {
        return SafeFinanceReportLaunch(
            report = primary,
            preset = initialPreset,
            anchorDay = safeAnchorDay,
            recovered = false,
        )
    }

    val fallbackPreset = if (role == UserRole.ADMIN) FinancePeriodPreset.WEEK else FinancePeriodPreset.DAY
    val fallback = repository.getScopedPeriodReport(
        scope = scope,
        preset = fallbackPreset,
        anchorDayKey = latestDayKey,
    )
    return SafeFinanceReportLaunch(
        report = fallback,
        preset = fallbackPreset,
        anchorDay = latestDayKey,
        recovered = true,
    )
}
