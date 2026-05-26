package com.lotterynet.pro.core.model

private const val DEFAULT_PALE_PAYOUT = 1000
private const val LEGACY_PALE_PAYOUT = 100000

data class PrizeTableConfig(
    val q1: Int = 60,
    val q2: Int = 12,
    val q3: Int = 4,
    val pale: Int = DEFAULT_PALE_PAYOUT,
    val pale12: Int = pale,
    val pale13: Int = pale,
    val pale23: Int = pale,
    val tripleta: Int = 20000,
    val tripleta3: Int = tripleta,
    val tripleta2: Int = 1000,
    val superPale: Int = 3000,
    val pick3Straight: Int = 500,
    val pick3Box3: Int = 160,
    val pick3Box6: Int = 80,
    val pick4Straight: Int = 5000,
    val pick4Box4: Int = 1200,
    val pick4Box6: Int = 800,
    val pick4Box12: Int = 400,
    val pick4Box24: Int = 200,
    val pick3BackPair: Int = 50,
    val pick4BackPair: Int = 50,
)

fun PrizeTableConfig.normalizedPrizeTableConfig(): PrizeTableConfig {
    fun normalizePale(value: Int): Int = when (value) {
        LEGACY_PALE_PAYOUT -> DEFAULT_PALE_PAYOUT
        else -> value
    }
    return copy(
        pale = normalizePale(pale),
        pale12 = normalizePale(pale12),
        pale13 = normalizePale(pale13),
        pale23 = normalizePale(pale23),
    )
}
