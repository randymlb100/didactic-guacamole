package com.lotterynet.pro.ui.sales

import com.lotterynet.pro.core.model.CloseState
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryCloseDecision
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PickPlayMode
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.model.SaleResolvedPlay
import com.lotterynet.pro.core.model.SaleStagedRow
import com.lotterynet.pro.core.model.SaleValidationResult
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.formatPlayDisplayNumber
import com.lotterynet.pro.core.remote.SupabaseEdgeException
import com.lotterynet.pro.core.remote.SupabaseEdgeFailureReason
import com.lotterynet.pro.core.sales.SaleLimitRemainingRow
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.core.storage.effectiveAdminSystemModeConfigForRole
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CancellationException

class SalesUiContractsTest {

    @Test
    fun `pos lite applies tight selling layout for handheld pos devices`() {
        val contract = resolveVentaPosLiteContract(
            windowMode = LotteryNetWindowMode.POS_TIGHT,
            posLiteEnabled = true,
        )
        val keypad = resolveVentaKeypadLayout(contract.windowMode)
        val staged = resolveVentaStagedListLayout(contract.windowMode)

        assertTrue(contract.includeSales)
        assertTrue(contract.useTightSellingLayout)
        assertFalse(keypad.showStatsBadges)
        assertTrue(keypad.totalAboveKeypad)
        assertEquals(44, keypad.keyHeightDp)
        assertTrue(keypad.numberKeyFontSp <= 22)
        assertTrue(keypad.commandKeyFontSp <= 14)
        assertTrue(staged.rowVerticalPaddingDp <= 2)
        assertTrue(staged.prioritizeListSpace)
    }

    @Test
    fun `pos lite can compact sale while normal sale keeps phone layout`() {
        val normal = resolveVentaPosLiteContract(
            windowMode = LotteryNetWindowMode.POS,
            posLiteEnabled = false,
        )
        val lite = resolveVentaPosLiteContract(
            windowMode = LotteryNetWindowMode.POS,
            posLiteEnabled = true,
        )

        assertEquals(LotteryNetWindowMode.POS, normal.windowMode)
        assertFalse(normal.useTightSellingLayout)
        assertEquals(LotteryNetWindowMode.POS_TIGHT, lite.windowMode)
        assertTrue(lite.useTightSellingLayout)
        assertTrue(resolveVentaKeypadLayout(lite.windowMode).keyHeightDp < resolveVentaKeypadLayout(normal.windowMode).keyHeightDp)
        assertTrue(resolveVentaStagedListLayout(lite.windowMode).prioritizeListSpace)
    }

    @Test
    fun `pos lite toggle is a real cashier sale command and keeps sale within small viewport`() {
        val contract = resolveVentaPosLiteControlContract(
            role = UserRole.CASHIER,
            viewportWidthDp = 360,
            viewportHeightDp = 640,
            posLiteEnabled = false,
        )

        assertTrue(contract.visible)
        assertTrue(contract.enabled)
        assertEquals("POS Lite", contract.label)
        assertTrue(contract.togglesPersistedMode)
        assertTrue(contract.reserveBottomSafePadding)
        assertTrue(contract.hideStatsBadges)
        assertTrue(contract.maxKeypadHeightDp <= 300)
    }

    @Test
    fun `sales background refresh ignores compose cancellation`() {
        assertFalse(
            shouldReportSalesBackgroundRefreshFailure(
                IllegalStateException("The coroutine scope left the composition"),
            ),
        )
        assertFalse(shouldReportSalesBackgroundRefreshFailure(CancellationException("normal screen close")))
        assertTrue(shouldReportSalesBackgroundRefreshFailure(IllegalStateException("server failed")))
    }

    @Test
    fun `sales results polling is disabled when realtime is active`() {
        assertTrue(shouldPollSalesResultsWinnerRefreshInBackground(realtimeEnabled = false))
        assertFalse(shouldPollSalesResultsWinnerRefreshInBackground(realtimeEnabled = true))
    }

    @Test
    fun `sales startup keeps local session when only supabase jwt is missing`() {
        val authRequired = SupabaseEdgeException(
            userMessage = "Sesion del servidor requerida. Inicia sesion con internet para continuar.",
            technicalMessage = "Missing Supabase Auth JWT for server-first operation.",
            reason = SupabaseEdgeFailureReason.AUTH_REQUIRED,
        )

        assertTrue(shouldKeepSalesSessionAfterStartupFailure(authRequired))
        assertFalse(shouldKeepSalesSessionAfterStartupFailure(IllegalStateException("local database corrupt")))
    }

    @Test
    fun `cashier server account guard blocks sales when admin disables account`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-1",
            username = "cajero01",
            adminId = "admin-1",
            adminUser = "admin01",
            banca = "Banca Norte",
        )
        val activeAccount = UserAccount(id = "cashier-1", user = "cajero01", role = UserRole.CASHIER, active = true)
        val blockedAccount = activeAccount.copy(active = false)

        assertEquals(SalesServerAccountGuard.ALLOW, resolveSalesServerAccountGuard(session, listOf(activeAccount)))
        assertEquals(SalesServerAccountGuard.CASHIER_BLOCKED_BY_ADMIN, resolveSalesServerAccountGuard(session, listOf(blockedAccount)))
        assertEquals(SalesServerAccountGuard.MISSING, resolveSalesServerAccountGuard(session, emptyList()))
    }

    @Test
    fun `cashier server account guard prefers master block when admin is blocked`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-1",
            username = "cajero01",
            adminId = "admin-1",
            adminUser = "admin01",
        )
        val cashier = UserAccount(id = "cashier-1", user = "cajero01", role = UserRole.CASHIER, active = false, adminId = "admin-1")
        val admin = UserAccount(id = "admin-1", user = "admin01", role = UserRole.ADMIN, active = false)

        val guard = resolveSalesServerAccountGuard(session, listOf(admin, cashier))

        assertEquals(SalesServerAccountGuard.ADMIN_BLOCKED_BY_MASTER, guard)
        assertEquals("Tu admin está bloqueado por Master.", guard.message)
    }

    @Test
    fun `cashier blocked by admin gets credential blocked message`() {
        assertEquals(
            "Tus credenciales están bloqueadas por admin.",
            SalesServerAccountGuard.CASHIER_BLOCKED_BY_ADMIN.message,
        )
    }

    @Test
    fun `server ticket validation timeout stays below anr window`() {
        assertTrue(SALES_SERVER_TICKET_VALIDATION_TIMEOUT_MS <= 4_500L)
    }

    @Test
    fun `lottery keypad keeps double zero shortcuts and decimal point is pick only`() {
        assertEquals(listOf("1", "2", "3", "000"), resolveVentaKeyRows(UserRole.CASHIER)[2])
        assertEquals(listOf("0", "00", "OK"), resolveVentaKeyRows(UserRole.CASHIER).last())
        assertFalse(resolveVentaKeyRows(UserRole.CASHIER).flatten().contains("."))
        assertEquals(2f, resolveVentaKeyWeight("OK"), 0.001f)
        assertEquals(listOf("1", "2", "3", "SELLER"), resolveVentaKeyRows(UserRole.ADMIN)[2])
        assertEquals(listOf("0", "00", "OK"), resolveVentaKeyRows(UserRole.ADMIN).last())
        assertFalse(resolveVentaKeyRows(UserRole.ADMIN).flatten().contains("."))
    }

    @Test
    fun `pick keypad uses compact video style command symbols`() {
        val rows = resolveVentaKeyRows(UserRole.ADMIN, pickKeypad = true)
        val keys = rows.flatten()

        assertFalse(keys.contains("SELLER"))
        assertFalse(keys.contains("000"))
        assertFalse(keys.contains("00"))
        assertFalse(keys.contains("S"))
        assertFalse(keys.contains("B"))
        assertTrue(keys.contains("."))
        assertEquals(listOf("7", "8", "9", "⌫"), rows[0])
        assertEquals(listOf("4", "5", "6", "-"), rows[1])
        assertEquals(listOf("1", "2", "3", "+"), rows[2])
        assertEquals(listOf("0", ".", "/", "*"), rows[3])
        assertEquals(listOf("OK", "PRINT"), rows[4])
        assertEquals(2f, resolveVentaKeyWeight("OK", pickKeypad = true), 0.001f)
        assertEquals(1, keys.count { it == "⌫" })
    }

    @Test
    fun `pick assisted entry detects pick type and straight box suffix`() {
        val pick3 = resolvePickAssistedEntry("252B")
        val pick4 = resolvePickAssistedEntry("2546S")

        assertEquals("252", pick3?.digits)
        assertEquals("Pick3", pick3?.lotteryType)
        assertEquals(PickPlayMode.BOX, pick3?.pickMode)
        assertEquals("2546", pick4?.digits)
        assertEquals("Pick4", pick4?.lotteryType)
        assertEquals(PickPlayMode.STRAIGHT, pick4?.pickMode)
    }

    @Test
    fun `pick assisted entry accepts minus as straight and plus as box`() {
        val straight = resolvePickAssistedEntry("123-")
        val box = resolvePickAssistedEntry("123+")

        assertEquals("123", straight?.digits)
        assertEquals("Pick3", straight?.lotteryType)
        assertEquals(PickPlayMode.STRAIGHT, straight?.pickMode)
        assertEquals("123", box?.digits)
        assertEquals("Pick3", box?.lotteryType)
        assertEquals(PickPlayMode.BOX, box?.pickMode)
    }

    @Test
    fun `pick assisted filter only shows compatible pick lotteries`() {
        val lotteries = listOf(
            lottery(id = "p3-fl", name = "Florida Pick 3", closeTime = "13:00").copy(type = "Pick3"),
            lottery(id = "p4-fl", name = "Florida Pick 4", closeTime = "13:00").copy(type = "Pick4"),
            lottery(id = "ny", name = "New York", closeTime = "20:00").copy(type = "NY"),
        )

        val filtered = filterPickAssistedLotteries(
            lotteries = lotteries,
            assistedEntry = resolvePickAssistedEntry("252B"),
        )

        assertEquals(listOf("p3-fl"), filtered.map { it.id })
    }

    @Test
    fun `pick assisted entry replaces stale normal lottery with compatible pick lottery`() {
        val lotteries = listOf(
            lottery(id = "ny", name = "New York", closeTime = "20:00").copy(type = "NY"),
            lottery(id = "p3-fl", name = "Florida Pick 3", closeTime = "13:00").copy(type = "Pick3"),
            lottery(id = "p4-fl", name = "Florida Pick 4", closeTime = "13:00").copy(type = "Pick4"),
        )

        val selected = resolvePickAssistedLotterySelection(
            currentSelection = listOf("ny"),
            lotteries = lotteries,
            assistedEntry = resolvePickAssistedEntry("252S"),
        )

        assertEquals(listOf("p3-fl"), selected)
    }

    @Test
    fun `pick assisted entry removes mixed normal selection and keeps compatible picks`() {
        val lotteries = listOf(
            lottery(id = "ny", name = "New York", closeTime = "20:00").copy(type = "NY"),
            lottery(id = "p3-fl", name = "Florida Pick 3", closeTime = "13:00").copy(type = "Pick3"),
            lottery(id = "p3-ny", name = "New York Pick 3", closeTime = "21:00").copy(type = "Pick3"),
        )

        val selected = resolvePickAssistedLotterySelection(
            currentSelection = listOf("ny", "p3-ny"),
            lotteries = lotteries,
            assistedEntry = resolvePickAssistedEntry("252B"),
        )

        assertEquals(listOf("p3-ny"), selected)
    }

    @Test
    fun `pick assisted entry switches to equivalent state and draw when pick size changes`() {
        val lotteries = listOf(
            lottery(id = "19", name = "NJ Pick 3 Dia", closeTime = "12:50 PM").copy(type = "Pick3", baseDrawTime = "12:59 PM"),
            lottery(id = "20", name = "NJ Pick 3 Noche", closeTime = "10:50 PM").copy(type = "Pick3", baseDrawTime = "10:57 PM"),
            lottery(id = "21", name = "NJ Pick 4 Dia", closeTime = "12:50 PM").copy(type = "Pick4", baseDrawTime = "12:59 PM"),
            lottery(id = "22", name = "NJ Pick 4 Noche", closeTime = "10:50 PM").copy(type = "Pick4", baseDrawTime = "10:57 PM"),
            lottery(id = "p4-fl", name = "Florida Pick 4 Midday Draw", closeTime = "13:25").copy(type = "Pick4", baseDrawTime = "1:30 PM"),
        )

        val daySelected = resolvePickAssistedLotterySelection(
            currentSelection = listOf("19"),
            lotteries = lotteries,
            assistedEntry = resolvePickAssistedEntry("2546+"),
        )
        val nightSelected = resolvePickAssistedLotterySelection(
            currentSelection = listOf("20"),
            lotteries = lotteries,
            assistedEntry = resolvePickAssistedEntry("2546-"),
        )

        assertEquals(listOf("21"), daySelected)
        assertEquals(listOf("22"), nightSelected)
    }

    @Test
    fun `pick digits without suffix switch equivalent lottery and can advance to amount`() {
        val lotteries = listOf(
            lottery(id = "19", name = "NJ Pick 3 Dia", closeTime = "12:50 PM").copy(type = "Pick3", baseDrawTime = "12:59 PM"),
            lottery(id = "21", name = "NJ Pick 4 Dia", closeTime = "12:50 PM").copy(type = "Pick4", baseDrawTime = "12:59 PM"),
        )
        val assistedEntry = resolvePickAssistedEntry(
            raw = "854",
            selectedLotteries = listOf(lotteries[1]),
            pickMode = PickPlayMode.STRAIGHT,
        )
        val selected = resolvePickAssistedLotterySelection(
            currentSelection = listOf("21"),
            lotteries = lotteries,
            assistedEntry = assistedEntry,
        )
        val pick3 = lotteries.first { it.id == selected.single() }
        val draft = com.lotterynet.pro.core.model.SaleDraft(
            selectedLotteryIds = selected,
            numberInput = assistedEntry?.digits.orEmpty(),
            amountInput = "",
            pickMode = assistedEntry?.pickMode ?: PickPlayMode.STRAIGHT,
        )
        val validator = com.lotterynet.pro.core.sales.SaleValidator()
        val detected = validator.detectPlay(draft, listOf(pick3))
        val hint = validator.getPartialHint(draft, listOf(pick3))

        assertEquals("Pick3", assistedEntry?.lotteryType)
        assertEquals(listOf("19"), selected)
        assertEquals("P3", detected?.playType)
        assertTrue(resolveNumberAdvanceState("854", detected != null, hint != null).canAdvanceToAmount)
    }

    @Test
    fun `future sale controls are disabled after removal`() {
        assertFalse(canUseFutureSale(UserRole.ADMIN))
        assertFalse(canUseFutureSale(UserRole.MASTER))
        assertFalse(canUseFutureSale(UserRole.CASHIER))
        assertEquals(SaleDrawDay.TODAY, resolveSaleFutureModeAfterRole(SaleDrawDay.TOMORROW, UserRole.ADMIN, featureEnabled = true))
    }

    @Test
    fun `lottery picker respects system lottery pick mode`() {
        val lotteries = listOf(
            lottery(id = "p3-fl", name = "Florida Pick 3", closeTime = "13:00").copy(type = "Pick3"),
            lottery(id = "p4-fl", name = "Florida Pick 4", closeTime = "13:00").copy(type = "Pick4"),
            lottery(id = "ny", name = "New York", closeTime = "20:00").copy(type = "NY"),
        )

        assertEquals(
            listOf("ny"),
            filterSaleLotteriesForSystemMode(lotteries, AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = false)).map { it.id },
        )
        assertEquals(
            listOf("p3-fl", "p4-fl"),
            filterSaleLotteriesForSystemMode(lotteries, AdminSystemModeConfig(lotteryModeEnabled = false, pickModeEnabled = true)).map { it.id },
        )
        assertEquals(
            listOf("p3-fl", "p4-fl", "ny"),
            filterSaleLotteriesForSystemMode(lotteries, AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true)).map { it.id },
        )
    }

    @Test
    fun `lottery only sale runtime excludes pick before close calculations`() {
        val lotteries = listOf(
            lottery(id = "p3-fl", name = "Florida Pick 3", closeTime = "13:00").copy(type = "Pick3"),
            lottery(id = "p4-fl", name = "Florida Pick 4", closeTime = "13:00").copy(type = "Pick4"),
            lottery(id = "ny", name = "New York", closeTime = "20:00").copy(type = "NY"),
        )

        val runtimeLotteries = resolveSaleRuntimeLotteriesForSystemMode(
            lotteries = lotteries,
            config = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = false),
        )

        assertEquals(listOf("ny"), runtimeLotteries.map { it.id })
    }

    @Test
    fun `pick mode includes static nj pick 3 and pick 4 lotteries`() {
        val lotteries = com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository().getAllLotteries()

        val pickIds = filterSaleLotteriesForSystemMode(
            lotteries,
            AdminSystemModeConfig(lotteryModeEnabled = false, pickModeEnabled = true),
        ).map { it.id }

        assertTrue(pickIds.contains("19"))
        assertTrue(pickIds.contains("20"))
        assertTrue(pickIds.contains("21"))
        assertTrue(pickIds.contains("22"))
    }

    @Test
    fun `sale picker keeps nj pick night rows in pick and hybrid modes`() {
        val lotteries = com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository().getAllLotteries()
        val decisions = lotteries.associate { lottery ->
            lottery.id to LotteryCloseDecision(
                isClosed = false,
                closeTime = lottery.baseCloseTime,
                state = CloseState.OPEN,
            )
        }

        val pickOnlyIds = resolveAvailableLotteryIdsForPicker(
            lotteries = filterSaleLotteriesForSystemMode(
                lotteries,
                AdminSystemModeConfig(lotteryModeEnabled = false, pickModeEnabled = true),
            ),
            decisionsByLotteryId = decisions,
        )
        val hybridIds = resolveAvailableLotteryIdsForPicker(
            lotteries = filterSaleLotteriesForSystemMode(
                lotteries,
                AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true),
            ),
            decisionsByLotteryId = decisions,
        )

        assertTrue(pickOnlyIds.contains("20"))
        assertTrue(pickOnlyIds.contains("22"))
        assertTrue(hybridIds.contains("20"))
        assertTrue(hybridIds.contains("22"))
    }

    @Test
    fun `combined pick lottery mode keeps large picker list available and ordered`() {
        val normalLotteries = (1..42).map { index ->
            lottery(
                id = "lot-$index",
                name = "Loteria $index",
                closeTime = "%02d:55".format(8 + (index % 12)),
            )
        }
        val pickLotteries = (1..105).map { index ->
            lottery(
                id = "pick-$index",
                name = "Pick $index",
                closeTime = "%02d:50".format(8 + (index % 12)),
            ).copy(type = if (index % 2 == 0) "Pick4" else "Pick3")
        }
        val lotteries = normalLotteries + pickLotteries
        val decisions = lotteries.associate { lottery ->
            lottery.id to LotteryCloseDecision(
                isClosed = false,
                closeTime = lottery.baseCloseTime,
                state = CloseState.OPEN,
            )
        }

        val systemFiltered = filterSaleLotteriesForSystemMode(
            lotteries = lotteries,
            config = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true),
        )
        val availableIds = resolveAvailableLotteryIdsForPicker(
            lotteries = systemFiltered,
            decisionsByLotteryId = decisions,
        )

        assertEquals(147, systemFiltered.size)
        assertEquals(147, availableIds.size)
        assertEquals(147, availableIds.distinct().size)
        assertTrue(availableIds.any { it.startsWith("lot-") })
        assertTrue(availableIds.any { it.startsWith("pick-") })
    }

    @Test
    fun `cashier sale filter hides pick when admin has not allowed cashier pick`() {
        val lotteries = listOf(
            lottery(id = "p3-fl", name = "Florida Pick 3 Midday", closeTime = "12:55 PM").copy(type = "Pick3"),
            lottery(id = "ny", name = "New York Tarde", closeTime = "14:25").copy(type = "NY"),
        )
        val cashierConfig = effectiveAdminSystemModeConfigForRole(
            AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true, cashierPickEnabled = false),
            UserRole.CASHIER,
        )

        val filtered = filterSaleLotteriesForSystemMode(lotteries, cashierConfig)

        assertEquals(listOf("ny"), filtered.map { it.id })
    }

    @Test
    fun `lottery picker in pick mode only shows pick lotteries without auto selecting`() {
        val lotteries = listOf(
            lottery(id = "p3-fl", name = "Florida Pick 3", closeTime = "13:00").copy(type = "Pick3"),
            lottery(id = "p4-fl", name = "Florida Pick 4", closeTime = "13:00").copy(type = "Pick4"),
            lottery(id = "ny", name = "New York", closeTime = "20:00").copy(type = "NY"),
        )

        val filtered = filterVentaLotteryPickerForMode(
            lotteries = lotteries,
            selectedLotteries = listOf(lotteries.first()),
            assistedEntry = null,
        )

        assertEquals(listOf("p3-fl", "p4-fl"), filtered.map { it.id })
    }

    @Test
    fun `lottery picker in pick mode removes canonical duplicate draws`() {
        val catalog = com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository()
        val lotteries = listOfNotNull(
            catalog.getLotteryById("US-P3-NY-NUMBERS-MIDDAY"),
            catalog.getLotteryById("US-P3-NY-PICK-3-MIDDAY"),
            catalog.getLotteryById("US-P4-NY-WIN-4-MIDDAY"),
        )

        val filtered = filterVentaLotteryPickerForMode(
            lotteries = lotteries,
            selectedLotteries = listOf(lotteries.first()),
            assistedEntry = null,
        )

        assertEquals(
            listOf("US-P3-NY-NUMBERS-MIDDAY", "US-P4-NY-WIN-4-MIDDAY"),
            filtered.map { it.id },
        )
    }

    @Test
    fun `lottery picker in pick mode keeps nj pick 3 and pick 4 night rows`() {
        val catalog = com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository()
        val lotteries = catalog.getAllLotteries()
        val selected = listOfNotNull(catalog.getLotteryById("20"))

        val filtered = filterVentaLotteryPickerForMode(
            lotteries = lotteries,
            selectedLotteries = selected,
            assistedEntry = null,
        ).map { it.id }

        assertTrue(filtered.contains("20"))
        assertTrue(filtered.contains("22"))
    }

    @Test
    fun `lottery picker in pick mode keeps nj pick 3 and pick 4 day rows`() {
        val catalog = com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository()
        val lotteries = catalog.getAllLotteries()
        val selected = listOfNotNull(catalog.getLotteryById("19"))

        val filtered = filterVentaLotteryPickerForMode(
            lotteries = lotteries,
            selectedLotteries = selected,
            assistedEntry = null,
        ).map { it.id }

        assertTrue(filtered.contains("19"))
        assertTrue(filtered.contains("21"))
    }

    @Test
    fun `pick mode key writes suffix without clearing entered digits`() {
        val box = applyPickModeKeyToNumber("252", PickPlayMode.BOX)
        val straight = applyPickModeKeyToNumber("252B", PickPlayMode.STRAIGHT)

        assertEquals("252B", box)
        assertEquals("252S", straight)
    }

    @Test
    fun `pick mode symbol writes video style suffix without clearing digits`() {
        assertEquals("252-", applyPickModeSymbolToNumber("252", "-"))
        assertEquals("252+", applyPickModeSymbolToNumber("252", "+"))
        assertEquals("252*", applyPickModeSymbolToNumber("252", "*"))
        assertEquals("252/", applyPickModeSymbolToNumber("252", "/"))
    }

    @Test
    fun `pick mode symbol replaces previous pick suffix`() {
        assertEquals("252+", applyPickModeSymbolToNumber("252-", "+"))
        assertEquals("252-", applyPickModeSymbolToNumber("252B", "-"))
        assertEquals("252*", applyPickModeSymbolToNumber("252S", "*"))
    }

    @Test
    fun `pick number input keeps trailing mode suffix only in pick mode`() {
        assertEquals("852S", sanitizeSaleNumberInput("852s", supportsPickModes = true))
        assertEquals("852B", sanitizeSaleNumberInput("852b", supportsPickModes = true))
        assertEquals("852-", sanitizeSaleNumberInput("852-", supportsPickModes = true))
        assertEquals("852+", sanitizeSaleNumberInput("852+", supportsPickModes = true))
        assertEquals("852*", sanitizeSaleNumberInput("852*", supportsPickModes = true))
        assertEquals("852/", sanitizeSaleNumberInput("852/", supportsPickModes = true))
        assertEquals("852", sanitizeSaleNumberInput("852s", supportsPickModes = false))
        assertEquals("852", sanitizeSaleNumberInput("8s52", supportsPickModes = true))
    }

    @Test
    fun `pick straight box shortcut detects three and four digit entries`() {
        assertEquals("123", resolvePickStraightBoxShortcut("123*")?.digits)
        assertEquals("Pick3", resolvePickStraightBoxShortcut("123*")?.lotteryType)
        assertEquals("1234", resolvePickStraightBoxShortcut("1234*")?.digits)
        assertEquals("Pick4", resolvePickStraightBoxShortcut("1234*")?.lotteryType)
        assertNull(resolvePickStraightBoxShortcut("12*"))
        assertNull(resolvePickStraightBoxShortcut("12345*"))
    }

    @Test
    fun `pick mode key advances to amount after valid pick digits`() {
        assertEquals(SaleInputTarget.AMOUNT, resolvePickModeKeyNextInput("852", PickPlayMode.STRAIGHT, SaleInputTarget.NUMBER))
        assertEquals(SaleInputTarget.AMOUNT, resolvePickModeKeyNextInput("878", PickPlayMode.BOX, SaleInputTarget.NUMBER))
        assertEquals(SaleInputTarget.NUMBER, resolvePickModeKeyNextInput("87", PickPlayMode.BOX, SaleInputTarget.NUMBER))
    }

    @Test
    fun `pick three digits without suffix can advance to amount`() {
        val pick3 = lottery(id = "p3-nj", name = "NJ Pick 3 Noche", closeTime = "22:50").copy(type = "Pick3")
        val draft = com.lotterynet.pro.core.model.SaleDraft(
            selectedLotteryIds = listOf(pick3.id),
            numberInput = "854",
            amountInput = "",
            pickMode = PickPlayMode.STRAIGHT,
        )
        val validator = com.lotterynet.pro.core.sales.SaleValidator()
        val detected = validator.detectPlay(draft, listOf(pick3))
        val hint = validator.getPartialHint(draft, listOf(pick3))

        assertEquals("P3", detected?.playType)
        assertTrue(resolveNumberAdvanceState("854", detected != null, hint != null).canAdvanceToAmount)
    }

    @Test
    fun `admin sale stays under admin until a cashier is selected`() {
        val admin = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin01",
            banca = "Banca Norte",
        )

        val seller = resolveSaleTicketSeller(admin, selectedCashier = null)

        assertEquals("admin-1", seller.sellerId)
        assertEquals("admin01", seller.sellerUser)
        assertEquals(UserRole.ADMIN, seller.role)
        assertEquals("admin01", seller.displayLabel)
    }

    @Test
    fun `admin sale can be attributed to selected cashier`() {
        val admin = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin01",
            banca = "Banca Norte",
        )
        val cashier = UserAccount(
            id = "cashier-2",
            user = "cajero02",
            role = UserRole.CASHIER,
            displayName = "Cajero 02",
            adminId = "admin-1",
        )

        val seller = resolveSaleTicketSeller(admin, cashier)

        assertEquals("cashier-2", seller.sellerId)
        assertEquals("cajero02", seller.sellerUser)
        assertEquals(UserRole.CASHIER, seller.role)
        assertEquals("Cajero 02", seller.displayLabel)
    }

    @Test
    fun `admin profile keeps result grace when sale is attributed to selected cashier`() {
        val admin = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin01",
        )
        val cashier = UserAccount(
            id = "cashier-2",
            user = "cajero02",
            role = UserRole.CASHIER,
            adminId = "admin-1",
        )

        val seller = resolveSaleTicketSeller(admin, cashier)

        assertEquals(UserRole.CASHIER, seller.role)
        assertTrue(canUseAdminResultGraceForSale(admin.role))
        assertFalse(canUseAdminResultGraceForSale(seller.role))
    }

    @Test
    fun `admin delegated seller resets after ticket is saved`() {
        assertNull(resolvePostTicketAdminSellerId(UserRole.ADMIN, selectedSellerId = "cashier-2"))
        assertNull(resolvePostTicketAdminSellerId(UserRole.ADMIN, selectedSellerId = null))
    }

    @Test
    fun `admin seller selector key is icon only with blue surface and white icon`() {
        val visual = resolveVentaSellerKeyVisualContract(active = false)
        val activeVisual = resolveVentaSellerKeyVisualContract(active = true)

        assertTrue(visual.iconOnly)
        assertEquals("blue", visual.backgroundTone)
        assertEquals("white", visual.iconTone)
        assertEquals("blue", activeVisual.backgroundTone)
        assertEquals("white", activeVisual.iconTone)
    }

    @Test
    fun `admin seller picker rows show cashier number and custom name`() {
        val rows = resolveAdminSellerPickerRows(
            cashiers = listOf(
                UserAccount(id = "10", user = "cajero10", role = UserRole.CASHIER, displayName = "Cajero 10 - Banca Yuniel"),
                UserAccount(id = "2", user = "cajero02", role = UserRole.CASHIER, displayName = "Pedro"),
                UserAccount(id = "1", user = "cajero01", role = UserRole.CASHIER, displayName = "Cajero 1 - Ana"),
            ),
            selectedSellerId = "2",
        )

        assertEquals(listOf("Cajero 1", "Cajero 2", "Cajero 10"), rows.map { it.title })
        assertEquals("Ana · cajero01", rows[0].subtitle)
        assertEquals("Pedro · cajero02", rows[1].subtitle)
        assertTrue(rows[1].selected)
    }

    @Test
    fun `admin seller picker keeps blocked cashiers visible but disabled`() {
        val rows = resolveAdminSellerPickerRows(
            cashiers = listOf(
                UserAccount(id = "1", user = "cajero01", role = UserRole.CASHIER, displayName = "Ana", active = false),
            ),
            selectedSellerId = null,
        )

        assertFalse(rows.single().enabled)
        assertEquals("Ana · cajero01 · Bloqueado", rows.single().subtitle)
    }

    @Test
    fun `published server result blocks staged ticket sale for primary and secondary lotteries`() {
        val stagedRows = listOf(
            SaleStagedRow(
                lotteryId = "lot-a",
                lotteryName = "Loteria A",
                secondaryLotteryId = "lot-b",
                secondaryLotteryName = "Loteria B",
                playType = "SP",
                label = "Super Pale",
                number = "12/34",
                displayNumber = "12/34",
                amount = 20.0,
            ),
        )
        val results = listOf(
            LotteryResult(lotteryId = "lot-b", date = "2026-05-05", first = "78"),
            LotteryResult(lotteryId = "lot-c", date = "2026-05-05", first = "11"),
        )

        assertEquals(
            setOf("lot-b"),
            resolvePublishedResultSaleBlockLotteryIds(stagedRows, results),
        )
    }

    @Test
    fun `published nj pick remote ids close legacy sale ids`() {
        val results = listOf(
            LotteryResult(lotteryId = "US-P3-NJ-PICK-3-MIDDAY", lotteryName = "New Jersey Pick 3 Midday Draw", date = "2026-05-10", pick3 = "3-2-9"),
            LotteryResult(lotteryId = "US-P3-NJ-PICK-3-EVENING", lotteryName = "New Jersey Pick 3 Evening Draw", date = "2026-05-10", pick3 = "5-1-4"),
            LotteryResult(lotteryId = "US-P4-NJ-PICK-4-MIDDAY", lotteryName = "New Jersey Pick 4 Midday Draw", date = "2026-05-10", pick4 = "1-1-7-8"),
            LotteryResult(lotteryId = "US-P4-NJ-PICK-4-EVENING", lotteryName = "New Jersey Pick 4 Evening Draw", date = "2026-05-10", pick4 = "7-4-0-2"),
        )

        assertEquals(
            setOf("19", "20", "21", "22"),
            resolvePublishedResultLotteryIdsForSale(results),
        )
    }

    @Test
    fun `published nj pick from previous date does not hide today sale`() {
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-NJ-PICK-3-EVENING",
                lotteryName = "New Jersey Pick 3 Evening Draw",
                date = "2026-05-09",
                pick3 = "5-1-4",
            ),
        )

        assertEquals(
            emptySet<String>(),
            resolvePublishedResultLotteryIdsForSale(results, resultDateKey = "2026-05-10"),
        )
    }

    @Test
    fun `published usa pick day alias blocks matching midday sale id`() {
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-NY-PICK-3-DAY",
                lotteryName = "New York Pick 3 Day Draw",
                date = "2026-05-10",
                pick3 = "9-2-8",
            ),
        )

        assertTrue(
            "Remote DAY result must also close the Android MIDDAY sale id.",
            "US-P3-NY-PICK-3-MIDDAY" in resolvePublishedResultLotteryIdsForSale(results, resultDateKey = "2026-05-10"),
        )
    }

    @Test
    fun `published pick number before official draw time is not marked as published result`() {
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-NJ-PICK-3-MIDDAY",
                lotteryName = "New Jersey Pick 3 Midday Draw",
                date = "2026-05-10",
                pick3 = "8-1-4",
            ),
        )

        assertEquals(
            emptySet<String>(),
            resolvePublishedResultLotteryIdsForSale(
                results = results,
                resultDateKey = "2026-05-10",
                nowUtcMs = utcMillis("2026-05-10T16:58:00Z"),
            ),
        )
    }

    @Test
    fun `published pick number after official draw time is marked as published result`() {
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-NJ-PICK-3-MIDDAY",
                lotteryName = "New Jersey Pick 3 Midday Draw",
                date = "2026-05-10",
                pick3 = "8-1-4",
            ),
        )

        assertEquals(
            setOf("19"),
            resolvePublishedResultLotteryIdsForSale(
                results = results,
                resultDateKey = "2026-05-10",
                nowUtcMs = utcMillis("2026-05-10T17:00:00Z"),
            ),
        )
    }

    @Test
    fun `published pick number at exact official draw minute is marked as published result`() {
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-NJ-PICK-3-MIDDAY",
                lotteryName = "New Jersey Pick 3 Midday Draw",
                date = "2026-05-10",
                pick3 = "8-1-4",
            ),
        )

        assertEquals(
            setOf("19"),
            resolvePublishedResultLotteryIdsForSale(
                results = results,
                resultDateKey = "2026-05-10",
                nowUtcMs = utcMillis("2026-05-10T16:59:00Z"),
            ),
        )
    }

    @Test
    fun `published pick number uses dst aware timezone in winter and summer`() {
        val result = LotteryResult(
            lotteryId = "US-P3-NJ-PICK-3-MIDDAY",
            lotteryName = "New Jersey Pick 3 Midday Draw",
            date = "2026-01-10",
            pick3 = "8-1-4",
        )

        assertEquals(
            emptySet<String>(),
            resolvePublishedResultLotteryIdsForSale(
                results = listOf(result),
                resultDateKey = "2026-01-10",
                nowUtcMs = utcMillis("2026-01-10T17:58:00Z"),
            ),
        )
        assertEquals(
            setOf("19"),
            resolvePublishedResultLotteryIdsForSale(
                results = listOf(result),
                resultDateKey = "2026-01-10",
                nowUtcMs = utcMillis("2026-01-10T17:59:00Z"),
            ),
        )
    }

    @Test
    fun `published nj evening pick waits until exact official night draw minute`() {
        val result = LotteryResult(
            lotteryId = "US-P3-NJ-PICK-3-EVENING",
            lotteryName = "New Jersey Pick 3 Evening Draw",
            date = "2026-05-10",
            pick3 = "5-1-4",
        )

        assertEquals(
            emptySet<String>(),
            resolvePublishedResultLotteryIdsForSale(
                results = listOf(result),
                resultDateKey = "2026-05-10",
                nowUtcMs = utcMillis("2026-05-11T02:56:00Z"),
            ),
        )
        assertEquals(
            setOf("20"),
            resolvePublishedResultLotteryIdsForSale(
                results = listOf(result),
                resultDateKey = "2026-05-10",
                nowUtcMs = utcMillis("2026-05-11T02:57:00Z"),
            ),
        )
    }

    @Test
    fun `published day alias pick result waits for mapped midday draw minute`() {
        val result = LotteryResult(
            lotteryId = "US-P3-ME-PICK-3-DAY",
            lotteryName = "Maine Pick 3 Day Draw",
            date = "2026-05-10",
            pick3 = "1-2-3",
        )

        assertEquals(
            emptySet<String>(),
            resolvePublishedResultLotteryIdsForSale(
                results = listOf(result),
                resultDateKey = "2026-05-10",
                nowUtcMs = utcMillis("2026-05-10T17:09:00Z"),
            ),
        )
        assertEquals(
            setOf("US-P3-ME-PICK-3-DAY"),
            resolvePublishedResultLotteryIdsForSale(
                results = listOf(result),
                resultDateKey = "2026-05-10",
                nowUtcMs = utcMillis("2026-05-10T17:10:00Z"),
            ),
        )
    }

    @Test
    fun `sale published result guard does not block nj night while night draw is still open`() {
        val catalog = com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository()
        val pick3Day = catalog.getLotteryById("19") ?: error("Missing NJ Pick 3 Dia")
        val pick3Night = catalog.getLotteryById("20") ?: error("Missing NJ Pick 3 Noche")
        val decisions = mapOf(
            "19" to LotteryCloseDecision(isClosed = true, reason = "Esperando resultado", closeTime = pick3Day.baseCloseTime, state = CloseState.CLOSED),
            "20" to LotteryCloseDecision(isClosed = false, reason = "155 min restantes", closeTime = pick3Night.baseCloseTime, state = CloseState.OPEN),
        )

        val effective = resolveSalePublishedResultBlockIds(
            publishedResultLotteryIds = setOf("19", "20"),
            decisionsWithoutPublishedResults = decisions,
        )

        assertEquals(setOf("19"), effective)
    }

    @Test
    fun `super pale secondary state requests second lottery when only one is selected`() {
        val state = resolveSuperPaleSecondaryState(
            selectedLotteryIds = listOf("lot-a"),
            availableLotteryIds = listOf("lot-a", "lot-b", "lot-c"),
            classicMode = "SP",
        )

        assertTrue(state.visible)
        assertEquals("lot-a", state.primaryLotteryId)
        assertEquals(null, state.secondaryLotteryId)
        assertEquals(listOf("lot-b", "lot-c"), state.availableSecondaryIds)
        assertTrue(state.requiresSecondarySelection)
    }

    @Test
    fun `super pale secondary state keeps secondary lottery when already selected`() {
        val state = resolveSuperPaleSecondaryState(
            selectedLotteryIds = listOf("lot-a", "lot-b"),
            availableLotteryIds = listOf("lot-a", "lot-b", "lot-c"),
            classicMode = "SP",
        )

        assertTrue(state.visible)
        assertEquals("lot-a", state.primaryLotteryId)
        assertEquals("lot-b", state.secondaryLotteryId)
        assertFalse(state.requiresSecondarySelection)
    }

    @Test
    fun `super pale live lottery selection does not auto choose first open lottery`() {
        assertEquals(
            emptyList<String>(),
            resolveLiveLotterySelection(
                currentSelection = emptyList(),
                availableLotteryIds = listOf("first", "second", "third"),
                classicMode = "SP",
            ),
        )
        assertEquals(
            listOf("first"),
            resolveLiveLotterySelection(
                currentSelection = listOf("first"),
                availableLotteryIds = listOf("first", "second", "third"),
                classicMode = "SP",
            ),
        )
    }

    @Test
    fun `activating super pale starts with free lottery choice instead of carrying last lottery`() {
        val transition = resolveClassicModeTransition(
            nextMode = "SP",
            currentSelection = listOf("lot-prev"),
            fallbackLotteryId = "lot-fallback",
        )

        assertEquals(emptyList<String>(), transition.selection)
        assertEquals("Super Pale activo: elige 2 loterías", transition.message)
    }

    @Test
    fun `ligar quick action reports box toggle for pick lotteries`() {
        val state = resolveQuickActionContract(
            supportsPickModes = true,
            classicMode = "Q",
            pickMode = PickPlayMode.STRAIGHT,
            canLigar = true,
            canToggleSuperPale = false,
            superPaleEnabled = false,
        )

        assertEquals("Box", state.ligarLabel)
        assertTrue(state.ligarEnabled)
        assertFalse(state.superPaleVisible)
    }

    @Test
    fun `super pale quick action exposes active label and explicit lots action`() {
        val state = resolveQuickActionContract(
            supportsPickModes = false,
            classicMode = "SP",
            pickMode = PickPlayMode.STRAIGHT,
            canLigar = false,
            canToggleSuperPale = true,
            superPaleEnabled = true,
        )

        assertEquals("Loterias", state.lotsLabel)
        assertEquals("Ligar", state.ligarLabel)
        assertEquals(com.lotterynet.pro.ui.common.ActionTone.Danger, state.lotsTone)
        assertEquals(com.lotterynet.pro.ui.common.ActionTone.IntenseBlue, state.ligarTone)
        assertTrue(state.ligarEnabled)
        assertEquals("SP Activo", state.superPaleLabel)
        assertTrue(state.superPaleVisible)
    }

    @Test
    fun `lottery picker keeps only open lotteries ordered by earliest close`() {
        val lotteries = listOf(
            lottery(id = "late", name = "Late", closeTime = "18:00"),
            lottery(id = "early", name = "Early", closeTime = "15:00"),
            lottery(id = "closed", name = "Closed", closeTime = "14:00"),
        )
        val decisions = mapOf(
            "late" to LotteryCloseDecision(isClosed = false, closeTime = "18:00", state = CloseState.OPEN),
            "early" to LotteryCloseDecision(isClosed = false, closeTime = "15:00", state = CloseState.WARNING),
            "closed" to LotteryCloseDecision(isClosed = true, closeTime = "14:00", state = CloseState.CLOSED),
        )

        val result = resolveAvailableLotteryIdsForPicker(
            lotteries = lotteries,
            decisionsByLotteryId = decisions,
        )

        assertEquals(listOf("early", "late"), result)
    }

    @Test
    fun `lottery picker excludes primary lottery when selecting super pale secondary`() {
        val lotteries = listOf(
            lottery(id = "a", name = "A", closeTime = "15:00"),
            lottery(id = "b", name = "B", closeTime = "16:00"),
        )
        val decisions = mapOf(
            "a" to LotteryCloseDecision(isClosed = false, closeTime = "15:00", state = CloseState.OPEN),
            "b" to LotteryCloseDecision(isClosed = false, closeTime = "16:00", state = CloseState.OPEN),
        )

        val result = resolveAvailableLotteryIdsForPicker(
            lotteries = lotteries,
            decisionsByLotteryId = decisions,
            excludedLotteryIds = setOf("a"),
        )

        assertEquals(listOf("b"), result)
    }

    @Test
    fun `initial lottery selection prefers earliest close and breaks ties by name`() {
        val lotteries = listOf(
            lottery(id = "king", name = "King Lottery", closeTime = "18:00"),
            lottery(id = "beta", name = "Beta Lotto", closeTime = "15:00"),
            lottery(id = "alpha", name = "Alpha Lotto", closeTime = "15:00"),
        )
        val decisions = mapOf(
            "king" to LotteryCloseDecision(isClosed = false, closeTime = "18:00", state = CloseState.OPEN),
            "beta" to LotteryCloseDecision(isClosed = false, closeTime = "15:00", state = CloseState.OPEN),
            "alpha" to LotteryCloseDecision(isClosed = false, closeTime = "15:00", state = CloseState.OPEN),
        )

        val result = resolveInitialLotterySelection(
            lotteries = lotteries,
            decisionsByLotteryId = decisions,
        )

        assertEquals(listOf("alpha"), result)
    }

    @Test
    fun `initial lottery selection prefers pick when pick sale mode is enabled`() {
        val normal = lottery(id = "normal", name = "Alpha Normal", closeTime = "12:00")
        val pick = lottery(id = "pick", name = "Beta Pick 3", closeTime = "18:00").copy(type = "Pick3")
        val lotteries = listOf(normal, pick)
        val decisions = mapOf(
            "normal" to LotteryCloseDecision(isClosed = false, closeTime = "12:00", state = CloseState.OPEN),
            "pick" to LotteryCloseDecision(isClosed = false, closeTime = "18:00", state = CloseState.OPEN),
        )

        val result = resolveInitialLotterySelection(
            lotteries = lotteries,
            decisionsByLotteryId = decisions,
            preferredLotteryIds = setOf("pick"),
        )

        assertEquals(listOf("pick"), result)
    }

    @Test
    fun `initial lottery selection falls back to first name when all are closed`() {
        val lotteries = listOf(
            lottery(id = "zeta", name = "Zeta Lotto", closeTime = "18:00"),
            lottery(id = "alfa", name = "Alfa Lotto", closeTime = "15:00"),
        )
        val decisions = mapOf(
            "zeta" to LotteryCloseDecision(isClosed = true, closeTime = "18:00", state = CloseState.CLOSED),
            "alfa" to LotteryCloseDecision(isClosed = true, closeTime = "15:00", state = CloseState.CLOSED),
        )

        val result = resolveInitialLotterySelection(
            lotteries = lotteries,
            decisionsByLotteryId = decisions,
        )

        assertEquals(listOf("alfa"), result)
    }

    @Test
    fun `live lottery selection moves closed selected lottery to next available close`() {
        val result = resolveLiveLotterySelection(
            currentSelection = listOf("closed"),
            availableLotteryIds = listOf("early", "late"),
            classicMode = "Q",
        )

        assertEquals(listOf("early"), result)
    }

    @Test
    fun `live lottery selection replaces normal selection with pick when pick is preferred`() {
        val result = resolveLiveLotterySelection(
            currentSelection = listOf("normal"),
            availableLotteryIds = listOf("normal", "pick"),
            classicMode = "Q",
            preferredLotteryIds = setOf("pick"),
        )

        assertEquals(listOf("pick"), result)
    }

    @Test
    fun `live lottery selection preserves multiple open selections for normal plays`() {
        val result = resolveLiveLotterySelection(
            currentSelection = listOf("late", "early"),
            availableLotteryIds = listOf("early", "late", "third"),
            classicMode = "Q",
        )

        assertEquals(listOf("late", "early"), result)
    }

    @Test
    fun `live lottery selection resets stale day selection to first open lottery`() {
        val result = resolveLiveLotterySelection(
            currentSelection = listOf("georgia-night"),
            availableLotteryIds = listOf("anguila-morning", "georgia-night"),
            classicMode = "Q",
            resetToFirstAvailable = true,
        )

        assertEquals(listOf("anguila-morning"), result)
    }

    @Test
    fun `live lottery selection resets stale super pale selection to first open pair`() {
        val result = resolveLiveLotterySelection(
            currentSelection = listOf("georgia-night", "florida-night"),
            availableLotteryIds = listOf("anguila-morning", "primera-dia", "georgia-night"),
            classicMode = "SP",
            resetToFirstAvailable = true,
        )

        assertEquals(listOf("anguila-morning", "primera-dia"), result)
    }

    @Test
    fun `lottery picker replaces normal lottery when selecting pick lottery`() {
        val result = applyLotteryPickerSelection(
            current = listOf("26"),
            selectedLotteryId = "19",
            selectedLotteryType = "Pick3",
            currentSelectionTypes = listOf("NJ"),
            target = LotteryPickerTarget.PRIMARY,
            classicMode = "Q",
        )

        assertEquals(listOf("19"), result)
    }

    @Test
    fun `lottery picker replaces pick lottery when selecting normal lottery`() {
        val result = applyLotteryPickerSelection(
            current = listOf("19"),
            selectedLotteryId = "26",
            selectedLotteryType = "NJ",
            currentSelectionTypes = listOf("Pick3"),
            target = LotteryPickerTarget.PRIMARY,
            classicMode = "Q",
        )

        assertEquals(listOf("26"), result)
    }

    @Test
    fun `lottery picker replaces pick 3 when selecting pick 4`() {
        val result = applyLotteryPickerSelection(
            current = listOf("19"),
            selectedLotteryId = "21",
            selectedLotteryType = "Pick4",
            currentSelectionTypes = listOf("Pick3"),
            target = LotteryPickerTarget.PRIMARY,
            classicMode = "Q",
        )

        assertEquals(listOf("21"), result)
    }

    @Test
    fun `lottery picker treats pick 3 type variants as pick lotteries`() {
        val result = applyLotteryPickerSelection(
            current = listOf("26"),
            selectedLotteryId = "19",
            selectedLotteryType = "pick 3",
            currentSelectionTypes = listOf("NJ"),
            target = LotteryPickerTarget.PRIMARY,
            classicMode = "Q",
        )

        assertEquals(listOf("19"), result)
    }

    @Test
    fun `lottery picker clears mixed normal selection when choosing pick 3`() {
        val result = applyLotteryPickerSelection(
            current = listOf("26", "19"),
            selectedLotteryId = "20",
            selectedLotteryType = "Pick3",
            currentSelectionTypes = listOf("NJ", "Pick3"),
            target = LotteryPickerTarget.PRIMARY,
            classicMode = "Q",
        )

        assertEquals(listOf("20"), result)
    }

    @Test
    fun `sales active type code resolves pick 3 variants`() {
        assertEquals(
            "P3",
            activeTypeCode(
                lottery(id = "19", name = "NJ Pick 3 Dia", closeTime = "12:50").copy(type = "pick 3"),
                classicMode = "Q",
                pickMode = PickPlayMode.STRAIGHT,
            ),
        )
        assertEquals(
            "P3BOX",
            activeTypeCode(
                lottery(id = "19", name = "NJ Pick 3 Dia", closeTime = "12:50").copy(type = "P3"),
                classicMode = "Q",
                pickMode = PickPlayMode.BOX,
            ),
        )
        assertEquals(
            "P3",
            activeTypeCode(
                lottery(id = "19", name = "NJ Pick 3 AM", closeTime = "12:50").copy(type = "Pick 3 AM"),
                classicMode = "Q",
                pickMode = PickPlayMode.STRAIGHT,
            ),
        )
        assertEquals(
            "P3",
            activeTypeCode(
                lottery(id = "19", name = "NJ Pick 3 PM", closeTime = "12:50").copy(type = "NJ Pick 3 PM"),
                classicMode = "Q",
                pickMode = PickPlayMode.STRAIGHT,
            ),
        )
    }

    @Test
    fun `post ticket lottery selection resets to earliest closing lottery after normal play`() {
        val result = resolvePostTicketLotterySelection(
            currentSelection = listOf("later"),
            availableLotteryIds = listOf("next", "later"),
            classicMode = "Q",
        )

        assertEquals(listOf("next"), result)
    }

    @Test
    fun `post ticket lottery selection keeps pick selected when pick is preferred`() {
        val result = resolvePostTicketLotterySelection(
            currentSelection = listOf("pick"),
            availableLotteryIds = listOf("normal", "pick"),
            classicMode = "Q",
            preferredLotteryIds = setOf("pick"),
        )

        assertEquals(listOf("pick"), result)
    }

    @Test
    fun `super pale activation uses the two lotteries already selected`() {
        val result = resolveSuperPaleActivationState(
            selectedLotteryIds = listOf("first", "second"),
        )

        assertTrue(result.canActivate)
        assertEquals(listOf("first", "second"), result.selection)
        assertEquals("Super Pale activado", result.message)
    }

    @Test
    fun `super pale activation rejects one selected lottery`() {
        val result = resolveSuperPaleActivationState(
            selectedLotteryIds = listOf("first"),
        )

        assertFalse(result.canActivate)
        assertEquals(listOf("first"), result.selection)
        assertEquals("Super Pale requiere 2 loterías seleccionadas", result.message)
    }

    @Test
    fun `super pale lottery button opens primary selection first`() {
        assertEquals(
            LotteryPickerTarget.PRIMARY,
            resolveLotteryPickerTargetForLotsButton(
                classicMode = "SP",
                selectedLotteryIds = listOf("last", "second"),
            ),
        )
        assertEquals(
            LotteryPickerTarget.PRIMARY,
            resolveLotteryPickerTargetForLotsButton(
                classicMode = "Q",
                selectedLotteryIds = listOf("last"),
            ),
        )
    }

    @Test
    fun `super pale picker continues to second lottery after choosing primary`() {
        val result = resolveNextLotteryPickerTargetAfterSelection(
            classicMode = "SP",
            nextSelection = listOf("new-primary"),
            currentTarget = LotteryPickerTarget.PRIMARY,
        )

        assertEquals(LotteryPickerTarget.SECONDARY, result)
        assertEquals(
            null,
            resolveNextLotteryPickerTargetAfterSelection(
                classicMode = "SP",
                nextSelection = listOf("new-primary", "new-secondary"),
                currentTarget = LotteryPickerTarget.SECONDARY,
            ),
        )
    }

    @Test
    fun `super pale post ticket returns to normal earliest lottery`() {
        val nextMode = resolvePostTicketClassicMode("SP")
        val result = resolvePostTicketLotterySelection(
            currentSelection = listOf("third", "second"),
            availableLotteryIds = listOf("first", "second", "third"),
            classicMode = nextMode,
        )

        assertEquals("Q", nextMode)
        assertEquals(listOf("first"), result)
    }

    @Test
    fun `lottery decision labels show remaining minutes for warning and danger`() {
        val warning = LotteryCloseDecision(
            isClosed = false,
            reason = "38 min restantes",
            closeTime = "15:00",
            state = CloseState.WARNING,
        )
        val danger = LotteryCloseDecision(
            isClosed = false,
            reason = "8 min restantes",
            closeTime = "15:00",
            state = CloseState.DANGER,
        )

        assertEquals("38 min restantes", presentLotteryDecisionPill(warning))
        assertEquals("38 min restantes", presentLotteryDecisionSubtitle(warning))
        assertEquals("8 min restantes", presentLotteryDecisionPill(danger))
        assertEquals("8 min restantes", presentLotteryDecisionSubtitle(danger))
    }

    @Test
    fun `lottery visible clocks use twelve hour format`() {
        assertEquals("9:55 AM", formatLotteryClock12("09:55"))
        assertEquals("5:55 PM", formatLotteryClock12("17:55"))
        assertEquals("12:50 PM", formatLotteryClock12("12:50 PM"))
    }

    @Test
    fun `closed lottery subtitle formats close time as twelve hour clock`() {
        val closed = LotteryCloseDecision(
            isClosed = true,
            reason = "Esperando resultado",
            closeTime = "17:55",
            state = CloseState.CLOSED,
        )

        assertEquals(
            "Cerró a las 5:55 PM y queda cerrada hasta que cambie el día.",
            presentLotteryDecisionSubtitle(closed),
        )
    }

    @Test
    fun `tight sales keypad layout removes badges and shrinks key spacing`() {
        val contract = resolveVentaKeypadLayout(LotteryNetWindowMode.POS_TIGHT)

        assertFalse(contract.showStatsBadges)
        assertEquals(0, contract.keySpacingDp)
        assertEquals(44, contract.keyHeightDp)
        assertTrue(contract.totalAboveKeypad)
    }

    @Test
    fun `phone sales keypad stays compact and drops helper badges`() {
        val contract = resolveVentaKeypadLayout(LotteryNetWindowMode.POS)

        assertFalse(contract.showStatsBadges)
        assertEquals(0, contract.keySpacingDp)
        assertEquals(48, contract.keyHeightDp)
    }

    @Test
    fun `wide sales keypad keeps badges for larger screens`() {
        val contract = resolveVentaKeypadLayout(LotteryNetWindowMode.WIDE)

        assertTrue(contract.showStatsBadges)
        assertEquals(2, contract.keySpacingDp)
        assertEquals(50, contract.keyHeightDp)
    }

    @Test
    fun `sale keypad keeps ok at the numeric bottom right`() {
        val keys = resolveVentaKeyRows().flatten()

        assertFalse(keys.contains("ENTER"))
        assertFalse(keys.contains("CLR"))
        assertTrue(keys.contains("PRINT"))
        assertTrue(keys.contains("OK"))
        assertEquals(1, keys.count { it == "PRINT" })
        assertEquals(1, keys.count { it == "OK" })
        assertEquals(4, resolveVentaKeyRows().size)
        assertTrue(resolveVentaKeyRows().dropLast(1).all { it.size == 4 })
        assertEquals(3, resolveVentaKeyRows().last().size)
        assertEquals("OK", resolveVentaKeyRows().last().last())
    }

    @Test
    fun `sale keypad typography keeps numbers large and bold while compact`() {
        val contract = resolveVentaKeypadLayout(LotteryNetWindowMode.POS)

        assertTrue(contract.numberKeyFontSp >= 24)
        assertTrue(contract.commandKeyFontSp >= 15)
        assertTrue(contract.strongTextOnly)
        assertTrue(contract.secondaryTextUsesInk)
    }

    @Test
    fun `staged venta list stays dense with invisible separators and stronger numbers`() {
        val contract = resolveVentaStagedListLayout(LotteryNetWindowMode.POS)

        assertTrue(contract.rowVerticalPaddingDp <= 3)
        assertEquals(0f, contract.separatorAlpha, 0.001f)
        assertTrue(contract.numberWeight >= 900)
        assertFalse(contract.enlargeNumberFont)
    }

    @Test
    fun `backspace key is destructive red and uses icon`() {
        val contract = resolveVentaKeyVisualContract("⌫")

        assertTrue(contract.dangerTone)
        assertTrue(contract.useIcon)
        assertEquals("Borrar", contract.contentDescription)
    }

    @Test
    fun `lottery button uses black surface with white icon`() {
        val contract = resolveVentaLotteryButtonVisualContract()

        assertEquals("intense-red", contract.background)
        assertEquals("white", contract.labelTint)
        assertEquals("white", contract.iconTint)
        assertTrue(contract.keepCurrentSize)
    }

    @Test
    fun `ticket action saves staged rows before opening preview`() {
        val contract = resolveTicketPreviewAccessContract(
            stagedRowCount = 2,
            hasLatestTicket = false,
        )

        assertTrue(contract.showAction)
        assertTrue(contract.shouldSaveBeforeOpen)
    }

    @Test
    fun `ticket action opens latest preview when there is no staged sale`() {
        val contract = resolveTicketPreviewAccessContract(
            stagedRowCount = 0,
            hasLatestTicket = true,
        )

        assertTrue(contract.showAction)
        assertFalse(contract.shouldSaveBeforeOpen)
    }

    @Test
    fun `print action stages valid current play before saving ticket`() {
        val contract = resolveTicketPrintOpenContract(
            stagedRowCount = 0,
            hasLatestTicket = false,
            currentEntryValid = true,
            number = "18",
            amount = "50",
            hasPendingConfirmation = false,
        )

        assertTrue(contract.showAction)
        assertTrue(contract.stageCurrentPlayBeforeSave)
        assertTrue(contract.saveBeforeOpen)
        assertFalse(contract.openLatestTicket)
        assertNull(contract.fallbackMessage)
    }

    @Test
    fun `print action does not hide incomplete current play behind latest ticket`() {
        val contract = resolveTicketPrintOpenContract(
            stagedRowCount = 0,
            hasLatestTicket = true,
            currentEntryValid = false,
            number = "18",
            amount = "",
            hasPendingConfirmation = false,
        )

        assertTrue(contract.showAction)
        assertFalse(contract.stageCurrentPlayBeforeSave)
        assertFalse(contract.saveBeforeOpen)
        assertFalse(contract.openLatestTicket)
        assertEquals("Completa la jugada y el monto antes de imprimir", contract.fallbackMessage)
    }

    @Test
    fun `sale thermal print failure stays in sale instead of opening printer screen`() {
        val result = resolveSaleThermalPrintResult(success = false, message = "No hay impresora conectada")

        assertEquals("No hay impresora conectada", result.message)
        assertFalse(result.closePreview)
        assertFalse(result.openPrinterSettings)
    }

    @Test
    fun `ticket delivery avoids bitmap preview for large multi lottery tickets`() {
        val ticket = TicketRecord(
            id = "large-ticket",
            plays = (1..5).flatMap { lotteryIndex ->
                (1..8).map { playIndex ->
                    com.lotterynet.pro.core.model.PlayItem(
                        number = playIndex.toString().padStart(2, '0'),
                        playType = "Q",
                        amount = 100.0,
                        lotteryName = "Loteria $lotteryIndex",
                    )
                }
            },
        )

        assertFalse(shouldRenderSaleDeliveryBitmapPreview(ticket))
    }

    @Test
    fun `ticket delivery preview uses instant snapshot instead of bitmap render`() {
        val ticket = TicketRecord(
            id = "small-ticket",
            plays = listOf(
                com.lotterynet.pro.core.model.PlayItem(
                    number = "18",
                    playType = "Q",
                    amount = 25.0,
                    lotteryName = "Gana Más",
                ),
            ),
            total = 25.0,
        )

        assertFalse(shouldRenderSaleDeliveryBitmapPreview(ticket))
    }

    @Test
    fun `venta entry strip balances jugada limit and amount boxes`() {
        val contract = resolveVentaEntryStripLayout(LotteryNetWindowMode.POS)

        assertTrue(contract.jugadaWeight <= contract.montoWeight)
        assertTrue(contract.limitWidthDp >= 100)
        assertTrue(contract.limitFontSp <= 15)
    }

    @Test
    fun `post add feedback stays silent to keep venta compact`() {
        assertEquals(null, resolvePostAddFeedbackMessage())
    }

    @Test
    fun `inline venta feedback hides normal notices but keeps real errors`() {
        assertFalse(
            shouldShowVentaInlineFeedbackBanner(
                feedbackMessage = "Jugada repetida no modificada",
                feedbackIsError = false,
                numberHasError = false,
            ),
        )
        assertFalse(
            shouldShowVentaInlineFeedbackBanner(
                feedbackMessage = "Monto requerido",
                feedbackIsError = true,
                numberHasError = true,
            ),
        )
        assertTrue(
            shouldShowVentaInlineFeedbackBanner(
                feedbackMessage = "Monto requerido",
                feedbackIsError = true,
                numberHasError = false,
            ),
        )
    }

    @Test
    fun `venta hides save and bluetooth sync status so staged plays stay visible`() {
        assertFalse(shouldShowVentaTicketSaveSyncStatus())
    }

    @Test
    fun `future sale mode is removed from sales`() {
        assertFalse(canUseFutureSale(UserRole.ADMIN))
        assertFalse(canUseFutureSale(UserRole.CASHIER))
        assertFalse(canUseFutureSaleControls(UserRole.ADMIN, featureEnabled = false))
        assertFalse(canUseFutureSaleControls(UserRole.ADMIN, featureEnabled = true))
        assertEquals(SaleDrawDay.TODAY, resolveSaleFutureModeAfterRole(SaleDrawDay.TOMORROW, UserRole.CASHIER, featureEnabled = true))
        assertEquals(SaleDrawDay.TODAY, resolveSaleFutureModeAfterRole(SaleDrawDay.TOMORROW, UserRole.ADMIN, featureEnabled = false))
        assertEquals(SaleDrawDay.TODAY, resolveSaleFutureModeAfterRole(SaleDrawDay.TOMORROW, UserRole.ADMIN, featureEnabled = true))
    }

    @Test
    fun `post add flow returns to jugada and keeps current amount`() {
        val state = resolvePostAddCarryState(amount = "65")

        assertEquals(SaleInputTarget.NUMBER, state.activeInput)
        assertEquals("", state.number)
        assertEquals("65", state.amount)
        assertTrue(state.replaceAmountOnNextDigit)
    }

    @Test
    fun `first amount digit replaces carried amount instead of appending`() {
        val state = applySaleKeypadInput(
            activeInput = SaleInputTarget.AMOUNT,
            key = "6",
            number = "25",
            amount = "65",
            replaceAmountOnNextDigit = true,
        )

        assertEquals("6", state.amount)
        assertFalse(state.replaceAmountOnNextDigit)
    }

    @Test
    fun `amount keeps appending normally after carry mode is consumed`() {
        val state = applySaleKeypadInput(
            activeInput = SaleInputTarget.AMOUNT,
            key = "7",
            number = "25",
            amount = "65",
            replaceAmountOnNextDigit = false,
        )

        assertEquals("657", state.amount)
        assertFalse(state.replaceAmountOnNextDigit)
    }

    @Test
    fun `amount keypad accepts one decimal point and two cents digits`() {
        val decimal = applySaleKeypadInput(
            activeInput = SaleInputTarget.AMOUNT,
            key = ".",
            number = "25",
            amount = "",
            replaceAmountOnNextDigit = false,
        )
        val centsOne = applySaleKeypadInput(decimal.activeInput, "5", decimal.number, decimal.amount, decimal.replaceAmountOnNextDigit)
        val centsTwo = applySaleKeypadInput(centsOne.activeInput, "0", centsOne.number, centsOne.amount, centsOne.replaceAmountOnNextDigit)
        val ignoredThirdCent = applySaleKeypadInput(centsTwo.activeInput, "9", centsTwo.number, centsTwo.amount, centsTwo.replaceAmountOnNextDigit)
        val ignoredSecondDot = applySaleKeypadInput(centsTwo.activeInput, ".", centsTwo.number, centsTwo.amount, centsTwo.replaceAmountOnNextDigit)

        assertEquals("0.", decimal.amount)
        assertEquals("0.50", centsTwo.amount)
        assertEquals("0.50", ignoredThirdCent.amount)
        assertEquals("0.50", ignoredSecondDot.amount)
    }

    @Test
    fun `decimal point does not change number input`() {
        val state = applySaleKeypadInput(
            activeInput = SaleInputTarget.NUMBER,
            key = ".",
            number = "25",
            amount = "",
            replaceAmountOnNextDigit = false,
        )

        assertEquals("25", state.number)
        assertEquals("", state.amount)
    }

    @Test
    fun `display formatter separates pale and super pale numbers`() {
        assertEquals("12/34", formatPlayDisplayNumber("12-34", "P"))
        assertEquals("56/78", formatPlayDisplayNumber("56-78", "SP"))
    }

    @Test
    fun `staged play list scrolls to newest top only when a play is appended`() {
        assertEquals(
            StagedPlayScrollAction.ScrollToFirst,
            resolveStagedPlayScrollAction(previousCount = 4, currentCount = 5),
        )
        assertEquals(
            StagedPlayScrollAction.KeepPosition,
            resolveStagedPlayScrollAction(previousCount = 5, currentCount = 4),
        )
        assertEquals(
            StagedPlayScrollAction.KeepPosition,
            resolveStagedPlayScrollAction(previousCount = 5, currentCount = 0),
        )
    }

    @Test
    fun `staged play list scrolls back to top when newest row changes without count change`() {
        assertEquals(
            StagedPlayScrollAction.ScrollToFirst,
            resolveStagedPlayScrollAction(
                previousCount = 5,
                currentCount = 5,
                previousFirstRowId = "old-top",
                currentFirstRowId = "new-top",
            ),
        )
        assertEquals(
            StagedPlayScrollAction.KeepPosition,
            resolveStagedPlayScrollAction(
                previousCount = 5,
                currentCount = 5,
                previousFirstRowId = "same-top",
                currentFirstRowId = "same-top",
            ),
        )
    }

    @Test
    fun `number entry cannot advance to amount when empty or invalid`() {
        assertFalse(resolveNumberAdvanceState(number = "", hasDetectedPlay = false, hasPartialHint = false).canAdvanceToAmount)
        assertFalse(resolveNumberAdvanceState(number = "1", hasDetectedPlay = false, hasPartialHint = true).canAdvanceToAmount)
        assertFalse(resolveNumberAdvanceState(number = "12345", hasDetectedPlay = false, hasPartialHint = false).canAdvanceToAmount)
        assertTrue(resolveNumberAdvanceState(number = "12", hasDetectedPlay = true, hasPartialHint = false).canAdvanceToAmount)
    }

    @Test
    fun `number entry shows red box when incomplete or malformed`() {
        assertFalse(resolveNumberAdvanceState(number = "", hasDetectedPlay = false, hasPartialHint = false).showNumberError)
        assertTrue(resolveNumberAdvanceState(number = "1", hasDetectedPlay = false, hasPartialHint = true).showNumberError)
        assertTrue(resolveNumberAdvanceState(number = "12345", hasDetectedPlay = false, hasPartialHint = false).showNumberError)
        assertFalse(resolveNumberAdvanceState(number = "12", hasDetectedPlay = true, hasPartialHint = false).showNumberError)
    }

    @Test
    fun `cashier sale limit badge shows configured limit and admin has no cap`() {
        val limits = CashierSalesLimitInputs(quiniela = 10000.0, pale = 500.0, tripleta = 75.0)

        assertEquals("10,000", resolveSaleLimitBadgeMain(UserRole.CASHIER, "Q", PickPlayMode.STRAIGHT, limits))
        assertEquals("500", resolveSaleLimitBadgeMain(UserRole.CASHIER, "P", PickPlayMode.STRAIGHT, limits))
        assertEquals("Sin tope", resolveSaleLimitBadgeMain(UserRole.ADMIN, "Q", PickPlayMode.STRAIGHT, limits))
    }

    @Test
    fun `cashier sale limit badge shows remaining global amount`() {
        val limits = CashierSalesLimitInputs(quiniela = 10000.0, pale = 500.0)

        assertEquals(
            "9,900",
            resolveSaleLimitBadgeMain(
                role = UserRole.CASHIER,
                classicMode = "Q",
                pickMode = PickPlayMode.STRAIGHT,
                limits = limits,
                sold = 75.0,
                pending = 25.0,
            ),
        )
        assertEquals(
            "0",
            resolveSaleLimitBadgeMain(
                role = UserRole.CASHIER,
                classicMode = "P",
                pickMode = PickPlayMode.STRAIGHT,
                limits = limits,
                sold = 700.0,
                pending = 0.0,
            ),
        )
    }

    @Test
    fun `cashier sale limit tone starts green and turns red when amount consumes limit`() {
        assertEquals(
            SaleLimitBadgeTone.GREEN,
            resolveSaleLimitBadgeTone(limit = 10000.0, sold = 0.0, pending = 0.0),
        )
        assertEquals(
            SaleLimitBadgeTone.RED,
            resolveSaleLimitBadgeTone(limit = 10000.0, sold = 0.0, pending = 25.0),
        )
        assertEquals(
            SaleLimitBadgeTone.RED,
            resolveSaleLimitBadgeTone(limit = 10000.0, sold = 50.0, pending = 0.0),
        )
        assertEquals(
            SaleLimitBadgeTone.GREEN,
            resolveSaleLimitBadgeTone(limit = 0.0, sold = 0.0, pending = 25.0),
        )
    }

    @Test
    fun `cashier sale limit preview includes current amount before adding play`() {
        assertEquals(30.0, resolveSaleLimitPendingPreview(stagedPending = 10.0, currentAmount = 20.0), 0.001)
        assertEquals(10.0, resolveSaleLimitPendingPreview(stagedPending = 10.0, currentAmount = null), 0.001)
    }

    @Test
    fun `cashier sale limit badge shows staged remaining when no active play is selected`() {
        val stagedLimit = SaleLimitRemainingRow(
            playType = "Q",
            number = "65",
            limit = 500.0,
            sold = 0.0,
            pending = 138.0,
        )

        assertEquals("362", resolveSaleLimitBadgeMain(UserRole.CASHIER, stagedLimit))
        assertEquals("Sin tope", resolveSaleLimitBadgeMain(UserRole.ADMIN, stagedLimit))
    }

    @Test
    fun `cashier sale limit can preview detected number before amount is entered`() {
        val stagedLimit = SaleLimitRemainingRow(
            playType = "Q",
            number = "65",
            limit = 500.0,
            sold = 0.0,
            pending = 138.0,
        )

        assertEquals(
            "362",
            resolveSaleLimitBadgeMain(
                role = UserRole.CASHIER,
                row = stagedLimit,
                currentAmount = null,
            ),
        )
    }

    @Test
    fun `cashier limits and exposure fallback polling are server friendly`() {
        assertTrue(CASHIER_LIMIT_PULL_INTERVAL_MS >= 60_000L)
        assertTrue(SALES_EXPOSURE_REFRESH_INTERVAL_MS >= 30_000L)
    }

    @Test
    fun `sales does not poll remote users in cashier background loop`() {
        assertFalse(shouldPollSalesServerAccountGuardInBackground(UserRole.CASHIER, realtimeEnabled = false))
        assertFalse(shouldPollSalesServerAccountGuardInBackground(UserRole.CASHIER, realtimeEnabled = true))
        assertFalse(shouldPollSalesServerAccountGuardInBackground(UserRole.ADMIN, realtimeEnabled = false))
    }

    @Test
    fun `pos wireless print uses integrated printer when no bluetooth is selected`() {
        assertEquals(
            SaleThermalPrintTarget.INTEGRATED,
            resolveSaleThermalPrintTarget(hasBluetoothPrinter = false, hasIntegratedPrinter = true),
        )
        assertEquals(
            listOf(SaleThermalPrintTarget.INTEGRATED),
            resolveSaleThermalPrintTargets(hasBluetoothPrinter = false, hasIntegratedPrinter = true),
        )
        assertEquals(
            listOf(SaleThermalPrintTarget.NONE),
            resolveSaleThermalPrintTargets(hasBluetoothPrinter = false, hasIntegratedPrinter = false),
        )
    }

    @Test
    fun `ticket delivery print keeps integrated and bluetooth paths independent`() {
        assertEquals(
            SaleThermalPrintTarget.BLUETOOTH,
            resolveSaleThermalPrintTarget(hasBluetoothPrinter = true, hasIntegratedPrinter = true),
        )
        assertEquals(
            listOf(SaleThermalPrintTarget.BLUETOOTH, SaleThermalPrintTarget.INTEGRATED),
            resolveSaleThermalPrintTargets(hasBluetoothPrinter = true, hasIntegratedPrinter = true),
        )
        assertEquals(
            listOf(SaleThermalPrintTarget.BLUETOOTH),
            resolveSaleThermalPrintTargets(hasBluetoothPrinter = true, hasIntegratedPrinter = false),
        )
    }

    @Test
    fun `pick 3 complete number stays in jugada until enter`() {
        val state = resolveAutoAdvanceInputAfterNumber(
            activeInput = SaleInputTarget.NUMBER,
            number = "123",
            selectedLotteries = listOf(lottery(id = "p3", name = "Pick 3", closeTime = "13:00").copy(type = "Pick3")),
            classicMode = "Q",
        )

        assertEquals(SaleInputTarget.NUMBER, state)
    }

    @Test
    fun `enter moves valid pick 3 number to amount`() {
        assertEquals(
            SaleInputTarget.AMOUNT,
            resolveEnterInputTarget(
                activeInput = SaleInputTarget.NUMBER,
                canAdvanceToAmount = true,
            ),
        )
        assertEquals(
            SaleInputTarget.NUMBER,
            resolveEnterInputTarget(
                activeInput = SaleInputTarget.NUMBER,
                canAdvanceToAmount = false,
            ),
        )
    }

    @Test
    fun `staged list keeps newest play visible at top`() {
        assertFalse(shouldReverseVentaStagedListForLatestPlay())
    }

    @Test
    fun `venta top qr action opens scanner choice flow`() {
        val contract = resolveVentaQrLookupActionContract()
        val choices = resolveVentaQrLookupChoices()

        assertEquals("QR", contract.label)
        assertEquals("buscar", contract.lookupMode)
        assertFalse(contract.autoScan)
        assertEquals(listOf("pagar", "duplicar"), choices.map { it.lookupMode })
        assertTrue(choices.all { it.autoScan })
    }

    @Test
    fun `duplicate staged play asks before adding more amount`() {
        val existing = SaleStagedRow(
            id = "row-1",
            lotteryId = "lot-a",
            lotteryName = "Loteria A",
            playType = "Q",
            label = "Quiniela",
            number = "15",
            displayNumber = "15",
            amount = 5.0,
        )
        val duplicate = findDuplicateStagedRows(
            existingRows = listOf(existing),
            validation = SaleValidationResult(
                isValid = true,
                normalizedAmount = 10.0,
                resolvedPlay = SaleResolvedPlay(
                    playType = "Q",
                    label = "Quiniela",
                    normalizedNumber = "15",
                    displayNumber = "15",
                ),
            ),
            selectedLotteries = listOf(lottery(id = "lot-a", name = "Loteria A", closeTime = "20:00")),
        )

        assertEquals(listOf(existing), duplicate)
        assertTrue(resolveDuplicatePlayPromptText(existing, 10.0).contains("sumar 10"))
    }

    @Test
    fun `ligar amount prompt suggests current amount but accepts replacement`() {
        val prompt = resolveLigarAmountPromptState(currentAmount = "25")

        assertEquals("25", prompt.initialAmount)
        assertEquals(10.0, parseLigarAmountForConfirmation("10") ?: 0.0, 0.001)
    }

    @Test
    fun `ligar amount prompt keeps user in amount entry when amount is missing`() {
        val prompt = resolveLigarAmountPromptState(currentAmount = "")

        assertEquals("", prompt.initialAmount)
        assertNull(parseLigarAmountForConfirmation(""))
        assertNull(parseLigarAmountForConfirmation("0"))
    }

    private fun lottery(id: String, name: String, closeTime: String): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = id,
            name = name,
            type = "RD",
            baseDrawTime = "20:00",
            baseCloseTime = closeTime,
            colorHex = "#ffffff",
        )
    }

    private fun utcMillis(value: String): Long = Instant.parse(value).toEpochMilli()
}
