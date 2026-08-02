package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.model.UserAccount
import kotlin.math.roundToLong

data class MasterRechargeFundSnapshot(
    val assigned: Double,
    val available: Double,
) {
    val consumed: Double
        get() = money((assigned - available).coerceAtLeast(0.0))
}

internal fun masterRechargeFundSnapshot(account: UserAccount): MasterRechargeFundSnapshot {
    return MasterRechargeFundSnapshot(
        assigned = money(account.rechargesAssignedBalance),
        available = money(account.rechargesBalance),
    )
}

internal fun replaceMasterRechargeFund(
    current: UserAccount,
    enabled: Boolean,
    amount: Double,
): UserAccount {
    val normalized = money(amount)
    return current.copy(
        rechargesEnabled = enabled,
        rechargesAssignedBalance = normalized,
        rechargesBalance = normalized,
        updatedAtEpochMs = System.currentTimeMillis(),
    )
}

internal fun addMasterRechargeBalance(
    current: UserAccount,
    amount: Double,
): UserAccount {
    val normalizedAmount = money(amount)
    return current.copy(
        rechargesBalance = money(current.rechargesBalance + normalizedAmount),
        updatedAtEpochMs = System.currentTimeMillis(),
    )
}

internal fun money(value: Double): Double {
    return (value.coerceAtLeast(0.0) * 100.0).roundToLong() / 100.0
}
