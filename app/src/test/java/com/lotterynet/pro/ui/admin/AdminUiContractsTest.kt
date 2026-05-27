package com.lotterynet.pro.ui.admin

import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.finance.FinanceActorPeriodRow
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.storage.buildCashierLimitPayloadWithDefault
import com.lotterynet.pro.core.storage.buildCashierLimitPayloadWithDefaultForUsers
import com.lotterynet.pro.core.storage.buildCashierLimitPayloadWithUser
import com.lotterynet.pro.core.storage.decodeCashierUserSalesLimitInputs
import com.lotterynet.pro.core.sync.cashierLimitRemoteKey
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.decodeCashierSalesLimitInputs
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.core.storage.effectiveAdminSystemModeConfigForRole
import com.lotterynet.pro.core.storage.encodeCashierSalesLimitInputs
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.users.SupervisorCashierFilter
import com.lotterynet.pro.ui.users.UserAccountFilter
import com.lotterynet.pro.ui.users.CashierInsightFilter
import com.lotterynet.pro.ui.users.buildCashierAccountMetrics
import com.lotterynet.pro.ui.users.buildCashierAdminInsightRows
import com.lotterynet.pro.ui.users.cashierAdminQuickActionLabels
import com.lotterynet.pro.ui.users.cashierAdminWindowSegmentOptions
import com.lotterynet.pro.ui.users.cashierInsightFilterOptions
import com.lotterynet.pro.ui.users.filterCashierAdminInsightRows
import com.lotterynet.pro.ui.users.filterSupervisorCashierOptions
import com.lotterynet.pro.ui.users.filterUserAccountsForAdmin
import com.lotterynet.pro.ui.users.supervisorCreateOrganizationLabels
import com.lotterynet.pro.ui.users.supervisorCashierFilterOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminUiContractsTest {

    @Test
    fun `phone admin dashboard merges risk and hides shortcut subtitles`() {
        val contract = resolveAdminDashboardLayout(LotteryNetWindowMode.POS)

        assertTrue(contract.mergeRiskIntoSummary)
        assertFalse(contract.showOperationBadges)
        assertFalse(contract.secondaryInitiallyExpanded)
        assertFalse(contract.showShortcutSubtitle)
        assertTrue(contract.compactSummary)
        assertTrue(contract.useCompactRows)
        assertFalse(contract.showLargeCards)
        assertTrue(contract.summaryPaddingVerticalDp <= 8)
        assertTrue(contract.shortcutPaddingVerticalDp <= 7)
    }

    @Test
    fun `admin screens slow fallback polling when realtime is available`() {
        assertEquals(60_000L, resolveAdminOperationalPollIntervalMs(realtimeEnabled = false))
        assertEquals(300_000L, resolveAdminOperationalPollIntervalMs(realtimeEnabled = true))
        assertEquals(60_000L, resolveAdminMonitorPollIntervalMs(realtimeEnabled = false))
        assertEquals(300_000L, resolveAdminMonitorPollIntervalMs(realtimeEnabled = true))
        assertEquals(60_000L, resolveAdminLotteryMonitorPollIntervalMs(realtimeEnabled = false))
        assertEquals(300_000L, resolveAdminLotteryMonitorPollIntervalMs(realtimeEnabled = true))
        assertEquals(60_000L, resolveAdminWinnersPollIntervalMs(realtimeEnabled = false))
        assertEquals(300_000L, resolveAdminWinnersPollIntervalMs(realtimeEnabled = true))
        assertEquals(60_000L, resolveCashierDetailPollIntervalMs(realtimeEnabled = false))
        assertEquals(300_000L, resolveCashierDetailPollIntervalMs(realtimeEnabled = true))
    }

    @Test
    fun `admin dashboard hides recargas shortcut when master blocks access`() {
        assertFalse(resolveAdminSecondaryShortcutTitles(rechargeVisible = false).contains("Recargas"))
        assertTrue(resolveAdminSecondaryShortcutTitles(rechargeVisible = true).contains("Recargas"))
    }

    @Test
    fun `manual lottery block prompt separates passed draw from active draw`() {
        val passed = resolveManualLotteryBlockPrompt(
            lotteryName = "La Primera Dia",
            ticketCount = 4,
            drawAlreadyPassed = true,
            dayKey = "2026-05-05",
        )
        val active = resolveManualLotteryBlockPrompt(
            lotteryName = "La Primera Dia",
            ticketCount = 4,
            drawAlreadyPassed = false,
            dayKey = "2026-05-05",
        )

        assertTrue(passed.title.contains("ya pasó", ignoreCase = true))
        assertTrue(passed.body.contains("siguiente día", ignoreCase = true))
        assertTrue(active.body.contains("todavía no ha pasado", ignoreCase = true))
        assertTrue(active.body.contains("anular", ignoreCase = true))
    }

    @Test
    fun `admin config separates lottery blocking from technical system section`() {
        assertEquals(
            listOf("Ajustes rápidos", "Operación", "Caja", "Bloqueo de lotería", "Resultados manuales", "Sistema"),
            adminConfigSectionTitles(),
        )
    }

    @Test
    fun `lottery block picker filters by name type and id`() {
        val rows = filterAdminLotteryBlockOptions(
            lotteries = listOf(
                adminBlockOption("1", "La Primera Dia", "Primera"),
                adminBlockOption("19", "NJ Pick 3 Dia", "Pick3"),
                adminBlockOption("44", "Georgia Dia", "Georgia"),
            ),
            query = "pick",
        )

        assertEquals(listOf("19"), rows.map { it.id })
    }

    @Test
    fun `lottery block picker sorts by close time and does not force first lottery`() {
        val lotteries = listOf(
            adminBlockOption("1", "La Primera Dia", "Primera", "7:55 PM"),
            adminBlockOption("19", "NJ Pick 3 Dia", "Pick3", "12:55 PM"),
            adminBlockOption("44", "Georgia Dia", "Georgia", "6:55 PM"),
        )

        val rows = filterAdminLotteryBlockOptions(lotteries = lotteries, query = "")

        assertEquals(listOf("19", "44", "1"), rows.map { it.id })
        assertEquals(null, resolveAdminLotteryBlockSelection(lotteries, selectedLotteryId = ""))
        assertEquals("44", resolveAdminLotteryBlockSelection(lotteries, selectedLotteryId = "44")?.id)
    }

    @Test
    fun `lottery block picker follows active system mode and does not truncate pick draws`() {
        val pickLotteries = (1..120).map { index ->
            adminBlockOption(
                id = "US-P3-TEST-$index",
                name = "Test Pick 3 $index",
                type = "Pick3",
                closeTime = "12:55 PM",
            )
        }
        val classic = listOf(adminBlockOption("1", "La Primera Dia", "Primera", "7:55 PM"))

        val rows = filterAdminLotteryBlockOptions(
            lotteries = classic + pickLotteries,
            query = "",
            config = AdminSystemModeConfig(lotteryModeEnabled = false, pickModeEnabled = true),
        )

        assertEquals(120, rows.size)
        assertTrue(rows.all { it.type == "Pick3" })
        assertFalse(rows.any { it.id == "1" })
    }

    @Test
    fun `lottery block picker hides pick draws when system mode is lottery only`() {
        val rows = filterAdminLotteryBlockOptions(
            lotteries = listOf(
                adminBlockOption("1", "La Primera Dia", "Primera", "7:55 PM"),
                adminBlockOption("US-P4-TEST", "Test Pick 4", "Pick4", "12:55 PM"),
            ),
            query = "",
            config = AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = false),
        )

        assertEquals(listOf("1"), rows.map { it.id })
    }

    @Test
    fun `system buttons expose modes without future sale`() {
        val adminRows = adminSystemModeRows(AdminSystemModeConfig(), UserRole.ADMIN)

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
            adminRows.map { it.label },
        )
        assertTrue(adminRows.first { it.label == "Admin: Solo Lotería" }.enabled)
        assertFalse(adminRows.any { it.label == "Venta futura" })
    }

    @Test
    fun `system mode ux is grouped and resolves admin cashier segments`() {
        val base = AdminSystemModeConfig()
        val pickAdmin = applyAdminModeSegment(base, "pick")
        val bothCashier = applyCashierDefaultModeSegment(base, "both")

        assertEquals(listOf("Operación", "Cajeros", "Servidor"), adminSystemGroupedSectionTitles())
        assertEquals("lottery", resolveAdminModeSegment(base))
        assertEquals("pick", resolveAdminModeSegment(pickAdmin))
        assertEquals("both", resolveCashierDefaultModeSegment(bothCashier))
    }

    @Test
    fun `admin account filters support activity and no movement views`() {
        val accounts = listOf(
            UserAccount(id = "c1", user = "cajero1", role = UserRole.CASHIER, lastSeenAtEpochMs = 1000L),
            UserAccount(id = "c2", user = "cajero2", role = UserRole.CASHIER, lastSeenAtEpochMs = null),
            UserAccount(id = "s1", user = "supervisor1", role = UserRole.SUPERVISOR, lastSeenAtEpochMs = 2000L),
        )

        assertEquals(listOf("supervisor1", "cajero1"), filterUserAccountsForAdmin(accounts, UserAccountFilter.WITH_ACTIVITY, "").map { it.user })
        assertEquals(listOf("cajero2"), filterUserAccountsForAdmin(accounts, UserAccountFilter.NO_MOVEMENT, "").map { it.user })
        assertEquals(listOf("supervisor1"), filterUserAccountsForAdmin(accounts, UserAccountFilter.SUPERVISORS, "super").map { it.user })
    }

    @Test
    fun `supervisor cashier picker filters many cajeros by assigned free and search`() {
        val cashiers = listOf(
            UserAccount(id = "c1", user = "cajero1", role = UserRole.CASHIER, displayName = "Banca Norte"),
            UserAccount(id = "c2", user = "cajero2", role = UserRole.CASHIER, displayName = "Banca Sur"),
            UserAccount(id = "c3", user = "cajero3", role = UserRole.CASHIER, displayName = "Centro"),
        )
        val assigned = mapOf("c1" to true, "c2" to false, "c3" to true)

        assertEquals(listOf("Todos", "Asignados", "Libres"), supervisorCashierFilterOptions().map { it.label })
        assertEquals(listOf("cajero1", "cajero3"), filterSupervisorCashierOptions(cashiers, assigned, "", SupervisorCashierFilter.ASSIGNED).map { it.user })
        assertEquals(listOf("cajero2"), filterSupervisorCashierOptions(cashiers, assigned, "", SupervisorCashierFilter.FREE).map { it.user })
        assertEquals(listOf("cajero1"), filterSupervisorCashierOptions(cashiers, assigned, "norte", SupervisorCashierFilter.ALL).map { it.user })
    }

    @Test
    fun `admin supervisor create form is organized like compact mockup`() {
        assertEquals(
            listOf("Usuario supervisor", "Nombre", "Clave manual", "Comisión supervisor %", "Cajeros disponibles"),
            supervisorCreateOrganizationLabels(),
        )
    }

    @Test
    fun `compact cashier helpers keep navigation separate from cashier editing`() {
        assertTrue(cashierAdminWindowSegmentOptions(UserRole.ADMIN).isEmpty())
        assertEquals(
            listOf("Todos", "Activos", "Bloqueados", "Venta", "Pérdida", "Beneficio"),
            cashierInsightFilterOptions(UserRole.SUPERVISOR).map { it.label },
        )
        assertTrue(cashierAdminQuickActionLabels(UserRole.ADMIN).isEmpty())
        assertTrue(cashierAdminQuickActionLabels(UserRole.SUPERVISOR).isEmpty())
        assertTrue(cashierAdminQuickActionLabels(UserRole.CASHIER).isEmpty())
    }

    @Test
    fun `compact cashier insight filters benefit and loss rows`() {
        val cashiers = listOf(
            UserAccount(id = "c1", user = "cajero1", role = UserRole.CASHIER, displayName = "Ana", active = true),
            UserAccount(id = "c2", user = "cajero2", role = UserRole.CASHIER, displayName = "Luis", active = false),
        )
        val metrics = buildCashierAccountMetrics(
            listOf(
                FinanceActorPeriodRow(
                    actorKey = "c1",
                    actorDisplay = "Ana",
                    summary = FinanceSummary(ventas = 1000.0, cajaDisponible = 900.0, premiosPendientes = 0.0),
                ),
                FinanceActorPeriodRow(
                    actorKey = "c2",
                    actorDisplay = "Luis",
                    summary = FinanceSummary(ventas = 100.0, cajaDisponible = -50.0, premiosPendientes = 0.0),
                ),
            ),
        )
        val rows = buildCashierAdminInsightRows(cashiers, metrics)

        assertEquals(listOf("cajero1"), filterCashierAdminInsightRows(rows, CashierInsightFilter.BENEFIT, "").map { it.account.user })
        assertEquals(listOf("cajero2"), filterCashierAdminInsightRows(rows, CashierInsightFilter.LOSS, "").map { it.account.user })
        assertEquals(listOf("cajero2"), filterCashierAdminInsightRows(rows, CashierInsightFilter.BLOCKED, "").map { it.account.user })
    }

    @Test
    fun `monitor compact view exposes benefit loss filters without duplicate role windows`() {
        assertTrue(adminMonitorRoleSegmentOptions(UserRole.SUPERVISOR).isEmpty())
        assertEquals(
            listOf("Todos", "Venta", "Pend. cobro", "Pérdida", "Beneficio"),
            adminMonitorFilterOptions().map { it.label },
        )
        assertEquals("Pérdida", monitorResultLabel(-1.0))
        assertEquals("Neutro", monitorResultLabel(0.0))
        assertEquals("Beneficio", monitorResultLabel(1.0))
    }

    @Test
    fun `monitor lottery filter follows cashier pick permission`() {
        val lotteries = listOf(
            adminBlockOption("US-P3-FL-PICK-3-MIDDAY", "Florida Pick 3 Midday", "Pick3"),
            adminBlockOption("8", "New York Tarde", "NY"),
        )
        val cashierConfig = effectiveAdminSystemModeConfigForRole(
            AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true, cashierPickEnabled = false),
            UserRole.CASHIER,
        )
        val adminConfig = effectiveAdminSystemModeConfigForRole(
            AdminSystemModeConfig(lotteryModeEnabled = true, pickModeEnabled = true, cashierPickEnabled = false),
            UserRole.ADMIN,
        )

        assertEquals(listOf("8"), filterMonitorLotteriesForSystemMode(lotteries, cashierConfig).map { it.id })
        assertEquals(listOf("US-P3-FL-PICK-3-MIDDAY", "8"), filterMonitorLotteriesForSystemMode(lotteries, adminConfig).map { it.id })
    }

    @Test
    fun `monitor ticket filter removes plays from hidden modes`() {
        val ticket = TicketRecord(
            id = "t1",
            plays = listOf(
                PlayItem(number = "123", playType = "P3", amount = 25.0, lotteryId = "US-P3-FL-PICK-3-MIDDAY"),
                PlayItem(number = "45", playType = "Q", amount = 10.0, lotteryId = "8"),
            ),
            subtotal = 35.0,
            total = 35.0,
        )

        val filtered = filterMonitorTicketsForLotteries(
            tickets = listOf(ticket),
            allowedLotteryIds = setOf("US-P3-FL-PICK-3-MIDDAY"),
        )

        assertEquals(1, filtered.single().plays.size)
        assertEquals("P3", filtered.single().plays.single().playType)
        assertEquals(25.0, filtered.single().total, 0.001)
    }

    @Test
    fun `monitor play dropdown follows visible lottery mode`() {
        val normal = adminBlockOption("8", "New York Tarde", "NY")
        val pick = adminBlockOption("US-P3-FL-PICK-3-MIDDAY", "Florida Pick 3 Midday", "Pick3")

        assertEquals(
            listOf("Quiniela", "Pale", "Tripleta", "Super Pale"),
            resolveLotteryMonitorPlayViews(listOf(normal)).map { it.label },
        )
        assertEquals(
            listOf("Pick 3", "Pick 4"),
            resolveLotteryMonitorPlayViews(listOf(pick)).map { it.label },
        )
        assertEquals(
            listOf("Quiniela", "Pale", "Tripleta", "Super Pale", "Pick 3", "Pick 4"),
            resolveLotteryMonitorPlayViews(listOf(normal, pick)).map { it.label },
        )
    }

    @Test
    fun `monitor play view falls back when selected view is not valid`() {
        val pick = adminBlockOption("US-P3-FL-PICK-3-MIDDAY", "Florida Pick 3 Midday", "Pick3")
        val views = resolveLotteryMonitorPlayViews(listOf(pick))

        assertEquals(LotteryMonitorPlayView.PICK_3, resolveSelectedLotteryMonitorPlayView(LotteryMonitorPlayView.QUINIELA.name, views))
        assertEquals(LotteryMonitorPlayView.PICK_4, resolveSelectedLotteryMonitorPlayView(LotteryMonitorPlayView.PICK_4.name, views))
    }

    @Test
    fun `phone admin monitor splits actions and compacts cards`() {
        val contract = resolveAdminMonitorLayout(LotteryNetWindowMode.POS)

        assertTrue(contract.splitActions)
        assertTrue(contract.compactSummary)
        assertTrue(contract.compactCards)
        assertTrue(contract.summaryPaddingVerticalDp <= 8)
        assertTrue(contract.rowPaddingVerticalDp <= 7)
    }

    @Test
    fun `cashier monitor cards use dense operational rows instead of stacked metric cards`() {
        val contract = resolveCashierMonitorCardVisualContract()

        assertTrue(contract.singleLineIdentity)
        assertTrue(contract.inlineMetrics)
        assertTrue(contract.singleStatusIndicator)
        assertFalse(contract.stackedMetricCards)
        assertTrue(contract.minTouchTargetDp >= 44)
        assertTrue(contract.rowPaddingVerticalDp <= 8)
    }

    @Test
    fun `cashier cards open quick action menu with scoped tickets and reports`() {
        val contract = resolveCashierCardActionContract(LotteryNetWindowMode.POS_TIGHT)

        assertTrue(contract.cardTapOpensMenu)
        assertEquals(listOf("Detalle", "Tickets", "Reporte", "Cuadre", "Cobros"), contract.actions)
        assertTrue(contract.filterTicketsByCashier)
        assertTrue(contract.filterReportsByCashier)
        assertTrue(contract.maxVisibleRowActions <= 1)
    }

    @Test
    fun `admin monitor cashier dropdown uses natural numeric order`() {
        val labels = sortMonitorCashierLabelsNatural(
            listOf(
                "Cajero 1 - Banca yuniel",
                "Cajero 10 - Banca yuniel",
                "Cajero 2 - Banca yuniel",
                "Cajero 21 - Banca yuniel",
            ),
        )

        assertEquals(
            listOf(
                "Cajero 1 - Banca yuniel",
                "Cajero 2 - Banca yuniel",
                "Cajero 10 - Banca yuniel",
                "Cajero 21 - Banca yuniel",
            ),
            labels,
        )
    }

    @Test
    fun `admin limits are grouped by operational risk`() {
        assertEquals(
            listOf("Mis límites de venta", "Límite de venta de cajeros", "Pagos", "Recargas", "Sistema"),
            adminLimitSections().map { it.label },
        )
    }

    @Test
    fun `admin dashboard exposes cashier sales limits as a critical shortcut`() {
        assertTrue(resolveAdminCriticalShortcutTitles().contains("Límite venta cajeros"))
    }

    @Test
    fun `cashier sales limits preserve practical defaults`() {
        val limits = CashierSalesLimitInputs()

        assertEquals(10000.0, limits.daySale, 0.001)
        assertEquals(10000.0, limits.quiniela, 0.001)
        assertEquals(500.0, limits.pale, 0.001)
        assertEquals(500.0, limits.superPale, 0.001)
        assertEquals(75.0, limits.tripleta, 0.001)
        assertEquals(500.0, limits.pick3Straight, 0.001)
        assertEquals(500.0, limits.pick3Box, 0.001)
        assertEquals(500.0, limits.pick4Straight, 0.001)
        assertEquals(500.0, limits.pick4Box, 0.001)
    }

    @Test
    fun `cashier sales limit payload round trips defaults`() {
        val limits = CashierSalesLimitInputs(
            daySale = 8000.0,
            quiniela = 1900.0,
            pale = 400.0,
            superPale = 350.0,
            tripleta = 80.0,
            pick3Straight = 300.0,
            pick3Box = 250.0,
            pick4Straight = 200.0,
            pick4Box = 150.0,
        )
        val decoded = decodeCashierSalesLimitInputs(encodeCashierSalesLimitInputs(limits))

        assertEquals(limits, decoded)
    }

    @Test
    fun `admin limits expose every compact sales field without way variants`() {
        assertEquals(
            listOf("Quiniela", "Pale", "Super Pale", "Tripleta", "Pick 3 Straight", "Pick 3 Box", "Pick 4 Straight", "Pick 4 Box"),
            adminSalesLimitFieldLabels(),
        )
        assertFalse(adminSalesLimitFieldLabels().any { it.contains("way", ignoreCase = true) })
    }

    @Test
    fun `cashier limits sync uses stable owner scoped supabase key`() {
        assertEquals("cashier_limits:admin-1", cashierLimitRemoteKey("admin-1"))
    }

    @Test
    fun `service first cashier default limit payload can be built without local write`() {
        val limits = CashierSalesLimitInputs(quiniela = 1800.0, pale = 450.0)
        val payload = buildCashierLimitPayloadWithDefault(
            currentPayload = null,
            limits = limits,
        )

        assertEquals(limits, decodeCashierSalesLimitInputs(payload))
    }

    @Test
    fun `service first cashier user limit payload preserves defaults`() {
        val defaults = CashierSalesLimitInputs(quiniela = 2000.0, pale = 500.0)
        val userLimits = CashierSalesLimitInputs(quiniela = 900.0, pale = 250.0)
        val payload = buildCashierLimitPayloadWithUser(
            currentPayload = encodeCashierSalesLimitInputs(defaults),
            username = "cajero1",
            limits = userLimits,
        )

        assertEquals(defaults, decodeCashierSalesLimitInputs(payload))
        assertTrue(payload.contains("\"cajero1\""))
        assertTrue(payload.contains("\"q\":900"))
    }

    @Test
    fun `global cashier limits overwrite every cashier visual limit`() {
        val oldDefaults = CashierSalesLimitInputs(quiniela = 2000.0, pale = 500.0)
        val oldUserLimits = CashierSalesLimitInputs(quiniela = 900.0, pale = 250.0)
        val currentPayload = buildCashierLimitPayloadWithUser(
            currentPayload = encodeCashierSalesLimitInputs(oldDefaults),
            username = "cajero1",
            limits = oldUserLimits,
        )
        val newGlobalLimits = CashierSalesLimitInputs(quiniela = 5000.0, pale = 700.0)

        val payload = buildCashierLimitPayloadWithDefaultForUsers(
            currentPayload = currentPayload,
            limits = newGlobalLimits,
            usernames = listOf("cajero1", "cajero2"),
        )

        assertEquals(newGlobalLimits, decodeCashierSalesLimitInputs(payload))
        assertEquals(newGlobalLimits, decodeCashierUserSalesLimitInputs(payload, "cajero1"))
        assertEquals(newGlobalLimits, decodeCashierUserSalesLimitInputs(payload, "cajero2"))
    }

    @Test
    fun `lottery monitor quiniela shows only played numbers ordered by sales desc`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                monitorTicket("t1", "cajero1", PlayItem(number = "05", playType = "Q", amount = 20.0, lotteryId = "1", lotteryName = "La Primera Día")),
                monitorTicket("t2", "admin1", PlayItem(number = "07", playType = "Q", amount = 50.0, lotteryId = "1", lotteryName = "La Primera Día")),
            ),
            lotteryId = "1",
            view = LotteryMonitorPlayView.QUINIELA,
        )

        assertEquals(2, rows.size)
        assertEquals("07", rows[0].displayNumber)
        assertEquals(50.0, rows[0].amount, 0.001)
        assertEquals("05", rows[1].displayNumber)
        assertEquals(20.0, rows[1].amount, 0.001)
        assertFalse(rows.any { it.amount == 0.0 })
    }

    @Test
    fun `lottery monitor quiniela empty day does not create placeholder numbers`() {
        val rows = buildLotteryMonitorRows(
            tickets = emptyList(),
            lotteryId = "1",
            view = LotteryMonitorPlayView.QUINIELA,
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `lottery monitor remaining limit ignores admin sales and subtracts cashier sales`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                TicketRecord(
                    id = "admin-sale",
                    sellerUser = "admin1",
                    role = UserRole.ADMIN,
                    plays = listOf(PlayItem(number = "03", playType = "Q", amount = 100.0, lotteryId = "1", lotteryName = "La Primera Día")),
                    total = 100.0,
                ),
                TicketRecord(
                    id = "cashier-sale",
                    sellerUser = "cajero1",
                    role = UserRole.CASHIER,
                    plays = listOf(PlayItem(number = "03", playType = "Q", amount = 100.0, lotteryId = "1", lotteryName = "La Primera Día")),
                    total = 100.0,
                ),
            ),
            lotteryId = "1",
            view = LotteryMonitorPlayView.QUINIELA,
            cashierSellerKeys = setOf("cajero1"),
            cashierLimits = CashierSalesLimitInputs(quiniela = 2_000.0),
        )

        val row = rows.single()
        assertEquals(200.0, row.amount, 0.001)
        assertEquals(100.0, row.cashierAmount, 0.001)
        assertEquals(2_000.0, row.limitAmount ?: 0.0, 0.001)
        assertEquals(1_900.0, row.remainingAmount ?: 0.0, 0.001)
    }

    @Test
    fun `lottery monitor pale tripleta and super pale show only played combinations`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                monitorTicket("p1", "cajero1", PlayItem(number = "0102", playType = "P", amount = 30.0, lotteryId = "1", lotteryName = "La Primera Día")),
                monitorTicket("p2", "cajero2", PlayItem(number = "0102", playType = "P", amount = 70.0, lotteryId = "1", lotteryName = "La Primera Día")),
                monitorTicket("q1", "cajero3", PlayItem(number = "99", playType = "Q", amount = 100.0, lotteryId = "1", lotteryName = "La Primera Día")),
            ),
            lotteryId = "1",
            view = LotteryMonitorPlayView.PALE,
        )

        assertEquals(1, rows.size)
        assertEquals("01/02", rows.single().displayNumber)
        assertEquals(100.0, rows.single().amount, 0.001)
        assertEquals(2, rows.single().playsCount)
        assertEquals(listOf("cajero1", "cajero2"), rows.single().actors)
    }

    @Test
    fun `lottery monitor pick 3 groups straight and box plays`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                monitorTicket("p3s", "cajero1", PlayItem(number = "123", playType = "P3", amount = 20.0, lotteryId = "US-P3-FL-PICK-3-MIDDAY", lotteryName = "Florida Pick 3")),
                monitorTicket("p3b", "cajero2", PlayItem(number = "123", playType = "P3BOX", amount = 30.0, lotteryId = "US-P3-FL-PICK-3-MIDDAY", lotteryName = "Florida Pick 3")),
                monitorTicket("p4", "cajero3", PlayItem(number = "1234", playType = "P4", amount = 50.0, lotteryId = "US-P4-FL-PICK-4-MIDDAY", lotteryName = "Florida Pick 4")),
            ),
            lotteryId = null,
            view = LotteryMonitorPlayView.PICK_3,
        )

        assertEquals(1, rows.size)
        assertEquals("123", rows.single().displayNumber)
        assertEquals(50.0, rows.single().amount, 0.001)
        assertEquals(2, rows.single().playsCount)
        assertEquals(listOf("cajero1", "cajero2"), rows.single().actors)
    }

    @Test
    fun `lottery monitor pick 4 groups straight and box plays`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                monitorTicket("p4s", "cajero1", PlayItem(number = "1234", playType = "P4", amount = 40.0, lotteryId = "US-P4-FL-PICK-4-MIDDAY", lotteryName = "Florida Pick 4")),
                monitorTicket("p4b", "cajero2", PlayItem(number = "1234", playType = "P4BOX", amount = 60.0, lotteryId = "US-P4-FL-PICK-4-MIDDAY", lotteryName = "Florida Pick 4")),
                monitorTicket("p3", "cajero3", PlayItem(number = "123", playType = "P3", amount = 25.0, lotteryId = "US-P3-FL-PICK-3-MIDDAY", lotteryName = "Florida Pick 3")),
            ),
            lotteryId = null,
            view = LotteryMonitorPlayView.PICK_4,
        )

        assertEquals(1, rows.size)
        assertEquals("1234", rows.single().displayNumber)
        assertEquals(100.0, rows.single().amount, 0.001)
        assertEquals(2, rows.single().playsCount)
        assertEquals(listOf("cajero1", "cajero2"), rows.single().actors)
    }

    @Test
    fun `lottery monitor pick filter still respects selected lottery id`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                monitorTicket("a", "cajero1", PlayItem(number = "123", playType = "P3", amount = 20.0, lotteryId = "US-P3-FL-PICK-3-MIDDAY", lotteryName = "Florida Pick 3")),
                monitorTicket("b", "cajero2", PlayItem(number = "123", playType = "P3BOX", amount = 30.0, lotteryId = "US-P3-NJ-PICK-3-NIGHT", lotteryName = "NJ Pick 3 Noche")),
            ),
            lotteryId = "US-P3-FL-PICK-3-MIDDAY",
            view = LotteryMonitorPlayView.PICK_3,
        )

        assertEquals(1, rows.size)
        assertEquals(20.0, rows.single().amount, 0.001)
        assertEquals(listOf("cajero1"), rows.single().actors)
    }

    @Test
    fun `lottery monitor caps visible number rows for production volume`() {
        val rows = (0 until 120).map { index ->
            LotteryNumberMonitorRow(
                displayNumber = index.toString().padStart(2, '0'),
                amount = index.toDouble(),
                playsCount = 1,
                actors = listOf("cajero$index"),
            )
        }

        val contract = resolveLotteryMonitorVisibleRows(rows)

        assertEquals(40, contract.visibleRows.size)
        assertEquals(80, contract.hiddenCount)
        assertEquals(120, contract.totalCount)
        assertTrue(contract.showViewAll)
        assertEquals("Ver todo", contract.overflowActionLabel)
    }

    @Test
    fun `lottery monitor todas includes plays from every lottery`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                monitorTicket("a", "cajero1", PlayItem(number = "11", playType = "Q", amount = 10.0, lotteryId = "1", lotteryName = "La Primera Día")),
                monitorTicket("b", "cajero2", PlayItem(number = "11", playType = "Q", amount = 15.0, lotteryId = "2", lotteryName = "Anguila Mañana")),
            ),
            lotteryId = null,
            view = LotteryMonitorPlayView.QUINIELA,
        )

        assertEquals("11", rows.first().displayNumber)
        assertEquals(25.0, rows.first().amount, 0.001)
    }

    @Test
    fun `admin monitor scope includes cashier tickets assigned to same admin`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "ADM-1",
            username = "Admin01",
            banca = "Banca Norte",
        )
        val cashiers = listOf(
            UserAccount(id = "CAJ-1", user = "Cajero01", role = UserRole.CASHIER, adminId = "adm-1", adminUser = "admin01"),
            UserAccount(id = "CAJ-2", user = "Cajero02", role = UserRole.CASHIER, adminId = "other", adminUser = "other"),
        )
        val tickets = listOf(
            TicketRecord(id = "admin-sale", sellerId = "ADM-1", adminId = "ADM-1", total = 200.0),
            TicketRecord(id = "cashier-sale", sellerUser = "cajero01", adminId = "ADM-1", total = 50.0),
            TicketRecord(id = "other-sale", sellerUser = "cajero02", adminId = "OTHER", total = 75.0),
        )

        val scoped = filterScopedMonitorTickets(session, cashiers, tickets)

        assertEquals(listOf("admin-sale", "cashier-sale"), scoped.map { it.id })
    }

    @Test
    fun `monitor selected cashier filters lottery play tickets`() {
        val tickets = listOf(
            monitorTicket("a", "cajero1", PlayItem(number = "11", playType = "Q", amount = 10.0, lotteryId = "1", lotteryName = "La Primera Día")),
            monitorTicket("b", "cajero2", PlayItem(number = "22", playType = "Q", amount = 15.0, lotteryId = "1", lotteryName = "La Primera Día")),
        )

        val selected = filterMonitorTicketsBySelectedCashier(
            tickets = tickets,
            selectedCashierId = "cashier-1",
            selectedSellerKeys = setOf("cashier-1", "cajero1"),
        )
        val rows = buildLotteryMonitorRows(selected, lotteryId = "1", view = LotteryMonitorPlayView.QUINIELA)

        assertEquals(1, selected.size)
        val playedRows = rows.filter { it.amount > 0.0 }
        assertEquals(1, playedRows.size)
        assertEquals("11", playedRows.single().displayNumber)
    }

    @Test
    fun `lottery monitor shows cashier display name instead of server id`() {
        val rows = buildLotteryMonitorRows(
            tickets = listOf(
                TicketRecord(
                    id = "ticket-cajero",
                    sellerId = "srv-cajero-01",
                    sellerUser = null,
                    plays = listOf(PlayItem(number = "03", playType = "Q", amount = 200.0, lotteryId = "1", lotteryName = "La Primera Día")),
                    total = 200.0,
                ),
            ),
            lotteryId = "1",
            view = LotteryMonitorPlayView.QUINIELA,
            actorLabelsByKey = mapOf("srv-cajero-01" to "Caja Norte"),
        )

        assertEquals(listOf("Caja Norte"), rows.single().actors)
    }

    @Test
    fun `lottery monitor share text includes selected filter totals and rows`() {
        val text = buildLotteryMonitorShareText(
            bancaName = "Banca Norte",
            dayKey = "2026-05-01",
            lotteryLabel = "Todas",
            viewLabel = "Quiniela",
            statusLabel = "Todas",
            rows = listOf(
                LotteryNumberMonitorRow("11", 25.0, 2, listOf("cajero1", "cajero2")),
                LotteryNumberMonitorRow("00", 0.0, 0, emptyList()),
            ),
        )

        assertTrue(text.contains("Banca Norte"))
        assertTrue(text.contains("Lotería: Todas"))
        assertTrue(text.contains("Vista: Quiniela"))
        assertTrue(text.contains("Total vendido: $ 25"))
        assertTrue(text.contains("11 · $ 25 · 2 jugada(s) · cajero1, cajero2"))
        assertFalse(text.contains("00 · $0"))
    }

    @Test
    fun `lottery winner monitor shows paid and pending prizes inside selected date range`() {
        val rows = buildLotteryWinnerMonitorRows(
            tickets = listOf(
                monitorWinnerTicket("win-today", "cajero1", "paid", 1_500.0, "2026-05-01T15:00:00Z"),
                monitorWinnerTicket("win-week", "cajero2", "winner", 700.0, "2026-04-29T15:00:00Z"),
                monitorWinnerTicket("old", "cajero3", "paid", 900.0, "2026-04-01T15:00:00Z"),
                monitorWinnerTicket("voided", "cajero4", "voided", 2_000.0, "2026-05-01T16:00:00Z"),
            ),
            nowUtcMs = utcMillis("2026-05-01T20:00:00Z"),
            period = LotteryWinnerMonitorPeriod.WEEK,
            manualDayKey = "2026-05-01",
            operationTerritory = LotteryTerritory.RD,
        )

        assertEquals(listOf("win-today", "win-week"), rows.map { it.ticketId })
        assertEquals(2_200.0, rows.sumOf { it.prizeAmount }, 0.001)
        assertTrue(rows.first().displayPlays.contains("Q 12"))
        assertEquals("2026-05-01", rows.first().dayKey)
    }

    @Test
    fun `lottery winner monitor manual period filters exact day`() {
        val rows = buildLotteryWinnerMonitorRows(
            tickets = listOf(
                monitorWinnerTicket("today", "cajero1", "paid", 1_500.0, "2026-05-01T15:00:00Z"),
                monitorWinnerTicket("yesterday", "cajero1", "paid", 700.0, "2026-04-30T15:00:00Z"),
            ),
            nowUtcMs = utcMillis("2026-05-01T20:00:00Z"),
            period = LotteryWinnerMonitorPeriod.MANUAL,
            manualDayKey = "2026-04-30",
            operationTerritory = LotteryTerritory.RD,
        )

        assertEquals(listOf("yesterday"), rows.map { it.ticketId })
    }

    @Test
    fun `monitor day key follows operation territory timezone`() {
        val utc = utcMillis("2026-05-01T03:30:00Z")

        assertEquals("2026-04-30", buildAdminMonitorDayKey(utc, LotteryTerritory.RD))
        assertEquals("2026-04-30", buildAdminMonitorDayKey(utc, LotteryTerritory.USA))
    }

    private fun monitorTicket(
        id: String,
        seller: String,
        play: PlayItem,
    ): TicketRecord {
        return TicketRecord(
            id = id,
            sellerUser = seller,
            plays = listOf(play),
            total = play.amount,
        )
    }

    private fun monitorWinnerTicket(
        id: String,
        seller: String,
        status: String,
        prize: Double,
        utcDate: String,
    ): TicketRecord {
        return TicketRecord(
            id = id,
            serial = id.uppercase(),
            sellerUser = seller,
            createdAtEpochMs = utcMillis(utcDate),
            status = status,
            totalPrize = prize,
            plays = listOf(PlayItem(number = "12", playType = "Q", amount = 25.0, lotteryId = "1", lotteryName = "La Primera Día")),
            total = 25.0,
        )
    }

    private fun utcMillis(value: String): Long {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(value)?.time ?: error("Invalid date $value")
    }

    private fun adminBlockOption(
        id: String,
        name: String,
        type: String,
        closeTime: String = "12:55 PM",
    ): LotteryCatalogItem {
        return LotteryCatalogItem(
            id = id,
            name = name,
            type = type,
            baseDrawTime = "1:00 PM",
            baseCloseTime = closeTime,
            colorHex = "#000000",
        )
    }
}
