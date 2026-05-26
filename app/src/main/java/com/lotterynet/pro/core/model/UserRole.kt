package com.lotterynet.pro.core.model

enum class UserRole {
    MASTER,
    ADMIN,
    SUPERVISOR,
    CASHIER,
    UNKNOWN;

    companion object {
        fun fromRaw(value: String?): UserRole {
            return when (value?.trim()?.lowercase()) {
                "master" -> MASTER
                "admin" -> ADMIN
                "supervisor" -> SUPERVISOR
                "supervisor/a" -> SUPERVISOR
                "cashier" -> CASHIER
                else -> UNKNOWN
            }
        }
    }
}
