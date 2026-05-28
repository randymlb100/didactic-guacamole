package com.lotterynet.pro.core.operations

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAccountOrderingTest {
    @Test
    fun `cashier sorting follows cashier number even when display name changes`() {
        val cashiers = listOf(
            cashier(id = "CAJ-10", user = "banca10", displayName = "Ana"),
            cashier(id = "CAJ-02", user = "banca02", displayName = "Zuleika"),
            cashier(id = "CAJ-01", user = "banca01", displayName = "Ramon"),
        )

        val sorted = sortCashierAccountsNatural(cashiers)

        assertEquals(listOf("banca01", "banca02", "banca10"), sorted.map { it.user })
    }

    @Test
    fun `cashier number ignores renamed display label`() {
        val renamed = cashier(id = "CAJ-04", user = "ramonc04", displayName = "Principal")

        assertEquals(4, naturalCashierNumber(renamed))
    }

    private fun cashier(id: String, user: String, displayName: String): UserAccount {
        return UserAccount(
            id = id,
            user = user,
            displayName = displayName,
            role = UserRole.CASHIER,
        )
    }
}
