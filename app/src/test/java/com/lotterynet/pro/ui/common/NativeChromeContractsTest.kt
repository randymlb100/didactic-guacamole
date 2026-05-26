package com.lotterynet.pro.ui.common

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeChromeContractsTest {

    @Test
    fun `bottom nav is lifted and enlarged on tight mobile screens`() {
        val contract = resolveBottomNavLayout(LotteryNetWindowMode.POS_TIGHT)

        assertEquals(74.dp, contract.barHeight)
        assertEquals(5.dp, contract.extraBottomGap)
        assertTrue(contract.showLabels)
        assertTrue(contract.iconBoxSelected > contract.iconBoxIdle)
    }

    @Test
    fun `bottom nav regular phone layout keeps larger tap area than previous compact bar`() {
        val contract = resolveBottomNavLayout(LotteryNetWindowMode.POS)

        assertEquals(76.dp, contract.barHeight)
        assertEquals(5.dp, contract.extraBottomGap)
        assertEquals(36.dp, contract.iconBoxSelected)
        assertTrue(contract.showLabels)
    }

    @Test
    fun `size profile shrinks shared primitives on phone modes`() {
        val tight = resolveLotteryNetSizeProfile(LotteryNetWindowMode.POS_TIGHT)
        val phone = resolveLotteryNetSizeProfile(LotteryNetWindowMode.POS)

        assertEquals(10.dp, tight.panelRadius)
        assertEquals(42.dp, tight.actionHeight)
        assertEquals(8.dp, tight.screenPaddingH)
        assertEquals(68.dp, phone.bottomNavHeight)
        assertEquals(46.dp, phone.actionHeight)
        assertTrue(phone.bottomNavLabel > tight.bottomNavLabel)
    }

    @Test
    fun `adaptive screen contract flattens tight screens and keeps larger layouts richer`() {
        val tight = resolveAdaptiveScreenContract(LotteryNetWindowMode.POS_TIGHT)
        val wide = resolveAdaptiveScreenContract(LotteryNetWindowMode.WIDE)

        assertTrue(tight.preferInlineStatus)
        assertTrue(tight.auxiliaryActionInline)
        assertTrue(wide.showSupportingText)
        assertFalse(tight.usePrimaryPanel)
        assertTrue(wide.usePrimaryPanel)
    }

    @Test
    fun `overflow contract keeps tighter lists shorter on phone modes`() {
        val tight = resolveOverflowLayoutContract(LotteryNetWindowMode.POS_TIGHT)
        val phone = resolveOverflowLayoutContract(LotteryNetWindowMode.POS)

        assertEquals(132.dp, tight.dropdownMaxHeight)
        assertEquals(156.dp, phone.dropdownMaxHeight)
        assertEquals(280.dp, tight.dropdownMaxWidth)
        assertEquals(320.dp, phone.dropdownMaxWidth)
        assertTrue(tight.listMaxHeightFraction < phone.listMaxHeightFraction)
        assertTrue(tight.sheetMaxHeightFraction < 0.9f)
    }

    @Test
    fun `adaptive viewport flags icon first chrome on compact height phones`() {
        val viewport = resolveAdaptiveViewportContract(
            widthClass = WindowWidthSizeClass.Compact,
            heightClass = WindowHeightSizeClass.Compact,
        )

        assertTrue(viewport.compactBottomNav)
        assertTrue(viewport.compactTypography)
        assertTrue(viewport.preferIconOnlyActions)
    }

    @Test
    fun `operational ui hides support copy first on tight pos`() {
        val tight = resolveOperationalUiContract(LotteryNetWindowMode.POS_TIGHT)
        val phone = resolveOperationalUiContract(LotteryNetWindowMode.POS)

        assertFalse(tight.showSupportingText)
        assertTrue(phone.showSupportingText)
        assertTrue(phone.primaryRowHeight > tight.primaryRowHeight)
        assertEquals(1, tight.maxSubtitleLines)
    }

    @Test
    fun `motion spec keeps pos animations short and non decorative`() {
        val tight = resolveAppMotionSpec(LotteryNetWindowMode.POS_TIGHT)
        val wide = resolveAppMotionSpec(LotteryNetWindowMode.WIDE)

        assertEquals(0, tight.shortMillis)
        assertEquals(0, tight.mediumMillis)
        assertFalse(tight.enableListMotion)
        assertFalse(tight.enableDecorativeMotion)
        assertTrue(wide.enableDecorativeMotion)
    }

    @Test
    fun `settings destinations are grouped by operation cashbox and system`() {
        val groups = resolveSettingsDestinationGroups()

        assertEquals(listOf("Operación", "Caja", "Sistema"), groups.keys.toList())
        assertEquals(listOf("Cajeros", "Loterías"), groups.getValue("Operación").map { it.label })
        assertEquals(listOf("Impresora", "Cuadre"), groups.getValue("Caja").map { it.label })
        assertEquals(
            groups.values.flatten().size,
            groups.values.flatten().map { it.label }.distinct().size,
        )
    }

    @Test
    fun `ticket sync ui contract exposes every operator visible state`() {
        assertEquals("Guardado local", resolveTicketSaveSyncUiContract(TicketSaveSyncStage.LOCAL_SAVED).label)
        assertEquals("Sincronizando", resolveTicketSaveSyncUiContract(TicketSaveSyncStage.SYNCING).label)
        assertEquals("Sincronizado", resolveTicketSaveSyncUiContract(TicketSaveSyncStage.SYNCED).label)
        assertEquals("Pendiente de sync", resolveTicketSaveSyncUiContract(TicketSaveSyncStage.PENDING).label)
        assertEquals("Error de sync", resolveTicketSaveSyncUiContract(TicketSaveSyncStage.ERROR).label)
    }

    @Test
    fun `ticket sync ui contract keeps failed supabase visible as pending after local save`() {
        val contract = resolveTicketSaveSyncUiContract(
            stage = TicketSaveSyncStage.PENDING,
            detail = "Sin conexión",
        )

        assertEquals("Pendiente de sync", contract.label)
        assertEquals("Ticket guardado en el celular. Sin conexión", contract.message)
        assertTrue(contract.requiresAttention)
    }

    @Test
    fun `compact visual profile keeps operator controls dense without oversized panels`() {
        val tight = resolveLotteryNetSizeProfile(LotteryNetWindowMode.POS_TIGHT)
        val phone = resolveLotteryNetSizeProfile(LotteryNetWindowMode.POS)

        assertTrue(tight.actionHeight in 40.dp..44.dp)
        assertTrue(phone.actionHeight in 44.dp..48.dp)
        assertTrue(tight.panelRadius <= 10.dp)
        assertTrue(phone.panelRadius <= 10.dp)
        assertTrue(tight.panelContentGap <= 6.dp)
    }

    @Test
    fun `bottom tickets tab opens summarized tickets`() {
        assertEquals(
            "com.lotterynet.pro.ui.tickets.TicketSummaryActivity",
            resolveBottomTabActivityClassName(UserRole.CASHIER, NativeBottomTab.LIST),
        )
        assertEquals(
            "com.lotterynet.pro.ui.tickets.TicketSummaryActivity",
            resolveBottomTabActivityClassName(UserRole.ADMIN, NativeBottomTab.LIST),
        )
    }

    @Test
    fun `dashboard tab does not open removed admin panel`() {
        assertEquals(
            "com.lotterynet.pro.ui.sales.SalesActivity",
            resolveBottomTabActivityClassName(UserRole.ADMIN, NativeBottomTab.DASHBOARD),
        )
    }

    @Test
    fun `bottom nav is hidden because sections open from header menu`() {
        assertFalse(shouldShowBottomNav(UserRole.CASHIER))
        assertFalse(shouldShowBottomNav(UserRole.ADMIN))
        assertFalse(shouldShowBottomNav(UserRole.MASTER))
    }

    @Test
    fun `visual palette keeps secondary text black and financial colors direct`() {
        val colors = resolveLotteryNetStatusColors()

        assertEquals(colors.ink, colors.muted)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0F9D58), colors.gain)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFC62828), colors.loss)
    }

    @Test
    fun `strong action tones use intense surfaces with white text`() {
        val colors = resolveLotteryNetStatusColors()
        val danger = resolveCompactActionButtonToneContract(ActionTone.Danger, enabled = true, active = false, colors = colors)
        val purple = resolveCompactActionButtonToneContract(ActionTone.Purple, enabled = true, active = false, colors = colors)

        assertEquals(colors.loss, danger.background)
        assertEquals(androidx.compose.ui.graphics.Color.White, danger.foreground)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF6D28D9), purple.background)
        assertEquals(androidx.compose.ui.graphics.Color.White, purple.foreground)
    }

    @Test
    fun `compact action tones follow operational intent colors`() {
        val colors = resolveLotteryNetStatusColors()

        val primary = resolveCompactActionButtonToneContract(ActionTone.Primary, enabled = true, active = false, colors = colors)
        val success = resolveCompactActionButtonToneContract(ActionTone.Success, enabled = true, active = false, colors = colors)
        val warning = resolveCompactActionButtonToneContract(ActionTone.Warning, enabled = true, active = false, colors = colors)
        val danger = resolveCompactActionButtonToneContract(ActionTone.Danger, enabled = true, active = false, colors = colors)
        val secondary = resolveCompactActionButtonToneContract(ActionTone.Secondary, enabled = true, active = false, colors = colors)

        assertEquals(colors.actionPrimary, primary.background)
        assertEquals(colors.gain, success.background)
        assertEquals(colors.warningSurface, warning.background)
        assertEquals(colors.warning, warning.foreground)
        assertEquals(colors.loss, danger.background)
        assertEquals(colors.actionPrimarySurface, secondary.background)
        assertEquals(colors.actionPrimary, secondary.foreground)
    }

    @Test
    fun `disabled compact action does not look active`() {
        val colors = resolveLotteryNetStatusColors()
        val disabled = resolveCompactActionButtonToneContract(ActionTone.Success, enabled = false, active = true, colors = colors)

        assertEquals(colors.panelAlt, disabled.background)
        assertEquals(colors.ink, disabled.foreground)
        assertFalse(disabled.background == colors.gain)
        assertFalse(disabled.background == colors.actionPrimary)
    }

    @Test
    fun `compact toggle switch uses lottery net active and neutral colors`() {
        val colors = resolveLotteryNetStatusColors()

        val success = resolveCompactToggleSwitchToneContract(ActionTone.Success, enabled = true, colors = colors)
        val warning = resolveCompactToggleSwitchToneContract(ActionTone.Warning, enabled = true, colors = colors)
        val disabled = resolveCompactToggleSwitchToneContract(ActionTone.Danger, enabled = false, colors = colors)

        assertEquals(colors.gain, success.checkedTrack)
        assertEquals(androidx.compose.ui.graphics.Color.White, success.checkedThumb)
        assertEquals(colors.warning, warning.checkedTrack)
        assertEquals(colors.panelAlt, success.uncheckedTrack)
        assertEquals(colors.neutral, success.uncheckedThumb)
        assertEquals(colors.panelAlt, disabled.disabledTrack)
        assertEquals(colors.border, disabled.disabledThumb)
    }

    @Test
    fun `compact segmented selector keeps selected state dense and clear`() {
        val colors = resolveLotteryNetStatusColors()
        val contract = resolveCompactSegmentedSelectorContract(colors)

        assertEquals(colors.actionPrimary, contract.selectedBackground)
        assertEquals(androidx.compose.ui.graphics.Color.White, contract.selectedForeground)
        assertEquals(colors.panelAlt, contract.unselectedBackground)
        assertEquals(colors.actionPrimary, contract.unselectedForeground)
        assertTrue(contract.minHeight <= 40.dp)
    }

    @Test
    fun `compact loading state uses functional tone without large panels`() {
        val colors = resolveLotteryNetStatusColors()
        val primary = resolveCompactLoadingStateContract(ActionTone.Primary, colors)
        val warning = resolveCompactLoadingStateContract(ActionTone.Warning, colors)

        assertEquals(colors.actionPrimary, primary.indicatorColor)
        assertEquals(colors.actionPrimarySurface, primary.surface)
        assertEquals(colors.warning, warning.indicatorColor)
        assertEquals(colors.warningSurface, warning.surface)
        assertTrue(primary.minHeight <= 40.dp)
    }

    @Test
    fun `compact text input has visible focus error and disabled contracts`() {
        val colors = resolveLotteryNetStatusColors()
        val contract = resolveCompactTextInputContract(colors)

        assertEquals(colors.panel, contract.container)
        assertEquals(colors.actionPrimary, contract.focusedBorder)
        assertEquals(colors.border, contract.unfocusedBorder)
        assertEquals(colors.loss, contract.errorBorder)
        assertEquals(colors.panelAlt, contract.disabledContainer)
        assertEquals(48.dp, contract.minHeight)
    }

    @Test
    fun `compact bulk toolbar summarizes selected visible work`() {
        val colors = resolveLotteryNetStatusColors()
        val empty = resolveCompactBulkToolbarContract(selectedCount = 0, colors = colors)
        val selected = resolveCompactBulkToolbarContract(selectedCount = 12, colors = colors)

        assertEquals("0 seleccionado(s)", empty.selectedLabel)
        assertEquals("12 seleccionado(s)", selected.selectedLabel)
        assertEquals(colors.ink, empty.foreground)
        assertEquals(colors.actionPrimary, selected.foreground)
        assertTrue(selected.minHeight <= 84.dp)
    }

    @Test
    fun `non sales visual redesign uses dense operational rows and keeps sale excluded`() {
        val contract = resolveNonSalesVisualRedesignContract()

        assertTrue(contract.excludeSales)
        assertTrue(contract.useDenseOperationalRows)
        assertTrue(contract.inlineMetrics)
        assertTrue(contract.singleStatusIndicator)
        assertTrue(contract.minTouchTargetDp >= 44)
        assertTrue(contract.actionSpacingDp >= 8)
        assertTrue(contract.animationDurationMs in 120..220)
        assertFalse(contract.useHeroCards)
    }

    @Test
    fun `dense operational row contract keeps repeated lists compact and readable`() {
        val contract = resolveDenseOperationalRowContract()

        assertEquals(6, contract.verticalPaddingDp)
        assertEquals(8, contract.horizontalPaddingDp)
        assertEquals(44, contract.minHeightDp)
        assertEquals(3, contract.maxInlineMetricCount)
        assertTrue(contract.usesChevronOpenAffordance)
        assertTrue(contract.supportsAnimatedStatusBackground)
    }

    @Test
    fun `non sales redesign scope never includes sales activity`() {
        val excluded = nonSalesVisualRedesignExcludedFiles()

        assertTrue(excluded.contains("app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt"))
        assertTrue(excluded.contains("app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt"))
    }

    @Test
    fun `pos lite viewport adapts globally for q2i class handheld screens`() {
        val contract = resolvePosLiteViewportContract(
            widthDp = 360,
            heightDp = 640,
            forcedPosLite = true,
        )

        assertEquals(LotteryNetWindowMode.POS_TIGHT, contract.windowMode)
        assertTrue(contract.singleColumn)
        assertTrue(contract.hideSecondaryCopy)
        assertTrue(contract.useDenseRows)
        assertTrue(contract.collapseSecondaryActions)
        assertTrue(contract.minTouchTargetDp >= 44)
        assertTrue(contract.contentHorizontalPaddingDp <= 10)
        assertTrue(contract.compactSelectorHeightDp <= 40)
        assertTrue(contract.compactDropdownPaddingVerticalDp <= 6)
    }

    @Test
    fun `fintech dropdown contract is blue with white bold content`() {
        val colors = resolveLotteryNetStatusColors()
        val contract = resolveFintechDropdownContract(colors)

        assertEquals(colors.actionPrimary, contract.background)
        assertEquals(androidx.compose.ui.graphics.Color.White, contract.foreground)
        assertEquals(colors.actionPrimary, contract.border)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, contract.valueWeight)
    }

    @Test
    fun `action feedback messages confirm server and save work`() {
        assertEquals("Guardado terminado.", resolveActionFeedbackMessage(ActionFeedbackKind.SAVE, success = true))
        assertEquals("Servidor actualizado.", resolveActionFeedbackMessage(ActionFeedbackKind.SERVER_REFRESH, success = true))
        assertEquals("Refrescado terminado.", resolveActionFeedbackMessage(ActionFeedbackKind.REFRESH, success = true))
        assertEquals("No se pudo actualizar.", resolveActionFeedbackMessage(ActionFeedbackKind.SERVER_REFRESH, success = false))
    }

    @Test
    fun `shared action vocabulary separates commands overflow and choices`() {
        val primary = resolveUiActionContract(UiActionRole.PRIMARY_COMMAND)
        val secondary = resolveUiActionContract(UiActionRole.SECONDARY_COMMAND)
        val overflow = resolveUiActionContract(UiActionRole.OVERFLOW_COMMAND)
        val choice = resolveUiActionContract(UiActionRole.CHOICE_OPTION)

        assertTrue(primary.visible)
        assertTrue(primary.countsAsPanelPrimary)
        assertEquals(ActionTone.Primary, primary.tone)
        assertTrue(secondary.visible)
        assertFalse(secondary.countsAsPanelPrimary)
        assertEquals(ActionTone.Secondary, secondary.tone)
        assertFalse(overflow.visible)
        assertTrue(overflow.inOverflowMenu)
        assertFalse(choice.countsAsCommand)
        assertTrue(choice.usesSelectionState)
    }

    @Test
    fun `panel action policy allows one primary and moves extra commands to overflow`() {
        val policy = resolvePanelActionPolicy(
            listOf(
                UiActionRole.PRIMARY_COMMAND,
                UiActionRole.PRIMARY_COMMAND,
                UiActionRole.SECONDARY_COMMAND,
                UiActionRole.CHOICE_OPTION,
            ),
        )

        assertEquals(1, policy.visiblePrimaryCount)
        assertEquals(1, policy.overflowCommandCount)
        assertEquals(1, policy.choiceCount)
        assertTrue(policy.keepsOnePrimaryPerPanel)
    }
}
