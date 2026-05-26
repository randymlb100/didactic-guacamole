package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.LotteryCalendarRule
import com.lotterynet.pro.core.model.LotteryCatalogItem

interface LotteryCatalogRepository {
    fun getAllLotteries(): List<LotteryCatalogItem>
    fun getLotteryById(id: String): LotteryCatalogItem?
    fun getLotteryByName(name: String): LotteryCatalogItem?
    fun getCalendarRule(): LotteryCalendarRule
}
