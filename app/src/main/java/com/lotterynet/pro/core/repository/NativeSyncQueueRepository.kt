package com.lotterynet.pro.core.repository

import org.json.JSONObject

interface NativeSyncQueueRepository {
    fun enqueue(ticketJson: JSONObject)
    fun peekAll(): List<JSONObject>
    fun removeByIds(ids: Collection<String>)
}
