package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.model.UserAccount
import kotlin.math.roundToLong

data class MasterRechargeFundServerReceipt(
    val requestedAmount: Double,
    val persistedAmount: Double,
    val updatedAt: String?,
)

sealed interface MasterRechargeFundUpdateResult {
    data class Confirmed(
        val account: UserAccount,
        val receipt: MasterRechargeFundServerReceipt,
    ) : MasterRechargeFundUpdateResult

    data class Rejected(
        val restoredAccount: UserAccount,
        val receipt: MasterRechargeFundServerReceipt,
    ) : MasterRechargeFundUpdateResult

    data class RolledBack(
        val restoredAccount: UserAccount,
        val error: Throwable,
    ) : MasterRechargeFundUpdateResult
}

class MasterRechargeFundUpdateCoordinator(
    private val writeLocal: (UserAccount) -> Unit,
    private val writeRemote: (accountId: String, enabled: Boolean, amount: Double) -> MasterRechargeFundServerReceipt,
) {
    fun update(
        current: UserAccount,
        enabled: Boolean,
        amount: Double,
    ): MasterRechargeFundUpdateResult {
        val normalizedAmount = amount.coerceAtLeast(0.0)
        val optimistic = current.copy(
            rechargesEnabled = enabled,
            rechargesAssignedBalance = normalizedAmount,
            rechargesBalance = normalizedAmount,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        writeLocal(optimistic)

        return runCatching {
            writeRemote(current.id, enabled, normalizedAmount)
        }.fold(
            onSuccess = { receipt ->
                if (sameMoney(receipt.requestedAmount, normalizedAmount) &&
                    sameMoney(receipt.persistedAmount, normalizedAmount)
                ) {
                    MasterRechargeFundUpdateResult.Confirmed(optimistic, receipt)
                } else {
                    writeLocal(current)
                    MasterRechargeFundUpdateResult.Rejected(current, receipt)
                }
            },
            onFailure = { error ->
                writeLocal(current)
                MasterRechargeFundUpdateResult.RolledBack(current, error)
            },
        )
    }
}

class MasterRechargeBalanceUpdateCoordinator(
    private val writeLocal: (UserAccount) -> Unit,
    private val writeRemote: (accountId: String, amount: Double) -> MasterRechargeFundServerReceipt,
) {
    fun add(current: UserAccount, amount: Double): MasterRechargeFundUpdateResult {
        val normalizedAmount = money(amount)
        val requestedBalance = money(current.rechargesBalance + normalizedAmount)
        val optimistic = current.copy(
            rechargesBalance = requestedBalance,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        writeLocal(optimistic)
        return runCatching { writeRemote(current.id, requestedBalance) }.fold(
            onSuccess = { receipt ->
                if (sameMoney(receipt.persistedAmount, requestedBalance)) {
                    MasterRechargeFundUpdateResult.Confirmed(optimistic, receipt)
                } else {
                    writeLocal(current)
                    MasterRechargeFundUpdateResult.Rejected(current, receipt)
                }
            },
            onFailure = { error ->
                writeLocal(current)
                MasterRechargeFundUpdateResult.RolledBack(current, error)
            },
        )
    }
}

internal fun sameMoney(left: Double, right: Double): Boolean {
    return left.isFinite() &&
        right.isFinite() &&
        (left * 100.0).roundToLong() == (right * 100.0).roundToLong()
}
