package com.lotterynet.pro.ui.navigation

import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRoutingContractsTest {
    @Test
    fun `cashier route is native sales`() {
        val route = resolveNativeHomeRoute(UserRole.CASHIER)

        assertEquals(NativeHomeRoute.SALES, route)
    }

    @Test
    fun `admin enters native sales and master keeps master dashboard`() {
        assertEquals(NativeHomeRoute.SALES, resolveNativeHomeRoute(UserRole.ADMIN))
        assertEquals(NativeHomeRoute.MASTER_DASHBOARD, resolveNativeHomeRoute(UserRole.MASTER))
    }

    @Test
    fun `native route resolves activity class names`() {
        assertEquals(
            "com.lotterynet.pro.ui.sales.SalesActivity",
            resolveNativeHomeActivityClassName(NativeHomeRoute.SALES),
        )
        assertEquals(
            "com.lotterynet.pro.ui.master.MasterDashboardActivity",
            resolveNativeHomeActivityClassName(NativeHomeRoute.MASTER_DASHBOARD),
        )
    }

    @Test
    fun `normal native tabs stay in native routes`() {
        val routes = listOf(
            NativeHomeRoute.SALES,
            NativeHomeRoute.MASTER_DASHBOARD,
        )

        assertTrue(routes.all { it != NativeHomeRoute.LOGIN })
    }
}
