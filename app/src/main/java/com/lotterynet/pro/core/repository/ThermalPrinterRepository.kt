package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.ThermalPrinterPrefs

interface ThermalPrinterRepository {
    fun getPrefs(): ThermalPrinterPrefs
    fun savePrefs(prefs: ThermalPrinterPrefs)
    fun applyClassicPreset(): ThermalPrinterPrefs
}
