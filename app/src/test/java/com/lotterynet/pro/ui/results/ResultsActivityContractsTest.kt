package com.lotterynet.pro.ui.results

import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.model.ClockSource
import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.ResultsSharePayload
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.TrustedClockSnapshot
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.results.PickResultIdentityResolver
import com.lotterynet.pro.core.repository.HolidayCalendarRepository
import com.lotterynet.pro.core.repository.TrustedClockRepository
import com.lotterynet.pro.core.render.resultsRenderCacheKey
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ResultsActivityContractsTest {

    @Test
    fun `phone results keeps filters actions and rows compact`() {
        val tight = resolveResultsLayout(LotteryNetWindowMode.POS_TIGHT)
        val phone = resolveResultsLayout(LotteryNetWindowMode.POS)

        assertTrue(tight.compactHeader)
        assertTrue(phone.compactHeader)
        assertTrue(tight.useCompactRows)
        assertTrue(tight.inlinePrimaryNumbers)
        assertTrue(tight.minTouchTargetDp >= 44)
        assertEquals(3, phone.actionColumns)
        assertTrue(tight.headerPaddingVerticalDp <= 6)
        assertTrue(phone.rowPaddingVerticalDp <= 7)
        assertTrue(tight.resultBallSizeDp <= phone.resultBallSizeDp)
    }

    @Test
    fun `wide results keeps room for richer result cards`() {
        val wide = resolveResultsLayout(LotteryNetWindowMode.WIDE)

        assertFalse(wide.compactHeader)
        assertTrue(wide.headerPaddingVerticalDp >= 8)
        assertTrue(wide.rowPaddingVerticalDp >= 9)
        assertTrue(wide.resultBallSizeDp >= 42)
    }

    @Test
    fun `compose cancellations are ignored for sentry reporting`() {
        assertFalse(shouldReportResultsRefreshError(CancellationException("left composition")))
    }

    @Test
    fun `results refresh timeout is treated as reportable failure`() {
        val timeout = runCatching {
            runBlocking {
                withTimeout(1L) {
                    while (true) {
                        delay(50L)
                    }
                }
            }
        }.exceptionOrNull() ?: error("Expected timeout")
        assertTrue(shouldReportResultsRefreshError(timeout))
    }

    @Test
    fun `results auto refresh retries quickly after draw`() {
        assertTrue(shouldRunResultsAutoRefresh(nowMs = 0L, lastStartedMs = null))
        assertFalse(shouldRunResultsAutoRefresh(nowMs = 29_000L, lastStartedMs = 0L))
        assertTrue(shouldRunResultsAutoRefresh(nowMs = 60_000L, lastStartedMs = 0L))
    }

    @Test
    fun `results auto refresh asks server only after draw is due`() {
        assertFalse(shouldAutoRefreshResultsFromServer(selectedDateIsToday = true, hasWaitingResult = false, hasRecoverableNoDrawResult = false, realtimeEnabled = false))
        assertTrue(shouldAutoRefreshResultsFromServer(selectedDateIsToday = true, hasWaitingResult = true, hasRecoverableNoDrawResult = false, realtimeEnabled = false))
        assertFalse(shouldAutoRefreshResultsFromServer(selectedDateIsToday = false, hasWaitingResult = true, hasRecoverableNoDrawResult = false, realtimeEnabled = false))
        assertTrue(shouldAutoRefreshResultsFromServer(selectedDateIsToday = true, hasWaitingResult = true, hasRecoverableNoDrawResult = false, realtimeEnabled = true))
        assertTrue(shouldAutoRefreshResultsFromServer(selectedDateIsToday = true, hasWaitingResult = false, hasRecoverableNoDrawResult = true, realtimeEnabled = true))
    }

    @Test
    fun `results auto refresh uses server friendly fallback delay`() {
        assertEquals(60_000L, resolveResultsAutoRefreshDelayMs(realtimeEnabled = false))
        assertEquals(60_000L, resolveResultsAutoRefreshDelayMs(realtimeEnabled = true))
    }

    @Test
    fun `results refresh applies only to the currently selected date`() {
        assertTrue(shouldApplyResultsRefreshForSelectedDate(refreshDate = "27-04-2026", selectedDate = "27-04-2026"))
        assertFalse(shouldApplyResultsRefreshForSelectedDate(refreshDate = "26-04-2026", selectedDate = "27-04-2026"))
    }

    @Test
    fun `results cache warms today yesterday and anteayer without duplicates`() {
        assertEquals(
            listOf("27-04-2026", "26-04-2026", "25-04-2026"),
            resolveResultsDateCacheWarmupDates(defaultDate = "27-04-2026", today = "27-04-2026"),
        )
        assertEquals(
            listOf("24-04-2026", "27-04-2026", "26-04-2026", "25-04-2026"),
            resolveResultsDateCacheWarmupDates(defaultDate = "24-04-2026", today = "27-04-2026"),
        )
    }

    @Test
    fun `published result is hidden until draw time passes`() {
        assertFalse(shouldPublishResultForDraw(selectedDateIsPast = false, selectedDateIsToday = true, minutesToDraw = 1))
        assertTrue(shouldPublishResultForDraw(selectedDateIsPast = false, selectedDateIsToday = true, minutesToDraw = 0))
        assertTrue(shouldPublishResultForDraw(selectedDateIsPast = true, selectedDateIsToday = false, minutesToDraw = 30))
    }

    @Test
    fun `real refresh failures still report to sentry`() {
        assertTrue(shouldReportResultsRefreshError(IllegalStateException("unexpected")))
    }

    @Test
    fun `results refresh action exposes spinning ready and error states`() {
        assertEquals(
            ResultsRefreshActionUi(spinning = true, enabled = false, message = "Buscando resultados remotos..."),
            resolveResultsRefreshActionUi(isRefreshing = true, lastManualRefreshSucceeded = null),
        )
        assertEquals(
            ResultsRefreshActionUi(spinning = false, enabled = true, message = "Resultados actualizados."),
            resolveResultsRefreshActionUi(isRefreshing = false, lastManualRefreshSucceeded = true),
        )
        assertEquals(
            ResultsRefreshActionUi(spinning = false, enabled = true, message = "Actualización falló."),
            resolveResultsRefreshActionUi(isRefreshing = false, lastManualRefreshSucceeded = false),
        )
    }

    @Test
    fun `results source labels hide technical provider names`() {
        assertEquals("Servidor", presentResultsSourceLabel("supabase"))
        assertEquals("Local", presentResultsSourceLabel("local"))
        assertEquals("No sorteo", presentResultsSourceLabel("no_draw"))
        assertEquals("Manual", presentResultsSourceLabel("manual-override"))
    }

    @Test
    fun `manual results editor is visible only for admin roles`() {
        assertTrue(shouldShowManualResultsEditor(UserRole.ADMIN))
        assertTrue(shouldShowManualResultsEditor(UserRole.MASTER))
        assertFalse(shouldShowManualResultsEditor(UserRole.CASHIER))
    }

    @Test
    fun `manual result validation respects lottery and pick formats`() {
        assertTrue(validateManualResultInput("12-34-56", "lottery"))
        assertTrue(validateManualResultInput("1-2-3", "pick3"))
        assertTrue(validateManualResultInput("1-2-3-4", "pick4"))
        assertFalse(validateManualResultInput("123", "pick3"))
        assertFalse(validateManualResultInput("12-34-56", "pick4"))
    }

    @Test
    fun `results action panel keeps share actions before print and copy`() {
        assertEquals(
            listOf(
                ResultsActionId.WHATSAPP,
                ResultsActionId.SHARE,
                ResultsActionId.PRINT,
            ),
            resolveResultsActionOrder(),
        )
    }

    @Test
    fun `results action panel exposes one export menu`() {
        val contract = resolveResultsExportMenuContract(showPrint = true)

        assertEquals("Exportar", contract.visibleButtonLabel)
        assertEquals(1, contract.visibleButtonCount)
        assertEquals(listOf("WhatsApp", "Compartir", "Imprimir", "Guardar", "Copiar"), contract.menuLabels)
        assertTrue(contract.usesOverflowMenu)
    }

    @Test
    fun `combined pick lottery mode hides print and shows window switch`() {
        assertEquals(
            listOf(
                ResultsActionId.WHATSAPP,
                ResultsActionId.SHARE,
            ),
            resolveResultsActionOrder(showPrint = false),
        )
        assertEquals(
            listOf(ResultsModeWindow.LOTTERY, ResultsModeWindow.PICK),
            resolveResultsModeWindowTabs(AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true)),
        )
        assertEquals(
            listOf(ResultsModeWindow.PICK),
            resolveResultsModeWindowTabs(AdminSystemModeConfig(lotteryModeEnabled = false, pickModeEnabled = true)),
        )
    }

    @Test
    fun `expected result ids skip lotteries with no draw on selected sunday`() {
        val catalog = StaticLotteryCatalogRepository()

        val expectedIds = expectedResultIdsForMode(
            lotteries = catalog.getAllLotteries(),
            config = AdminSystemModeConfig(lotteryModeEnabled = false, pickModeEnabled = true),
            calendarRule = catalog.getCalendarRule(),
            date = "10-05-2026",
        )

        assertFalse("Texas Daily 4 Night does not draw on Sunday", PickResultIdentityResolver.canonicalKeyForExpectedId("US-P4-TX-DAILY-4-NIGHT") in expectedIds)
        assertFalse("Arkansas Cash 3 Midday does not draw on Sunday", PickResultIdentityResolver.canonicalKeyForExpectedId("US-P3-AR-CASH-3-MIDDAY") in expectedIds)
        assertFalse("West Virginia Daily 4 Day does not draw on Sunday", PickResultIdentityResolver.canonicalKeyForExpectedId("US-P4-WV-DAILY-4-DAY") in expectedIds)
        assertTrue("Arkansas Cash 3 Evening draws on Sunday", PickResultIdentityResolver.canonicalKeyForExpectedId("US-P3-AR-CASH-3-EVENING") in expectedIds)
        assertTrue("South Carolina Pick 4 Evening draws on Sunday", PickResultIdentityResolver.canonicalKeyForExpectedId("US-P4-SC-PICK-4-EVENING") in expectedIds)
    }

    @Test
    fun `pick results split into digit balls preserving leading zeroes`() {
        assertEquals(listOf("0", "1", "0"), splitPickDigits("010"))
        assertEquals(listOf("0", "0", "1", "2"), splitPickDigits("0012"))
    }

    @Test
    fun `pick result rows hide redundant pick label in app cards`() {
        assertFalse(shouldShowPickResultTextLabel())
    }

    @Test
    fun `result prize labels use medal icons in app cards`() {
        assertEquals("🥇", resultPrizeIconLabel(0))
        assertEquals("🥈", resultPrizeIconLabel(1))
        assertEquals("🥉", resultPrizeIconLabel(2))
        assertEquals("🏅", resultPrizeIconLabel(3))
    }

    @Test
    fun `result prize medal icons are large enough to identify the prize`() {
        assertEquals(18, resultPrizeIconFontSizeSp())
    }

    @Test
    fun `result cards hide source badge above prize numbers`() {
        assertFalse(shouldShowResultSourceBadge())
    }

    @Test
    fun `published result rows expose individual share action`() {
        assertTrue(shouldShowIndividualResultShareAction(rowHasResult = true))
        assertFalse(shouldShowIndividualResultShareAction(rowHasResult = false))
    }

    @Test
    fun `whatsapp uses paged capture only when sharing multiple result rows`() {
        val single = ResultsSharePayload(
            bancaName = "Banca",
            dateLabel = "25-04-2026",
            rows = listOf(ResultShareRow("Anguila", "57", "87", "37")),
        )
        val multiple = ResultsSharePayload(
            bancaName = "Banca",
            dateLabel = "25-04-2026",
            rows = listOf(
                ResultShareRow("Anguila", "57", "87", "37"),
                ResultShareRow("Haiti", "35", "90", "64"),
            ),
        )

        assertFalse(shouldShareResultsAsSingleWhatsAppListImage(single, whatsappOnly = true, role = UserRole.CASHIER))
        assertFalse(shouldShareResultsAsSingleWhatsAppListImage(multiple, whatsappOnly = false, role = UserRole.CASHIER))
        assertTrue(shouldShareResultsAsSingleWhatsAppListImage(multiple, whatsappOnly = true, role = UserRole.CASHIER))
    }

    @Test
    fun `admin results sharing does not use cashier generic capture`() {
        val single = ResultsSharePayload(
            bancaName = "Banca",
            dateLabel = "25-04-2026",
            rows = listOf(ResultShareRow("Anguila", "57", "87", "37")),
        )
        val multiple = single.copy(
            rows = listOf(
                ResultShareRow("Anguila", "57", "87", "37"),
                ResultShareRow("Haiti", "35", "90", "64"),
            ),
        )

        assertEquals(ResultsShareRenderMode.ADMIN_POSTER, resolveResultsShareRenderMode(UserRole.ADMIN, single, whatsappOnly = false))
        assertEquals(ResultsShareRenderMode.ADMIN_POSTER, resolveResultsShareRenderMode(UserRole.ADMIN, single, whatsappOnly = true))
        assertEquals(ResultsShareRenderMode.ADMIN_POSTER, resolveResultsShareRenderMode(UserRole.ADMIN, multiple, whatsappOnly = true))
        assertEquals(ResultsShareRenderMode.CASHIER_GENERIC, resolveResultsShareRenderMode(UserRole.CASHIER, multiple, whatsappOnly = true))
    }

    @Test
    fun `whatsapp result capture groups rows into eight cards per image`() {
        val rows = (1..8).map { ResultShareRow("Lotería $it", "01", "02", "03") }

        val pages = chunkResultsForWhatsAppCapture(rows)

        assertEquals(8, resultsWhatsAppCardsPerImage())
        assertEquals(listOf(8), pages.map { it.size })
    }

    @Test
    fun `result whatsapp page cache key includes page index`() {
        val rows = listOf(ResultShareRow("Anguila", "01", "02", "03"))

        assertNotEquals(
            resultsRenderCacheKey("25-04-2026", rows, pageIndex = 0),
            resultsRenderCacheKey("25-04-2026", rows, pageIndex = 1),
        )
    }

    @Test
    fun `result whatsapp page cache key includes template`() {
        val rows = listOf(ResultShareRow("Anguila", "01", "02", "03"))

        assertNotEquals(
            resultsRenderCacheKey("25-04-2026", rows, pageIndex = 0, template = "LOTTERY_POSTER"),
            resultsRenderCacheKey("25-04-2026", rows, pageIndex = 0, template = "PICK3_DENSE_LIST"),
        )
    }

    @Test
    fun `whatsapp result capture sends only published rows`() {
        val rows = listOf(
            ResultShareRow("Disponible 1", "01", "02", "03"),
            ResultShareRow("Pendiente", "", "", ""),
            ResultShareRow("Disponible Pick", "", "", "", pick3 = "1-2-3"),
        )

        val available = publishedResultShareRows(rows)

        assertEquals(listOf("Disponible 1", "Disponible Pick"), available.map { it.displayName })
    }

    @Test
    fun `whatsapp result capture keeps pick rows without numbers for status labels`() {
        val rows = listOf(
            ResultShareRow("Pendiente normal", "", "", "", stateLabel = "Pendiente"),
            ResultShareRow("Texas Pick 3 Morning", "", "", "", stateLabel = "Pendiente"),
            ResultShareRow("California Daily 4 Day", "", "", "", stateLabel = "Sin resultado"),
        )

        val available = publishedResultShareRows(rows)

        assertEquals(listOf("Texas Pick 3 Morning", "California Daily 4 Day"), available.map { it.displayName })
    }

    @Test
    fun `four published results only generate one four row whatsapp page`() {
        val rows = (1..26).map { index ->
            if (index <= 4) {
                ResultShareRow("Loteria $index", "01", "02", "03")
            } else {
                ResultShareRow("Pendiente $index", "", "", "")
            }
        }

        val pages = chunkResultsForWhatsAppCapture(rows)

        assertEquals(listOf(4), pages.map { it.size })
    }

    @Test
    fun `whatsapp capture date label matches current result image style`() {
        assertEquals("Sábado 25 abr", formatWhatsAppResultDateLabel("25-04-2026"))
    }

    @Test
    fun `whatsapp full list capture uses screenshot style cards`() {
        assertEquals(ResultsWhatsAppCaptureStyle.SCREENSHOT_LIST, resolveResultsWhatsAppCaptureStyle())
    }

    @Test
    fun `whatsapp full list capture exports hd png width`() {
        assertEquals(1600, resultsWhatsAppCaptureCanvasWidthPx(screenWidthPx = 720, density = 2f))
        assertEquals(1920, resultsWhatsAppCaptureCanvasWidthPx(screenWidthPx = 1080, density = 3f))
    }

    @Test
    fun `whatsapp full list capture keeps large readable visual elements`() {
        val spec = resolveResultsWhatsAppCaptureVisualSpec()

        assertTrue(spec.logoMaxWidthDp >= 210)
        assertTrue(spec.logoMaxHeightDp >= 76)
        assertTrue(spec.logoBoxHeightDp >= 86)
        assertTrue(spec.timeFontSp >= 22)
        assertTrue(spec.ballSizeDp >= 96)
        assertTrue(spec.numberFontSp >= 48)
    }

    @Test
    fun `whatsapp full list capture centers lottery logos in a fixed rail`() {
        val spec = resolveResultsWhatsAppCaptureVisualSpec()

        assertEquals(228, spec.infoColumnWidthDp)
        assertEquals(214, spec.logoMaxWidthDp)
        assertEquals(78, spec.logoMaxHeightDp)
        assertTrue(spec.logoBoxHeightDp < spec.ballSizeDp)
    }

    @Test
    fun `normal result share chunks every published row without poster page cap`() {
        val rows = (1..26).map { index ->
            ResultShareRow("Loteria $index", "01", "02", "03")
        }

        val pages = chunkResultsForNormalShareCapture(rows)

        assertEquals(26, pages.flatten().size)
        assertEquals(listOf(8, 8, 8, 2), pages.map { it.size })
    }

    @Test
    fun `normal result share renders one image for each normal share chunk`() {
        val rows = (1..26).map { index ->
            ResultShareRow("Loteria $index", "01", "02", "03")
        }

        val pages = normalResultsSharePages(rows)

        assertEquals(26, pages.flatMap { it.rows }.size)
        assertEquals(listOf(8, 8, 8, 2), pages.map { it.rows.size })
        assertEquals(listOf(0, 1, 2, 3), pages.map { it.pageIndex })
    }

    @Test
    fun `normal result share keeps haiti bolet when it falls on later page`() {
        val rows = (1..8).map { index ->
            ResultShareRow("Loteria $index", "01", "02", "03")
        } + ResultShareRow(
            displayName = "Haiti Bolet 11:30 AM",
            first = "42",
            second = "45",
            third = "90",
            logoAssetPath = "lot-logos/haiti_bolet.svg",
        )

        val pages = normalResultsSharePages(rows)

        assertEquals(2, pages.size)
        assertEquals("Haiti Bolet 11:30 AM", pages[1].rows.single().displayName)
        assertEquals("lot-logos/haiti_bolet.svg", pages[1].rows.single().logoAssetPath)
    }

    @Test
    fun `normal lottery share keeps image rendering even with many rows`() {
        val massiveNormal = ResultsSharePayload(
            bancaName = "Banca",
            dateLabel = "25-04-2026",
            rows = (1..41).map { index -> ResultShareRow("Loteria $index", "01", "02", "03", logoAssetPath = "lot-logos/$index.png") },
        )

        assertFalse(shouldShareResultsAsPlainText(massiveNormal))
    }

    @Test
    fun `pick only result share uses image dense list instead of plain text`() {
        val pickRows = ResultsSharePayload(
            bancaName = "Banca",
            dateLabel = "25-04-2026",
            rows = listOf(
                ResultShareRow("Texas Pick 3 Day", "", "", "", pick3 = "8-5-2", logoAssetPath = "lot-logos/us-pick/pick3/tx.svg"),
                ResultShareRow("Virginia Pick 4 Night", "", "", "", pick4 = "1-0-6-5", logoAssetPath = "lot-logos/us-pick/pick4/va.svg"),
            ),
        )
        val mixedRows = pickRows.copy(
            rows = pickRows.rows + ResultShareRow("Loteria Nacional", "01", "02", "03"),
        )

        assertFalse(shouldShareResultsAsPlainText(pickRows))
        assertFalse(shouldShareResultsAsPlainText(mixedRows))
        assertEquals(
            listOf(
                NativeBitmapExport.ResultsShareImageTemplate.PICK3_DENSE_LIST,
                NativeBitmapExport.ResultsShareImageTemplate.PICK4_DENSE_LIST,
            ),
            NativeBitmapExport.resolveResultsShareImagePages(pickRows).map { it.template },
        )
    }

    @Test
    fun `results clocks normalize to twelve hour display and sort minutes`() {
        assertEquals("9:55 AM", formatResultsClock12("09:55"))
        assertEquals("5:55 PM", formatResultsClock12("17:55"))
        assertEquals("12:59 PM", formatResultsClock12("12:59 PM"))

        assertEquals(10 * 60, parseResultsClockMinutes("10:00 AM"))
        assertEquals(12 * 60 + 30, parseResultsClockMinutes("12:30 PM"))
        assertEquals(21 * 60, parseResultsClockMinutes("9:00 PM"))
    }

    @Test
    fun `remote pick result creates visible row with state and draw time`() {
        val lottery = buildSyntheticPickLotteryForResult(
            LotteryResult(
                lotteryId = "US-P3-FL-PICK-3-9-48-PM",
                lotteryName = "Florida Pick 3",
                date = "2026-05-09",
                pick3 = "9-2-0",
            ),
        )

        assertEquals("US-P3-FL-PICK-3-9-48-PM", lottery?.id)
        assertEquals("Florida Pick 3 9:48 PM", lottery?.name)
        assertEquals("Pick3", lottery?.type)
        assertEquals("9:48 PM", lottery?.baseDrawTime)
        assertEquals("lot-logos/us-pick/pick3/fl.svg", lottery?.logoAssetPath)
    }

    @Test
    fun `remote daily pick variants keep evening and night draw times`() {
        val indianaEvening = buildSyntheticPickLotteryForResult(
            LotteryResult(
                lotteryId = "US-P4-IN-DAILY-4-EVENING",
                lotteryName = "Indiana Daily 4 Evening Draw",
                date = "2026-05-09",
                pick4 = "3-1-7-6",
            ),
        )
        val texasNight = buildSyntheticPickLotteryForResult(
            LotteryResult(
                lotteryId = "US-P4-TX-DAILY-4-NIGHT",
                lotteryName = "Texas Daily 4 Night Draw",
                date = "2026-05-09",
                pick4 = "4-6-2-3",
            ),
        )

        assertEquals("11:00 PM", indianaEvening?.baseDrawTime)
        assertEquals("10:12 PM", texasNight?.baseDrawTime)
    }

    @Test
    fun `remote pick variants do not collapse into generic eleven pm catalog row`() {
        val arizonaPick3 = LotteryCatalogItem(
            id = "US-P3-AZ",
            name = "Arizona Pick 3",
            type = "Pick3",
            baseDrawTime = "11:00 PM",
            baseCloseTime = "10:55 PM",
            colorHex = "#0ea5e9",
            territory = LotteryTerritory.USA,
        )
        val dayResult = LotteryResult(
            lotteryId = "US-P3-AZ-PICK-3-DAY",
            lotteryName = "Arizona Pick 3",
            date = "09-05-2026",
            pick3 = "2-6-4",
        )

        assertTrue(shouldSkipGenericPickCatalogRow(arizonaPick3, listOf(dayResult)))
        assertFalse(shouldMatchResultByCatalogName(arizonaPick3, dayResult))
        assertEquals("US-P3-AZ-PICK-3-DAY", buildSyntheticPickLotteryForResult(dayResult)?.id)
        assertEquals("1:00 PM", buildSyntheticPickLotteryForResult(dayResult)?.baseDrawTime)
    }

    @Test
    fun `remote pick board shows pick three and pick four day and night rows separately`() {
        val catalog = listOf(
            pickCatalog("US-P3-AZ", "Arizona Pick 3", "Pick3"),
            pickCatalog("US-P4-FL", "Florida Pick 4", "Pick4"),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-AZ-PICK-3-1-00-PM",
                lotteryName = "Arizona Pick 3",
                date = "09-05-2026",
                pick3 = "2-6-4",
            ),
            LotteryResult(
                lotteryId = "US-P3-AZ-PICK-3-11-00-PM",
                lotteryName = "Arizona Pick 3",
                date = "09-05-2026",
                pick3 = "8-7-1",
            ),
            LotteryResult(
                lotteryId = "US-P4-FL-PICK-4-12-30-PM",
                lotteryName = "Florida Pick 4",
                date = "09-05-2026",
                pick4 = "2-5-4-6",
            ),
            LotteryResult(
                lotteryId = "US-P4-FL-PICK-4-9-45-PM",
                lotteryName = "Florida Pick 4",
                date = "09-05-2026",
                pick4 = "7-0-8-2",
            ),
        )

        val rows = buildResultsBoardRowVerificationSummary(
            lotteries = catalog,
            results = results,
            selectedDate = "09-05-2026",
            nowUtcMs = Instant.parse("2026-05-10T12:00:00Z").toEpochMilli(),
            operationTerritory = LotteryTerritory.RD,
            closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository()),
        )

        assertEquals(
            listOf(
                "US-P4-FL-PICK-4-12-30-PM",
                "US-P3-AZ-PICK-3-1-00-PM",
                "US-P4-FL-PICK-4-9-45-PM",
                "US-P3-AZ-PICK-3-11-00-PM",
            ),
            rows.map { it.first },
        )
        assertFalse(rows.any { it.first == "US-P3-AZ" || it.first == "US-P4-FL" })
        assertTrue(rows.all { it.second })
    }

    @Test
    fun `pick board matches timed result id to catalog draw row`() {
        val catalog = listOf(
            pickCatalog("US-P4-FL-PICK-4-EVENING", "Florida Pick 4 Evening", "Pick4").copy(
                baseDrawTime = "9:45 PM",
            ),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P4-FL-PICK-4-9-45-PM",
                lotteryName = "Florida Pick 4",
                date = "09-05-2026",
                pick4 = "7-0-8-2",
            ),
        )

        val rows = buildResultsBoardRowVerificationSummary(
            lotteries = catalog,
            results = results,
            selectedDate = "09-05-2026",
            nowUtcMs = Instant.parse("2026-05-10T12:00:00Z").toEpochMilli(),
            operationTerritory = LotteryTerritory.RD,
            closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository()),
        )

        assertEquals(listOf("US-P4-FL-PICK-4-EVENING" to true), rows)
    }

    @Test
    fun `sold georgia and west virginia pick catalog rows match saved results`() {
        val catalog = StaticLotteryCatalogRepository()
        val lotteries = listOfNotNull(
            catalog.getLotteryById("US-P3-GA-PICK-3-MIDDAY"),
            catalog.getLotteryById("US-P3-GA-PICK-3-EVENING"),
            catalog.getLotteryById("US-P4-GA-CASH-4-MIDDAY"),
            catalog.getLotteryById("US-P3-WV-DAILY-3-DAY"),
            catalog.getLotteryById("US-P4-WV-DAILY-4-DAY"),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-GA-PICK-3-MIDDAY",
                lotteryName = "Georgia Pick 3 Midday Draw",
                date = "14-05-2026",
                pick3 = "9-9-0",
            ),
            LotteryResult(
                lotteryId = "US-P3-GA-PICK-3-EVENING",
                lotteryName = "Georgia Pick 3 Evening Draw",
                date = "14-05-2026",
                pick3 = "6-4-0",
            ),
            LotteryResult(
                lotteryId = "US-P4-GA-CASH-4-MIDDAY",
                lotteryName = "Georgia Cash 4 Midday Draw",
                date = "14-05-2026",
                pick4 = "0-6-3-1",
            ),
            LotteryResult(
                lotteryId = "US-P3-WV-DAILY-3-DAY",
                lotteryName = "West Virginia Daily 3 Day Draw",
                date = "14-05-2026",
                pick3 = "6-3-3",
            ),
            LotteryResult(
                lotteryId = "US-P4-WV-DAILY-4-DAY",
                lotteryName = "West Virginia Daily 4 Day Draw",
                date = "14-05-2026",
                pick4 = "0-7-9-2",
            ),
        )

        val rows = buildResultsBoardRowVerificationSummary(
            lotteries = lotteries,
            results = results,
            selectedDate = "14-05-2026",
            nowUtcMs = Instant.parse("2026-05-15T12:00:00Z").toEpochMilli(),
            operationTerritory = LotteryTerritory.RD,
            closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository()),
        )

        assertEquals(
            listOf(
                "US-P4-GA-CASH-4-MIDDAY" to true,
                "US-P3-GA-PICK-3-MIDDAY" to true,
                "US-P3-GA-PICK-3-EVENING" to true,
                "US-P3-WV-DAILY-3-DAY" to true,
                "US-P4-WV-DAILY-4-DAY" to true,
            ),
            rows,
        )
    }

    @Test
    fun `remote pick result outside sale catalog is not shown as a sellable result`() {
        val catalog = listOf(
            pickCatalog("US-P3-GA-PICK-3-MIDDAY", "Georgia Pick 3 Midday", "Pick3").copy(
                baseDrawTime = "12:29 PM",
            ),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-GA-PICK-3-MIDDAY",
                lotteryName = "Georgia Pick 3 Midday Draw",
                date = "14-05-2026",
                pick3 = "9-9-0",
            ),
            LotteryResult(
                lotteryId = "US-P3-XX-PICK-3-MIDDAY",
                lotteryName = "Unknown Pick 3 Midday Draw",
                date = "14-05-2026",
                pick3 = "1-2-3",
            ),
        )

        val rows = buildResultsBoardRowVerificationSummary(
            lotteries = catalog,
            results = results,
            selectedDate = "14-05-2026",
            nowUtcMs = Instant.parse("2026-05-15T12:00:00Z").toEpochMilli(),
            operationTerritory = LotteryTerritory.RD,
            closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository()),
        )

        assertEquals(listOf("US-P3-GA-PICK-3-MIDDAY" to true), rows)
    }

    @Test
    fun `combined pick lottery mode keeps large result sets separated by window`() {
        val normalLotteries = (1..42).map { index ->
            LotteryCatalogItem(
                id = index.toString(),
                name = "Loteria $index",
                type = "RD",
                baseDrawTime = "8:00 PM",
                baseCloseTime = "7:55 PM",
                colorHex = "#111111",
                territory = LotteryTerritory.RD,
            )
        }
        val normalResults = normalLotteries.map { lottery ->
            LotteryResult(
                lotteryId = lottery.id,
                lotteryName = lottery.name,
                date = "09-05-2026",
                first = "01",
                second = "02",
                third = "03",
            )
        }
        val pickResults = largeRemotePickResults(pick3Count = 63, pick4Count = 42)
        val config = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true)
        val closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository())
        val nowUtcMs = Instant.parse("2026-05-10T12:00:00Z").toEpochMilli()

        val lotteryWindow = buildResultsBoardSectionVerificationSummary(
            lotteries = normalLotteries,
            results = normalResults + pickResults,
            selectedDate = "09-05-2026",
            nowUtcMs = nowUtcMs,
            operationTerritory = LotteryTerritory.RD,
            closePolicy = closePolicy,
            systemModeConfig = config,
            selectedWindow = ResultsModeWindow.LOTTERY,
        )
        val pickWindow = buildResultsBoardSectionVerificationSummary(
            lotteries = normalLotteries,
            results = normalResults + pickResults,
            selectedDate = "09-05-2026",
            nowUtcMs = nowUtcMs,
            operationTerritory = LotteryTerritory.RD,
            closePolicy = closePolicy,
            systemModeConfig = config,
            selectedWindow = ResultsModeWindow.PICK,
        )

        assertEquals(listOf("Loterías" to 42), lotteryWindow)
        assertEquals(emptyList<Pair<String, Int>>(), pickWindow)
    }

    @Test
    fun `result input filtering avoids building hidden pick rows in lottery window`() {
        val lotteries = listOf(
            LotteryCatalogItem(
                id = "1",
                name = "La Primera Día",
                type = "Primera",
                baseDrawTime = "12:00 PM",
                baseCloseTime = "11:55",
                colorHex = "#111111",
                territory = LotteryTerritory.RD,
            ),
            pickCatalog("US-P3-NY-NUMBERS-MIDDAY", "New York Numbers Midday", "Pick3"),
            pickCatalog("US-P3-NY-NUMBERS-EVENING", "New York Numbers Evening", "Pick3"),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "1",
                lotteryName = "La Primera Día",
                date = "09-05-2026",
                first = "01",
                second = "02",
                third = "03",
            ),
            LotteryResult(
                lotteryId = "US-P3-NY-NUMBERS-MIDDAY",
                lotteryName = "New York Numbers Midday Draw",
                date = "09-05-2026",
                pick3 = "7-3-2",
            ),
            LotteryResult(
                lotteryId = "US-P3-NY-NUMBERS-EVENING",
                lotteryName = "New York Numbers Evening Draw",
                date = "09-05-2026",
                pick3 = "7-1-7",
            ),
        )

        val filtered = filterResultsInputsForModeWindow(
            lotteries = lotteries,
            results = results,
            config = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true),
            selectedWindow = ResultsModeWindow.LOTTERY,
        )

        assertEquals(listOf("1"), filtered.lotteries.map { it.id })
        assertEquals(listOf("1"), filtered.results.map { it.lotteryId })
    }

    @Test
    fun `pick results window removes canonical duplicate catalog draws`() {
        val catalog = com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository()
        val lotteries = listOfNotNull(
            catalog.getLotteryById("US-P3-NY-NUMBERS-MIDDAY"),
            catalog.getLotteryById("US-P3-NY-PICK-3-MIDDAY"),
            catalog.getLotteryById("US-P4-NY-WIN-4-MIDDAY"),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P3-NY-NUMBERS-MIDDAY",
                lotteryName = "New York Numbers Midday Draw",
                date = "09-05-2026",
                pick3 = "1-2-3",
            ),
            LotteryResult(
                lotteryId = "US-P4-NY-WIN-4-MIDDAY",
                lotteryName = "New York Win 4 Midday Draw",
                date = "09-05-2026",
                pick4 = "1-2-3-4",
            ),
        )

        val summary = buildResultsBoardRowVerificationSummary(
            lotteries = lotteries,
            results = results,
            selectedDate = "09-05-2026",
            nowUtcMs = Instant.parse("2026-05-11T20:00:00Z").toEpochMilli(),
            operationTerritory = LotteryTerritory.RD,
            closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository()),
        )

        assertEquals(
            listOf("US-P3-NY-NUMBERS-MIDDAY" to true, "US-P4-NY-WIN-4-MIDDAY" to true),
            summary,
        )
    }

    @Test
    fun `remote pick result keeps old nj catalog separate`() {
        val lottery = buildSyntheticPickLotteryForResult(
            LotteryResult(
                lotteryId = "19",
                lotteryName = "NJ Pick 3 Dia",
                date = "2026-05-09",
                pick3 = "8-5-4",
            ),
        )

        assertEquals(null, lottery)
    }

    @Test
    fun `pick board ignores stale nj result from another date`() {
        val catalog = listOf(pickCatalog("20", "NJ Pick 3 Noche", "Pick3"))
        val results = listOf(
            LotteryResult(
                lotteryId = "20",
                lotteryName = "NJ Pick 3 Noche",
                date = "2026-05-09",
                pick3 = "5-1-4",
            ),
        )

        val rows = buildResultsBoardRowVerificationSummary(
            lotteries = catalog,
            results = results,
            selectedDate = "10-05-2026",
            nowUtcMs = Instant.parse("2026-05-11T03:00:00Z").toEpochMilli(),
            operationTerritory = LotteryTerritory.RD,
            closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository()),
        )

        assertEquals(listOf("20" to false), rows)
    }

    @Test
    fun `historic pick pending and no draw statuses stay explicit in board`() {
        val catalog = listOf(
            pickCatalog("US-P4-SC-PICK-4-EVENING", "South Carolina Pick 4 Evening", "Pick4"),
            pickCatalog("US-P3-AR-CASH-3-MIDDAY", "Arkansas Cash 3 Midday", "Pick3"),
        )
        val results = listOf(
            LotteryResult(
                lotteryId = "US-P4-SC-PICK-4-EVENING",
                lotteryName = "South Carolina Pick 4 Evening",
                date = "10-05-2026",
                status = "missing_from_sources",
                source = "Servidor",
            ),
            LotteryResult(
                lotteryId = "US-P3-AR-CASH-3-MIDDAY",
                lotteryName = "Arkansas Cash 3 Midday",
                date = "10-05-2026",
                status = "no_draw",
                source = "no_draw",
            ),
        )

        val summary = buildResultsBoardStateVerificationSummary(
            lotteries = catalog,
            results = results,
            selectedDate = "10-05-2026",
            nowUtcMs = Instant.parse("2026-05-12T15:00:00Z").toEpochMilli(),
            operationTerritory = LotteryTerritory.RD,
            closePolicy = LotteryClosePolicy(FixedTrustedClockRepository(), EmptyHolidayCalendarRepository()),
        )

        assertEquals(
            listOf(
                "US-P3-AR-CASH-3-MIDDAY" to "No hubo sorteo",
                "US-P4-SC-PICK-4-EVENING" to "Pendiente",
            ),
            summary,
        )
    }

    @Test
    fun `results auto refresh is throttled to avoid fighting scroll`() {
        assertTrue(shouldRunResultsAutoRefresh(nowMs = 1_000L, lastStartedMs = null))
        assertFalse(shouldRunResultsAutoRefresh(nowMs = 60_000L, lastStartedMs = 1_000L))
        assertTrue(shouldRunResultsAutoRefresh(nowMs = 61_000L, lastStartedMs = 1_000L))
    }

    @Test
    fun `initial results refresh lets local cache decide before asking server`() {
        assertFalse(shouldForceRemoteOnInitialResultsLoad(selectedDate = "27-04-2026", today = "27-04-2026"))
        assertFalse(shouldForceRemoteOnInitialResultsLoad(selectedDate = "26-04-2026", today = "27-04-2026"))
        assertFalse(shouldForceRemoteOnInitialResultsLoad(selectedDate = "25-04-2026", today = "27-04-2026"))
    }

    @Test
    fun `today yesterday and anteayer still hydrate remote once on open`() {
        assertFalse(shouldSkipInitialResultsHydration(selectedDate = "27-04-2026", today = "27-04-2026", hasCompleteLocalResults = true))
        assertFalse(shouldSkipInitialResultsHydration(selectedDate = "26-04-2026", today = "27-04-2026", hasCompleteLocalResults = true))
        assertFalse(shouldSkipInitialResultsHydration(selectedDate = "25-04-2026", today = "27-04-2026", hasCompleteLocalResults = true))
        assertTrue(shouldSkipInitialResultsHydration(selectedDate = "24-04-2026", today = "27-04-2026", hasCompleteLocalResults = true))
        assertFalse(shouldSkipInitialResultsHydration(selectedDate = "24-04-2026", today = "27-04-2026", hasCompleteLocalResults = false))
    }

    @Test
    fun `initial load does not force remote before painting local cache`() {
        assertFalse(shouldForceRemoteOnInitialResultsLoad(selectedDate = "27-04-2026", today = "27-04-2026"))
        assertFalse(shouldForceRemoteOnInitialResultsLoad(selectedDate = "26-04-2026", today = "27-04-2026"))
        assertFalse(shouldForceRemoteOnInitialResultsLoad(selectedDate = "25-04-2026", today = "27-04-2026"))
        assertFalse(shouldForceRemoteOnInitialResultsLoad(selectedDate = "24-04-2026", today = "27-04-2026"))
    }

    @Test
    fun `selected date reload uses local cache for that date after app restart`() {
        val todayRows = listOf(LotteryResult(lotteryId = "today", date = "27-04-2026", first = "01"))
        val yesterdayRows = listOf(LotteryResult(lotteryId = "yesterday", date = "26-04-2026", first = "02"))

        val sameDate = resolveLocalResultsForSelectedDate(
            selectedDate = "27-04-2026",
            defaultDate = "27-04-2026",
            initialResults = todayRows,
            loadLocalResults = { error("Should not reload initial date") },
        )
        val changedDate = resolveLocalResultsForSelectedDate(
            selectedDate = "26-04-2026",
            defaultDate = "27-04-2026",
            initialResults = todayRows,
            loadLocalResults = { date -> if (date == "26-04-2026") yesterdayRows else emptyList() },
        )

        assertEquals(listOf("today"), sameDate.map { it.lotteryId })
        assertEquals(listOf("yesterday"), changedDate.map { it.lotteryId })
    }

    @Test
    fun `historic results use stable board clock while today follows live clock`() {
        assertEquals(2_000L, resolveResultsBoardClockUtcMs(selectedDate = "27-04-2026", today = "27-04-2026", liveUtcMs = 2_000L, stableUtcMs = 1_000L))
        assertEquals(1_000L, resolveResultsBoardClockUtcMs(selectedDate = "26-04-2026", today = "27-04-2026", liveUtcMs = 2_000L, stableUtcMs = 1_000L))
    }

    private fun pickCatalog(id: String, name: String, type: String): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = id,
            name = name,
            type = type,
            baseDrawTime = "11:00 PM",
            baseCloseTime = "10:55 PM",
            colorHex = "#0ea5e9",
            territory = LotteryTerritory.USA,
        )
    }

    private fun largeRemotePickResults(pick3Count: Int, pick4Count: Int): List<LotteryResult> {
        val stateCodes = listOf(
            "AL", "AR", "AZ", "CA", "CO", "CT", "DC", "DE", "FL", "GA",
            "IA", "ID", "IL", "IN", "KS", "KY", "LA", "MA", "MD", "MI",
            "MN", "MO", "NC", "NE", "NJ", "NM", "NY", "OH", "OK", "OR",
            "PA", "SC", "TN", "TX", "VA", "WA",
        )
        fun rows(prefix: String, gameName: String, count: Int): List<LotteryResult> {
            val draws = listOf("MIDDAY", "EVENING", "NIGHT")
            return (0 until count).map { index ->
                val state = stateCodes[index % stateCodes.size]
                val draw = draws[index / stateCodes.size % draws.size]
                val id = "US-$prefix-$state-${gameName.uppercase().replace(" ", "-")}-$draw"
                LotteryResult(
                    lotteryId = id,
                    lotteryName = "$state $gameName ${draw.lowercase().replaceFirstChar { it.uppercase() }} Draw",
                    date = "09-05-2026",
                    pick3 = if (prefix == "P3") "${index % 10}-${(index + 1) % 10}-${(index + 2) % 10}" else null,
                    pick4 = if (prefix == "P4") "${index % 10}-${(index + 1) % 10}-${(index + 2) % 10}-${(index + 3) % 10}" else null,
                )
            }
        }
        return rows("P3", "Pick 3", pick3Count) + rows("P4", "Pick 4", pick4Count)
    }

    private class FixedTrustedClockRepository : TrustedClockRepository {
        override fun getTrustedUtcMs(): Long = Instant.parse("2026-05-10T12:00:00Z").toEpochMilli()

        override fun getSnapshot(territory: LotteryTerritory): TrustedClockSnapshot {
            return TrustedClockSnapshot(
                trustedUtcMs = getTrustedUtcMs(),
                operationTerritory = territory,
            )
        }

        override fun syncFromUtc(utcMs: Long, source: ClockSource): Boolean = true

        override fun getOperationTimeZone(territory: LotteryTerritory): String {
            return when (territory) {
                LotteryTerritory.RD -> "America/Santo_Domingo"
                LotteryTerritory.USA -> "America/New_York"
            }
        }
    }

    private class EmptyHolidayCalendarRepository : HolidayCalendarRepository {
        override fun getObservedHolidayMap(year: Int, territory: LotteryTerritory): Map<String, String> = emptyMap()
        override fun getDynamicNoDrawLotteryIds(dateKey: String): Set<String> = emptySet()
        override fun getHolidayReason(dateKey: String, lotteryId: String, territory: LotteryTerritory): String? = null
    }
}
