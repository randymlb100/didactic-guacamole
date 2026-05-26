package com.lotterynet.pro.core.permissions

import com.lotterynet.pro.core.model.UserRole

enum class RoleCapability {
    SELL_TICKETS,
    SELL_RECHARGES,
    VIEW_TICKETS,
    DUPLICATE_TICKETS,
    PAY_TICKETS,
    DELETE_TICKETS,
    DELETE_OWN_RECENT_TICKET,
    VIEW_RESULTS,
    VIEW_FINANCE,
    VIEW_REPORTS,
    VIEW_ASSIGNED_OPERATIONS,
    PRINT,
    MANAGE_USERS,
    MANAGE_LIMITS,
    MANAGE_SYSTEM,
    MONITOR_OPERATIONS,
    MANAGE_BANKS,
    VIEW_AUDIT,
    CHECK_UPDATES,
}

fun capabilitiesForRole(role: UserRole): Set<RoleCapability> {
    return when (role) {
        UserRole.MASTER -> setOf(
            RoleCapability.MANAGE_BANKS,
            RoleCapability.VIEW_FINANCE,
            RoleCapability.VIEW_AUDIT,
            RoleCapability.CHECK_UPDATES,
        )
        UserRole.ADMIN -> setOf(
            RoleCapability.SELL_TICKETS,
            RoleCapability.SELL_RECHARGES,
            RoleCapability.VIEW_TICKETS,
            RoleCapability.DUPLICATE_TICKETS,
            RoleCapability.PAY_TICKETS,
            RoleCapability.DELETE_TICKETS,
            RoleCapability.VIEW_RESULTS,
            RoleCapability.VIEW_FINANCE,
            RoleCapability.VIEW_REPORTS,
            RoleCapability.PRINT,
            RoleCapability.MANAGE_USERS,
            RoleCapability.MANAGE_LIMITS,
            RoleCapability.MANAGE_SYSTEM,
            RoleCapability.MONITOR_OPERATIONS,
            RoleCapability.VIEW_AUDIT,
            RoleCapability.CHECK_UPDATES,
        )
        UserRole.SUPERVISOR -> setOf(
            RoleCapability.VIEW_TICKETS,
            RoleCapability.VIEW_RESULTS,
            RoleCapability.VIEW_FINANCE,
            RoleCapability.VIEW_REPORTS,
            RoleCapability.VIEW_ASSIGNED_OPERATIONS,
            RoleCapability.PRINT,
            RoleCapability.MONITOR_OPERATIONS,
            RoleCapability.CHECK_UPDATES,
        )
        UserRole.CASHIER -> setOf(
            RoleCapability.SELL_TICKETS,
            RoleCapability.SELL_RECHARGES,
            RoleCapability.VIEW_TICKETS,
            RoleCapability.DUPLICATE_TICKETS,
            RoleCapability.PAY_TICKETS,
            RoleCapability.DELETE_OWN_RECENT_TICKET,
            RoleCapability.VIEW_RESULTS,
            RoleCapability.VIEW_FINANCE,
            RoleCapability.VIEW_REPORTS,
            RoleCapability.PRINT,
            RoleCapability.CHECK_UPDATES,
        )
        UserRole.UNKNOWN -> emptySet()
    }
}

fun canRolePerform(role: UserRole, capability: RoleCapability): Boolean {
    return capability in capabilitiesForRole(role)
}
