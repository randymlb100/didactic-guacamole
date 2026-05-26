package com.lotterynet.pro.ui.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeContractsTest {
    @Test
    fun `small app text is black weight for readability`() {
        assertEquals(FontWeight.Black, LotteryNetTypography.bodySmall.fontWeight)
        assertEquals(FontWeight.Black, LotteryNetTypography.labelSmall.fontWeight)
        assertEquals(FontWeight.Black, LotteryNetTypography.labelMedium.fontWeight)
    }

    @Test
    fun `material secondary text is not gray`() {
        assertEquals(Ink, InkSoft)
    }
}
