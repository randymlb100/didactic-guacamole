package com.lotterynet.pro.core.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryLogoBitmapLoaderTest {
    @Test
    fun `wide logo target size is capped for list scroll`() {
        val size = LotteryLogoBitmapLoader.resolveTargetSize(
            sourceWidth = 1200,
            sourceHeight = 600,
        )

        assertEquals(192, size.width)
        assertEquals(96, size.height)
    }

    @Test
    fun `small logo target size is not enlarged`() {
        val size = LotteryLogoBitmapLoader.resolveTargetSize(
            sourceWidth = 80,
            sourceHeight = 40,
        )

        assertEquals(80, size.width)
        assertEquals(40, size.height)
    }

    @Test
    fun `sample size uses power of two downsampling`() {
        assertEquals(
            4,
            LotteryLogoBitmapLoader.resolveInSampleSize(
                sourceWidth = 1200,
                sourceHeight = 600,
            ),
        )
    }

    @Test
    fun `transparent logo padding is trimmed before display`() {
        val bounds = LotteryLogoBitmapLoader.findVisibleLogoBoundsValues(
            width = 10,
            height = 8,
            alphaAt = { x, y -> if (x in 2..7 && y in 1..5) 255 else 0 },
        )

        assertEquals(2, bounds?.left)
        assertEquals(1, bounds?.top)
        assertEquals(8, bounds?.right)
        assertEquals(6, bounds?.bottom)
    }

    @Test
    fun `logo disk cache file name is stable and safe`() {
        assertEquals(
            "v2-lot-logos-us-pick-pick3-ny.svg.png",
            LotteryLogoBitmapLoader.cacheFileNameForAsset("lot-logos/us-pick/pick3/ny.svg"),
        )
    }

    @Test
    fun `logo prewarm paths ignore blanks and duplicates`() {
        assertEquals(
            listOf(
                "lot-logos/normal/ny.png",
                "lot-logos/us-pick/pick3/ny.svg",
            ),
            LotteryLogoBitmapLoader.normalizeLogoAssetPaths(
                listOf(
                    "lot-logos/normal/ny.png",
                    "",
                    null,
                    "lot-logos/normal/ny.png",
                    " lot-logos/us-pick/pick3/ny.svg ",
                ),
            ),
        )
    }

    @Test
    fun `every catalog lottery has a bundled local logo asset`() {
        val assetRoot = File("src/main/assets")
        val missing = StaticLotteryCatalogRepository().getAllLotteries()
            .filter { it.logoAssetPath.isNullOrBlank() || !File(assetRoot, it.logoAssetPath.orEmpty()).isFile }
            .map { "${it.id}:${it.name}:${it.logoAssetPath}" }

        assertTrue("Missing lottery logos: $missing", missing.isEmpty())
    }
}
