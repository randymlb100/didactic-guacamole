package com.lotterynet.pro.ui.sales

import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryCloseDecision
import com.lotterynet.pro.core.model.PickPlayMode
import com.lotterynet.pro.core.model.CloseState
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.ui.common.ActionTone
import java.security.MessageDigest
import java.util.Locale

internal enum class LotteryPickerTarget {
    PRIMARY,
    SECONDARY,
}

internal enum class CashierStartupWork {
    LOAD_SESSION,
    LOAD_LOCAL_DRAFT,
    LOAD_LOCAL_CATALOG,
    LOAD_LOCAL_LIMITS,
    HYDRATE_REMOTE_TICKETS,
    HYDRATE_REMOTE_LIMITS,
    HYDRATE_REMOTE_USERS,
    FLUSH_SYNC_QUEUE,
    RENDER_TICKET_BITMAP,
}

internal data class CashierStartupPlan(
    val firstFrameWork: Set<CashierStartupWork>,
    val afterFirstFrameWork: Set<CashierStartupWork>,
)

internal data class SaleThermalPrintResult(
    val message: String,
    val closePreview: Boolean,
    val openPrinterSettings: Boolean,
)

internal data class SaleSaveGateDecision(
    val canStartSave: Boolean,
    val message: String? = null,
)

internal data class SaleSubmissionIdentity(
    val clientRequestId: String,
    val fingerprint: String,
    val createdAtEpochMs: Long,
)

internal fun resolveSaleSaveGate(
    isSaveInFlight: Boolean,
    stagedRowCount: Int,
): SaleSaveGateDecision {
    return when {
        isSaveInFlight -> SaleSaveGateDecision(
            canStartSave = false,
            message = "Venta ya está validando. Espera que termine.",
        )
        stagedRowCount <= 0 -> SaleSaveGateDecision(
            canStartSave = false,
            message = "No hay jugadas para guardar",
        )
        else -> SaleSaveGateDecision(canStartSave = true)
    }
}

internal fun buildSaleSubmissionFingerprint(
    adminId: String?,
    adminUser: String?,
    sellerId: String?,
    sellerUser: String?,
    drawDateKey: String,
    rows: List<com.lotterynet.pro.core.model.SaleStagedRow>,
): String {
    val raw = buildString {
        append(adminId.orEmpty().trim()).append('|')
        append(adminUser.orEmpty().trim()).append('|')
        append(sellerId.orEmpty().trim()).append('|')
        append(sellerUser.orEmpty().trim()).append('|')
        append(drawDateKey.trim()).append('|')
        rows.forEach { row ->
            append(row.lotteryId.trim()).append(',')
            append(row.lotteryName.trim()).append(',')
            append(row.secondaryLotteryId.orEmpty().trim()).append(',')
            append(row.secondaryLotteryName.orEmpty().trim()).append(',')
            append(row.playType.trim()).append(',')
            append(row.number.trim()).append(',')
            append("%.2f".format(Locale.US, row.amount)).append(';')
        }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString("") { byte -> "%02x".format(byte) }
}

internal fun resolveSaleSubmissionIdentity(
    current: SaleSubmissionIdentity?,
    nextFingerprint: String,
    nowEpochMs: Long,
): SaleSubmissionIdentity {
    if (current != null && current.fingerprint == nextFingerprint) {
        return current
    }
    return SaleSubmissionIdentity(
        clientRequestId = "native-$nowEpochMs",
        fingerprint = nextFingerprint,
        createdAtEpochMs = nowEpochMs,
    )
}

internal enum class SaleThermalPrintTarget {
    BLUETOOTH,
    INTEGRATED,
    NONE,
}

internal fun resolveSaleThermalPrintTarget(
    hasBluetoothPrinter: Boolean,
    hasIntegratedPrinter: Boolean,
): SaleThermalPrintTarget {
    return resolveSaleThermalPrintTargets(
        hasBluetoothPrinter = hasBluetoothPrinter,
        hasIntegratedPrinter = hasIntegratedPrinter,
    ).first()
}

internal fun resolveSaleThermalPrintTargets(
    hasBluetoothPrinter: Boolean,
    hasIntegratedPrinter: Boolean,
): List<SaleThermalPrintTarget> {
    return when {
        hasIntegratedPrinter && hasBluetoothPrinter -> listOf(
            SaleThermalPrintTarget.BLUETOOTH,
            SaleThermalPrintTarget.INTEGRATED,
        )
        hasIntegratedPrinter -> listOf(SaleThermalPrintTarget.INTEGRATED)
        hasBluetoothPrinter -> listOf(SaleThermalPrintTarget.BLUETOOTH)
        else -> listOf(SaleThermalPrintTarget.NONE)
    }
}

internal fun resolveSaleThermalPrintResult(success: Boolean, message: String): SaleThermalPrintResult {
    return SaleThermalPrintResult(
        message = message,
        closePreview = success,
        openPrinterSettings = false,
    )
}

internal fun shouldRenderSaleDeliveryBitmapPreview(ticket: TicketRecord): Boolean {
    return false
}

internal fun resolveCashierStartupPlan(): CashierStartupPlan {
    return CashierStartupPlan(
        firstFrameWork = setOf(
            CashierStartupWork.LOAD_SESSION,
            CashierStartupWork.LOAD_LOCAL_DRAFT,
            CashierStartupWork.LOAD_LOCAL_CATALOG,
            CashierStartupWork.LOAD_LOCAL_LIMITS,
        ),
        afterFirstFrameWork = setOf(
            CashierStartupWork.HYDRATE_REMOTE_TICKETS,
            CashierStartupWork.HYDRATE_REMOTE_LIMITS,
            CashierStartupWork.HYDRATE_REMOTE_USERS,
            CashierStartupWork.FLUSH_SYNC_QUEUE,
        ),
    )
}

internal data class SuperPaleSecondaryState(
    val visible: Boolean,
    val primaryLotteryId: String? = null,
    val secondaryLotteryId: String? = null,
    val availableSecondaryIds: List<String> = emptyList(),
    val requiresSecondarySelection: Boolean = false,
)

internal data class SuperPaleActivationState(
    val canActivate: Boolean,
    val selection: List<String>,
    val message: String,
)

internal data class QuickActionContract(
    val lotsLabel: String = "Loterias",
    val ligarLabel: String = "Ligar",
    val lotsTone: ActionTone = ActionTone.Danger,
    val ligarTone: ActionTone = ActionTone.IntenseBlue,
    val ligarEnabled: Boolean,
    val superPaleVisible: Boolean,
    val superPaleLabel: String,
)

internal data class LigarAmountPromptState(
    val initialAmount: String,
)

internal fun resolveLigarAmountPromptState(currentAmount: String): LigarAmountPromptState {
    return LigarAmountPromptState(initialAmount = currentAmount.trim())
}

internal fun parseLigarAmountForConfirmation(input: String): Double? {
    val amount = input.trim().replace(',', '.').toDoubleOrNull()
    return amount?.takeIf { it > 0.0 }
}

internal data class VentaLotteryButtonVisualContract(
    val background: String,
    val iconTint: String,
    val labelTint: String,
    val keepCurrentSize: Boolean,
)

internal fun resolveVentaLotteryButtonVisualContract(): VentaLotteryButtonVisualContract {
    return VentaLotteryButtonVisualContract(
        background = "intense-red",
        iconTint = "white",
        labelTint = "white",
        keepCurrentSize = true,
    )
}

internal fun resolveSuperPaleSecondaryState(
    selectedLotteryIds: List<String>,
    availableLotteryIds: List<String>,
    classicMode: String,
): SuperPaleSecondaryState {
    if (classicMode != "SP") {
        return SuperPaleSecondaryState(visible = false)
    }
    val primaryLotteryId = selectedLotteryIds.firstOrNull()
    val secondaryLotteryId = selectedLotteryIds.getOrNull(1)
    return SuperPaleSecondaryState(
        visible = true,
        primaryLotteryId = primaryLotteryId,
        secondaryLotteryId = secondaryLotteryId,
        availableSecondaryIds = availableLotteryIds.filterNot { it == primaryLotteryId },
        requiresSecondarySelection = primaryLotteryId != null && secondaryLotteryId == null,
    )
}

internal fun resolveQuickActionContract(
    supportsPickModes: Boolean,
    classicMode: String,
    pickMode: PickPlayMode,
    canLigar: Boolean,
    canToggleSuperPale: Boolean,
    superPaleEnabled: Boolean,
): QuickActionContract {
    val ligarLabel = when {
        supportsPickModes && pickMode == PickPlayMode.BOX -> "Straight"
        supportsPickModes -> "Box"
        else -> "Ligar"
    }
    val superPaleLabel = if (superPaleEnabled || classicMode == "SP") "SP Activo" else "Super Pale"
    return QuickActionContract(
        ligarEnabled = true,
        ligarLabel = ligarLabel,
        superPaleVisible = canToggleSuperPale,
        superPaleLabel = superPaleLabel,
    )
}

internal fun resolveVisibleClassicMode(
    preferredMode: String,
    number: String,
): String {
    if (preferredMode == "SP") {
        return "SP"
    }
    return when (number.filter(Char::isDigit).length) {
        4 -> "P"
        6 -> "T"
        else -> "Q"
    }
}

internal fun resolveAvailableLotteryIdsForPicker(
    lotteries: List<LotteryCatalogItem>,
    decisionsByLotteryId: Map<String, LotteryCloseDecision>,
    excludedLotteryIds: Set<String> = emptySet(),
): List<String> {
    return lotteries
        .asSequence()
        .filterNot { it.id in excludedLotteryIds }
        .mapNotNull { lottery ->
            val decision = decisionsByLotteryId[lottery.id] ?: return@mapNotNull null
            if (decision.isClosed) return@mapNotNull null
            Triple(lottery.id, parsePickerMinutes(decision.closeTime ?: lottery.baseCloseTime), lottery.name)
        }
        .sortedWith(compareBy<Triple<String, Int, String>>({ it.second }, { it.third }))
        .map { it.first }
        .toList()
}

internal fun resolveSalePublishedResultBlockIds(
    publishedResultLotteryIds: Set<String>,
    decisionsWithoutPublishedResults: Map<String, LotteryCloseDecision>,
): Set<String> {
    return publishedResultLotteryIds.filterTo(linkedSetOf()) { lotteryId ->
        decisionsWithoutPublishedResults[lotteryId]?.isClosed == true
    }
}

internal fun resolveInitialLotterySelection(
    lotteries: List<LotteryCatalogItem>,
    decisionsByLotteryId: Map<String, LotteryCloseDecision>,
    preferredLotteryIds: Set<String> = emptySet(),
): List<String> {
    val preferredLotteries = lotteries.filter { it.id in preferredLotteryIds }
    val preferredId = resolveAvailableLotteryIdsForPicker(
        lotteries = preferredLotteries.ifEmpty { lotteries },
        decisionsByLotteryId = decisionsByLotteryId,
    ).firstOrNull()
        ?: lotteries
            .sortedBy { it.name.lowercase() }
            .firstOrNull()
            ?.id
    return preferredId?.let(::listOf).orEmpty()
}

internal fun resolveLiveLotterySelection(
    currentSelection: List<String>,
    availableLotteryIds: List<String>,
    classicMode: String,
    preferredLotteryIds: Set<String> = emptySet(),
    resetToFirstAvailable: Boolean = false,
): List<String> {
    if (availableLotteryIds.isEmpty()) return emptyList()
    if (resetToFirstAvailable) {
        val preferredAvailable = availableLotteryIds.filter { it in preferredLotteryIds }
        return if (classicMode == "SP") {
            preferredAvailable.ifEmpty { availableLotteryIds }.distinct().take(2)
        } else {
            listOf(preferredAvailable.firstOrNull() ?: availableLotteryIds.first())
        }
    }
    if (classicMode != "SP") {
        val openSelection = currentSelection.filter { it in availableLotteryIds }.distinct()
        val preferredAvailable = availableLotteryIds.filter { it in preferredLotteryIds }
        if (preferredAvailable.isNotEmpty() && openSelection.none { it in preferredAvailable }) {
            return listOf(preferredAvailable.first())
        }
        return if (openSelection.isNotEmpty()) {
            openSelection
        } else {
            listOf(availableLotteryIds.first())
        }
    }

    val currentPrimary = currentSelection.firstOrNull()?.takeIf { it in availableLotteryIds }
    val currentSecondary = currentSelection.getOrNull(1)
        ?.takeIf { it in availableLotteryIds && it != currentPrimary }
    return listOfNotNull(currentPrimary, currentSecondary)
}

internal fun resolveSuperPaleActivationSelection(
    availableLotteryIds: List<String>,
): List<String> {
    return availableLotteryIds.distinct().take(2)
}

internal fun resolveSuperPaleActivationState(
    selectedLotteryIds: List<String>,
): SuperPaleActivationState {
    val selection = selectedLotteryIds.distinct()
    if (selection.size != 2) {
        return SuperPaleActivationState(
            canActivate = false,
            selection = selection,
            message = "Super Pale requiere 2 loterías seleccionadas",
        )
    }
    return SuperPaleActivationState(
        canActivate = true,
        selection = selection.take(2),
        message = "Super Pale activado",
    )
}

internal fun resolveLotteryPickerTargetForLotsButton(
    classicMode: String,
    selectedLotteryIds: List<String>,
): LotteryPickerTarget {
    return LotteryPickerTarget.PRIMARY
}

internal fun resolveNextLotteryPickerTargetAfterSelection(
    classicMode: String,
    nextSelection: List<String>,
    currentTarget: LotteryPickerTarget,
): LotteryPickerTarget? {
    if (classicMode != "SP") return null
    return if (nextSelection.size < 2) LotteryPickerTarget.SECONDARY else null
}

internal fun resolvePostTicketClassicMode(classicMode: String): String {
    return if (classicMode == "SP") "Q" else classicMode
}

internal fun resolvePostTicketLotterySelection(
    currentSelection: List<String>,
    availableLotteryIds: List<String>,
    classicMode: String,
    preferredLotteryIds: Set<String> = emptySet(),
): List<String> {
    if (availableLotteryIds.isEmpty()) return emptyList()
    val preferredAvailable = availableLotteryIds.filter { it in preferredLotteryIds }
    if (classicMode != "SP" && preferredAvailable.isNotEmpty()) {
        val retained = currentSelection.firstOrNull { it in preferredAvailable }
        return listOf(retained ?: preferredAvailable.first())
    }
    return if (classicMode == "SP") {
        availableLotteryIds.take(2)
    } else {
        listOf(availableLotteryIds.first())
    }
}

internal fun preferredPickLotteryIdsForSaleMode(
    lotteries: List<LotteryCatalogItem>,
    config: AdminSystemModeConfig,
): Set<String> {
    return if (config.pickModeEnabled) {
        lotteries.filter(::supportsPickModes).mapTo(linkedSetOf()) { it.id }
    } else {
        emptySet()
    }
}

internal fun presentLotteryDecisionPill(decision: LotteryCloseDecision): String {
    val reason = decision.reason.orEmpty()
    val lowerReason = reason.lowercase(Locale.getDefault())
    return when (decision.state) {
        CloseState.CLOSED -> if (lowerReason.contains("banca")) "Cerrada por banca" else "Esperando resultado"
        CloseState.DANGER -> reason.takeIf { it.contains("min", true) } ?: "Cierra ya"
        CloseState.WARNING -> reason.takeIf { it.contains("min", true) } ?: "Por cerrar"
        CloseState.OPEN -> "Abierta"
    }
}

internal fun presentLotteryDecisionSubtitle(decision: LotteryCloseDecision): String {
    val reason = decision.reason?.takeIf { it.isNotBlank() }.orEmpty()
    val closeTime = decision.closeTime?.takeIf { it.isNotBlank() }?.let(::formatLotteryClock12)
    if (reason.contains("banca", true)) {
        return "La banca cerró este sorteo manualmente para hoy."
    }
    return when (decision.state) {
        CloseState.CLOSED -> closeTime?.let { "Cerró a las $it y queda cerrada hasta que cambie el día." }
            ?: reason.ifBlank { "Cerrada por hoy." }
        CloseState.DANGER,
        CloseState.WARNING -> reason.takeIf { it.contains("min", true) }
            ?: closeTime?.let { "Disponible hasta $it." }
            ?: "Disponible."
        CloseState.OPEN -> reason.ifBlank { closeTime?.let { "Disponible hasta $it." } ?: "Disponible." }
    }
}

internal fun formatLotteryClock12(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return text
    val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""")
        .find(text.uppercase(Locale.US))
        ?: return text
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2]
    val meridiem = match.groupValues.getOrNull(3).orEmpty()
    if (meridiem == "AM" && hour == 12) hour = 0
    if (meridiem == "PM" && hour < 12) hour += 12
    val suffix = if (hour >= 12) "PM" else "AM"
    val hour12 = when (val normalized = hour % 12) {
        0 -> 12
        else -> normalized
    }
    return "$hour12:$minute $suffix"
}

private fun parsePickerMinutes(raw: String): Int {
    val text = raw.trim().uppercase()
    val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""").find(text) ?: return Int.MAX_VALUE
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val meridiem = match.groupValues.getOrNull(3).orEmpty()
    if (meridiem == "AM" && hour == 12) hour = 0
    if (meridiem == "PM" && hour < 12) hour += 12
    return hour * 60 + minute
}
