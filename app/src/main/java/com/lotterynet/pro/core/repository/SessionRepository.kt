package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SavedLogin
import com.lotterynet.pro.core.model.SessionSnapshot

interface SessionRepository {
    fun getSavedLogin(): SavedLogin?
    fun saveSavedLogin(savedLogin: SavedLogin?)
    fun getActiveSession(): ActiveSession?
    fun saveActiveSession(activeSession: ActiveSession?)
    fun getSessionSnapshot(): SessionSnapshot?
    fun saveSessionSnapshot(snapshot: SessionSnapshot?)
    fun clearSession()
}
