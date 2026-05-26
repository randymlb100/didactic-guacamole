package com.lotterynet.pro.ui.shell

import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellUiContractsTest {

    @Test
    fun `phone shell hides repeated descriptions and date row`() {
        val contract = resolveShellMenuLayout(LotteryNetWindowMode.POS)

        assertFalse(contract.showActionDescriptions)
        assertFalse(contract.showDayLabel)
    }

    @Test
    fun `non sales redesign makes shell menu dense instead of large cards on phone`() {
        val contract = resolveShellMenuLayout(LotteryNetWindowMode.POS)

        assertTrue(contract.useDenseRows)
        assertFalse(contract.showLargeCards)
        assertTrue(contract.collapseSecondaryActions)
    }

    @Test
    fun `wide shell keeps richer menu context`() {
        val contract = resolveShellMenuLayout(LotteryNetWindowMode.WIDE)

        assertTrue(contract.showActionDescriptions)
        assertTrue(contract.showDayLabel)
    }

    @Test
    fun `shell menu scroll uses lazy sections to reduce work while scrolling`() {
        assertEquals(ShellScrollStrategy.LAZY_SECTIONS, resolveShellScrollStrategy())
    }

    @Test
    fun `cashier shell menu only exposes operational selling sections`() {
        assertEquals(listOf("Operación", "Caja", "Sistema"), resolveShellMenuSectionTitles(UserRole.CASHIER))
    }

    @Test
    fun `cashier shell actions only expose selling tickets results recharge and logout`() {
        assertEquals(
            listOf("Vender", "Tickets", "Resultados", "Recargas", "Repetir ticket", "Cuadre", "Reporte", "Impresora", "Actualizar sistema", "Modo POS", "Cerrar Sesión"),
            resolveShellMenuActionTitles(UserRole.CASHIER),
        )
    }

    @Test
    fun `cashier profile keeps sale first and secondary actions compact`() {
        assertEquals(
            listOf("Vender", "Tickets", "Resultados", "Recargas", "Repetir ticket", "Cuadre", "Reporte", "Impresora"),
            resolveShellRolePriorityActionTitles(UserRole.CASHIER),
        )
        assertEquals("Vender", resolveShellButtonRoutes(UserRole.CASHIER).first().title)
    }

    @Test
    fun `shell hides recharge action when master blocks recharge access`() {
        assertFalse(resolveShellMenuActionTitles(UserRole.ADMIN, rechargeVisible = false).contains("Recargas"))
        assertFalse(resolveShellMenuActionTitles(UserRole.CASHIER, rechargeVisible = false).contains("Recargas"))
        assertFalse(resolveShellButtonRoutes(UserRole.ADMIN, rechargeVisible = false).any { it.title == "Recargas" })
    }

    @Test
    fun `cashier shell cashbox actions omit premios dead entry`() {
        assertEquals(
            listOf("Cuadre", "Reporte", "Impresora"),
            resolveShellCashboxActionTitles(UserRole.CASHIER),
        )
    }

    @Test
    fun `cashier dashboard uses finance caja after commission`() {
        assertEquals(
            4200.0,
            resolveShellCashTotalForRole(
                role = UserRole.CASHIER,
                visibleSalesTotal = 5000.0,
                scopedCajaDisponible = 4200.0,
            ),
            0.0,
        )
    }

    @Test
    fun `admin repeat ticket stays beside summarized tickets`() {
        val actions = resolveShellMenuActionTitles(UserRole.ADMIN)

        assertTrue(actions.indexOf("Repetir ticket") > actions.indexOf("Tickets"))
        assertTrue(actions.indexOf("Repetir ticket") < actions.indexOf("Cuadre"))
    }

    @Test
    fun `admin shell menu keeps grouped control sections`() {
        assertEquals(listOf("Operación", "Caja", "Administración", "Sistema"), resolveShellMenuSectionTitles(UserRole.ADMIN))
    }

    @Test
    fun `admin shell actions do not expose removed admin panel`() {
        val actions = resolveShellMenuActionTitles(UserRole.ADMIN)

        assertFalse(actions.contains("Panel admin"))
        assertFalse(actions.contains("Dashboard"))
        assertFalse(actions.contains("Sync"))
        assertEquals("Vender", actions.first())
    }

    @Test
    fun `shell asks for runtime permissions needed by printer camera and notifications`() {
        val permissions = resolveStartupRuntimePermissions(36)

        assertTrue(permissions.contains(android.Manifest.permission.CAMERA))
        assertTrue(permissions.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(permissions.contains(android.Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun `logout clears active session but keeps remembered user`() {
        val policy = resolveLogoutClearPolicy()

        assertTrue(policy.clearActiveSession)
        assertTrue(policy.clearSessionSnapshot)
        assertFalse(policy.clearSavedLogin)
    }

    @Test
    fun `permission status summarizes missing bluetooth and camera`() {
        val status = resolvePermissionStatusMessage(
            missingPermissions = listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.CAMERA,
            ),
        )

        assertEquals("Permiso Bluetooth pendiente · Permiso cámara pendiente", status)
        assertEquals("Listo para imprimir y escanear", resolvePermissionStatusMessage(emptyList()))
    }

    @Test
    fun `master keeps its own compact control groups`() {
        assertEquals(listOf("Operación master", "Sesión"), resolveShellMenuSectionTitles(UserRole.MASTER))
    }

    @Test
    fun `master shell actions exclude business operations`() {
        val actions = resolveShellMenuActionTitles(UserRole.MASTER)

        assertEquals(listOf("Panel master", "Crear banca", "Auditoría", "Actualizar sistema", "Cerrar sesión"), actions)
        assertEquals(listOf("Panel master", "Crear banca", "Auditoría"), resolveShellRolePriorityActionTitles(UserRole.MASTER))
        assertFalse(actions.contains("Cuadre"))
        assertFalse(actions.contains("Recargas"))
        assertFalse(actions.contains("Vender"))
        assertFalse(actions.contains("Tickets"))
    }

    @Test
    fun `master shell does not load business dashboard snapshot`() {
        assertFalse(shouldLoadShellBusinessSnapshot(UserRole.MASTER))
        assertTrue(shouldLoadShellBusinessSnapshot(UserRole.ADMIN))
        assertTrue(shouldLoadShellBusinessSnapshot(UserRole.CASHIER))
    }

    @Test
    fun `business shell refreshes dashboard metrics on resume`() {
        assertFalse(shouldRefreshShellDashboardOnResume(UserRole.MASTER))
        assertTrue(shouldRefreshShellDashboardOnResume(UserRole.ADMIN))
        assertTrue(shouldRefreshShellDashboardOnResume(UserRole.CASHIER))
        assertTrue(shouldRefreshShellDashboardOnResume(UserRole.SUPERVISOR))
    }

    @Test
    fun `shell first frame excludes dashboard metrics and sync hydration`() {
        val plan = resolveShellStartupPlan()

        assertTrue(plan.firstFrameWork.contains(ShellStartupWork.LOAD_SESSION))
        assertFalse(plan.firstFrameWork.contains(ShellStartupWork.TOUCH_LAST_SEEN))
        assertFalse(plan.firstFrameWork.contains(ShellStartupWork.LOAD_DASHBOARD_METRICS))
        assertFalse(plan.firstFrameWork.contains(ShellStartupWork.HYDRATE_REMOTE_DATA))
        assertTrue(plan.afterFirstFrameWork.contains(ShellStartupWork.TOUCH_LAST_SEEN))
        assertTrue(plan.afterFirstFrameWork.contains(ShellStartupWork.LOAD_DASHBOARD_METRICS))
        assertTrue(plan.afterFirstFrameWork.contains(ShellStartupWork.HYDRATE_REMOTE_DATA))
    }
}
