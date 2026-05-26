package com.lotterynet.pro.ui.navigation

import android.content.Context
import android.content.Intent
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.login.LoginActivity
import com.lotterynet.pro.ui.master.MasterDashboardActivity
import com.lotterynet.pro.ui.sales.SalesActivity
import com.lotterynet.pro.ui.shell.ShellActivity

enum class NativeHomeRoute {
    SALES,
    SHELL_MENU,
    MASTER_DASHBOARD,
    LOGIN,
}

fun resolveNativeHomeRoute(role: UserRole): NativeHomeRoute {
    return when (role) {
        UserRole.CASHIER -> NativeHomeRoute.SALES
        UserRole.ADMIN -> NativeHomeRoute.SALES
        UserRole.SUPERVISOR -> NativeHomeRoute.SHELL_MENU
        UserRole.MASTER -> NativeHomeRoute.MASTER_DASHBOARD
        else -> NativeHomeRoute.LOGIN
    }
}

fun resolveNativeHomeActivityClassName(route: NativeHomeRoute): String {
    return when (route) {
        NativeHomeRoute.SALES -> "com.lotterynet.pro.ui.sales.SalesActivity"
        NativeHomeRoute.SHELL_MENU -> "com.lotterynet.pro.ui.shell.ShellActivity"
        NativeHomeRoute.MASTER_DASHBOARD -> "com.lotterynet.pro.ui.master.MasterDashboardActivity"
        NativeHomeRoute.LOGIN -> "com.lotterynet.pro.ui.login.LoginActivity"
    }
}

fun nativeHomeIntent(context: Context, route: NativeHomeRoute): Intent {
    return when (route) {
        NativeHomeRoute.SALES -> Intent(context, SalesActivity::class.java)
        NativeHomeRoute.SHELL_MENU -> Intent(context, ShellActivity::class.java)
        NativeHomeRoute.MASTER_DASHBOARD -> Intent(context, MasterDashboardActivity::class.java)
        NativeHomeRoute.LOGIN -> Intent(context, LoginActivity::class.java)
    }
}
