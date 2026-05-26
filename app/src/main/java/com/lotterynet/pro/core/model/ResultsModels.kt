package com.lotterynet.pro.core.model

data class LotteryResult(
    val lotteryId: String,
    val lotteryName: String? = null,
    val date: String,
    val first: String? = null,
    val second: String? = null,
    val third: String? = null,
    val pick3: String? = null,
    val pick4: String? = null,
    val source: String? = null,
    val status: String? = null,
    val fetchedAtEpochMs: Long = System.currentTimeMillis(),
    val isManualOverride: Boolean = false,
    val manualEditedBy: String? = null,
    val manualEditedAt: String? = null,
)
