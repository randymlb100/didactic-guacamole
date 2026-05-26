package com.lotterynet.pro.core.catalog

import com.lotterynet.pro.core.model.LotteryCatalogItem
import java.util.Locale

class LotteryAssetResolver {
    fun resolveLogoAssetPath(lotteryId: String): String? {
        return StaticLotteryCatalogRepository.logosById[lotteryId]
            ?: resolveUsPickLogoAssetPath(lotteryId = lotteryId, lotteryType = null)
    }

    fun resolveLogoAssetPath(item: LotteryCatalogItem): String? {
        return item.logoAssetPath
            ?: StaticLotteryCatalogRepository.logosById[item.id]
            ?: resolveUsPickLogoAssetPath(lotteryId = item.id, lotteryType = item.type)
    }

    fun resolveAssetUrl(item: LotteryCatalogItem): String? {
        val path = resolveLogoAssetPath(item) ?: return null
        return "file:///android_asset/$path"
    }

    private fun resolveUsPickLogoAssetPath(
        lotteryId: String,
        lotteryType: String?,
    ): String? {
        val normalizedId = lotteryId.uppercase(Locale.US)
        if (!normalizedId.startsWith("US-P")) return null
        val idParts = normalizedId.split("-")
        val stateCode = idParts.getOrNull(2)
            ?.lowercase(Locale.US)
            ?.takeIf { it.matches(Regex("[a-z]{2}|dc")) }
            ?: return null
        val gameFolder = when {
            normalizedId.startsWith("US-P4-") -> "pick4"
            normalizedId.startsWith("US-P3-") -> "pick3"
            lotteryType?.contains("4") == true -> "pick4"
            lotteryType?.contains("3") == true -> "pick3"
            else -> return null
        }
        return "lot-logos/us-pick/$gameFolder/$stateCode.svg"
    }
}
