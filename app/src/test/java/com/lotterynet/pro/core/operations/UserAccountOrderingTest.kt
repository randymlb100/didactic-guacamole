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

    @Test
    fun `cashier display label prefers human name but not technical id`() {
        val named = cashier(id = "CAJ-AF4874", user = "CAJ-AF4874", displayName = "Moreno")
        val numberedFallback = cashier(id = "CAJ-03", user = "CAJ-03", displayName = "")
        val compactNumberedFallback = cashier(id = "CAJ03", user = "CAJ03", displayName = "")
        val technicalFallback = cashier(id = "CAJ-AF4874", user = "CAJ-AF4874", displayName = "")

        assertEquals("Moreno", cashierDisplayLabel(named))
        assertEquals("Cajero 03", cashierDisplayLabel(numberedFallback))
        assertEquals("Cajero 03", cashierDisplayLabel(compactNumberedFallback))
        assertEquals("Cajero sin nombre", cashierDisplayLabel(technicalFallback))
    }

    @Test
    fun `cashier sorting does not move when display names are alphabetical`() {
        val cashiers = listOf(
            cashier(id = "CAJ-03", user = "bancay03", displayName = "Ana"),
            cashier(id = "CAJ-01", user = "bancay01", displayName = "Zoe"),
            cashier(id = "CAJ-02", user = "bancay02", displayName = "Carlos"),
        )

        val sorted = sortCashierAccountsNatural(cashiers)

        assertEquals(listOf("Zoe", "Carlos", "Ana"), sorted.map(::cashierDisplayLabel))
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
