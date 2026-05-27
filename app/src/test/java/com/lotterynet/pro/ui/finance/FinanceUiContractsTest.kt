package com.lotterynet.pro.ui.finance

import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.FinancePeriodPreset
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceUiContractsTest {

    @Test
    fun `phone finance prioritizes delivery actions`() {
        val layout = resolveFinanceLayout(LotteryNetWindowMode.POS)
        val actions = resolveFinanceHeaderActionOrder(layout.prioritizeDeliveryActions)

        assertTrue(layout.compactHeader)
        assertTrue(layout.useDenseRows)
        assertTrue(layout.inlineTotals)
        assertTrue(layout.headerPaddingVerticalDp <= 6)
        assertEquals(3, layout.actionColumns)
        assertEquals(
            listOf(
                FinanceHeaderActionId.WHATSAPP,
                FinanceHeaderActionId.SHARE,
                FinanceHeaderActionId.PRINT,
            ),
            actions.take(3),
        )
    }

    @Test
    fun `finance action order does not navigate to period reports`() {
        val layout = resolveFinanceLayout(LotteryNetWindowMode.WIDE)
        val actions = resolveFinanceHeaderActionOrder(layout.prioritizeDeliveryActions)

        assertFalse(actions.contains(FinanceHeaderActionId.REPORTS))
    }

    @Test
    fun `finance compact action bar keeps delivery actions ordered`() {
        val layout = resolveFinanceLayout(LotteryNetWindowMode.POS_TIGHT)
        val rows = resolveFinanceHeaderActionRows(
            resolveFinanceHeaderActionOrder(layout.prioritizeDeliveryActions),
            layout.actionColumns,
        )

        assertEquals(
            listOf(
                listOf(FinanceHeaderActionId.WHATSAPP, FinanceHeaderActionId.SHARE, FinanceHeaderActionId.PRINT),
                listOf(FinanceHeaderActionId.THERMAL, FinanceHeaderActionId.SAVE),
            ),
            rows,
        )
    }

    @Test
    fun `finance export actions collapse behind one export menu`() {
        val contract = resolveFinanceExportMenuContract(resolveFinanceHeaderActionOrder(prioritizeDeliveryActions = true))

        assertEquals("Exportar", contract.visibleButtonLabel)
        assertEquals(1, contract.visibleButtonCount)
        assertEquals(
            listOf("WhatsApp", "Compartir", "Imprimir", "Térmico", "Guardar"),
            contract.menuLabels,
        )
        assertTrue(contract.usesOverflowMenu)
    }

    @Test
    fun `finance period options use full labels in fixed order`() {
        val options = financePeriodOptions()

        assertEquals(
            listOf("Día", "Semana", "Quincena", "Mes", "Calendario"),
            options.map { it.label },
        )
    }

    @Test
    fun `finance period options are a single dropdown source`() {
        val options = financePeriodOptions()

        assertEquals(FinancePeriodPreset.DAY, options.first().preset)
        assertEquals(FinancePeriodPreset.CALENDAR, options.last().preset)
    }

    @Test
    fun `finance period buttons use short labels on small screens`() {
        val compactLabels = financePeriodOptions().map { financePeriodButtonLabel(it, LotteryNetWindowMode.POS_TIGHT) }
        val wideLabels = financePeriodOptions().map { financePeriodButtonLabel(it, LotteryNetWindowMode.WIDE) }

        assertEquals(listOf("Día", "Semana", "15 días", "Mes", "Rango"), compactLabels)
        assertEquals(listOf("Día", "Semana", "Quincena", "Mes", "Calendario"), wideLabels)
        assertTrue(compactLabels.all { it.length <= 7 })
    }

    @Test
    fun `finance quick date options expose today and yesterday`() {
        val options = financeDateQuickOptions("2026-04-27")

        assertEquals(listOf("Hoy", "Ayer"), options.map { it.label })
        assertEquals(listOf("2026-04-27", "2026-04-26"), options.map { it.dayKey })
    }

    @Test
    fun `finance calendar marks selected day inside month grid`() {
        val days = financeCalendarMonthDays(anchorDayKey = "2026-04-27", selectedDayKey = "2026-04-15")
        val selected = days.single { it.selected }

        assertEquals("2026-04-15", selected.dayKey)
        assertEquals(15, selected.dayNumber)
        assertTrue(days.size % 7 == 0)
    }

    @Test
    fun `finance selected date label prefers operator language`() {
        assertEquals("Hoy", resolveFinanceSelectedDateLabel("2026-04-27", "2026-04-27"))
        assertEquals("Ayer", resolveFinanceSelectedDateLabel("2026-04-26", "2026-04-27"))
        assertEquals("2026-04-20", resolveFinanceSelectedDateLabel("2026-04-20", "2026-04-27"))
    }

    @Test
    fun `manual finance range normalizes inverted date selection`() {
        val range = normalizeManualFinanceRange("2026-04-16", "2026-04-08")

        assertEquals("2026-04-08", range.fromDayKey)
        assertEquals("2026-04-16", range.toDayKey)
        assertEquals("8 abr - 16 abr", formatManualFinanceRangeLabel(range.fromDayKey, range.toDayKey))
    }

    @Test
    fun `finance exposes one calendar entry point for manual range`() {
        assertEquals(1, resolveFinanceCalendarEntryPointCount())
    }

    @Test
    fun `finance screen money removes rd prefix for compact operation`() {
        assertEquals("1,250", formatFinanceScreenMoney(1250.0))
        assertEquals("-300", formatFinanceScreenMoney(-300.0))
        assertEquals("+300", formatFinanceScreenSignedMoney(300.0))
    }

    @Test
    fun `finance export label follows selected period instead of stale day`() {
        assertEquals(
            "8 abr - 16 abr",
            resolveFinanceActionPeriodLabel(
                selectedDayKey = "2026-04-27",
                selectedPeriodLabel = "8 abr - 16 abr",
            ),
        )
    }

    @Test
    fun `finance month options expose complete previous months`() {
        val options = financeMonthOptions(anchorDayKey = "2026-04-24", monthsBack = 4)

        assertEquals(
            listOf("Abril 2026", "Marzo 2026", "Febrero 2026", "Enero 2026"),
            options.map { it.label },
        )
        assertEquals("2026-01-01", options.last().fromDayKey)
        assertEquals("2026-01-31", options.last().toDayKey)
        assertEquals("2026-01-31", options.last().anchorDayKey)
    }

    @Test
    fun `invalid requested preset falls back by role`() {
        assertEquals(FinancePeriodPreset.WEEK, resolveRequestedFinancePreset("NOPE", UserRole.ADMIN))
        assertEquals(FinancePeriodPreset.DAY, resolveRequestedFinancePreset("NOPE", UserRole.CASHIER))
    }

    @Test
    fun `finance period report uses a single vertical scroll container`() {
        assertEquals(
            listOf(FinanceReportScrollContainer.SCREEN),
            resolveFinanceReportScrollContainers(),
        )
    }

    @Test
    fun `finance root keeps bottom content above phone navigation`() {
        assertTrue(shouldApplyFinanceSafeDrawingInsets())
    }

    @Test
    fun `finance operation sections are tailored by operational profile`() {
        assertEquals(
            listOf("GLOBAL", "TEAM", "ALERTS"),
            resolveFinanceOperationSections(UserRole.ADMIN, hasActorSummary = true),
        )
        assertEquals(
            listOf("MASTER_GLOBAL", "BANKS", "ALERTS"),
            resolveFinanceOperationSections(UserRole.MASTER, hasActorSummary = false),
        )
        assertEquals(
            listOf("PROFILE", "ACTOR"),
            resolveFinanceOperationSections(UserRole.CASHIER, hasActorSummary = true),
        )
    }

    @Test
    fun `supervisor finance shows assigned group sections only`() {
        assertEquals(
            listOf("SUPERVISOR_GROUP", "TEAM", "ALERTS"),
            resolveFinanceOperationSections(UserRole.SUPERVISOR, hasActorSummary = false),
        )
    }

    @Test
    fun `cashier finance keeps profile and cashier cuadre sections`() {
        assertEquals(
            listOf("PROFILE", "ACTOR"),
            resolveFinanceOperationSections(UserRole.CASHIER, hasActorSummary = true),
        )
    }

    @Test
    fun `all operational profiles can open finance business screens`() {
        assertTrue(canOpenFinanceForRole(UserRole.MASTER))
        assertTrue(canOpenFinanceForRole(UserRole.ADMIN))
        assertTrue(canOpenFinanceForRole(UserRole.SUPERVISOR))
        assertTrue(canOpenFinanceForRole(UserRole.CASHIER))
    }

    @Test
    fun `finance share text groups important cuadre values`() {
        val text = buildFinanceShareText(
            bancaName = "Banca Norte",
            dayKey = "Hoy",
            summary = FinanceSummary(
                ventas = 1_000.0,
                ticketsCount = 8,
                activos = 6,
                anuladosCount = 1,
                borradosCount = 1,
                recargas = 100.0,
                comision = 50.0,
                premiosPagados = 200.0,
                premiosPendientes = 300.0,
                cajaDisponible = 850.0,
            ),
            turnoDiff = 25.0,
        )

        assertTrue(text.contains("Cuadre - Banca Norte"))
        assertTrue(text.contains("Periodo: Hoy"))
        assertTrue(text.contains("Ingresos"))
        assertTrue(text.contains("Salidas"))
        assertTrue(text.contains("Caja"))
        assertTrue(text.contains("Ventas: $ 1,000"))
        assertTrue(text.contains("Recargas: $ 100"))
        assertTrue(text.contains("Comisión: $ 50"))
        assertTrue(text.contains("Premios pendientes: $ 300"))
        assertTrue(text.contains("Tickets: 8"))
        assertTrue(text.contains("Activos: 6"))
        assertTrue(text.contains("Anulados/Borrados: 2"))
        assertTrue(text.contains("Diferencia turno: +$ 25"))
    }
}
