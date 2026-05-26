package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.RechargeRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class RechargeRepositoryContractsTest {

    @Test
    fun `recharge record persists product type status and provider reference`() {
        val record = RechargeRecord(
            id = "rec-rr-1",
            providerId = "claro",
            providerName = "Claro",
            phoneNumber = "8095550000",
            amount = 150.0,
            productType = "paquetico",
            status = "pending",
            providerReference = "RR-987",
            createdAtEpochMs = 1_777_000_000_000,
        )

        val restored = rechargeJsonToRecord(rechargeRecordToJson(record))

        assertEquals("paquetico", restored.productType)
        assertEquals("pending", restored.status)
        assertEquals("RR-987", restored.providerReference)
    }
}
