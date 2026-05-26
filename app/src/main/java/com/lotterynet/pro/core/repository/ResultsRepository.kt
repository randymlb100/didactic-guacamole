package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.LotteryResult

interface ResultsRepository {
    fun getResultsForDate(date: String): List<LotteryResult>
    fun saveResultsForDate(date: String, results: List<LotteryResult>)
    fun clearResultsForDate(date: String)
}
