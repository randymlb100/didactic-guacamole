package com.lotterynet.pro.ui.navigation

import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.permissions.RoleCapability
import com.lotterynet.pro.core.permissions.canRolePerform
import com.lotterynet.pro.ui.shell.resolveShellButtonRoutes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSafetyContractsTest {

    @Test
    fun `every shell route points to a declared activity allowed for that role`() {
        val manifestActivities = declaredManifestActivities()

        listOf(UserRole.MASTER, UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.CASHIER).forEach { role ->
            resolveShellButtonRoutes(role).forEach { route ->
                val destination = resolveNativeDestinationByActivity(route.activityClassName)

                assertTrue("${route.title} is not declared", manifestActivities.contains(route.activityClassName))
                assertTrue("${route.title} is not known", destination != null)
                assertTrue("${route.title} is blocked for $role", canOpenNativeDestination(role, destination!!))
            }
        }
    }

    @Test
    fun `every native destination activity is declared in manifest`() {
        val manifestActivities = declaredManifestActivities()

        NativeDestination.entries
            .filterNot { it == NativeDestination.LOGIN }
            .forEach { destination ->
                assertTrue("${destination.name} missing from manifest", manifestActivities.contains(destination.activityClassName))
            }
    }

    @Test
    fun `admin direct dashboard routes are registered and admin only`() {
        val adminOnlyActivities = setOf(
            "com.lotterynet.pro.ui.admin.AdminAlertsActivity",
            "com.lotterynet.pro.ui.admin.AdminLotteryMonitorActivity",
        )

        adminOnlyActivities.forEach { activityClassName ->
            val destination = resolveNativeDestinationByActivity(activityClassName)
            assertTrue("$activityClassName is missing from native route map", destination != null)
            assertTrue("$activityClassName must be admin only", canOpenNativeDestination(UserRole.ADMIN, destination!!))
            assertFalse("$activityClassName must block cashier", canOpenNativeDestination(UserRole.CASHIER, destination))
            assertFalse("$activityClassName must block master", canOpenNativeDestination(UserRole.MASTER, destination))
        }
    }

    @Test
    fun `ticket lookup modes are normalized before building navigation`() {
        assertEquals("duplicar", normalizeTicketLookupMode("duplicar"))
        assertEquals("pagar", normalizeTicketLookupMode("pagar"))
        assertEquals("anular", normalizeTicketLookupMode("anular"))
        assertEquals("duplicar", normalizeTicketLookupMode("bad-mode"))
        assertEquals("duplicar", normalizeTicketLookupMode(null))
        assertEquals("duplicar", normalizeTicketLookupModeForRole(UserRole.SUPERVISOR, "pagar"))
        assertEquals("duplicar", normalizeTicketLookupModeForRole(UserRole.SUPERVISOR, "anular"))
        assertEquals("duplicar", normalizeTicketLookupModeForRole(UserRole.SUPERVISOR, "duplicar"))
    }

    @Test
    fun `blocked role destinations resolve to safe fallback destinations`() {
        assertFalse(canOpenNativeDestination(UserRole.MASTER, NativeDestination.SALES))
        assertEquals(
            NativeDestination.MASTER_DASHBOARD,
            resolveBlockedDestinationFallback(UserRole.MASTER, NativeDestination.SALES),
        )

        assertFalse(canOpenNativeDestination(UserRole.CASHIER, NativeDestination.ADMIN_CONFIG))
        assertEquals(
            NativeDestination.SHELL_MENU,
            resolveBlockedDestinationFallback(UserRole.CASHIER, NativeDestination.ADMIN_CONFIG),
        )
    }

    @Test
    fun `admin cashier and master matrices stay explicit`() {
        assertEquals(
            setOf(
                NativeDestination.SALES,
                NativeDestination.TICKET_SUMMARY,
                NativeDestination.TICKET_LOOKUP,
                NativeDestination.FINANCE,
                NativeDestination.OPERATIONAL_REPORT,
                NativeDestination.RESULTS,
                NativeDestination.RECHARGE,
                NativeDestination.PRINTER,
                NativeDestination.ADMIN_WINNERS,
                NativeDestination.USER_ACCOUNTS,
                NativeDestination.ADMIN_LIMITS,
                NativeDestination.ADMIN_MONITOR,
                NativeDestination.ADMIN_LOTTERY_MONITOR,
                NativeDestination.ADMIN_ALERTS,
                NativeDestination.ADMIN_CONFIG,
                NativeDestination.ADMIN_AUDIT,
                NativeDestination.ADMIN_DASHBOARD,
                NativeDestination.SHELL_MENU,
                NativeDestination.TICKET_OFFICIAL,
            ),
            allowedNativeDestinations(UserRole.ADMIN),
        )
        assertTrue(canOpenNativeDestination(UserRole.ADMIN, NativeDestination.OPERATIONAL_REPORT))
        assertTrue(canOpenNativeDestination(UserRole.CASHIER, NativeDestination.OPERATIONAL_REPORT))
        assertFalse(canOpenNativeDestination(UserRole.MASTER, NativeDestination.OPERATIONAL_REPORT))
        assertEquals(
            setOf(
                NativeDestination.SALES,
                NativeDestination.TICKET_SUMMARY,
                NativeDestination.TICKET_LOOKUP,
                NativeDestination.TICKET_OFFICIAL,
                NativeDestination.FINANCE,
                NativeDestination.OPERATIONAL_REPORT,
                NativeDestination.RESULTS,
                NativeDestination.RECHARGE,
                NativeDestination.PRINTER,
                NativeDestination.SHELL_MENU,
            ),
            allowedNativeDestinations(UserRole.CASHIER),
        )
        assertEquals(
            setOf(
                NativeDestination.SHELL_MENU,
                NativeDestination.MASTER_DASHBOARD,
                NativeDestination.MASTER_CREATE_BANK,
                NativeDestination.FINANCE,
                NativeDestination.ADMIN_AUDIT,
            ),
            allowedNativeDestinations(UserRole.MASTER),
        )
    }

    @Test
    fun `supervisor is read only with report monitor and assigned ticket access`() {
        assertEquals(
            setOf(
                NativeDestination.TICKET_SUMMARY,
                NativeDestination.TICKET_OFFICIAL,
                NativeDestination.FINANCE,
                NativeDestination.OPERATIONAL_REPORT,
                NativeDestination.RESULTS,
                NativeDestination.PRINTER,
                NativeDestination.ADMIN_MONITOR,
                NativeDestination.ADMIN_LOTTERY_MONITOR,
                NativeDestination.SHELL_MENU,
            ),
            allowedNativeDestinations(UserRole.SUPERVISOR),
        )
        assertFalse(canOpenNativeDestination(UserRole.SUPERVISOR, NativeDestination.SALES))
        assertFalse(canOpenNativeDestination(UserRole.SUPERVISOR, NativeDestination.RECHARGE))
        assertFalse(canOpenNativeDestination(UserRole.SUPERVISOR, NativeDestination.USER_ACCOUNTS))
        assertFalse(canOpenNativeDestination(UserRole.SUPERVISOR, NativeDestination.ADMIN_CONFIG))
        assertTrue(canOpenNativeDestination(UserRole.SUPERVISOR, NativeDestination.FINANCE))
        assertFalse(canOpenNativeDestination(UserRole.SUPERVISOR, NativeDestination.TICKET_LOOKUP))
    }

    @Test
    fun `role capability matrix keeps allowed work explicit`() {
        assertTrue(canRolePerform(UserRole.MASTER, RoleCapability.MANAGE_BANKS))
        assertTrue(canRolePerform(UserRole.MASTER, RoleCapability.VIEW_AUDIT))
        assertFalse(canRolePerform(UserRole.MASTER, RoleCapability.SELL_TICKETS))
        assertFalse(canRolePerform(UserRole.MASTER, RoleCapability.DELETE_TICKETS))

        assertTrue(canRolePerform(UserRole.ADMIN, RoleCapability.SELL_TICKETS))
        assertTrue(canRolePerform(UserRole.ADMIN, RoleCapability.SELL_RECHARGES))
        assertTrue(canRolePerform(UserRole.ADMIN, RoleCapability.PAY_TICKETS))
        assertTrue(canRolePerform(UserRole.ADMIN, RoleCapability.DELETE_TICKETS))
        assertTrue(canRolePerform(UserRole.ADMIN, RoleCapability.MANAGE_USERS))
        assertTrue(canRolePerform(UserRole.ADMIN, RoleCapability.MANAGE_SYSTEM))

        assertTrue(canRolePerform(UserRole.SUPERVISOR, RoleCapability.VIEW_ASSIGNED_OPERATIONS))
        assertTrue(canRolePerform(UserRole.SUPERVISOR, RoleCapability.VIEW_REPORTS))
        assertFalse(canRolePerform(UserRole.SUPERVISOR, RoleCapability.SELL_TICKETS))
        assertFalse(canRolePerform(UserRole.SUPERVISOR, RoleCapability.SELL_RECHARGES))
        assertFalse(canRolePerform(UserRole.SUPERVISOR, RoleCapability.PAY_TICKETS))
        assertFalse(canRolePerform(UserRole.SUPERVISOR, RoleCapability.DELETE_TICKETS))
        assertFalse(canRolePerform(UserRole.SUPERVISOR, RoleCapability.DUPLICATE_TICKETS))

        assertTrue(canRolePerform(UserRole.CASHIER, RoleCapability.SELL_TICKETS))
        assertTrue(canRolePerform(UserRole.CASHIER, RoleCapability.SELL_RECHARGES))
        assertTrue(canRolePerform(UserRole.CASHIER, RoleCapability.PAY_TICKETS))
        assertTrue(canRolePerform(UserRole.CASHIER, RoleCapability.DUPLICATE_TICKETS))
        assertTrue(canRolePerform(UserRole.CASHIER, RoleCapability.DELETE_OWN_RECENT_TICKET))
        assertFalse(canRolePerform(UserRole.CASHIER, RoleCapability.DELETE_TICKETS))
        assertFalse(canRolePerform(UserRole.CASHIER, RoleCapability.MANAGE_USERS))
    }

    @Test
    fun `native home intent never depends on class for name lookup`() {
        assertEquals(
            "com.lotterynet.pro.ui.sales.SalesActivity",
            safeNativeHomeActivityClassName(UserRole.ADMIN),
        )
        assertEquals(
            "com.lotterynet.pro.ui.sales.SalesActivity",
            safeNativeHomeActivityClassName(UserRole.CASHIER),
        )
        assertEquals(
            "com.lotterynet.pro.ui.master.MasterDashboardActivity",
            safeNativeHomeActivityClassName(UserRole.MASTER),
        )
        assertEquals(
            "com.lotterynet.pro.ui.login.LoginActivity",
            safeNativeHomeActivityClassName(UserRole.UNKNOWN),
        )
        assertEquals(
            "com.lotterynet.pro.ui.shell.ShellActivity",
            safeNativeHomeActivityClassName(UserRole.SUPERVISOR),
        )
    }

    @Test
    fun `critical production screens stay portrait until adaptive layouts are implemented`() {
        val manifest = File(System.getProperty("user.dir"), "src/main/AndroidManifest.xml").readText()
        val portraitLockedActivities = portraitLockedManifestActivities(manifest)

        val criticalScreens = setOf(
            "com.lotterynet.pro.ui.sales.SalesActivity",
            "com.lotterynet.pro.ui.results.ResultsActivity",
            "com.lotterynet.pro.ui.tickets.TicketSummaryActivity",
            "com.lotterynet.pro.ui.tickets.TicketLookupActivity",
            "com.lotterynet.pro.ui.tickets.TicketDetailActivity",
            "com.lotterynet.pro.ui.tickets.TicketOfficialActivity",
            "com.lotterynet.pro.ui.finance.FinanceActivity",
            "com.lotterynet.pro.ui.finance.FinanceReportsActivity",
            "com.lotterynet.pro.ui.report.OperationalReportActivity",
        )

        assertEquals(
            "Critical POS/work screens must stay portrait until they have explicit tablet/landscape layouts",
            criticalScreens,
            criticalScreens.intersect(portraitLockedActivities),
        )
    }

    private fun portraitLockedManifestActivities(manifest: String): Set<String> {
        return Regex("""<activity\b(?:(?!</activity>|/>).)*(?:/>|</activity>)""", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(manifest)
            .mapNotNull { match ->
                val block = match.value
                val name = Regex("""android:name="([^"]+)"""").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val orientation = Regex("""android:screenOrientation="([^"]+)"""").find(block)?.groupValues?.get(1)
                if (orientation == "portrait") {
                    if (name.startsWith(".ui.")) "com.lotterynet.pro$name" else name
                } else {
                    null
                }
            }
            .toSet()
    }

    private fun declaredManifestActivities(): Set<String> {
        val manifest = File(System.getProperty("user.dir"), "src/main/AndroidManifest.xml").readText()
        return Regex("""android:name="([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .filter { it.startsWith(".ui.") }
            .map { "com.lotterynet.pro$it" }
            .toSet()
    }
}
