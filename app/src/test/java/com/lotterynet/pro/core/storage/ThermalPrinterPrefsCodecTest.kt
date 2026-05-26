package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalPrinterPrefsCodecTest {

    @Test
    fun `thermal prefs codec preserves component scales`() {
        val prefs = ThermalPrinterPrefs(
            selectedPrinterAddress = " 00:11 ",
            lotteryScale = "large",
            playTypeScale = "compact",
            playNumberScale = "large",
            amountScale = "large",
            securityScale = "compact",
            totalScale = "large",
        )

        val decoded = ThermalPrinterPrefsCodec.decode(ThermalPrinterPrefsCodec.encode(prefs))

        assertEquals("00:11", decoded.selectedPrinterAddress)
        assertEquals("large", decoded.lotteryScale)
        assertEquals("compact", decoded.playTypeScale)
        assertEquals("large", decoded.playNumberScale)
        assertEquals("large", decoded.amountScale)
        assertEquals("compact", decoded.securityScale)
        assertEquals("large", decoded.totalScale)
    }

    @Test
    fun `legacy item scale falls back into play type scale`() {
        val decoded = ThermalPrinterPrefsCodec.decode(
            """{"itemScale":"large","paperWidth":"58"}""",
        )

        assertEquals("large", decoded.itemScale)
        assertEquals("large", decoded.playTypeScale)
        assertEquals("normal", decoded.playNumberScale)
        assertEquals("normal", decoded.amountScale)
    }
}
