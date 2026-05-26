package com.lotterynet.pro.core.sales

import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.PickPlayMode
import com.lotterynet.pro.core.model.SaleDraft
import com.lotterynet.pro.core.model.SaleInputHint
import com.lotterynet.pro.core.model.SaleResolvedPlay
import com.lotterynet.pro.core.model.SaleStagedRow
import com.lotterynet.pro.core.model.SaleValidationResult
import java.util.Locale

class SaleValidator {
    fun detectPlay(
        draft: SaleDraft,
        selectedLotteries: List<LotteryCatalogItem>,
    ): SaleResolvedPlay? {
        if (selectedLotteries.isEmpty()) return null
        val pickInput = resolvePickInput(draft.numberInput, draft.pickMode)
        val digits = pickInput.digits
        if (draft.superPaleEnabled) {
            if (selectedLotteries.size != 2 || digits.length != 4) return null
            val first = digits.take(2)
            val second = digits.drop(2)
            if (first == second) return null
            return SaleResolvedPlay(
                playType = "SP",
                label = "Super Pale",
                normalizedNumber = "$first-$second",
                displayNumber = "$first/$second",
                splitNumbers = listOf(first, second),
            )
        }

        val pickType = selectedPickLotteryType(selectedLotteries)
        if (pickType != null) {
            return detectPickPlay(digits, pickType, pickInput.pickMode)
        }

        return detectClassicPlay(digits, draft.classicMode)
    }

    fun getPartialHint(
        draft: SaleDraft,
        selectedLotteries: List<LotteryCatalogItem>,
    ): SaleInputHint? {
        val pickInput = resolvePickInput(draft.numberInput, draft.pickMode)
        val digits = pickInput.digits
        if (draft.superPaleEnabled) {
            if (digits.isEmpty() || digits.length >= 4) return null
            val missing = 4 - digits.length
            return SaleInputHint(
                main = "SP",
                sub = "faltan $missing dígito" + if (missing == 1) "" else "s",
            )
        }

        val pickType = selectedPickLotteryType(selectedLotteries)
        if (pickType == "Pick3" && digits.length in 1..2) {
            return SaleInputHint(
                main = if (pickInput.pickMode == PickPlayMode.BOX) "BOX" else "STR",
                sub = "3 dígitos · ${pickModeLabel(pickType, pickInput.pickMode)}",
            )
        }
        if (pickType == "Pick4" && digits.length in 1..3) {
            return SaleInputHint(
                main = if (pickInput.pickMode == PickPlayMode.BOX) "BOX" else "STR",
                sub = "4 dígitos · ${pickModeLabel(pickType, pickInput.pickMode)}",
            )
        }
        if (pickType != null) return null

        return when (digits.length) {
            1 -> SaleInputHint(main = "Q", sub = "falta 1 dígito")
            3 -> SaleInputHint(main = "P", sub = "falta 1 dígito")
            5 -> SaleInputHint(main = "T", sub = "falta 1 dígito")
            else -> null
        }
    }

    fun validate(
        draft: SaleDraft,
        selectedLotteries: List<LotteryCatalogItem>,
    ): SaleValidationResult {
        val amount = draft.amountInput.trim().replace(',', '.').toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            return SaleValidationResult(isValid = false, errorMessage = "Escribe un monto válido")
        }
        if (selectedLotteries.isEmpty()) {
            return SaleValidationResult(isValid = false, errorMessage = "Selecciona al menos una lotería")
        }

        val pickInput = resolvePickInput(draft.numberInput, draft.pickMode)
        val digits = pickInput.digits
        val pickType = selectedPickLotteryType(selectedLotteries)
        if (hasCents(amount) && pickType == null) {
            return SaleValidationResult(isValid = false, errorMessage = "Centavos solo permitido en Pick")
        }
        if (draft.superPaleEnabled) {
            if (selectedLotteries.size != 2) {
                return SaleValidationResult(isValid = false, errorMessage = "Super Pale requiere exactamente 2 loterías")
            }
            val play = detectPlay(draft, selectedLotteries)
            if (digits.length != 4) {
                return SaleValidationResult(isValid = false, errorMessage = "Super Pale necesita 4 dígitos")
            }
            if (play == null) {
                return SaleValidationResult(isValid = false, errorMessage = "Super Pale requiere 2 números distintos")
            }
            return SaleValidationResult(
                isValid = true,
                normalizedAmount = amount,
                resolvedPlay = play,
            )
        }

        if (pickType != null) {
            return validatePick(digits, amount, pickType, pickInput.pickMode)
        }

        return validateClassic(digits, amount, draft.classicMode)
    }

    fun mergeIntoRows(
        existing: List<SaleStagedRow>,
        validation: SaleValidationResult,
        selectedLotteries: List<LotteryCatalogItem>,
    ): List<SaleStagedRow> {
        if (!validation.isValid || validation.resolvedPlay == null || validation.normalizedAmount == null) {
            return existing
        }

        val play = validation.resolvedPlay
        return if (play.playType == "SP") {
            val primary = selectedLotteries.first()
            val secondary = selectedLotteries.getOrNull(1)
            mergeRow(
                existing = existing,
                newRow = SaleStagedRow(
                    lotteryId = primary.id,
                    lotteryName = primary.name,
                    secondaryLotteryId = secondary?.id,
                    secondaryLotteryName = secondary?.name,
                    playType = play.playType,
                    label = play.label,
                    number = play.normalizedNumber,
                    displayNumber = play.displayNumber,
                    amount = validation.normalizedAmount,
                ),
            )
        } else {
            selectedLotteries.fold(existing) { rows, lottery ->
                mergeRow(
                    existing = rows,
                    newRow = SaleStagedRow(
                        lotteryId = lottery.id,
                        lotteryName = lottery.name,
                        playType = play.playType,
                        label = play.label,
                        number = play.normalizedNumber,
                        displayNumber = play.displayNumber,
                        amount = validation.normalizedAmount,
                    ),
                )
            }
        }
    }

    fun buildLigarRows(
        existing: List<SaleStagedRow>,
        amount: Double,
        target: LigarBuildTarget = LigarBuildTarget.QUINIELA,
    ): LigarBuildResult {
        val uniqueQuinielas = existing
            .filter { it.playType == "Q" && it.secondaryLotteryId == null }
            .distinctBy { "${it.lotteryId}|${it.number}" }

        val byLottery = uniqueQuinielas.groupBy { it.lotteryId }
        var merged = existing
        var generated = emptyList<SaleStagedRow>()
        var quinielaCount = 0
        var paleCount = 0
        var tripletaCount = 0
        var blockedCount = 0

        if (target == LigarBuildTarget.QUINIELA) {
            existing
                .filter { it.playType == "P" || it.playType == "T" }
                .filter { it.secondaryLotteryId == null }
                .forEach { source ->
                    source.number.chunked(2).filter { it.length == 2 }.distinct().forEach { number ->
                        val candidate = SaleStagedRow(
                            lotteryId = source.lotteryId,
                            lotteryName = source.lotteryName,
                            playType = "Q",
                            label = "Quiniela",
                            number = number,
                            displayNumber = number,
                            amount = amount,
                        )
                        if (hasDuplicateRow(merged, candidate)) {
                            blockedCount += 1
                        } else {
                            merged = merged + candidate
                            generated = generated + candidate
                            quinielaCount += 1
                        }
                    }
                }
            return LigarBuildResult(
                rows = generated + existing,
                quinielaCount = quinielaCount,
                paleCount = paleCount,
                tripletaCount = tripletaCount,
                blockedCount = blockedCount,
            )
        }

        byLottery.values.forEach { rows ->
            if (rows.size < 2) return@forEach
            val lotteryName = rows.first().lotteryName

            if (target == LigarBuildTarget.PALE) {
                rows.map { it.number }.combinations(2).forEach { pair ->
                    val candidate = SaleStagedRow(
                        lotteryId = rows.first().lotteryId,
                        lotteryName = lotteryName,
                        playType = "P",
                        label = "Pale",
                        number = pair.joinToString(""),
                        displayNumber = pair.joinToString("/"),
                        amount = amount,
                    )
                    if (hasDuplicateRow(merged, candidate)) {
                        blockedCount += 1
                    } else {
                        merged = merged + candidate
                        generated = generated + candidate
                        paleCount += 1
                    }
                }
            }

            if (target == LigarBuildTarget.TRIPLETA && rows.size >= 3) {
                rows.map { it.number }.combinations(3).forEach { trio ->
                    val candidate = SaleStagedRow(
                        lotteryId = rows.first().lotteryId,
                        lotteryName = lotteryName,
                        playType = "T",
                        label = "Tripleta",
                        number = trio.joinToString(""),
                        displayNumber = trio.joinToString("/"),
                        amount = amount,
                    )
                    if (hasDuplicateRow(merged, candidate)) {
                        blockedCount += 1
                    } else {
                        merged = merged + candidate
                        generated = generated + candidate
                        tripletaCount += 1
                    }
                }
            }
        }

        return LigarBuildResult(
            rows = generated + existing,
            quinielaCount = quinielaCount,
            paleCount = paleCount,
            tripletaCount = tripletaCount,
            blockedCount = blockedCount,
        )
    }

    private fun validatePick(
        digits: String,
        amount: Double,
        lotteryType: String,
        pickMode: PickPlayMode,
    ): SaleValidationResult {
        val normalizedType = normalizePickLotteryType(lotteryType) ?: lotteryType
        val expected = if (normalizedType == "Pick4") 4 else 3
        if (digits.length != expected) {
            return SaleValidationResult(
                isValid = false,
                errorMessage = "Esta lotería requiere $expected dígitos",
            )
        }
        val play = detectPickPlay(digits, normalizedType, pickMode)
            ?: return SaleValidationResult(isValid = false, errorMessage = "Número inválido")
        return SaleValidationResult(
            isValid = true,
            normalizedAmount = amount,
            resolvedPlay = play,
        )
    }

    private fun validateClassic(
        digits: String,
        amount: Double,
        classicMode: String,
    ): SaleValidationResult {
        return when (classicMode) {
            "P" -> {
                if (digits.length != 4) {
                    SaleValidationResult(false, errorMessage = "Pale necesita 4 dígitos")
                } else {
                    SaleValidationResult(
                        isValid = true,
                        normalizedAmount = amount,
                        resolvedPlay = SaleResolvedPlay(
                            playType = "P",
                            label = "Pale",
                            normalizedNumber = digits,
                            displayNumber = "${digits.slice(0..1)}/${digits.slice(2..3)}",
                        ),
                    )
                }
            }

            "T" -> {
                if (digits.length != 6) {
                    SaleValidationResult(false, errorMessage = "Tripleta necesita 6 dígitos")
                } else {
                    SaleValidationResult(
                        isValid = true,
                        normalizedAmount = amount,
                        resolvedPlay = SaleResolvedPlay(
                            playType = "T",
                            label = "Tripleta",
                            normalizedNumber = digits,
                            displayNumber = "${digits.slice(0..1)}/${digits.slice(2..3)}/${digits.slice(4..5)}",
                        ),
                    )
                }
            }

            "SP" -> {
                SaleValidationResult(false, errorMessage = "Activa Super Pale y selecciona 2 loterías")
            }

            else -> {
                if (digits.length != 2) {
                    SaleValidationResult(false, errorMessage = "Quiniela necesita 2 dígitos")
                } else {
                    val play = detectClassicPlay(digits, classicMode)
                        ?: return SaleValidationResult(false, errorMessage = "Número inválido")
                    SaleValidationResult(
                        isValid = true,
                        normalizedAmount = amount,
                        resolvedPlay = play,
                    )
                }
            }
        }
    }

    private fun supportsPick(lottery: LotteryCatalogItem): Boolean {
        return resolvePickLotteryType(lottery) != null ||
            lottery.playCapabilities.supportsStraight ||
            lottery.playCapabilities.supportsBox
    }

    private fun hasCents(amount: Double): Boolean {
        return kotlin.math.abs(amount - kotlin.math.round(amount)) > 0.0001
    }

    private fun selectedPickLotteryType(selectedLotteries: List<LotteryCatalogItem>): String? {
        if (selectedLotteries.isEmpty()) return null
        val firstType = resolvePickLotteryType(selectedLotteries.first()) ?: return null
        if (selectedLotteries.any { !supportsPick(it) }) return null
        return if (selectedLotteries.all { resolvePickLotteryType(it) == firstType }) firstType else null
    }

    private fun detectPickPlay(
        digits: String,
        lotteryType: String,
        pickMode: PickPlayMode,
    ): SaleResolvedPlay? {
        val normalizedType = normalizePickLotteryType(lotteryType) ?: lotteryType
        val expected = if (normalizedType == "Pick4") 4 else 3
        if (digits.length != expected) return null
        val playType = when {
            normalizedType == "Pick4" && pickMode == PickPlayMode.BOX -> "P4BOX"
            normalizedType == "Pick4" -> "P4"
            pickMode == PickPlayMode.BOX -> "P3BOX"
            else -> "P3"
        }
        return SaleResolvedPlay(
            playType = playType,
            label = pickModeLabel(normalizedType, pickMode),
            normalizedNumber = digits,
            displayNumber = digits + if (pickMode == PickPlayMode.BOX) "B" else "S",
        )
    }

    private fun resolvePickInput(
        raw: String,
        fallbackMode: PickPlayMode,
    ): PickInput {
        val trimmed = raw.trim().uppercase(Locale.US)
        val suffixMode = when (trimmed.lastOrNull()) {
            'B' -> PickPlayMode.BOX
            'S' -> PickPlayMode.STRAIGHT
            '+' -> PickPlayMode.BOX
            '-' -> PickPlayMode.STRAIGHT
            else -> null
        }
        return PickInput(
            digits = trimmed.filter(Char::isDigit),
            pickMode = suffixMode ?: fallbackMode,
        )
    }

    private fun detectClassicPlay(
        digits: String,
        classicMode: String,
    ): SaleResolvedPlay? {
        return when (classicMode) {
            "P" -> if (digits.length == 4) {
                SaleResolvedPlay(
                    playType = "P",
                    label = "Pale",
                    normalizedNumber = digits,
                    displayNumber = "${digits.slice(0..1)}/${digits.slice(2..3)}",
                )
            } else {
                null
            }

            "T" -> if (digits.length == 6) {
                SaleResolvedPlay(
                    playType = "T",
                    label = "Tripleta",
                    normalizedNumber = digits,
                    displayNumber = "${digits.slice(0..1)}/${digits.slice(2..3)}/${digits.slice(4..5)}",
                )
            } else {
                null
            }

            "Q" -> if (digits.length == 2) {
                SaleResolvedPlay(
                    playType = "Q",
                    label = "Quiniela",
                    normalizedNumber = digits,
                    displayNumber = digits,
                )
            } else {
                null
            }

            else -> null
        }
    }

    private fun pickModeLabel(
        lotteryType: String,
        pickMode: PickPlayMode,
    ): String {
        val base = if ((normalizePickLotteryType(lotteryType) ?: lotteryType) == "Pick4") "Pick 4" else "Pick 3"
        return "$base ${if (pickMode == PickPlayMode.BOX) "Box" else "Straight"}"
    }

    private fun normalizePickLotteryType(type: String): String? {
        val normalized = type.filter(Char::isLetterOrDigit).lowercase(Locale.US)
        return when {
            normalized == "pick4" || normalized == "p4" || normalized.contains("pick4") -> "Pick4"
            normalized == "pick3" || normalized == "p3" || normalized.contains("pick3") -> "Pick3"
            else -> null
        }
    }

    private fun resolvePickLotteryType(lottery: LotteryCatalogItem): String? {
        return normalizePickLotteryType(lottery.type)
            ?: normalizePickLotteryType(lottery.name)
    }

    private data class PickInput(
        val digits: String,
        val pickMode: PickPlayMode,
    )

    private fun mergeRow(
        existing: List<SaleStagedRow>,
        newRow: SaleStagedRow,
    ): List<SaleStagedRow> {
        val duplicate = existing.firstOrNull { row ->
            row.lotteryId == newRow.lotteryId &&
                row.secondaryLotteryId == newRow.secondaryLotteryId &&
                row.playType == newRow.playType &&
                row.number == newRow.number
        }
        return if (duplicate == null) {
            existing + newRow
        } else {
            existing.map { row ->
                if (row.id == duplicate.id) row.copy(amount = row.amount + newRow.amount) else row
            }
        }
    }

    private fun hasDuplicateRow(
        existing: List<SaleStagedRow>,
        newRow: SaleStagedRow,
    ): Boolean {
        return existing.any { row ->
            row.lotteryId == newRow.lotteryId &&
                row.secondaryLotteryId == newRow.secondaryLotteryId &&
                row.playType == newRow.playType &&
                row.number == newRow.number
        }
    }

    private fun <T> List<T>.combinations(size: Int): List<List<T>> {
        if (size <= 0 || size > this.size) return emptyList()
        if (size == 1) return map { listOf(it) }
        if (size == this.size) return listOf(this)

        val result = mutableListOf<List<T>>()
        for (index in 0..this.size - size) {
            val head = this[index]
            val tail = this.subList(index + 1, this.size)
            tail.combinations(size - 1).forEach { combo ->
                result += listOf(head) + combo
            }
        }
        return result
    }
}

fun resolveLigarBuildTargets(existing: List<SaleStagedRow>): List<LigarBuildTarget> {
    val targets = mutableListOf<LigarBuildTarget>()
    val hasCompositeClassic = existing.any {
        it.secondaryLotteryId == null && (it.playType == "P" || it.playType == "T")
    }
    if (hasCompositeClassic) {
        targets += LigarBuildTarget.QUINIELA
    }

    val quinielaGroups = existing
        .filter { it.playType == "Q" && it.secondaryLotteryId == null }
        .distinctBy { "${it.lotteryId}|${it.number}" }
        .groupBy { it.lotteryId }
        .values
    val hasTwoQuinielas = quinielaGroups
        .any { it.size >= 2 }
    if (hasTwoQuinielas) {
        targets += LigarBuildTarget.PALE
    }

    val hasThreeQuinielas = quinielaGroups
        .any { it.size >= 3 }
    if (hasThreeQuinielas) {
        targets += LigarBuildTarget.TRIPLETA
    }
    return targets.distinct()
}

data class LigarBuildResult(
    val rows: List<SaleStagedRow>,
    val quinielaCount: Int,
    val paleCount: Int,
    val tripletaCount: Int,
    val blockedCount: Int,
)

enum class LigarBuildTarget {
    QUINIELA,
    PALE,
    TRIPLETA,
}
