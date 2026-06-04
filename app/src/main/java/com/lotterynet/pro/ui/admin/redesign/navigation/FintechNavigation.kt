package com.lotterynet.pro.ui.admin.redesign.navigation

enum class FintechScreen(val route: String) {
    DASHBOARD("dashboard"),
    USERS("users"),
    BANCAS("bancas"),
    REPORTS("reports"),
    LIMITS("limits");

    companion object {
        fun fromRoute(route: String?): FintechScreen {
            return values().firstOrNull { it.route == route?.trim()?.lowercase() } ?: DASHBOARD
        }
    }
}
