package com.lotterynet.pro.core.sales

import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryPlayCapabilities
import com.lotterynet.pro.core.model.PickPlayMode
import com.lotterynet.pro.core.model.SaleDraft
import com.lotterynet.pro.core.model.SaleStagedRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleValidatorContractsTest {

    private val validator = SaleValidator()

    @Test
    fun `new jersey lottery rejects three digits as normal quiniela`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("26"),
                numberInput = "369",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(newJerseyPm()),
        )

        assertEquals(false, result.isValid)
        assertEquals("Quiniela necesita 2 dígitos", result.errorMessage)
    }

    @Test
    fun `new jersey lottery keeps four digits as pale when pale mode is active`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("26"),
                numberInput = "7898",
                amountInput = "10",
                classicMode = "P",
            ),
            selectedLotteries = listOf(newJerseyPm()),
        )

        assertTrue(result.errorMessage.orEmpty(), result.isValid)
        assertEquals("P", result.resolvedPlay?.playType)
        assertEquals("7898", result.resolvedPlay?.normalizedNumber)
    }

    @Test
    fun `pick 3 lottery accepts exactly three digits`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "369",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick3()),
        )

        assertTrue(result.errorMessage.orEmpty(), result.isValid)
        assertEquals("P3", result.resolvedPlay?.playType)
        assertEquals("369", result.resolvedPlay?.normalizedNumber)
        assertEquals("369S", result.resolvedPlay?.displayNumber)
    }

    @Test
    fun `pick 3 accepts catalog type variants with spaces and lowercase`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("p3-space"),
                numberInput = "123",
                amountInput = "20",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick3().copy(id = "p3-space", type = "pick 3")),
        )

        assertTrue(result.errorMessage.orEmpty(), result.isValid)
        assertEquals("P3", result.resolvedPlay?.playType)
        assertEquals("Pick 3 Straight", result.resolvedPlay?.label)
    }

    @Test
    fun `pick lottery accepts decimal cents amount`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "369",
                amountInput = "0.50",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick3()),
        )

        assertTrue(result.errorMessage.orEmpty(), result.isValid)
        assertEquals(0.50, result.normalizedAmount ?: 0.0, 0.001)
        assertEquals("P3", result.resolvedPlay?.playType)
    }

    @Test
    fun `normal lottery rejects decimal cents amount`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("25"),
                numberInput = "12",
                amountInput = "0.50",
                classicMode = "Q",
            ),
            selectedLotteries = listOf(newJerseyAm()),
        )

        assertEquals(false, result.isValid)
        assertEquals("Centavos solo permitido en Pick", result.errorMessage)
    }

    @Test
    fun `pick 3 am and pm catalog types accept three digits`() {
        listOf("Pick 3 AM", "Pick 3 PM", "NJ Pick 3 AM").forEach { type ->
            val result = validator.validate(
                draft = SaleDraft(
                    selectedLotteryIds = listOf("p3-time"),
                    numberInput = "986",
                    amountInput = "10",
                    pickMode = PickPlayMode.STRAIGHT,
                ),
                selectedLotteries = listOf(pick3().copy(id = "p3-time", type = type)),
            )

            assertTrue(type + ": " + result.errorMessage.orEmpty(), result.isValid)
            assertEquals("P3", result.resolvedPlay?.playType)
            assertEquals("986", result.resolvedPlay?.normalizedNumber)
            assertEquals("986S", result.resolvedPlay?.displayNumber)
        }
    }

    @Test
    fun `pick 3 accepts three digits when remote catalog only has pick name`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("p3-remote"),
                numberInput = "852",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(
                pick3().copy(
                    id = "p3-remote",
                    name = "NJ Pick 3 PM",
                    type = "NJ",
                    playCapabilities = LotteryPlayCapabilities(),
                ),
            ),
        )

        assertTrue(result.errorMessage.orEmpty(), result.isValid)
        assertEquals("P3", result.resolvedPlay?.playType)
        assertEquals("852", result.resolvedPlay?.normalizedNumber)
        assertEquals("852S", result.resolvedPlay?.displayNumber)
    }

    @Test
    fun `pick 3 rejects four digits with three digit message`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "1234",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick3()),
        )

        assertEquals(false, result.isValid)
        assertEquals("Esta lotería requiere 3 dígitos", result.errorMessage)
    }

    @Test
    fun `new jersey am pm stays normal and rejects three digits`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("25"),
                numberInput = "369",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(newJerseyAm()),
        )

        assertEquals(false, result.isValid)
        assertEquals("Quiniela necesita 2 dígitos", result.errorMessage)
    }

    @Test
    fun `nj am pm remains a normal lottery and does not become pick 3 by name`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("25"),
                numberInput = "12",
                amountInput = "10",
                classicMode = "Q",
            ),
            selectedLotteries = listOf(newJerseyAm().copy(name = "NJ AM PM", type = "NJ")),
        )

        assertTrue(result.errorMessage.orEmpty(), result.isValid)
        assertEquals("Q", result.resolvedPlay?.playType)
        assertEquals("12", result.resolvedPlay?.normalizedNumber)
    }

    @Test
    fun `pick 4 lottery accepts exactly four digits`() {
        val result = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("21"),
                numberInput = "7898",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick4()),
        )

        assertTrue(result.errorMessage.orEmpty(), result.isValid)
        assertEquals("P4", result.resolvedPlay?.playType)
        assertEquals("7898", result.resolvedPlay?.normalizedNumber)
        assertEquals("7898S", result.resolvedPlay?.displayNumber)
    }

    @Test
    fun `pick rows keep clean digits in saved number and suffix only for display`() {
        val pick3Box = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "854",
                amountInput = "25",
                pickMode = PickPlayMode.BOX,
            ),
            selectedLotteries = listOf(pick3()),
        )
        val pick4Straight = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("21"),
                numberInput = "2546S",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick4()),
        )

        assertEquals("854", pick3Box.resolvedPlay?.normalizedNumber)
        assertEquals("854B", pick3Box.resolvedPlay?.displayNumber)
        assertEquals("854", validator.mergeIntoRows(emptyList(), pick3Box, listOf(pick3())).single().number)
        assertEquals("854B", validator.mergeIntoRows(emptyList(), pick3Box, listOf(pick3())).single().displayNumber)
        assertEquals("2546", pick4Straight.resolvedPlay?.normalizedNumber)
        assertEquals("2546S", pick4Straight.resolvedPlay?.displayNumber)
        assertEquals("2546", validator.mergeIntoRows(emptyList(), pick4Straight, listOf(pick4())).single().number)
        assertEquals("2546S", validator.mergeIntoRows(emptyList(), pick4Straight, listOf(pick4())).single().displayNumber)
    }

    @Test
    fun `typed pick suffix overrides selected mode but keeps saved number clean`() {
        val straight = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "852s",
                amountInput = "5",
                pickMode = PickPlayMode.BOX,
            ),
            selectedLotteries = listOf(pick3()),
        )
        val box = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "852b",
                amountInput = "5",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick3()),
        )

        assertTrue(straight.errorMessage.orEmpty(), straight.isValid)
        assertEquals("P3", straight.resolvedPlay?.playType)
        assertEquals("852", straight.resolvedPlay?.normalizedNumber)
        assertEquals("852S", straight.resolvedPlay?.displayNumber)
        assertEquals("852", validator.mergeIntoRows(emptyList(), straight, listOf(pick3())).single().number)
        assertEquals("852S", validator.mergeIntoRows(emptyList(), straight, listOf(pick3())).single().displayNumber)
        assertEquals("Pick 3 Straight", validator.mergeIntoRows(emptyList(), straight, listOf(pick3())).single().label)
        assertTrue(box.errorMessage.orEmpty(), box.isValid)
        assertEquals("P3BOX", box.resolvedPlay?.playType)
        assertEquals("852", box.resolvedPlay?.normalizedNumber)
        assertEquals("852B", box.resolvedPlay?.displayNumber)
        assertEquals("852", validator.mergeIntoRows(emptyList(), box, listOf(pick3())).single().number)
        assertEquals("852B", validator.mergeIntoRows(emptyList(), box, listOf(pick3())).single().displayNumber)
        assertEquals("Pick 3 Box", validator.mergeIntoRows(emptyList(), box, listOf(pick3())).single().label)
    }

    @Test
    fun `video style pick symbols override selected mode but keep saved number clean`() {
        val straight = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "852-",
                amountInput = "5",
                pickMode = PickPlayMode.BOX,
            ),
            selectedLotteries = listOf(pick3()),
        )
        val box = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "852+",
                amountInput = "5",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick3()),
        )

        assertTrue(straight.errorMessage.orEmpty(), straight.isValid)
        assertEquals("P3", straight.resolvedPlay?.playType)
        assertEquals("852", straight.resolvedPlay?.normalizedNumber)
        assertEquals("852S", straight.resolvedPlay?.displayNumber)
        assertTrue(box.errorMessage.orEmpty(), box.isValid)
        assertEquals("P3BOX", box.resolvedPlay?.playType)
        assertEquals("852", box.resolvedPlay?.normalizedNumber)
        assertEquals("852B", box.resolvedPlay?.displayNumber)
    }

    @Test
    fun `typed pick suffix appears in staged list while server number stays clean`() {
        val straight = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "256s",
                amountInput = "10",
                pickMode = PickPlayMode.BOX,
            ),
            selectedLotteries = listOf(pick3()),
        )
        val box = validator.validate(
            draft = SaleDraft(
                selectedLotteryIds = listOf("19"),
                numberInput = "256b",
                amountInput = "10",
                pickMode = PickPlayMode.STRAIGHT,
            ),
            selectedLotteries = listOf(pick3()),
        )

        val straightRow = validator.mergeIntoRows(emptyList(), straight, listOf(pick3())).single()
        val boxRow = validator.mergeIntoRows(emptyList(), box, listOf(pick3())).single()

        assertEquals("P3", straightRow.playType)
        assertEquals("256", straightRow.number)
        assertEquals("256S", straightRow.displayNumber)
        assertEquals("P3BOX", boxRow.playType)
        assertEquals("256", boxRow.number)
        assertEquals("256B", boxRow.displayNumber)
    }

    @Test
    fun `ligar pale adds only new pale rows at the top`() {
        val existing = listOf(
            stagedQuiniela("12"),
            stagedQuiniela("34"),
            stagedQuiniela("56"),
        )

        val result = validator.buildLigarRows(
            existing = existing,
            amount = 10.0,
            target = LigarBuildTarget.PALE,
        )

        assertEquals(3, result.paleCount)
        assertEquals(0, result.tripletaCount)
        assertEquals(listOf("P", "P", "P"), result.rows.take(3).map { it.playType })
        assertEquals(listOf("12/34", "12/56", "34/56"), result.rows.take(3).map { it.displayNumber })
        assertEquals(listOf("Q", "Q", "Q"), result.rows.drop(3).map { it.playType })
    }

    @Test
    fun `ligar tripleta adds only tripleta rows at the top`() {
        val existing = listOf(
            stagedQuiniela("12"),
            stagedQuiniela("34"),
            stagedQuiniela("56"),
        )

        val result = validator.buildLigarRows(
            existing = existing,
            amount = 10.0,
            target = LigarBuildTarget.TRIPLETA,
        )

        assertEquals(0, result.paleCount)
        assertEquals(1, result.tripletaCount)
        assertEquals(listOf("T"), result.rows.take(1).map { it.playType })
        assertEquals(listOf("12/34/56"), result.rows.take(1).map { it.displayNumber })
        assertEquals(listOf("Q", "Q", "Q"), result.rows.drop(1).map { it.playType })
    }

    @Test
    fun `ligar quiniela from tripleta adds only missing quinielas at the top`() {
        val existing = listOf(
            stagedTripleta("182898"),
            stagedQuiniela("28"),
            stagedQuiniela("98"),
        )

        val result = validator.buildLigarRows(
            existing = existing,
            amount = 10.0,
            target = LigarBuildTarget.QUINIELA,
        )

        assertEquals(1, result.quinielaCount)
        assertEquals(0, result.paleCount)
        assertEquals(0, result.tripletaCount)
        assertEquals("Q", result.rows.first().playType)
        assertEquals("18", result.rows.first().displayNumber)
        assertEquals(listOf("T", "Q", "Q"), result.rows.drop(1).map { it.playType })
    }

    @Test
    fun `ligar options read current rows for quiniela pale and tripleta`() {
        assertEquals(
            listOf(LigarBuildTarget.PALE),
            resolveLigarBuildTargets(listOf(stagedQuiniela("18"), stagedQuiniela("28"))),
        )
        assertEquals(
            listOf(LigarBuildTarget.PALE, LigarBuildTarget.TRIPLETA),
            resolveLigarBuildTargets(listOf(stagedQuiniela("18"), stagedQuiniela("28"), stagedQuiniela("98"))),
        )
        assertEquals(
            listOf(LigarBuildTarget.QUINIELA, LigarBuildTarget.PALE),
            resolveLigarBuildTargets(listOf(stagedTripleta("182898"), stagedQuiniela("28"), stagedQuiniela("98"))),
        )
    }

    @Test
    fun `ligar options continue offering pale and tripleta after an existing pale`() {
        val rows = listOf(
            stagedPale("1828"),
            stagedQuiniela("18"),
            stagedQuiniela("28"),
            stagedQuiniela("98"),
        )

        assertEquals(
            listOf(LigarBuildTarget.QUINIELA, LigarBuildTarget.PALE, LigarBuildTarget.TRIPLETA),
            resolveLigarBuildTargets(rows),
        )
    }

    private fun newJerseyPm(): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = "26",
            name = "New Jersey PM",
            type = "NJ",
            baseDrawTime = "10:57 PM",
            baseCloseTime = "10:50 PM",
            colorHex = "#115e59",
        )
    }

    private fun newJerseyAm(): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = "25",
            name = "New Jersey AM",
            type = "NJ",
            baseDrawTime = "12:59 PM",
            baseCloseTime = "12:50 PM",
            colorHex = "#0f766e",
        )
    }

    private fun pick3(): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = "19",
            name = "NJ Pick 3 Dia",
            type = "Pick3",
            baseDrawTime = "12:59 PM",
            baseCloseTime = "12:50 PM",
            colorHex = "#0ea5e9",
            playCapabilities = pickCapabilities(),
        )
    }

    private fun pick4(): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = "21",
            name = "NJ Pick 4 Dia",
            type = "Pick4",
            baseDrawTime = "12:59 PM",
            baseCloseTime = "12:50 PM",
            colorHex = "#16a34a",
            playCapabilities = pickCapabilities(),
        )
    }

    private fun pickCapabilities(): LotteryPlayCapabilities {
        return LotteryPlayCapabilities(
            supportsStraight = true,
            supportsBox = true,
        )
    }

    private fun stagedQuiniela(number: String): SaleStagedRow {
        return SaleStagedRow(
            lotteryId = "lot-1",
            lotteryName = "Loteria 1",
            playType = "Q",
            label = "Quiniela",
            number = number,
            displayNumber = number,
            amount = 5.0,
        )
    }

    private fun stagedTripleta(number: String): SaleStagedRow {
        return SaleStagedRow(
            lotteryId = "lot-1",
            lotteryName = "Loteria 1",
            playType = "T",
            label = "Tripleta",
            number = number,
            displayNumber = "${number.slice(0..1)}/${number.slice(2..3)}/${number.slice(4..5)}",
            amount = 5.0,
        )
    }

    private fun stagedPale(number: String): SaleStagedRow {
        return SaleStagedRow(
            lotteryId = "lot-1",
            lotteryName = "Loteria 1",
            playType = "P",
            label = "Pale",
            number = number,
            displayNumber = "${number.slice(0..1)}/${number.slice(2..3)}",
            amount = 5.0,
        )
    }
}
