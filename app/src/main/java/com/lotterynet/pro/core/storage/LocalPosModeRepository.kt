package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit

object PosModeStorageKeys {
    const val PREFS_NAME = "lotterynet_pos_mode_v1"
    const val ENABLED_KEY = "manual_pos_mode_enabled"
}

class LocalPosModeRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PosModeStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(PosModeStorageKeys.ENABLED_KEY, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(PosModeStorageKeys.ENABLED_KEY, enabled) }
    }
}
