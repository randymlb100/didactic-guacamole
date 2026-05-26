package com.lotterynet.pro.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun `whole money keeps cents only when needed`() {
        assertEquals("$ 200.89", formatWholeMoney(200.89))
        assertEquals("$ 0.99", formatWholeMoney(0.99))
        assertEquals("$ -25.90", formatWholeMoney(-25.90))
        assertEquals("$ 25", formatWholeMoney(25.0))
    }

    @Test
    fun `whole amount omits currency but keeps cents when needed`() {
        assertEquals("200.89", formatWholeAmount(200.89))
        assertEquals("0.99", formatWholeAmount(0.99))
        assertEquals("-25.90", formatWholeAmount(-25.90))
        assertEquals("25", formatWholeAmount(25.0))
    }

    @Test
    fun `signed whole money keeps explicit sign`() {
        assertEquals("+$ 25.90", formatSignedWholeMoney(25.90))
        assertEquals("-$ 25.90", formatSignedWholeMoney(-25.90))
        assertEquals("+$ 0", formatSignedWholeMoney(0.0))
    }
}
