package com.lotterynet.pro.core.users

interface UsersRemoteStore {
    fun fetchUsersPayload(): String?
    fun upsertUsersPayload(payloadJson: String)
}
