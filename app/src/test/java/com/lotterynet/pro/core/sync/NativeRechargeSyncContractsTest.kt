package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.RechargeRecord
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeRechargeSyncContractsTest {

    @Test
    fun `native recharge converts to web compatible payload and parses back`() {
        val recharge = RechargeRecord(
            id = "rec-native-1",
            providerId = "claro",
            providerName = "Claro",
            phoneNumber = "8095550000",
            amount = 150.0,
            productType = "recarga",
            status = "approved",
            providerReference = "RR-123",
            userId = "cashier-1",
            userName = "Caja 1",
            adminId = "admin-1",
            adminUser = "admin",
            createdAtEpochMs = 1_776_000_000_000,
        )

        val payload = JSONArray().put(rechargeRecordToWebCompatibleJson(recharge)).toString()
        val parsed = parseWebRechargesPayload(payload)

        assertEquals(1, parsed.size)
        assertEquals("rec-native-1", parsed.first().id)
        assertEquals("claro", parsed.first().providerId)
        assertEquals("Claro", parsed.first().providerName)
        assertEquals("8095550000", parsed.first().phoneNumber)
        assertEquals(150.0, parsed.first().amount, 0.0)
        assertEquals("recarga", parsed.first().productType)
        assertEquals("approved", parsed.first().status)
        assertEquals("RR-123", parsed.first().providerReference)
        assertEquals("cashier-1", parsed.first().userId)
        assertEquals("admin-1", parsed.first().adminId)
    }

    @Test
    fun `remote recharge merge prefers pending local version before upload`() {
        val remote = listOf(
            RechargeRecord(id = "R-1", amount = 25.0, createdAtEpochMs = 100L),
        )
        val local = listOf(
            RechargeRecord(id = "R-1", amount = 50.0, createdAtEpochMs = 100L),
            RechargeRecord(id = "R-2", amount = 75.0, createdAtEpochMs = 200L),
        )

        val merged = mergeRechargesPreferImported(existing = remote, imported = local)

        assertEquals(listOf("R-2", "R-1"), merged.map { it.id })
        assertEquals(50.0, merged.first { it.id == "R-1" }.amount, 0.0)
    }
}
