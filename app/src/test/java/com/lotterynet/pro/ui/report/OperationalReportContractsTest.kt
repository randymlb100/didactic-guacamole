package com.lotterynet.pro.ui.report

import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.OperationalReportActorFilter
import com.lotterynet.pro.core.finance.OperationalReportHealth
import com.lotterynet.pro.core.finance.OperationalReportManualTarget
import com.lotterynet.pro.core.finance.OperationalReportSyncStatus
import com.lotterynet.pro.core.finance.buildOperationalReportActorFilters
import com.lotterynet.pro.core.finance.buildOperationalReportShareText
import com.lotterynet.pro.core.finance.operationalReportCommissionPercent
import com.lotterynet.pro.core.finance.resolveOperationalReportHealth
import com.lotterynet.pro.core.finance.updateOperationalReportManualRange
import com.lotterynet.pro.core.finance.resolveOperationalReportNet
import com.lotterynet.pro.core.finance.resolveOperationalReportFilterForRefresh
import com.lotterynet.pro.core.finance.resolveOperationalReportSelectedFilter
import com.lotterynet.pro.core.finance.resolveOperationalReportSyncLabel
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalReportContractsTest {

    @Test
    fun `phone operational report uses compact header dense rows and inline totals`() {
        val layout = resolveOperationalReportLayout(LotteryNetWindowMode.POS)

        assertTrue(layout.compactHeader)
        assertTrue(layout.useDenseRows)
        assertTrue(layout.inlineTotals)
        assertTrue(layout.metricPaddingVerticalDp <= 5)
    }

    @Test
    fun `business result reserves pending prizes before payout`() {
        val summary = FinanceSummary(
            ventas = 2_000.0,
            recargas = 500.0,
            comision = 200.0,
            premiosPagados = 700.0,
            premiosPendientes = 900.0,
        )

        assertEquals(700.0, resolveOperationalReportNet(summary), 0.001)
        assertEquals(OperationalReportHealth.POSITIVE, resolveOperationalReportHealth(summary))
    }

    @Test
    fun `business health turns negative when paid prizes and commission exceed income`() {
        val summary = FinanceSummary(
            ventas = 500.0,
            recargas = 0.0,
            comision = 50.0,
            premiosPagados = 700.0,
        )

        assertEquals(-250.0, resolveOperationalReportNet(summary), 0.001)
        assertEquals(OperationalReportHealth.NEGATIVE, resolveOperationalReportHealth(summary))
    }

    @Test
    fun `actor filter labels keep todos admin and cashier clear`() {
        assertEquals("Todos", OperationalReportActorFilter.All.label)
        assertEquals("Admin", OperationalReportActorFilter.Admin.label)
        assertEquals("Supervisor 01", OperationalReportActorFilter.Supervisor("supervisor-1", "Supervisor 01").label)
        assertEquals("Cajero 01", OperationalReportActorFilter.Cashier("cashier-1", "Cajero 01").label)
    }

    @Test
    fun `admin actor filters include active supervisors before cashiers`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca yuniel",
        )
        val supervisors = listOf(
            UserAccount(id = "sup2", user = "sup2", role = UserRole.SUPERVISOR, displayName = "Supervisor 2", active = true, adminId = "admin-1", banca = "Banca yuniel"),
            UserAccount(id = "sup1", user = "sup1", role = UserRole.SUPERVISOR, displayName = "Supervisor 1", active = true, adminId = "admin-1", banca = "Banca yuniel"),
            UserAccount(id = "supX", user = "supX", role = UserRole.SUPERVISOR, displayName = "Supervisor X", active = true, adminId = "other-admin", banca = "Otra banca"),
            UserAccount(id = "supInactive", user = "supInactive", role = UserRole.SUPERVISOR, displayName = "Supervisor inactivo", active = false, adminId = "admin-1", banca = "Banca yuniel"),
        )
        val cashiers = listOf(
            UserAccount(id = "ten", user = "cajero10", role = UserRole.CASHIER, displayName = "Cajero 10", adminId = "admin-1", banca = "Banca yuniel"),
            UserAccount(id = "two", user = "cajero2", role = UserRole.CASHIER, displayName = "Cajero 2", adminId = "admin-1", banca = "Banca yuniel"),
            UserAccount(id = "one", user = "cajero1", role = UserRole.CASHIER, displayName = "Cajero 1", adminId = "admin-1", banca = "Banca yuniel"),
            UserAccount(id = "ana", user = "ana", role = UserRole.CASHIER, displayName = "Ana", adminId = "admin-1", banca = "Banca yuniel"),
        )

        val filters = buildOperationalReportActorFilters(session, cashiers, supervisors)

        assertEquals(
            listOf("Todos", "Admin", "Supervisor 1", "Supervisor 2", "Cajero 1", "Cajero 2", "Cajero 10", "Ana"),
            filters.map { it.label },
        )
        assertTrue(filters.any { it is OperationalReportActorFilter.Supervisor && it.key == "supervisor:sup1" })
    }

    @Test
    fun `supervisor actor filters include todos and assigned active cashiers only`() {
        val session = ActiveSession(
            role = UserRole.SUPERVISOR,
            userId = "SUP-1",
            username = "sup01",
            adminId = "ADM-1",
            adminUser = "admin01",
        )
        val assigned = UserAccount(
            id = "CAJ-1",
            user = "cajero01",
            role = UserRole.CASHIER,
            active = true,
            supervisorIds = listOf("SUP-1"),
            supervisorUsers = listOf("sup01"),
        )
        val inactiveAssigned = assigned.copy(id = "CAJ-2", user = "cajero02", active = false)
        val other = assigned.copy(id = "CAJ-3", user = "cajero03", supervisorIds = emptyList(), supervisorUsers = emptyList())

        val filters = buildOperationalReportActorFilters(session, listOf(other, inactiveAssigned, assigned))

        assertEquals(listOf("Todos", "cajero01"), filters.map { it.label })
    }

    @Test
    fun `cashier report filter shows human labels while keeping cashier number order`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            banca = "Banca yuniel",
        )
        val cashiers = listOf(
            UserAccount(id = "CAJ-03", user = "bancay03", role = UserRole.CASHIER, displayName = "Ana", adminId = "admin-1", banca = "Banca yuniel"),
            UserAccount(id = "CAJ-01", user = "bancay01", role = UserRole.CASHIER, displayName = "Zoe", adminId = "admin-1", banca = "Banca yuniel"),
            UserAccount(id = "CAJ-02", user = "bancay02", role = UserRole.CASHIER, displayName = "Carlos", adminId = "admin-1", banca = "Banca yuniel"),
        )

        val filters = buildOperationalReportActorFilters(session, cashiers)

        assertEquals(listOf("Todos", "Admin", "Zoe", "Carlos", "Ana"), filters.map { it.label })
    }

    @Test
    fun `cashier report filter avoids technical cashier id when there is a stable number`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
        )
        val cashiers = listOf(
            UserAccount(id = "CAJ-03", user = "CAJ-03", role = UserRole.CASHIER, adminId = "admin-1"),
        )

        val filters = buildOperationalReportActorFilters(session, cashiers)

        assertEquals(listOf("Todos", "Admin", "Cajero 03"), filters.map { it.label })
    }

    @Test
    fun `cashier report hides actor selector and stays on own profile`() {
        assertFalse(shouldShowOperationalReportActorFilter(emptyList()))
        assertFalse(shouldShowOperationalReportActorFilter(listOf(OperationalReportActorFilter.All)))
        assertFalse(
            shouldShowOperationalReportActorFilter(
                listOf(OperationalReportActorFilter.Cashier("CAJ-21", "Banca 21")),
            ),
        )
        assertTrue(
            shouldShowOperationalReportActorFilter(
                listOf(
                    OperationalReportActorFilter.All,
                    OperationalReportActorFilter.Cashier("cashier-1", "Cajero 01"),
                ),
            ),
        )
    }

    @Test
    fun `cashier report uses own cashier filter instead of global todos`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "technical-auth-id",
            username = "bancay21",
            authUserId = "auth-user-21",
        )
        val cashiers = listOf(
            UserAccount(
                id = "CAJ-21",
                user = "bancay21",
                role = UserRole.CASHIER,
                displayName = "Banca 21",
                authUserId = "auth-user-21",
            ),
        )

        val filters = buildOperationalReportActorFilters(session, cashiers)
        val selected = resolveOperationalReportSelectedFilter(filters, OperationalReportActorFilter.All)

        assertEquals(listOf("Banca 21"), filters.map { it.label })
        assertEquals("cashier:CAJ-21", selected.key)
    }

    @Test
    fun `report selected filter falls back to first valid filter not global`() {
        val ownFilter = OperationalReportActorFilter.Cashier("CAJ-21", "Banca 21")

        val selected = resolveOperationalReportSelectedFilter(
            filters = listOf(ownFilter),
            selected = OperationalReportActorFilter.All,
        )

        assertEquals(ownFilter, selected)
    }

    @Test
    fun `refresh keeps selected cashier when bootstrap list is temporarily incomplete`() {
        val selected = OperationalReportActorFilter.Cashier("CAJ-21", "Banca 21")

        val resolved = resolveOperationalReportFilterForRefresh(
            filters = listOf(OperationalReportActorFilter.All),
            selected = selected,
        )

        assertEquals(selected, resolved)
    }

    @Test
    fun `refresh prefers an exact forced cashier present in the refreshed list`() {
        val selected = OperationalReportActorFilter.Cashier("CAJ-OLD", "Caja anterior")
        val refreshed = OperationalReportActorFilter.Cashier("CAJ-21", "Banca 21")

        val resolved = resolveOperationalReportFilterForRefresh(
            filters = listOf(OperationalReportActorFilter.All, refreshed),
            selected = selected,
            forcedCashierKey = "CAJ-21",
        )

        assertEquals(refreshed, resolved)
    }

    @Test
    fun `sync labels distinguish fresh server data from cached copy`() {
        assertEquals("Actualizado", resolveOperationalReportSyncLabel(OperationalReportSyncStatus.UPDATED))
        assertEquals("Sin conexión usando última copia", resolveOperationalReportSyncLabel(OperationalReportSyncStatus.CACHED_COPY))
        assertEquals("No se pudo cargar servidor", resolveOperationalReportSyncLabel(OperationalReportSyncStatus.SERVER_FAILED))
    }

    @Test
    fun `report visual contract stays lightweight without chart`() {
        val contract = resolveOperationalReportLayout(LotteryNetWindowMode.POS_TIGHT)

        assertFalse(contract.showChart)
        assertTrue(contract.showLedgerStrip)
        assertTrue(contract.metricPaddingVerticalDp <= 6)
    }

    @Test
    fun `report server action and labels avoid technical provider words`() {
        val labels = operationalReportVisibleServerLabels()

        assertTrue(labels.contains("Actualizar servidor"))
        assertFalse(labels.any { it.contains("supabase", ignoreCase = true) || it.contains("superbase", ignoreCase = true) })
    }

    @Test
    fun `report summary labels and tones stay operational`() {
        val positive = buildOperationalReportMetricSpecs(
            FinanceSummary(
                ventas = 1_000.0,
                recargas = 200.0,
                comision = 100.0,
                premiosPagados = 50.0,
                cajaDisponible = 1_050.0,
            ),
        )
        val negative = buildOperationalReportMetricSpecs(
            FinanceSummary(
                ventas = 100.0,
                recargas = 0.0,
                comision = 50.0,
                premiosPagados = 200.0,
                cajaDisponible = -150.0,
            ),
        )
        val neutral = buildOperationalReportMetricSpecs(
            FinanceSummary(
                ventas = 100.0,
                comision = 100.0,
            ),
        )

        assertEquals(listOf("Venta", "Recarga", "Comisión", "Premio", "Caja", "Beneficio"), positive.map { it.label })
        assertEquals("ink", positive.first { it.label == "Venta" }.tone)
        assertEquals("ink", positive.first { it.label == "Recarga" }.tone)
        assertTrue(positive.first { it.label == "Venta" }.bold)
        assertTrue(positive.first { it.label == "Recarga" }.bold)
        assertEquals("loss", positive.first { it.label == "Comisión" }.tone)
        assertEquals("gain", positive.first { it.label == "Beneficio" }.tone)
        assertEquals("loss", negative.first { it.label == "Beneficio" }.tone)
        assertEquals("ink", neutral.first { it.label == "Beneficio" }.tone)
    }

    @Test
    fun `supervisor report shows supervision commission as the main result`() {
        val specs = buildOperationalReportMetricSpecs(
            FinanceSummary(
                ventas = 2_000.0,
                recargas = 0.0,
                comision = 200.0,
                premiosPagados = 300.0,
                cajaDisponible = 1_500.0,
                supervisorComision = 150.0,
            ),
            isSupervisorCommissionReport = true,
        )

        assertEquals("Comisión supervisión", resolveOperationalReportPrimaryResultLabel(true))
        assertEquals("Beneficio", resolveOperationalReportPrimaryResultLabel(false))
        assertEquals(listOf("Venta", "Recarga", "Comisión", "Premio", "Caja", "Comisión supervisión"), specs.map { it.label })
        assertEquals(150.0, specs.last().value, 0.001)
        assertEquals("gain", specs.last().tone)
    }

    @Test
    fun `manual report calendar keeps desde hasta range ordered`() {
        val fromUpdated = updateOperationalReportManualRange(
            fromDayKey = "2026-04-29",
            toDayKey = "2026-04-29",
            selectedDayKey = "2026-04-27",
            target = OperationalReportManualTarget.FROM,
        )

        assertEquals("2026-04-27", fromUpdated.fromDayKey)
        assertEquals("2026-04-29", fromUpdated.toDayKey)

        val toUpdatedBeforeFrom = updateOperationalReportManualRange(
            fromDayKey = "2026-04-27",
            toDayKey = "2026-04-29",
            selectedDayKey = "2026-04-25",
            target = OperationalReportManualTarget.TO,
        )

        assertEquals("2026-04-25", toUpdatedBeforeFrom.fromDayKey)
        assertEquals("2026-04-27", toUpdatedBeforeFrom.toDayKey)
    }

    @Test
    fun `report filter selection keeps period range and actor together`() {
        val selection = OperationalReportFilterSelection(
            period = OperationalReportPeriod.MANUAL,
            fromDayKey = "2026-07-01",
            toDayKey = "2026-07-25",
            actorFilter = OperationalReportActorFilter.Cashier("cashier-1", "Cajero 1"),
        )

        assertEquals(OperationalReportPeriod.MANUAL, selection.period)
        assertEquals("2026-07-01", selection.fromDayKey)
        assertEquals("2026-07-25", selection.toDayKey)
        assertEquals("cashier:cashier-1", selection.actorFilter.key)
    }

    @Test
    fun `historical report header shows selected range instead of today`() {
        val report = com.lotterynet.pro.core.finance.OperationalReportViewState(
            periodLabel = "01/07/2026–15/07/2026",
            filter = OperationalReportActorFilter.All,
            syncStatus = OperationalReportSyncStatus.CACHED_COPY,
            summary = FinanceSummary(),
            trend = emptyList(),
            actorRows = emptyList(),
        )

        assertEquals(
            "Banca Norte · 01/07/2026–15/07/2026",
            operationalReportHeaderSubtitle("Banca Norte", report, "2026-07-25"),
        )
    }

    @Test
    fun `manual report period tap reopens calendar after apply collapsed it`() {
        assertTrue(shouldExpandManualReportCalendarAfterPeriodTap(OperationalReportPeriod.MANUAL))
        assertFalse(shouldExpandManualReportCalendarAfterPeriodTap(OperationalReportPeriod.WEEK))
    }

    @Test
    fun `share text includes period filter totals and server status`() {
        val text = buildOperationalReportShareText(
            bancaName = "Banca Norte",
            periodLabel = "Hoy",
            filter = OperationalReportActorFilter.Cashier("cashier-1", "Cajero 01"),
            syncStatus = OperationalReportSyncStatus.CACHED_COPY,
            summary = FinanceSummary(
                ventas = 1_000.0,
                recargas = 100.0,
                comision = 50.0,
                premiosPagados = 200.0,
                cajaDisponible = 850.0,
                premiosPendientes = 300.0,
            ),
        )

        assertTrue(text.contains("Reporte - Banca Norte"))
        assertTrue(text.contains("Periodo: Hoy"))
        assertTrue(text.contains("Operador: Cajero 01"))
        assertTrue(text.contains("Estado: Sin conexión usando última copia"))
        assertTrue(text.contains("Ingresos"))
        assertTrue(text.contains("Salidas"))
        assertTrue(text.contains("Resultado"))
        assertTrue(text.contains("Comisión: $ 50 (5%)"))
        assertTrue(text.contains("Premios pagados: $ 200"))
        assertTrue(text.contains("Tickets: 0"))
        assertTrue(text.contains("Beneficio: $ 550"))
        assertTrue(text.contains("Premios pendientes: $ 300"))
    }

    @Test
    fun `share text for supervisor report is compact and highlights supervision commission`() {
        val text = buildOperationalReportShareText(
            bancaName = "Banca Norte",
            periodLabel = "Mes",
            filter = OperationalReportActorFilter.Supervisor("sup1", "Supervisor 1"),
            syncStatus = OperationalReportSyncStatus.UPDATED,
            summary = FinanceSummary(
                ventas = 2_000.0,
                recargas = 0.0,
                comision = 200.0,
                premiosPagados = 300.0,
                supervisorComision = 150.0,
            ),
            isSupervisorCommissionReport = true,
        )

        assertTrue(text.contains("Reporte supervisor - Banca Norte"))
        assertTrue(text.contains("Operador: Supervisor 1"))
        assertTrue(text.contains("Comisión supervisión: $ 150"))
        assertFalse(text.contains("Beneficio:"))
        assertFalse(text.contains("Comisión supervisor:"))
    }

    @Test
    fun `commission percent is transparent in report`() {
        assertEquals("10%", operationalReportCommissionPercent(FinanceSummary(ventas = 1_000.0, comision = 100.0)))
        assertEquals("5%", operationalReportCommissionPercent(FinanceSummary(ventas = 7_535.0, comision = 376.0)))
        assertEquals("0%", operationalReportCommissionPercent(FinanceSummary(ventas = 0.0, comision = 100.0)))
    }
}
