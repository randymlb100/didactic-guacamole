package com.lotterynet.pro.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShareIntentContractsTest {

    @Test
    fun `whatsapp action uses system chooser instead of forcing a package`() {
        val policy = NativeBitmapExport.resolveShareTargetPolicy(whatsappOnly = true)

        assertEquals("Elige donde compartir", policy.chooserTitle)
        assertFalse(policy.forceWhatsAppPackage)
    }
}
