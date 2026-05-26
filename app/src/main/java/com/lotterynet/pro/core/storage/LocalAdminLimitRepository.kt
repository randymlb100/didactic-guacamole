package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit

data class AdminOperationalLimits(
    val cashierPayoutLimit: Double = 0.0,
)

class LocalAdminLimitRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(AdminLimitStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getLimits(): AdminOperationalLimits {
        return AdminOperationalLimits(
            cashierPayoutLimit = prefs.getFloat(AdminLimitStorageKeys.CASHIER_PAYOUT_LIMIT_KEY, 0f).toDouble().coerceAtLeast(0.0),
        )
    }

    fun saveLimits(limits: AdminOperationalLimits) {
        prefs.edit {
            putFloat(AdminLimitStorageKeys.CASHIER_PAYOUT_LIMIT_KEY, limits.cashierPayoutLimit.toFloat())
        }
    }
}
