package com.lotterynet.pro.core.storage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RechargeLimitRepositoryContractsTest {

    @Test
    fun `recharge limits decode owner payload from server`() {
        val decoded = decodeRechargeLimitSettingsPayload(
            """{"globalPerTx":350,"masterPerTx":900}""",
        )

        assertEquals(350.0, decoded?.globalPerTx ?: 0.0, 0.001)
        assertEquals(900.0, decoded?.masterPerTx ?: 0.0, 0.001)
    }

    @Test
    fun `recharge limits keep legacy master payload compatible`() {
        val decoded = decodeRechargeLimitSettingsPayload(JSONObject("""{"recarga":500}"""))

        assertEquals(0.0, decoded?.globalPerTx ?: -1.0, 0.001)
        assertEquals(500.0, decoded?.masterPerTx ?: 0.0, 0.001)
    }

    @Test
    fun `invalid recharge limit payload is ignored`() {
        assertNull(decodeRechargeLimitSettingsPayload("""{"other":10}"""))
    }
}
