package com.lotterynet.pro.core.model

data class OfficialTicketVisualSpec(
    val templateVersion: String,
    val canvasWidthPx: Int,
    val renderScale: Int,
    val headerHeightPx: Int,
    val metaHeightPx: Int,
    val totalBarHeightPx: Int,
    val qrSectionHeightPx: Int,
    val footerHeightPx: Int,
    val primaryNavy: String,
    val secondaryNavy: String,
    val accentGold: String,
    val accentGoldSoft: String,
    val surface: String,
    val surfaceAlt: String,
    val outline: String,
)

data class TicketQrPlayPayload(
    val type: String,
    val number: String,
    val amount: Double,
    val lotteryName: String = "",
    val secondaryLotteryName: String = "",
)

data class TicketQrPayload(
    val version: Int = 2,
    val id: String,
    val banca: String,
    val lots: String,
    val date: String,
    val total: Double,
    val securityCode: String = "",
    val plays: List<TicketQrPlayPayload> = emptyList(),
)

data class ShareEnvelope(
    val title: String,
    val fileName: String? = null,
    val text: String = "",
)

data class ResultsVisualSpec(
    val templateVersion: String,
    val pageWidthPx: Int,
    val primaryBackground: String,
    val cardBackground: String,
    val titleColor: String,
    val accentColor: String,
    val outlineColor: String,
)

data class ResultShareRow(
    val displayName: String,
    val first: String,
    val second: String,
    val third: String,
    val pick3: String? = null,
    val pick4: String? = null,
    val source: String? = null,
    val accentColor: String? = null,
    val logoAssetPath: String? = null,
    val drawTimeLabel: String? = null,
    val stateLabel: String? = null,
)

data class ResultsSharePayload(
    val bancaName: String,
    val dateLabel: String,
    val rows: List<ResultShareRow> = emptyList(),
    val bancaLogoUri: String = "",
)
