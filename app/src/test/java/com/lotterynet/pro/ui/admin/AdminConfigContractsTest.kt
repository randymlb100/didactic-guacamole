package com.lotterynet.pro.ui.admin

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminConfigContractsTest {

    @Test
    fun `system caja shortcuts do not duplicate premios or thermal ticket`() {
        val shortcuts = adminConfigCajaShortcutTitles()

        assertEquals(listOf("Impresora"), shortcuts)
        assertFalse(shortcuts.contains("Premios"))
        assertFalse(shortcuts.any { it.contains("térmico", ignoreCase = true) || it.contains("termico", ignoreCase = true) })
    }

    @Test
    fun `operation shortcuts merge cajeros and limits`() {
        val shortcuts = adminConfigOperationShortcutTitles()

        assertEquals(listOf("Cajeros"), shortcuts)
        assertFalse(shortcuts.contains("Límites"))
        assertFalse(shortcuts.contains("Usuarios"))
        assertFalse(adminConfigOperationShortcutDescriptions().any { it.contains("comisi", ignoreCase = true) })
    }

    @Test
    fun `admin monitor pos layout stays dense and readable`() {
        val layout = resolveAdminMonitorLayout(LotteryNetWindowMode.POS_TIGHT)

        assertTrue(layout.compactSummary)
        assertTrue(layout.compactCards)
        assertTrue(layout.useCompactRows)
        assertFalse(layout.showLargeCards)
        assertTrue(layout.summaryPaddingVerticalDp <= 6)
        assertTrue(layout.rowPaddingVerticalDp <= 5)
        assertTrue(layout.statPaddingDp <= 6)
        assertTrue(layout.boldPrimaryText)
    }

    @Test
    fun `admin monitor keeps required compact actions including server refresh`() {
        assertEquals(
            listOf("Actualizar servidor", "WhatsApp", "Compartir", "Guardar", "Impr."),
            adminMonitorActionLabels(),
        )
    }

    @Test
    fun `system mode rows expose separate admin and cashier mode buttons`() {
        val rows = adminSystemModeRows(
            AdminSystemModeConfig(
                lotteryModeEnabled = true,
                pickModeEnabled = true,
                cashierModeEnabled = true,
                cashierLotteryModeEnabled = false,
                cashierPickModeEnabled = true,
            ),
            UserRole.ADMIN,
        )

        assertEquals(
            listOf(
                "Modo POS Lite",
                "Admin: Solo Lotería",
                "Admin: Solo Pick",
                "Admin: Lotería + Pick",
                "Cajero: Solo Lotería",
                "Cajero: Solo Pick",
                "Cajero: Lotería + Pick",
            ),
            rows.map { it.label },
        )
        assertTrue(rows.first { it.label == "Admin: Lotería + Pick" }.enabled)
        assertTrue(rows.first { it.label == "Cajero: Solo Pick" }.enabled)
    }

    @Test
    fun `cashier system mode defaults to lottery only without duplicate master toggle`() {
        val rows = adminSystemModeRows(AdminSystemModeConfig(), UserRole.ADMIN)

        assertFalse(rows.any { it.label == "Cajero: modos Pick" })
        assertTrue(rows.first { it.label == "Cajero: Solo Lotería" }.enabled)
        assertFalse(rows.first { it.label == "Cajero: Solo Pick" }.enabled)
        assertFalse(rows.first { it.label == "Cajero: Lotería + Pick" }.enabled)
    }

    @Test
    fun `system mode selection commits locally and schedules server sync`() {
        val event = resolveSystemModeSelectionCommitMessage(serverSyncStarted = true)

        assertEquals("Enviando", event.syncStatus)
        assertEquals("Modo de sistema guardado y enviando al servidor...", event.statusMessage)
    }

    @Test
    fun `system save button remains available for manual retry`() {
        assertEquals("Reintentar guardar", adminSystemModeSaveButtonLabel())
    }

    @Test
    fun `configuration sections separate sale control from system mode`() {
        val sections = adminConfigSectionTitles()

        assertEquals("Centro de ajustes", sections.first())
        assertTrue(sections.indexOf("Loterías y jugadas") > sections.indexOf("Venta y POS"))
        assertTrue(sections.indexOf("Caja y tickets") > sections.indexOf("Loterías y jugadas"))
        assertEquals(1, sections.count { it == "Venta y POS" })
        assertEquals(1, sections.count { it == "Loterías y jugadas" })
    }

    @Test
    fun `system configuration hub uses interactive component pattern`() {
        val contract = adminConfigInteractionContract()

        assertEquals(listOf("Venta y POS", "Loterías y jugadas", "Caja y tickets", "Servidor y sincronización"), adminConfigSystemHubCardTitles())
        assertTrue(contract.usesSummaryCards)
        assertTrue(contract.usesSwitchForBooleanSetting)
        assertTrue(contract.usesSegmentedChoicesForModes)
        assertTrue(contract.usesBottomSheetForSecondarySelection)
    }

    @Test
    fun `settings destinations are unique and grouped for real navigation`() {
        val ids = adminConfigDestinationIds()

        assertEquals(ids.size, ids.toSet().size)
        assertEquals("OPERACIÓN", adminConfigAreaGroup("sale"))
        assertEquals("OPERACIÓN", adminConfigAreaGroup("blocks"))
        assertEquals("CAJA", adminConfigAreaGroup("cash"))
        assertEquals("SISTEMA", adminConfigAreaGroup("system"))
    }

    @Test
    fun `settings details keep navigation separate from business callbacks`() {
        val contract = adminSettingsNavigationContract()

        assertTrue(contract.usesOverviewAndDetail)
        assertTrue(contract.systemBackReturnsToOverview)
        assertTrue(contract.groupsDestinations)
        assertTrue(contract.preservesBusinessCallbacks)
    }

    @Test
    fun `blocking controls stay readable on compact screens`() {
        val contract = adminBlockControlLayoutContract()

        assertTrue(contract.stacksDestructiveActions)
        assertTrue(contract.showsSelectedDuration)
        assertTrue(contract.keepsLotteryActionBelowIdentity)
    }

    @Test
    fun `settings search filters destinations without changing the destination model`() {
        assertEquals(listOf("Venta y POS"), filterAdminConfigAreaTitles("pos"))
        assertEquals(listOf("Caja y tickets"), filterAdminConfigAreaTitles("impresora"))
        assertEquals(4, filterAdminConfigAreaTitles(" ").size)
        assertTrue(filterAdminConfigAreaTitles("no existe").isEmpty())
    }

    @Test
    fun `system mode labels stay short for compact mobile cards`() {
        assertEquals("Solo Lotería", adminConfigModeLabel("lottery"))
        assertEquals("Solo Pick", adminConfigModeLabel("pick"))
        assertEquals("Lotería + Pick", adminConfigModeLabel("both"))
        assertEquals("Lot.", adminConfigModeShortLabel("lottery"))
        assertEquals("Pick", adminConfigModeShortLabel("pick"))
        assertEquals("L+P", adminConfigModeShortLabel("both"))
    }

    @Test
    fun `manual result date options behave as choices not commands`() {
        val contract = resolveAdminManualResultDateSelectorContract(optionCount = 3)

        assertEquals(3, contract.optionCount)
        assertTrue(contract.usesSegmentedChoice)
        assertFalse(contract.countsAsPrimaryCommand)
        assertTrue(contract.minTouchTargetDp >= 44)
    }

    @Test
    fun `initial system mode uses server value when available`() {
        val local = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = false)
        val server = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true)

        val resolved = resolveInitialAdminSystemModeConfig(local, server)

        assertTrue(resolved.lotteryModeEnabled)
        assertTrue(resolved.pickModeEnabled)
    }

    @Test
    fun `system mode sync writes every owner alias needed by clean cashier installs`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "auth-user-id",
            username = "nicola01",
            adminId = "ADM-C5FFB0",
            adminUser = "nicola01",
            banca = "Poderoso",
        )

        val keys = resolveAdminSystemModeSyncKeys(
            session = session,
            ownerKeys = listOf("ADM-C5FFB0", "auth-user-id", "nicola01"),
        )

        assertEquals(listOf("ADM-C5FFB0", "auth-user-id", "nicola01"), keys)
    }

    @Test
    fun `manual result editor lists only closed missing results`() {
        val lotteries = listOf(
            adminLottery("1", "La Primera Dia", "12:00 PM"),
            adminLottery("2", "Anguila Manana", "10:00 AM"),
            adminLottery("3", "La Suerte 12:30", "12:30 PM"),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "1",
                lotteryName = "La Primera Dia",
                date = "15-05-2026",
                first = "01",
                second = "02",
                third = "03",
            ),
        )

        val editable = filterManualResultEditableLotteries(
            lotteries = lotteries,
            results = results,
            selectedDate = "15-05-2026",
            todayDate = "15-05-2026",
            nowUtcMs = 0L,
            hasDrawPassed = { lottery, _ -> lottery.id != "3" },
        )

        assertEquals(listOf("2"), editable.map { it.id })
    }

    @Test
    fun `manual result editor keeps manual override available for correction`() {
        val lotteries = listOf(adminLottery("1", "La Primera Dia", "12:00 PM"))
        val results = listOf(
            LotteryResult(
                lotteryId = "1",
                lotteryName = "La Primera Dia",
                date = "15-05-2026",
                first = "01",
                second = "02",
                third = "03",
                isManualOverride = true,
            ),
        )

        val editable = filterManualResultEditableLotteries(
            lotteries = lotteries,
            results = results,
            selectedDate = "15-05-2026",
            todayDate = "15-05-2026",
            nowUtcMs = 0L,
            hasDrawPassed = { _, _ -> false },
        )

        assertEquals(listOf("1"), editable.map { it.id })
    }

    private fun adminLottery(
        id: String,
        name: String,
        drawTime: String,
    ): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = id,
            name = name,
            type = "RD",
            baseDrawTime = drawTime,
            baseCloseTime = drawTime,
            colorHex = "#111111",
            territory = LotteryTerritory.RD,
        )
    }
}
