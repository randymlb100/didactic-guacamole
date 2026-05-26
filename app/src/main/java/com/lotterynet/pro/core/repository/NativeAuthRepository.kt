package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.ActiveSession

interface NativeAuthRepository {
    fun authenticate(username: String, password: String, remember: Boolean): ActiveSession?
}
