package com.lotterynet.pro.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PosModeContractsTest {
    @Test
    fun `manual pos mode forces tight layout for integrated printer devices`() {
        assertEquals(
            LotteryNetWindowMode.POS_TIGHT,
            resolveEffectiveLotteryNetWindowMode(
                automaticMode = LotteryNetWindowMode.WIDE,
                manualPosModeEnabled = true,
            ),
        )
        assertEquals(
            LotteryNetWindowMode.TABLET,
            resolveEffectiveLotteryNetWindowMode(
                automaticMode = LotteryNetWindowMode.TABLET,
                manualPosModeEnabled = false,
            ),
        )
    }

    @Test
    fun `pos lite keeps q2i class screens in compact single column mode`() {
        val contract = resolvePosLiteViewportContract(
            widthDp = 360,
            heightDp = 640,
            forcedPosLite = true,
        )

        assertEquals(LotteryNetWindowMode.POS_TIGHT, contract.windowMode)
        assertEquals(true, contract.singleColumn)
        assertEquals(true, contract.collapseSecondaryActions)
    }
}
