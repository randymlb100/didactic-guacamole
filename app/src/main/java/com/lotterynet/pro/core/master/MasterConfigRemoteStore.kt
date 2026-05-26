package com.lotterynet.pro.core.master

interface MasterConfigRemoteStore {
    fun probeAccess()
    fun fetchValue(key: String): Any?
    fun fetchUpdatedAt(key: String): String?
    fun upsertJsonValue(key: String, rawJsonValue: String)
}
