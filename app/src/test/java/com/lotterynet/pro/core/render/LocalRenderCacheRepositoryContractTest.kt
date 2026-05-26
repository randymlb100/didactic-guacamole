package com.lotterynet.pro.core.render

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRenderCacheRepositoryContractTest {
    @Test
    fun `render cache filename keeps only safe characters`() {
        assertEquals("ticket-abc123.png", renderCacheFileName("ticket:abc/123"))
    }
}
