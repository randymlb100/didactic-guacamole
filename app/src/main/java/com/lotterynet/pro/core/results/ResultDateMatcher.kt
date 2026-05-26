package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.model.LotteryResult

fun resultBelongsToDate(result: LotteryResult, requestedDate: String): Boolean {
    val resultDate = result.date.trim()
    if (resultDate.isBlank()) return true
    return normalizeResultDateKey(resultDate) == normalizeResultDateKey(requestedDate)
}

fun normalizeResultDateKey(raw: String): String {
    val parts = raw.trim().split("-")
    if (parts.size != 3) return raw.trim()
    return when {
        parts[0].length == 4 -> "${parts[0]}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}"
        parts[2].length == 4 -> "${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
        else -> raw.trim()
    }
}
