package com.lotterynet.pro.core.model

data class LotteryCatalogItem(
    val id: String,
    val name: String,
    val type: String,
    val baseDrawTime: String,
    val baseCloseTime: String,
    val colorHex: String,
    val logoAssetPath: String? = null,
    val territory: LotteryTerritory = LotteryTerritory.RD,
    val timeZoneId: String? = null,
    val playCapabilities: LotteryPlayCapabilities = LotteryPlayCapabilities(),
    val sundayOverride: LotterySchedule? = null,
    val standardTimeOverride: LotterySchedule? = null,
    val usesExplicitCloseTime: Boolean = false,
)

data class LotterySchedule(
    val drawTime: String,
    val closeTime: String,
)

data class LotteryPlayCapabilities(
    val supportsStraight: Boolean = false,
    val supportsBox: Boolean = false,
    val supportsQuiniela: Boolean = false,
    val supportsPale: Boolean = false,
    val supportsTripleta: Boolean = false,
    val supportsSuperPale: Boolean = false,
)

data class LotteryCalendarRule(
    val noDrawDatesByLottery: Map<String, Set<String>> = emptyMap(),
    val noDrawAllDates: Set<String> = emptySet(),
    val holidayDisabledDates: Map<String, Set<String>> = emptyMap(),
    val holidayAllDisabledDates: Set<String> = emptySet(),
    val dayDisabledByWeekday: Map<Int, Set<String>> = emptyMap(),
    val americanLotteryIds: Set<String> = emptySet(),
    val dominicanLotteryIds: Set<String> = emptySet(),
)

enum class LotteryTerritory {
    RD,
    USA,
}
