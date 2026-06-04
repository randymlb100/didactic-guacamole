package com.lotterynet.pro.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class PickPlayMode {
    STRAIGHT,
    BOX,
}

data class SaleDraft(
    val selectedLotteryIds: List<String> = emptyList(),
    val secondaryLotteryId: String? = null,
    val numberInput: String = "",
    val amountInput: String = "",
    val classicMode: String = "Q",
    val pickMode: PickPlayMode = PickPlayMode.STRAIGHT,
    val superPaleEnabled: Boolean = false,
)

data class SaleDraftSnapshot(
    val draft: SaleDraft = SaleDraft(),
    val stagedRows: List<SaleStagedRow> = emptyList(),
    val savedAtEpochMs: Long = System.currentTimeMillis(),
)

data class SaleResolvedPlay(
    val playType: String,
    val label: String,
    val normalizedNumber: String,
    val displayNumber: String,
    val splitNumbers: List<String> = emptyList(),
)

data class SaleInputHint(
    val main: String,
    val sub: String,
)

data class SaleValidationResult(
    val isValid: Boolean,
    val resolvedPlay: SaleResolvedPlay? = null,
    val normalizedAmount: Double? = null,
    val errorMessage: String? = null,
)

data class SaleStagedRow(
    val id: String = UUID.randomUUID().toString(),
    val lotteryId: String,
    val lotteryName: String,
    val secondaryLotteryId: String? = null,
    val secondaryLotteryName: String? = null,
    val playType: String,
    val label: String,
    val number: String,
    val displayNumber: String,
    val amount: Double,
)

data class PlayItem(
    val number: String,
    val playType: String,
    val amount: Double,
    val lotteryId: String? = null,
    val lotteryName: String? = null,
    val secondaryLotteryId: String? = null,
    val secondaryLotteryName: String? = null,
    val straightDigits: String? = null,
    val boxDigits: String? = null,
)

data class WinningPlayDetail(
    val lotteryName: String,
    val playType: String,
    val playedNumber: String,
    val resultNumber: String,
    val hitPosition: String,
    val amount: Double,
    val payoutAmount: Double,
)

data class TicketRecord(
    val id: String,
    val serial: String? = null,
    val securityCode: String? = null,
    val sellerId: String? = null,
    val sellerUser: String? = null,
    val adminId: String? = null,
    val adminUser: String? = null,
    val role: UserRole = UserRole.UNKNOWN,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val drawDateKey: String? = null,
    val plays: List<PlayItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val totalPrize: Double = 0.0,
    val winningDetails: List<WinningPlayDetail> = emptyList(),
    val status: String = "active",
    val note: String? = null,
)

fun TicketRecord.effectiveDrawDateKey(): String {
    return drawDateKey?.takeIf { it.isNotBlank() } ?: dominicanDayKey(createdAtEpochMs)
}

fun TicketRecord.isPaidStatus(): Boolean = isPaidTicketStatus(status)

fun TicketRecord.isPendingWinnerStatus(): Boolean = isPendingWinnerTicketStatus(status)

fun isPaidTicketStatus(status: String): Boolean {
    return when (status.trim().lowercase(Locale.US)) {
        "paid",
        "pagado",
        "paid_out",
        "payout",
        "cobrado",
        "premio_pagado" -> true
        else -> false
    }
}

fun isPendingWinnerTicketStatus(status: String): Boolean {
    return when (status.trim().lowercase(Locale.US)) {
        "winner",
        "ganador",
        "pending_winner",
        "premio_pendiente",
        "pendiente_pago" -> true
        else -> false
    }
}

fun dominicanDayKey(epochMs: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}

data class DeletedTicketRef(
    val id: String,
    val dayKey: String,
    val deletedAtEpochMs: Long = System.currentTimeMillis(),
    val total: Double = 0.0,
    val sellerId: String? = null,
    val sellerUser: String? = null,
    val adminId: String? = null,
    val adminUser: String? = null,
    val role: UserRole = UserRole.UNKNOWN,
)

data class ExposureSummary(
    val sold: Double = 0.0,
    val soldByActor: Double = 0.0,
    val pending: Double = 0.0,
    val pendingByActor: Double = 0.0,
)
