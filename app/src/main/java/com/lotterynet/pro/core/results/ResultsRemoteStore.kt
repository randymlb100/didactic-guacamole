package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.model.LotteryResult

interface ResultsRemoteStore {
    fun fetchResultsForDate(date: String, forceLive: Boolean = false): List<LotteryResult>
}
