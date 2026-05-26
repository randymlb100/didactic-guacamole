package com.lotterynet.pro.ui.shell

import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellMenuContractsTest {

    @Test
    fun `cashier menu includes cuadre access`() {
        assertTrue(shouldShowCashboxControls(UserRole.CASHIER))
    }

    @Test
    fun `operator menu uses task groups that match compact adjustment hierarchy`() {
        assertEquals(listOf("Operación", "Caja", "Administración", "Sistema"), resolveShellMenuSectionTitles(UserRole.ADMIN))
        assertEquals(listOf("Operación", "Caja", "Sistema"), resolveShellMenuSectionTitles(UserRole.CASHIER))
        assertEquals(listOf("Operación", "Caja", "Sistema"), resolveShellMenuSectionTitles(UserRole.CASHIER, manualPosModeEnabled = true))
        assertEquals(listOf("Supervisión", "Consulta", "Soporte"), resolveShellMenuSectionTitles(UserRole.SUPERVISOR))
    }

    @Test
    fun `supervisor menu has no sale recharge user or destructive ticket actions`() {
        val titles = resolveShellMenuActionTitles(UserRole.SUPERVISOR)
        val routes = resolveShellButtonRoutes(UserRole.SUPERVISOR).map { it.title to it.lookupMode }.toMap()

        listOf("Mis cajeros", "Monitoreo", "Reporte", "Tickets", "Resultados", "Finanzas", "Impresora").forEach { title ->
            assertTrue(titles.contains(title))
        }
        listOf("Vender", "Recargas", "Cajeros", "Sistema", "Eliminar Ticket", "Cobros", "Cuadre", "Repetir ticket", "Duplicar ticket").forEach { title ->
            assertEquals(-1, titles.indexOf(title))
        }
        assertFalse(routes.containsKey("Duplicar ticket"))
        assertFalse(routes.containsKey("Cuadre"))
        assertFalse(routes.containsKey("Cobros"))
        assertFalse(routes.containsKey("Eliminar Ticket"))
        assertEquals("com.lotterynet.pro.ui.admin.AdminMonitorActivity", resolveShellButtonRoutes(UserRole.SUPERVISOR).first { it.title == "Mis cajeros" }.activityClassName)
    }

    @Test
    fun `supervisor entry prioritizes monitoring report tickets and results`() {
        assertEquals(
            listOf("Supervisión", "Consulta", "Soporte"),
            resolveShellMenuSectionTitles(UserRole.SUPERVISOR),
        )
        assertEquals(
            listOf("Mis cajeros", "Monitoreo", "Reporte", "Tickets", "Resultados", "Finanzas"),
            resolveShellRolePriorityActionTitles(UserRole.SUPERVISOR),
        )
        assertEquals(
            listOf("Mis cajeros", "Monitoreo", "Reporte", "Tickets"),
            resolveShellMenuActionTitles(UserRole.SUPERVISOR).take(4),
        )
    }

    @Test
    fun `supervisor badge uses full role name`() {
        assertEquals("Supervisor", resolveShellRoleBadgeLabel(UserRole.SUPERVISOR))
        assertEquals("Cajero", resolveShellRoleBadgeLabel(UserRole.CASHIER))
    }

    @Test
    fun `supervisor header includes assigned cashier count before money totals`() {
        assertEquals(
            listOf("Cajeros", "Venta", "Caja", "Pend."),
            resolveShellHeaderMetricLabels(UserRole.SUPERVISOR),
        )
        assertEquals(
            listOf("Venta", "Caja", "Pend."),
            resolveShellHeaderMetricLabels(UserRole.ADMIN),
        )
    }

    @Test
    fun `operator menu keeps tickets inside operation and removes duplicate sync shortcut`() {
        val titles = resolveShellMenuActionTitles(UserRole.ADMIN)

        assertTrue(titles.indexOf("Vender") < titles.indexOf("Tickets"))
        assertTrue(titles.indexOf("Tickets") < titles.indexOf("Resultados"))
        assertEquals(-1, titles.indexOf("Sync"))
        assertTrue(titles.indexOf("Impresora") < titles.indexOf("Sistema"))
    }

    @Test
    fun `admin action titles keep printer and system settings separated`() {
        val titles = resolveShellMenuActionTitles(UserRole.ADMIN)

        assertTrue(titles.indexOf("Impresora") < titles.indexOf("Sistema"))
        assertTrue(titles.contains("Reporte"))
        assertTrue(titles.indexOf("Cuadre") < titles.indexOf("Reporte"))
        assertTrue(titles.indexOf("Reporte") < titles.indexOf("Monitoreo"))
    }

    @Test
    fun `admin menu keeps cashier limits as the single visible cashier limit access`() {
        val titles = resolveShellMenuActionTitles(UserRole.ADMIN)
        val routes = resolveShellButtonRoutes(UserRole.ADMIN).map { it.title }

        assertEquals(-1, titles.indexOf("Cajeros"))
        assertTrue(titles.contains("Límites"))
        assertEquals(-1, titles.indexOf("Reglas de banca"))
        assertTrue(!routes.contains("Cajeros"))
        assertTrue(!routes.contains("Reglas de banca"))
        assertTrue(routes.contains("Límites"))
    }

    @Test
    fun `operator menu keeps ticket summary repeat and cuadre visible separately`() {
        val cashierTitles = resolveShellMenuActionTitles(UserRole.CASHIER)
        val adminTitles = resolveShellMenuActionTitles(UserRole.ADMIN)

        listOf("Tickets", "Repetir ticket", "Cuadre", "Reporte").forEach { title ->
            assertTrue(cashierTitles.contains(title))
            assertTrue(adminTitles.contains(title))
        }
        assertTrue(cashierTitles.indexOf("Tickets") < cashierTitles.indexOf("Repetir ticket"))
        assertTrue(cashierTitles.indexOf("Repetir ticket") < cashierTitles.indexOf("Cuadre"))
        assertTrue(cashierTitles.indexOf("Cuadre") < cashierTitles.indexOf("Reporte"))
        assertEquals(-1, cashierTitles.indexOf("Cobros"))
        assertEquals(listOf("Cuadre", "Reporte", "Impresora"), resolveShellCashboxActionTitles(UserRole.CASHIER))
    }

    @Test
    fun `system update action is available from every shell role`() {
        listOf(UserRole.CASHIER, UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.MASTER).forEach { role ->
            assertTrue(resolveShellMenuActionTitles(role).contains("Actualizar sistema"))
        }
    }

    @Test
    fun `cashier system section exposes only universal pos mode control`() {
        assertTrue(resolveShellMenuActionTitles(UserRole.CASHIER).contains("Modo POS"))
        assertFalse(resolveShellMenuActionTitles(UserRole.CASHIER).contains("Perfil cajero"))
        assertTrue(resolveShellMenuActionTitles(UserRole.CASHIER, manualPosModeEnabled = true).contains("Modo POS"))
        assertTrue(resolveShellButtonRoutes(UserRole.CASHIER).map { it.title }.contains("Modo POS"))
    }

    @Test
    fun `shell button routes point to native destinations and lookup modes`() {
        val routes = resolveShellButtonRoutes(UserRole.ADMIN).associateBy { it.title }

        assertEquals("com.lotterynet.pro.ui.sales.SalesActivity", routes.getValue("Vender").activityClassName)
        assertEquals("com.lotterynet.pro.ui.tickets.TicketSummaryActivity", routes.getValue("Tickets").activityClassName)
        assertEquals("com.lotterynet.pro.ui.results.ResultsActivity", routes.getValue("Resultados").activityClassName)
        assertEquals("com.lotterynet.pro.ui.recharge.RecargasActivity", routes.getValue("Recargas").activityClassName)
        assertEquals("com.lotterynet.pro.ui.finance.FinanceActivity", routes.getValue("Cuadre").activityClassName)
        assertEquals("com.lotterynet.pro.ui.report.OperationalReportActivity", routes.getValue("Reporte").activityClassName)
        assertFalse(routes.containsKey("Premios"))
        assertEquals("com.lotterynet.pro.ui.users.UserAccountsActivity", routes.getValue("Supervisor").activityClassName)
        assertEquals(null, routes.getValue("Supervisor").lookupMode)
        assertEquals("com.lotterynet.pro.ui.admin.AdminLimitsActivity", routes.getValue("Límites").activityClassName)
        assertEquals("com.lotterynet.pro.ui.printer.PrinterActivity", routes.getValue("Impresora").activityClassName)
        assertEquals("com.lotterynet.pro.ui.tickets.TicketLookupActivity", routes.getValue("Repetir ticket").activityClassName)
        assertEquals("duplicar", routes.getValue("Repetir ticket").lookupMode)
        assertEquals("pagar", routes.getValue("Cobros").lookupMode)
        assertEquals("anular", routes.getValue("Eliminar Ticket").lookupMode)
    }

    @Test
    fun `shell compact layout keeps rows dense and readable on small screens`() {
        val tight = resolveShellMenuLayout(com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS_TIGHT)
        val phone = resolveShellMenuLayout(com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS)

        assertEquals(false, tight.showActionDescriptions)
        assertTrue(tight.actionPaddingDp <= 6)
        assertTrue(phone.actionPaddingDp <= 7)
        assertTrue(tight.iconSizeDp <= 28)
        assertTrue(phone.iconSizeDp <= 30)
    }
}
