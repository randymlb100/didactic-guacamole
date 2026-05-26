package com.lotterynet.pro.core.model

data class ThermalPrinterPrefs(
    val selectedPrinterAddress: String = "",
    val paperWidth: String = "58",
    val fontFamily: String = "consolas",
    val typeLabelMode: String = "single",
    val widthMode: String = "standard",
    val customChars: String = "32",
    val density: String = "tight",
    val separator: String = "short",
    val headerScale: String = "compact",
    val serialScale: String = "normal",
    val itemScale: String = "compact",
    val lotteryScale: String = "normal",
    val playTypeScale: String = "compact",
    val playNumberScale: String = "normal",
    val amountScale: String = "normal",
    val securityScale: String = "normal",
    val totalScale: String = "normal",
    val previewZoom: String = "100",
    val showOriginal: Boolean = true,
    val showAddress: Boolean = false,
    val showPhone: Boolean = true,
    val showDateTime: Boolean = true,
    val showDrawTime: Boolean = true,
    val showSecurity: Boolean = true,
    val showFooter: Boolean = false,
)

data class ThermalPreviewPolicy(
    val canPreview: Boolean,
    val canEditPrinterPrefs: Boolean,
)
