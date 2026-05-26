package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.PresenceState

interface PresenceRepository {
    fun getPresence(user: String): PresenceState?
    fun savePresence(state: PresenceState)
}
