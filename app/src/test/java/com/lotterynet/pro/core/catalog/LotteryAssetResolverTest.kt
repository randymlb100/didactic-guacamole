package com.lotterynet.pro.core.catalog

import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryTerritory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LotteryAssetResolverTest {
    @Test
    fun `all catalog lotteries resolve to local logo assets`() {
        val catalog = StaticLotteryCatalogRepository()
        val resolver = LotteryAssetResolver()
        val assetRoot = File("src/main/assets")

        catalog.getAllLotteries().forEach { lottery ->
            val logo = resolver.resolveLogoAssetPath(lottery)
            assertNotNull("Missing logo for ${lottery.id} ${lottery.name}", logo)
            assertTrue(
                "Logo asset does not exist for ${lottery.id}: $logo",
                File(assetRoot, logo!!).exists(),
            )
        }
    }

    @Test
    fun `haiti bolet draws are available in catalog with local logo`() {
        val catalog = StaticLotteryCatalogRepository()

        val morning = catalog.getLotteryById("27")
        val evening = catalog.getLotteryById("28")

        assertEquals("Haiti Bolet 11:30 AM", morning?.name)
        assertEquals("11:30 AM", morning?.baseDrawTime)
        assertEquals("11:25", morning?.baseCloseTime)
        assertEquals("lot-logos/haiti_bolet.svg", morning?.logoAssetPath)
        assertEquals("Haiti Bolet 6:30 PM", evening?.name)
        assertEquals("6:30 PM", evening?.baseDrawTime)
        assertEquals("18:25", evening?.baseCloseTime)
        assertEquals("lot-logos/haiti_bolet.svg", evening?.logoAssetPath)
    }

    @Test
    fun `dynamic us pick result ids resolve to bundled pick logos`() {
        val resolver = LotteryAssetResolver()

        val pick3 = LotteryCatalogItem(
            id = "US-P3-FL-PICK-3-EVENING",
            name = "Florida Pick 3 Evening",
            type = "Pick3",
            baseDrawTime = "9:48 PM",
            baseCloseTime = "9:40 PM",
            colorHex = "#0ea5e9",
            territory = LotteryTerritory.USA,
        )
        val pick4 = LotteryCatalogItem(
            id = "US-P4-GA-CASH-4-NIGHT",
            name = "Georgia Cash 4 Night",
            type = "Pick4",
            baseDrawTime = "11:34 PM",
            baseCloseTime = "11:25 PM",
            colorHex = "#16a34a",
            territory = LotteryTerritory.USA,
        )

        assertEquals("lot-logos/us-pick/pick3/fl.svg", resolver.resolveLogoAssetPath(pick3))
        assertEquals("lot-logos/us-pick/pick4/ga.svg", resolver.resolveLogoAssetPath(pick4))
    }
}
