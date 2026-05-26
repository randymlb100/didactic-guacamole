package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.PrizeTableConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class CashierPrizePayoutRepositoryTest {
    @Test
    fun `cashier prize payload stores default and user override separately`() {
        val defaultConfig = PrizeTableConfig(q1 = 70, pale12 = 90, pale13 = 80, pale23 = 60, tripleta3 = 400, tripleta2 = 40)
        val userConfig = PrizeTableConfig(q1 = 75, pale12 = 120, pale13 = 100, pale23 = 70, tripleta3 = 500, tripleta2 = 50)

        val withDefault = buildCashierPrizePayoutPayloadWithDefault(null, defaultConfig)
        val withUser = buildCashierPrizePayoutPayloadWithUser(withDefault, "cajero01", userConfig)

        assertEquals(defaultConfig, decodeCashierPrizeDefaultPayout(withUser))
        assertEquals(userConfig, decodeCashierPrizeUserPayout(withUser, "cajero01"))
        assertEquals(defaultConfig, decodeCashierPrizeUserPayout(withUser, "sin-ajuste"))
    }

    @Test
    fun `cashier prize payload falls back to global config when payload is missing`() {
        val global = PrizeTableConfig(q1 = 65, pale12 = 88, tripleta2 = 22)

        assertEquals(global, decodeCashierPrizeDefaultPayout(null, global))
        assertEquals(global, decodeCashierPrizeUserPayout(null, "cajero01", global))
    }

    @Test
    fun `legacy pale payout payload is normalized on read`() {
        val payload = """
            {
              "defaults": {
                "pale": 100000,
                "pale12": 100000,
                "pale13": 100000,
                "pale23": 100000
              }
            }
        """.trimIndent()

        val config = decodeCashierPrizeDefaultPayout(payload)

        assertEquals(1000, config.pale)
        assertEquals(1000, config.pale12)
        assertEquals(1000, config.pale13)
        assertEquals(1000, config.pale23)
    }
}
