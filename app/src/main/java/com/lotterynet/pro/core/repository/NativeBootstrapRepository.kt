package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.sync.BootstrapResult

interface NativeBootstrapRepository {
    fun bootstrapUsers(): BootstrapResult
}
