package com.lotterynet.pro.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.lotterynet.pro.core.catalog.LotteryLogoBitmapLoader
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.LocalPosModeRepository
import com.lotterynet.pro.ui.finance.FinanceActivity
import com.lotterynet.pro.ui.master.MasterDashboardActivity
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.results.ResultsActivity
import com.lotterynet.pro.ui.sales.SalesActivity
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.tickets.TicketLookupActivity
import com.lotterynet.pro.ui.tickets.TicketSummaryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenChromeSpec(
    val title: String,
    val subtitle: String? = null,
    val showBottomNav: Boolean = true,
    val activeBottomTab: NativeBottomTab? = null,
    val rightAction: ScreenChromeAction? = null,
    val menuContext: String? = null,
)

data class ScreenChromeAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val label: String? = null,
    val spinning: Boolean = false,
)

enum class ActionTone {
    Primary,
    Secondary,
    Success,
    Warning,
    Danger,
    IntenseBlue,
    Purple,
}

enum class ActionFeedbackKind {
    SAVE,
    SERVER_REFRESH,
    REFRESH,
    APPLY,
}

enum class UiActionRole {
    PRIMARY_COMMAND,
    SECONDARY_COMMAND,
    OVERFLOW_COMMAND,
    CHOICE_OPTION,
}

data class UiActionContract(
    val visible: Boolean,
    val inOverflowMenu: Boolean,
    val countsAsCommand: Boolean,
    val countsAsPanelPrimary: Boolean,
    val usesSelectionState: Boolean,
    val tone: ActionTone,
)

data class PanelActionPolicy(
    val visiblePrimaryCount: Int,
    val overflowCommandCount: Int,
    val choiceCount: Int,
    val keepsOnePrimaryPerPanel: Boolean,
)

fun resolveUiActionContract(role: UiActionRole): UiActionContract {
    return when (role) {
        UiActionRole.PRIMARY_COMMAND -> UiActionContract(
            visible = true,
            inOverflowMenu = false,
            countsAsCommand = true,
            countsAsPanelPrimary = true,
            usesSelectionState = false,
            tone = ActionTone.Primary,
        )

        UiActionRole.SECONDARY_COMMAND -> UiActionContract(
            visible = true,
            inOverflowMenu = false,
            countsAsCommand = true,
            countsAsPanelPrimary = false,
            usesSelectionState = false,
            tone = ActionTone.Secondary,
        )

        UiActionRole.OVERFLOW_COMMAND -> UiActionContract(
            visible = false,
            inOverflowMenu = true,
            countsAsCommand = true,
            countsAsPanelPrimary = false,
            usesSelectionState = false,
            tone = ActionTone.Secondary,
        )

        UiActionRole.CHOICE_OPTION -> UiActionContract(
            visible = true,
            inOverflowMenu = false,
            countsAsCommand = false,
            countsAsPanelPrimary = false,
            usesSelectionState = true,
            tone = ActionTone.Secondary,
        )
    }
}

fun resolvePanelActionPolicy(roles: List<UiActionRole>): PanelActionPolicy {
    var primarySeen = false
    var visiblePrimaryCount = 0
    var overflowCommandCount = 0
    var choiceCount = 0
    roles.forEach { role ->
        when (role) {
            UiActionRole.PRIMARY_COMMAND -> {
                if (primarySeen) {
                    overflowCommandCount += 1
                } else {
                    primarySeen = true
                    visiblePrimaryCount += 1
                }
            }

            UiActionRole.SECONDARY_COMMAND -> Unit
            UiActionRole.OVERFLOW_COMMAND -> overflowCommandCount += 1
            UiActionRole.CHOICE_OPTION -> choiceCount += 1
        }
    }
    return PanelActionPolicy(
        visiblePrimaryCount = visiblePrimaryCount,
        overflowCommandCount = overflowCommandCount,
        choiceCount = choiceCount,
        keepsOnePrimaryPerPanel = visiblePrimaryCount <= 1,
    )
}

fun resolveActionFeedbackMessage(kind: ActionFeedbackKind, success: Boolean): String {
    if (!success) return "No se pudo actualizar."
    return when (kind) {
        ActionFeedbackKind.SAVE -> "Guardado terminado."
        ActionFeedbackKind.SERVER_REFRESH -> "Servidor actualizado."
        ActionFeedbackKind.REFRESH -> "Refrescado terminado."
        ActionFeedbackKind.APPLY -> "Cambio aplicado."
    }
}

enum class TicketSaveSyncStage {
    LOCAL_SAVED,
    SYNCING,
    SYNCED,
    PENDING,
    ERROR,
}

data class TicketSaveSyncUiContract(
    val label: String,
    val message: String,
    val tone: ActionTone,
    val requiresAttention: Boolean,
)

fun resolveTicketSaveSyncUiContract(
    stage: TicketSaveSyncStage,
    detail: String? = null,
): TicketSaveSyncUiContract {
    val cleanDetail = detail?.trim().orEmpty()
    fun withDetail(prefix: String): String = if (cleanDetail.isBlank()) prefix else "$prefix $cleanDetail"
    return when (stage) {
        TicketSaveSyncStage.LOCAL_SAVED -> TicketSaveSyncUiContract(
            label = "Guardado local",
            message = "Ticket guardado en el celular.",
            tone = ActionTone.Success,
            requiresAttention = false,
        )

        TicketSaveSyncStage.SYNCING -> TicketSaveSyncUiContract(
            label = "Sincronizando",
            message = "Ticket guardado en el celular. Subiendo al servidor...",
            tone = ActionTone.Primary,
            requiresAttention = false,
        )

        TicketSaveSyncStage.SYNCED -> TicketSaveSyncUiContract(
            label = "Sincronizado",
            message = "Ticket guardado en el celular y servidor.",
            tone = ActionTone.Success,
            requiresAttention = false,
        )

        TicketSaveSyncStage.PENDING -> TicketSaveSyncUiContract(
            label = "Pendiente de sync",
            message = withDetail("Ticket guardado en el celular."),
            tone = ActionTone.Warning,
            requiresAttention = true,
        )

        TicketSaveSyncStage.ERROR -> TicketSaveSyncUiContract(
            label = "Error de sync",
            message = withDetail("Ticket guardado en el celular."),
            tone = ActionTone.Danger,
            requiresAttention = true,
        )
    }
}

enum class NativeBottomTab {
    SALE,
    LIST,
    DASHBOARD,
    RESULTS,
    MENU,
}

internal fun shouldShowBottomNav(role: UserRole): Boolean = false

enum class SettingsDestination(
    val group: String,
    val label: String,
) {
    USERS("Operación", "Cajeros"),
    LOTTERIES("Operación", "Loterías"),
    LIMITS("Operación", "Límites"),
    PRINTER("Caja", "Impresora"),
    FINANCE("Caja", "Cuadre"),
    SERVER("Sistema", "Servidor"),
    SYNC("Sistema", "Sync"),
    DIAGNOSTICS("Sistema", "Diagnóstico"),
}

enum class LotteryNetWindowMode {
    POS,
    POS_TIGHT,
    TABLET,
    WIDE,
}

data class AppMotionSpec(
    val shortMillis: Int,
    val mediumMillis: Int,
    val enableListMotion: Boolean,
    val enableDecorativeMotion: Boolean,
)

data class OperationalUiContract(
    val showSupportingText: Boolean,
    val useIconLead: Boolean,
    val maxSubtitleLines: Int,
    val primaryRowHeight: Dp,
)

data class LotteryNetStatusColors(
    val background: Color,
    val chrome: Color,
    val chromeSurface: Color,
    val chromeText: Color,
    val chromeMuted: Color,
    val gain: Color,
    val loss: Color,
    val warning: Color,
    val neutral: Color,
    val ink: Color,
    val muted: Color,
    val panel: Color,
    val panelAlt: Color,
    val border: Color,
    val sale: Color,
    val saleSurface: Color,
    val tickets: Color,
    val ticketsSurface: Color,
    val results: Color,
    val resultsSurface: Color,
    val finance: Color,
    val financeSurface: Color,
    val recharge: Color,
    val rechargeSurface: Color,
    val admin: Color,
    val adminSurface: Color,
    val printer: Color,
    val printerSurface: Color,
    val actionPrimary: Color,
    val actionPrimarySurface: Color,
    val warningSurface: Color,
    val dangerSurface: Color,
)

data class LotteryNetSizeProfile(
    val topBarHeight: Dp,
    val topBarIcon: Dp,
    val bottomNavHeight: Dp,
    val bottomNavIcon: Dp,
    val bottomNavLabel: TextUnit,
    val actionHeight: Dp,
    val actionIcon: Dp,
    val rowHeight: Dp,
    val screenPaddingH: Dp,
    val screenPaddingV: Dp,
    val sectionGap: Dp,
    val panelRadius: Dp,
    val logoPadding: Dp,
    val panelContentGap: Dp,
)

data class LotteryNetVisualSpec(
    val windowMode: LotteryNetWindowMode,
    val sizes: LotteryNetSizeProfile,
    val colors: LotteryNetStatusColors,
)

data class CompactActionButtonToneContract(
    val background: Color,
    val foreground: Color,
    val border: Color,
)

data class CompactToggleSwitchToneContract(
    val checkedTrack: Color,
    val checkedThumb: Color,
    val uncheckedTrack: Color,
    val uncheckedThumb: Color,
    val disabledTrack: Color,
    val disabledThumb: Color,
)

data class CompactSegmentedSelectorContract(
    val selectedBackground: Color,
    val selectedForeground: Color,
    val unselectedBackground: Color,
    val unselectedForeground: Color,
    val border: Color,
    val minHeight: Dp,
)

data class CompactLoadingStateContract(
    val indicatorColor: Color,
    val surface: Color,
    val foreground: Color,
    val minHeight: Dp,
)

data class CompactTextInputContract(
    val container: Color,
    val focusedBorder: Color,
    val unfocusedBorder: Color,
    val errorBorder: Color,
    val disabledContainer: Color,
    val minHeight: Dp,
)

data class CompactBulkToolbarContract(
    val background: Color,
    val border: Color,
    val foreground: Color,
    val selectedLabel: String,
    val minHeight: Dp,
)

data class FintechDropdownContract(
    val background: Color,
    val foreground: Color,
    val border: Color,
    val valueWeight: FontWeight,
)

internal data class NonSalesVisualRedesignContract(
    val excludeSales: Boolean,
    val useDenseOperationalRows: Boolean,
    val inlineMetrics: Boolean,
    val singleStatusIndicator: Boolean,
    val minTouchTargetDp: Int,
    val actionSpacingDp: Int,
    val animationDurationMs: Int,
    val useHeroCards: Boolean,
)

internal data class DenseOperationalRowContract(
    val verticalPaddingDp: Int,
    val horizontalPaddingDp: Int,
    val minHeightDp: Int,
    val maxInlineMetricCount: Int,
    val usesChevronOpenAffordance: Boolean,
    val supportsAnimatedStatusBackground: Boolean,
)

internal data class PosLiteViewportContract(
    val windowMode: LotteryNetWindowMode,
    val singleColumn: Boolean,
    val hideSecondaryCopy: Boolean,
    val useDenseRows: Boolean,
    val collapseSecondaryActions: Boolean,
    val minTouchTargetDp: Int,
    val contentHorizontalPaddingDp: Int,
    val compactSelectorHeightDp: Int,
    val compactDropdownPaddingVerticalDp: Int,
)

internal data class VisualGrammarContract(
    val maxPrimaryActionsPerPanel: Int,
    val badgesOnlyForStatusOrCounts: Boolean,
    val dropdownsForLongOptionLists: Boolean,
    val bottomSheetsForSecondaryActionGroups: Boolean,
    val minTouchTargetDp: Int,
    val controlSpacingDp: Int,
    val motionDurationMs: Int,
)

internal data class AdaptiveActionGroupContract(
    val visiblePrimaryCount: Int,
    val visibleSecondaryCount: Int,
    val overflowCount: Int,
    val useBottomSheet: Boolean,
)

internal data class FilterBandContract(
    val useFlowRow: Boolean,
    val stackLongDropdowns: Boolean,
    val maxLabelLines: Int,
)

internal fun resolveNonSalesVisualRedesignContract(): NonSalesVisualRedesignContract {
    return NonSalesVisualRedesignContract(
        excludeSales = true,
        useDenseOperationalRows = true,
        inlineMetrics = true,
        singleStatusIndicator = true,
        minTouchTargetDp = 44,
        actionSpacingDp = 8,
        animationDurationMs = 160,
        useHeroCards = false,
    )
}

internal fun resolveVisualGrammarContract(): VisualGrammarContract {
    return VisualGrammarContract(
        maxPrimaryActionsPerPanel = 1,
        badgesOnlyForStatusOrCounts = true,
        dropdownsForLongOptionLists = true,
        bottomSheetsForSecondaryActionGroups = true,
        minTouchTargetDp = 44,
        controlSpacingDp = 8,
        motionDurationMs = 160,
    )
}

internal fun resolveAdaptiveActionGroupContract(
    windowMode: LotteryNetWindowMode,
    commandCount: Int,
): AdaptiveActionGroupContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    val visiblePrimary = if (commandCount > 0) 1 else 0
    val visibleSecondary = if (compact) {
        1.coerceAtMost(commandCount - visiblePrimary)
    } else {
        2.coerceAtMost(commandCount - visiblePrimary)
    }
    val overflow = (commandCount - visiblePrimary - visibleSecondary).coerceAtLeast(0)
    return AdaptiveActionGroupContract(
        visiblePrimaryCount = visiblePrimary,
        visibleSecondaryCount = visibleSecondary,
        overflowCount = overflow,
        useBottomSheet = compact && overflow > 0,
    )
}

internal fun resolveFilterBandContract(windowMode: LotteryNetWindowMode): FilterBandContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return FilterBandContract(
        useFlowRow = true,
        stackLongDropdowns = compact,
        maxLabelLines = if (compact) 2 else 1,
    )
}

internal fun resolveDenseOperationalRowContract(): DenseOperationalRowContract {
    return DenseOperationalRowContract(
        verticalPaddingDp = 6,
        horizontalPaddingDp = 8,
        minHeightDp = 44,
        maxInlineMetricCount = 3,
        usesChevronOpenAffordance = true,
        supportsAnimatedStatusBackground = true,
    )
}

internal fun nonSalesVisualRedesignExcludedFiles(): Set<String> {
    return setOf(
        "app/src/main/java/com/lotterynet/pro/ui/sales/SalesActivity.kt",
        "app/src/test/java/com/lotterynet/pro/ui/sales/SalesUiContractsTest.kt",
    )
}

internal fun resolvePosLiteViewportContract(
    widthDp: Int,
    heightDp: Int,
    forcedPosLite: Boolean,
): PosLiteViewportContract {
    val tight = forcedPosLite || widthDp <= 380 || heightDp <= 680
    return PosLiteViewportContract(
        windowMode = if (tight) LotteryNetWindowMode.POS_TIGHT else LotteryNetWindowMode.POS,
        singleColumn = tight,
        hideSecondaryCopy = tight,
        useDenseRows = true,
        collapseSecondaryActions = tight,
        minTouchTargetDp = 44,
        contentHorizontalPaddingDp = if (tight) 8 else 12,
        compactSelectorHeightDp = if (tight) 40 else 44,
        compactDropdownPaddingVerticalDp = if (tight) 6 else 8,
    )
}

internal fun resolveFintechDropdownContract(colors: LotteryNetStatusColors): FintechDropdownContract {
    return FintechDropdownContract(
        background = colors.actionPrimary,
        foreground = Color.White,
        border = colors.actionPrimary,
        valueWeight = FontWeight.Bold,
    )
}

internal data class BottomNavLayoutContract(
    val barHeight: Dp,
    val iconBoxSelected: Dp,
    val iconBoxIdle: Dp,
    val extraBottomGap: Dp,
    val showLabels: Boolean,
    val iconLabelGap: Dp,
    val labelTopPadding: Dp,
)

internal data class AdaptiveScreenContract(
    val usePrimaryPanel: Boolean,
    val showSupportingText: Boolean,
    val preferInlineStatus: Boolean,
    val contentSpacing: Dp,
    val sectionSpacing: Dp,
    val auxiliaryActionInline: Boolean,
)

internal data class OverflowLayoutContract(
    val dropdownMaxHeight: Dp,
    val dropdownMaxWidth: Dp,
    val sheetMaxHeightFraction: Float,
    val listMaxHeightFraction: Float,
    val compactSpacing: Dp,
    val sheetHorizontalPadding: Dp,
    val sheetVerticalPadding: Dp,
)

internal data class AdaptiveViewportContract(
    val widthClass: WindowWidthSizeClass,
    val heightClass: WindowHeightSizeClass,
    val compactBottomNav: Boolean,
    val compactTypography: Boolean,
    val preferIconOnlyActions: Boolean,
)

private data class NativeBottomNavItem(
    val tab: NativeBottomTab,
    val label: String,
    val icon: ImageVector,
    val fg: Color,
    val bg: Color,
)

object NativeLogoResolver {
    const val BRAND_ICON_ASSET = "icon.svg"

    private val providerLogos = mapOf(
        "claro" to "logo_claro.svg",
        "altice" to "logo_altice.svg",
        "viva" to "logo_viva.svg",
        "moun" to "logo_moun.svg",
        "wind" to "logo_wind.svg",
        "digicel" to "logo_digicel.svg",
        "natcom" to "logo_natcom.svg",
    )

    fun providerAsset(providerId: String?): String? {
        return providerLogos[providerId?.trim()?.lowercase()]
    }
}

internal fun resolveBottomNavLayout(windowMode: LotteryNetWindowMode): BottomNavLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> BottomNavLayoutContract(
            barHeight = 74.dp,
            iconBoxSelected = 34.dp,
            iconBoxIdle = 32.dp,
            extraBottomGap = 5.dp,
            showLabels = true,
            iconLabelGap = 2.dp,
            labelTopPadding = 1.dp,
        )

        LotteryNetWindowMode.POS -> BottomNavLayoutContract(
            barHeight = 76.dp,
            iconBoxSelected = 36.dp,
            iconBoxIdle = 32.dp,
            extraBottomGap = 5.dp,
            showLabels = true,
            iconLabelGap = 2.dp,
            labelTopPadding = 2.dp,
        )

        else -> BottomNavLayoutContract(
            barHeight = 64.dp,
            iconBoxSelected = 40.dp,
            iconBoxIdle = 36.dp,
            extraBottomGap = 8.dp,
            showLabels = true,
            iconLabelGap = 3.dp,
            labelTopPadding = 1.dp,
        )
    }
}

internal fun resolveAppMotionSpec(windowMode: LotteryNetWindowMode): AppMotionSpec {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> AppMotionSpec(
            shortMillis = 0,
            mediumMillis = 0,
            enableListMotion = false,
            enableDecorativeMotion = false,
        )

        LotteryNetWindowMode.POS -> AppMotionSpec(
            shortMillis = 0,
            mediumMillis = 0,
            enableListMotion = false,
            enableDecorativeMotion = false,
        )

        else -> AppMotionSpec(
            shortMillis = 140,
            mediumMillis = 180,
            enableListMotion = true,
            enableDecorativeMotion = true,
        )
    }
}

internal fun resolveOperationalUiContract(windowMode: LotteryNetWindowMode): OperationalUiContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> OperationalUiContract(
            showSupportingText = false,
            useIconLead = true,
            maxSubtitleLines = 1,
            primaryRowHeight = 44.dp,
        )

        LotteryNetWindowMode.POS -> OperationalUiContract(
            showSupportingText = true,
            useIconLead = true,
            maxSubtitleLines = 1,
            primaryRowHeight = 46.dp,
        )

        else -> OperationalUiContract(
            showSupportingText = true,
            useIconLead = true,
            maxSubtitleLines = 2,
            primaryRowHeight = 50.dp,
        )
    }
}

internal fun resolveSettingsDestinationGroups(): Map<String, List<SettingsDestination>> =
    SettingsDestination.values()
        .filterNot { it == SettingsDestination.LIMITS }
        .groupBy { it.group }

internal fun resolveLotteryNetSizeProfile(mode: LotteryNetWindowMode): LotteryNetSizeProfile {
    return when (mode) {
        LotteryNetWindowMode.POS_TIGHT -> LotteryNetSizeProfile(
            topBarHeight = 48.dp,
            topBarIcon = 16.dp,
            bottomNavHeight = 66.dp,
            bottomNavIcon = 18.dp,
            bottomNavLabel = 8.5.sp,
            actionHeight = 42.dp,
            actionIcon = 12.dp,
            rowHeight = 36.dp,
            screenPaddingH = 8.dp,
            screenPaddingV = 6.dp,
            sectionGap = 8.dp,
            panelRadius = 10.dp,
            logoPadding = 2.dp,
            panelContentGap = 6.dp,
        )

        LotteryNetWindowMode.POS -> LotteryNetSizeProfile(
            topBarHeight = 50.dp,
            topBarIcon = 18.dp,
            bottomNavHeight = 68.dp,
            bottomNavIcon = 18.dp,
            bottomNavLabel = 9.sp,
            actionHeight = 46.dp,
            actionIcon = 13.dp,
            rowHeight = 40.dp,
            screenPaddingH = 10.dp,
            screenPaddingV = 7.dp,
            sectionGap = 9.dp,
            panelRadius = 10.dp,
            logoPadding = 3.dp,
            panelContentGap = 7.dp,
        )

        LotteryNetWindowMode.TABLET -> LotteryNetSizeProfile(
            topBarHeight = 54.dp,
            topBarIcon = 20.dp,
            bottomNavHeight = 64.dp,
            bottomNavIcon = 20.dp,
            bottomNavLabel = 10.sp,
            actionHeight = 50.dp,
            actionIcon = 16.dp,
            rowHeight = 44.dp,
            screenPaddingH = 16.dp,
            screenPaddingV = 12.dp,
            sectionGap = 12.dp,
            panelRadius = 12.dp,
            logoPadding = 5.dp,
            panelContentGap = 9.dp,
        )

        LotteryNetWindowMode.WIDE -> LotteryNetSizeProfile(
            topBarHeight = 56.dp,
            topBarIcon = 20.dp,
            bottomNavHeight = 64.dp,
            bottomNavIcon = 20.dp,
            bottomNavLabel = 10.sp,
            actionHeight = 50.dp,
            actionIcon = 16.dp,
            rowHeight = 44.dp,
            screenPaddingH = 16.dp,
            screenPaddingV = 12.dp,
            sectionGap = 12.dp,
            panelRadius = 12.dp,
            logoPadding = 5.dp,
            panelContentGap = 9.dp,
        )
    }
}

internal fun resolveAdaptiveScreenContract(windowMode: LotteryNetWindowMode): AdaptiveScreenContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> AdaptiveScreenContract(
            usePrimaryPanel = false,
            showSupportingText = false,
            preferInlineStatus = true,
            contentSpacing = 6.dp,
            sectionSpacing = 8.dp,
            auxiliaryActionInline = true,
        )

        LotteryNetWindowMode.POS -> AdaptiveScreenContract(
            usePrimaryPanel = true,
            showSupportingText = false,
            preferInlineStatus = true,
            contentSpacing = 8.dp,
            sectionSpacing = 10.dp,
            auxiliaryActionInline = true,
        )

        else -> AdaptiveScreenContract(
            usePrimaryPanel = true,
            showSupportingText = true,
            preferInlineStatus = false,
            contentSpacing = 10.dp,
            sectionSpacing = 12.dp,
            auxiliaryActionInline = false,
        )
    }
}

internal fun resolveOverflowLayoutContract(windowMode: LotteryNetWindowMode): OverflowLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> OverflowLayoutContract(
            dropdownMaxHeight = 132.dp,
            dropdownMaxWidth = 280.dp,
            sheetMaxHeightFraction = 0.78f,
            listMaxHeightFraction = 0.42f,
            compactSpacing = 5.dp,
            sheetHorizontalPadding = 6.dp,
            sheetVerticalPadding = 6.dp,
        )

        LotteryNetWindowMode.POS -> OverflowLayoutContract(
            dropdownMaxHeight = 156.dp,
            dropdownMaxWidth = 320.dp,
            sheetMaxHeightFraction = 0.82f,
            listMaxHeightFraction = 0.48f,
            compactSpacing = 6.dp,
            sheetHorizontalPadding = 8.dp,
            sheetVerticalPadding = 8.dp,
        )

        else -> OverflowLayoutContract(
            dropdownMaxHeight = 260.dp,
            dropdownMaxWidth = 420.dp,
            sheetMaxHeightFraction = 0.90f,
            listMaxHeightFraction = 0.60f,
            compactSpacing = 10.dp,
            sheetHorizontalPadding = 12.dp,
            sheetVerticalPadding = 12.dp,
        )
    }
}

internal fun resolveAdaptiveViewportContract(
    widthClass: WindowWidthSizeClass,
    heightClass: WindowHeightSizeClass,
): AdaptiveViewportContract {
    val tightHeight = heightClass == WindowHeightSizeClass.Compact
    val compactWidth = widthClass == WindowWidthSizeClass.Compact
    return AdaptiveViewportContract(
        widthClass = widthClass,
        heightClass = heightClass,
        compactBottomNav = compactWidth && tightHeight,
        compactTypography = compactWidth,
        preferIconOnlyActions = compactWidth && tightHeight,
    )
}

@Composable
fun rememberLotteryNetVisualSpec(): LotteryNetVisualSpec {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val widthClass = fallbackWidthSizeClass(widthDp)
    val heightClass = fallbackHeightSizeClass(heightDp)
    val manualPosModeEnabled = remember(context) { LocalPosModeRepository(context).isEnabled() }
    val viewport = remember(widthClass, heightClass) {
        resolveAdaptiveViewportContract(widthClass, heightClass)
    }
    val automaticMode = when {
        widthClass == WindowWidthSizeClass.Expanded || widthDp >= 1100 -> LotteryNetWindowMode.WIDE
        widthClass == WindowWidthSizeClass.Medium || widthDp >= 760 -> LotteryNetWindowMode.TABLET
        viewport.compactBottomNav || heightDp < 690 || widthDp < 380 -> LotteryNetWindowMode.POS_TIGHT
        else -> LotteryNetWindowMode.POS
    }
    val posLiteViewport = remember(widthDp, heightDp, manualPosModeEnabled) {
        resolvePosLiteViewportContract(widthDp, heightDp, manualPosModeEnabled)
    }
    val mode = if (manualPosModeEnabled || posLiteViewport.windowMode == LotteryNetWindowMode.POS_TIGHT) {
        posLiteViewport.windowMode
    } else {
        resolveEffectiveLotteryNetWindowMode(automaticMode, manualPosModeEnabled)
    }
    val sizes = resolveLotteryNetSizeProfile(mode)
    return remember(mode) {
        LotteryNetVisualSpec(
            windowMode = mode,
            sizes = sizes,
            colors = resolveLotteryNetStatusColors(),
        )
    }
}

internal fun resolveEffectiveLotteryNetWindowMode(
    automaticMode: LotteryNetWindowMode,
    manualPosModeEnabled: Boolean,
): LotteryNetWindowMode {
    return if (manualPosModeEnabled) LotteryNetWindowMode.POS_TIGHT else automaticMode
}

internal fun resolveLotteryNetStatusColors(): LotteryNetStatusColors {
    val ink = Color(0xFF07111F)
    return LotteryNetStatusColors(
        background = Color(0xFFF4F7FB),
        chrome = Color(0xFF12345A),
        chromeSurface = Color(0xFF1E4E7D),
        chromeText = Color(0xFFFFFFFF),
        chromeMuted = Color(0xFFD8E7F8),
        gain = Color(0xFF0F9D58),
        loss = Color(0xFFC62828),
        warning = Color(0xFFEA8A12),
        neutral = Color(0xFF64748B),
        ink = ink,
        muted = ink,
        panel = Color(0xFFFFFFFF),
        panelAlt = Color(0xFFF8FAFD),
        border = Color(0xFFD7E2EF),
        sale = Color(0xFF0F9D58),
        saleSurface = Color(0xFFE8F8F1),
        tickets = Color(0xFF2154D6),
        ticketsSurface = Color(0xFFE8F0FF),
        results = Color(0xFFC6891A),
        resultsSurface = Color(0xFFFFF5DA),
        finance = Color(0xFF118263),
        financeSurface = Color(0xFFE4F7F0),
        recharge = Color(0xFFE86F2E),
        rechargeSurface = Color(0xFFFFEEE3),
        admin = Color(0xFF21427A),
        adminSurface = Color(0xFFE9F0FF),
        printer = Color(0xFF162235),
        printerSurface = Color(0xFFEDF3FA),
        actionPrimary = Color(0xFF2154D6),
        actionPrimarySurface = Color(0xFFE8F0FF),
        warningSurface = Color(0xFFFFF5DA),
        dangerSurface = Color(0xFFFEF2F2),
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun fallbackWidthSizeClass(widthDp: Int): WindowWidthSizeClass {
    return when {
        widthDp >= 840 -> WindowWidthSizeClass.Expanded
        widthDp >= 600 -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Compact
    }
}

private fun fallbackHeightSizeClass(heightDp: Int): WindowHeightSizeClass {
    return when {
        heightDp >= 900 -> WindowHeightSizeClass.Expanded
        heightDp >= 480 -> WindowHeightSizeClass.Medium
        else -> WindowHeightSizeClass.Compact
    }
}

@Composable
fun AppTopBar(
    spec: ScreenChromeSpec,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    val adaptive = resolveAdaptiveScreenContract(visual.windowMode)
    val compactTopBar = visual.windowMode == LotteryNetWindowMode.POS_TIGHT
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(visual.sizes.topBarHeight)
                .background(visual.colors.chrome)
                .padding(horizontal = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 0.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onOpenMenu,
                shape = RoundedCornerShape(9.dp),
                color = Color.Transparent,
                border = null,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.size(if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 34.dp else 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Menu,
                        contentDescription = "Abrir menú",
                        tint = visual.colors.chromeText,
                        modifier = Modifier.size(visual.sizes.topBarIcon),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp),
            ) {
                Text(
                    text = spec.title,
                    style = (if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium).merge(
                        TextStyle(
                            color = visual.colors.chromeText,
                            fontWeight = FontWeight.Bold,
                        ),
                    ),
                    maxLines = if (compactTopBar) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                spec.subtitle?.takeIf { it.isNotBlank() && (adaptive.showSupportingText || visual.windowMode != LotteryNetWindowMode.POS_TIGHT) }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.merge(
                            TextStyle(
                                color = visual.colors.chromeMuted,
                                fontWeight = FontWeight.Bold,
                            ),
                        ),
                        maxLines = if (compactTopBar) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            spec.rightAction?.let { action ->
                val spinTransition = rememberInfiniteTransition(label = "screen-action-spin")
                val spinRotation by spinTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "screen-action-rotation",
                )
                if (action.label.isNullOrBlank()) {
                    Surface(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        shape = RoundedCornerShape(9.dp),
                        color = Color.Transparent,
                        border = null,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Box(
                            modifier = Modifier.size(if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 34.dp else 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = action.contentDescription,
                                tint = if (action.enabled) visual.colors.chromeText else visual.colors.chromeMuted,
                                modifier = Modifier
                                    .size(visual.sizes.topBarIcon)
                                    .graphicsLayer {
                                        rotationZ = if (action.spinning) spinRotation else 0f
                                    },
                            )
                        }
                    }
                } else {
                    CompactActionButton(
                        label = action.label,
                        onClick = action.onClick,
                        icon = action.icon,
                        modifier = Modifier.padding(end = 4.dp),
                        tone = ActionTone.Primary,
                    )
                }
            }
        }
        HorizontalDivider(color = visual.colors.border)
    }
}

@Composable
fun CompactSectionDivider() {
    val visual = rememberLotteryNetVisualSpec()
    Spacer(modifier = Modifier.height(visual.sizes.sectionGap))
    HorizontalDivider(color = visual.colors.border)
}

@Composable
fun CompactActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tone: ActionTone = if (active) ActionTone.Success else ActionTone.Secondary,
) {
    val visual = rememberLotteryNetVisualSpec()
    val resolvedTone = if (active && tone == ActionTone.Secondary) ActionTone.Success else tone
    val toneContract = resolveCompactActionButtonToneContract(resolvedTone, enabled, active, visual.colors)
    val motion = tween<Color>(durationMillis = 140)
    val sizeMotion = tween<Dp>(durationMillis = 140)
    val scaleMotion = tween<Float>(durationMillis = 120)
    val background by animateColorAsState(toneContract.background, animationSpec = motion, label = "actionBackground")
    val foreground by animateColorAsState(toneContract.foreground, animationSpec = motion, label = "actionForeground")
    val borderColor by animateColorAsState(toneContract.border, animationSpec = motion, label = "actionBorder")
    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            resolvedTone == ActionTone.Secondary -> 0.dp
            else -> 1.dp
        },
        animationSpec = sizeMotion,
        label = "actionElevation",
    )
    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.985f
            active -> 1.015f
            else -> 1f
        },
        animationSpec = scaleMotion,
        label = "actionScale",
    )
    Surface(
        modifier = modifier
            .height(visual.sizes.actionHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick,
        enabled = enabled,
        tonalElevation = elevation,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(visual.sizes.actionIcon))
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.merge(
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = foreground,
                    ),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CompactToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ActionTone = ActionTone.Success,
) {
    val visual = rememberLotteryNetVisualSpec()
    val contract = resolveCompactToggleSwitchToneContract(tone, enabled, visual.colors)
    val scale by animateFloatAsState(
        targetValue = if (checked && enabled) 1.03f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "switchStateScale",
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = contract.checkedThumb,
            checkedTrackColor = contract.checkedTrack,
            checkedBorderColor = contract.checkedTrack,
            uncheckedThumbColor = contract.uncheckedThumb,
            uncheckedTrackColor = contract.uncheckedTrack,
            uncheckedBorderColor = visual.colors.border,
            disabledCheckedThumbColor = contract.disabledThumb,
            disabledCheckedTrackColor = contract.disabledTrack,
            disabledCheckedBorderColor = visual.colors.border,
            disabledUncheckedThumbColor = contract.disabledThumb,
            disabledUncheckedTrackColor = contract.disabledTrack,
            disabledUncheckedBorderColor = visual.colors.border,
        ),
    )
}

@Composable
fun CompactSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    tone: ActionTone = ActionTone.Success,
) {
    val visual = rememberLotteryNetVisualSpec()
    val border by animateColorAsState(
        targetValue = if (checked && enabled) {
            when (tone) {
                ActionTone.Success -> visual.colors.gain.copy(alpha = 0.55f)
                ActionTone.Warning -> visual.colors.warning.copy(alpha = 0.55f)
                ActionTone.Danger -> visual.colors.loss.copy(alpha = 0.55f)
                else -> visual.colors.actionPrimary.copy(alpha = 0.48f)
            }
        } else {
            visual.colors.border
        },
        animationSpec = tween(durationMillis = 140),
        label = "switchRowBorder",
    )
    val background by animateColorAsState(
        targetValue = if (checked && enabled) {
            when (tone) {
                ActionTone.Success -> visual.colors.financeSurface
                ActionTone.Warning -> visual.colors.warningSurface
                ActionTone.Danger -> visual.colors.dangerSurface
                else -> visual.colors.actionPrimarySurface
            }
        } else {
            visual.colors.panelAlt
        },
        animationSpec = tween(durationMillis = 140),
        label = "switchRowBackground",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = background,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) visual.colors.ink else visual.colors.neutral,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CompactToggleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                tone = tone,
            )
        }
    }
}

@Composable
fun CompactSegmentedSelector(
    options: List<QuickFilterChip>,
    selectedId: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = options.size.coerceAtLeast(1),
    enabled: Boolean = true,
) {
    val visual = rememberLotteryNetVisualSpec()
    val contract = resolveCompactSegmentedSelectorContract(visual.colors)
    CompactAdaptiveGrid(
        itemCount = options.size,
        modifier = modifier,
        columns = columns.coerceAtLeast(1),
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) { index, itemModifier ->
        val option = options[index]
        val selected = option.id == selectedId
        val background by animateColorAsState(
            targetValue = if (selected) contract.selectedBackground else contract.unselectedBackground,
            animationSpec = tween(durationMillis = 140),
            label = "segmentBackground",
        )
        val foreground by animateColorAsState(
            targetValue = if (selected) contract.selectedForeground else contract.unselectedForeground,
            animationSpec = tween(durationMillis = 140),
            label = "segmentForeground",
        )
        val border by animateColorAsState(
            targetValue = if (selected) contract.selectedBackground else contract.border,
            animationSpec = tween(durationMillis = 140),
            label = "segmentBorder",
        )
        val elevation by animateDpAsState(
            targetValue = if (selected) 1.dp else 0.dp,
            animationSpec = tween(durationMillis = 140),
            label = "segmentElevation",
        )
        Surface(
            modifier = itemModifier.height(contract.minHeight),
            shape = RoundedCornerShape(visual.sizes.panelRadius),
            color = background,
            contentColor = foreground,
            border = BorderStroke(1.dp, border),
            onClick = { if (enabled) onSelected(option.id) },
            enabled = enabled,
            tonalElevation = elevation,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun CompactLoadingState(
    label: String,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Primary,
) {
    val visual = rememberLotteryNetVisualSpec()
    val contract = resolveCompactLoadingStateContract(tone, visual.colors)
    val border by animateColorAsState(
        targetValue = contract.indicatorColor.copy(alpha = 0.34f),
        animationSpec = tween(durationMillis = 180),
        label = "loadingBorder",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = contract.minHeight),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = contract.surface,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = contract.indicatorColor,
                strokeWidth = 2.dp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = contract.foreground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CompactTextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: ImageVector? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val contract = resolveCompactTextInputContract(visual.colors)
    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { text ->
        { Text(text) }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = contract.minHeight),
        label = { Text(label) },
        placeholder = placeholderContent,
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, tint = visual.colors.muted) }
        },
        enabled = enabled,
        isError = isError,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.merge(TextStyle(color = visual.colors.ink, fontWeight = FontWeight.Bold)),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        colors = lotteryNetTextFieldColors(),
    )
}

@Composable
fun CompactBulkToolbar(
    selectedCount: Int,
    visibleCount: Int,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    applyLabel: String = "Aplicar",
    enabled: Boolean = true,
) {
    val visual = rememberLotteryNetVisualSpec()
    val contract = resolveCompactBulkToolbarContract(selectedCount, visual.colors)
    val background by animateColorAsState(contract.background, animationSpec = tween(durationMillis = 140), label = "bulkBackground")
    val border by animateColorAsState(contract.border, animationSpec = tween(durationMillis = 140), label = "bulkBorder")
    val foreground by animateColorAsState(contract.foreground, animationSpec = tween(durationMillis = 140), label = "bulkForeground")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = contract.minHeight),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = background,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = contract.selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$visibleCount visibles",
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CompactActionButton(
                    label = "Visibles",
                    onClick = onSelectVisible,
                    modifier = Modifier.weight(1f),
                    enabled = enabled && visibleCount > 0,
                    tone = ActionTone.Secondary,
                )
                CompactActionButton(
                    label = "Limpiar",
                    onClick = onClearSelection,
                    modifier = Modifier.weight(1f),
                    enabled = enabled && selectedCount > 0,
                    tone = ActionTone.Secondary,
                )
                CompactActionButton(
                    label = applyLabel,
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    enabled = enabled && visibleCount > 0,
                    tone = ActionTone.Primary,
                )
            }
        }
    }
}

internal fun resolveCompactActionButtonToneContract(
    tone: ActionTone,
    enabled: Boolean,
    active: Boolean,
    colors: LotteryNetStatusColors,
): CompactActionButtonToneContract {
    val resolvedTone = if (active && tone == ActionTone.Secondary) ActionTone.Success else tone
    return when {
        !enabled -> CompactActionButtonToneContract(
            background = colors.panelAlt,
            foreground = colors.ink,
            border = colors.border.copy(alpha = 0.65f),
        )
        resolvedTone == ActionTone.Primary -> CompactActionButtonToneContract(
            background = colors.actionPrimary,
            foreground = Color.White,
            border = colors.actionPrimary,
        )
        resolvedTone == ActionTone.Success -> CompactActionButtonToneContract(
            background = colors.gain,
            foreground = Color.White,
            border = colors.gain,
        )
        resolvedTone == ActionTone.Warning -> CompactActionButtonToneContract(
            background = colors.warningSurface,
            foreground = colors.warning,
            border = colors.warning.copy(alpha = 0.55f),
        )
        resolvedTone == ActionTone.Danger -> CompactActionButtonToneContract(
            background = colors.loss,
            foreground = Color.White,
            border = colors.loss,
        )
        resolvedTone == ActionTone.IntenseBlue -> CompactActionButtonToneContract(
            background = Color(0xFF005BFF),
            foreground = Color.White,
            border = Color(0xFF005BFF),
        )
        resolvedTone == ActionTone.Purple -> CompactActionButtonToneContract(
            background = Color(0xFF6D28D9),
            foreground = Color.White,
            border = Color(0xFF6D28D9),
        )
        else -> CompactActionButtonToneContract(
            background = colors.actionPrimarySurface,
            foreground = colors.actionPrimary,
            border = colors.actionPrimary.copy(alpha = 0.42f),
        )
    }
}

internal fun resolveCompactToggleSwitchToneContract(
    tone: ActionTone,
    enabled: Boolean,
    colors: LotteryNetStatusColors,
): CompactToggleSwitchToneContract {
    val checkedTrack = when (tone) {
        ActionTone.Success -> colors.gain
        ActionTone.Warning -> colors.warning
        ActionTone.Danger -> colors.loss
        ActionTone.Primary,
        ActionTone.IntenseBlue,
        ActionTone.Purple,
        ActionTone.Secondary -> colors.actionPrimary
    }
    return CompactToggleSwitchToneContract(
        checkedTrack = if (enabled) checkedTrack else colors.panelAlt,
        checkedThumb = Color.White,
        uncheckedTrack = colors.panelAlt,
        uncheckedThumb = colors.neutral,
        disabledTrack = colors.panelAlt,
        disabledThumb = colors.border,
    )
}

internal fun resolveCompactSegmentedSelectorContract(colors: LotteryNetStatusColors): CompactSegmentedSelectorContract {
    return CompactSegmentedSelectorContract(
        selectedBackground = colors.actionPrimary,
        selectedForeground = Color.White,
        unselectedBackground = colors.panelAlt,
        unselectedForeground = colors.actionPrimary,
        border = colors.border,
        minHeight = 38.dp,
    )
}

internal fun resolveCompactLoadingStateContract(
    tone: ActionTone,
    colors: LotteryNetStatusColors,
): CompactLoadingStateContract {
    val indicator = when (tone) {
        ActionTone.Success -> colors.gain
        ActionTone.Warning -> colors.warning
        ActionTone.Danger -> colors.loss
        else -> colors.actionPrimary
    }
    val surface = when (tone) {
        ActionTone.Success -> colors.financeSurface
        ActionTone.Warning -> colors.warningSurface
        ActionTone.Danger -> colors.dangerSurface
        else -> colors.actionPrimarySurface
    }
    return CompactLoadingStateContract(
        indicatorColor = indicator,
        surface = surface,
        foreground = colors.ink,
        minHeight = 38.dp,
    )
}

internal fun resolveCompactTextInputContract(colors: LotteryNetStatusColors): CompactTextInputContract {
    return CompactTextInputContract(
        container = colors.panel,
        focusedBorder = colors.actionPrimary,
        unfocusedBorder = colors.border,
        errorBorder = colors.loss,
        disabledContainer = colors.panelAlt,
        minHeight = 48.dp,
    )
}

internal fun resolveCompactBulkToolbarContract(
    selectedCount: Int,
    colors: LotteryNetStatusColors,
): CompactBulkToolbarContract {
    val hasSelection = selectedCount > 0
    return CompactBulkToolbarContract(
        background = if (hasSelection) colors.actionPrimarySurface else colors.panelAlt,
        border = if (hasSelection) colors.actionPrimary.copy(alpha = 0.42f) else colors.border,
        foreground = if (hasSelection) colors.actionPrimary else colors.ink,
        selectedLabel = "$selectedCount seleccionado(s)",
        minHeight = 84.dp,
    )
}

data class MetricStripItem(
    val label: String,
    val value: String,
    val tone: Color,
)

data class QuickFilterChip(
    val id: String,
    val label: String,
)

@Composable
fun CompactPanel(
    modifier: Modifier = Modifier,
    alt: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = if (alt) visual.colors.panelAlt else visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = if (alt) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(visual.sizes.panelContentGap),
            content = content,
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    alt: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompactPanel(
        modifier = modifier,
        alt = alt,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun AppHeaderCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Color? = null,
    status: String? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.let {
                OperationalIconBadge(icon = it, tone = tone ?: visual.colors.actionPrimary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            status?.takeIf { it.isNotBlank() }?.let {
                StatusPill(label = it, tone = tone ?: visual.colors.neutral)
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color,
) {
    CompactStatusBadge(label = label, modifier = modifier, tone = tone)
}

@Composable
fun SyncStatusBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val resolvedTone = tone ?: visual.colors.gain
    SectionCard(
        modifier = modifier,
        alt = true,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(label = title, tone = resolvedTone)
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.ink,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DashboardMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    tone: Color? = null,
    icon: ImageVector? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val resolvedTone = tone ?: visual.colors.actionPrimary
    SectionCard(modifier = modifier, alt = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon?.let { OperationalIconBadge(icon = it, tone = resolvedTone, modifier = Modifier.size(32.dp)) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = visual.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.titleLarge, color = resolvedTone, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun MoneyMetricCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    helper: String? = null,
) {
    DashboardMetricCard(
        label = label,
        value = amount,
        meta = helper,
        tone = tone,
        modifier = modifier,
    )
}

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    CompactActionButton(
        label = label,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        tone = tone,
        icon = icon,
        enabled = enabled,
    )
}

@Composable
fun LoadingButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Primary,
    icon: ImageVector? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactActionButton(
        label = if (loading) "Procesando..." else label,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 52.dp),
        tone = tone,
        icon = if (loading) null else icon,
        enabled = !loading,
    )
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = visual.colors.actionPrimary,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
fun FilterChipGroup(
    filters: List<QuickFilterChip>,
    selectedId: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuickFilterChips(
        filters = filters,
        selectedId = selectedId,
        onSelected = onSelected,
        modifier = modifier,
    )
}

@Composable
fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar",
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeight: Dp = 54.dp,
) {
    val visual = rememberLotteryNetVisualSpec()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = visual.colors.muted)
        },
        placeholder = {
            Text(placeholder, color = visual.colors.muted, style = MaterialTheme.typography.bodyMedium)
        },
        textStyle = MaterialTheme.typography.bodyMedium.merge(TextStyle(color = visual.colors.ink)),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = visual.colors.panel,
            unfocusedContainerColor = visual.colors.panel,
            disabledContainerColor = visual.colors.panelAlt,
            focusedIndicatorColor = visual.colors.actionPrimary,
            unfocusedIndicatorColor = visual.colors.border,
            focusedLabelColor = visual.colors.actionPrimary,
            unfocusedLabelColor = visual.colors.muted,
            cursorColor = visual.colors.actionPrimary,
        ),
    )
}

@Composable
fun DropdownSelectorCard(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val dropdown = resolveFintechDropdownContract(visual.colors)
    val targetBackground = tone ?: dropdown.background
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 140),
        label = "dropdownBackground",
    )
    val border by animateColorAsState(
        targetValue = dropdown.border,
        animationSpec = tween(durationMillis = 140),
        label = "dropdownBorder",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp),
        onClick = onClick,
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = background,
        border = BorderStroke(1.dp, border),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = dropdown.foreground.copy(alpha = 0.86f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = dropdown.foreground, fontWeight = dropdown.valueWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = dropdown.foreground)
        }
    }
}

@Composable
fun CashierCard(
    name: String,
    username: String,
    status: String,
    modifier: Modifier = Modifier,
    sales: String,
    recharges: String,
    cash: String,
    active: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val tone = if (active) visual.colors.gain else visual.colors.loss
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    SectionCard(modifier = cardModifier, alt = false) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = visual.colors.ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(username, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill(label = status, tone = tone)
        }
        MetricStrip(
            items = listOf(
                MetricStripItem("Venta", sales, visual.colors.gain),
                MetricStripItem("Recarga", recharges, visual.colors.recharge),
                MetricStripItem("Caja", cash, visual.colors.actionPrimary),
            ),
        )
    }
}

@Composable
fun UserCard(
    title: String,
    subtitle: String,
    role: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
) {
    CompactRecordRow(
        title = title,
        subtitle = subtitle,
        meta = meta ?: role,
        modifier = modifier,
        badgeLabel = if (active) "Activo" else "Bloqueado",
        badgeTone = if (active) rememberLotteryNetVisualSpec().colors.gain else rememberLotteryNetVisualSpec().colors.loss,
        onClick = onClick,
    )
}

@Composable
fun TicketPreviewCard(
    serial: String,
    title: String,
    total: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(serial, style = MaterialTheme.typography.titleMedium, color = visual.colors.ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            status?.let { StatusPill(label = it, tone = visual.colors.gain) }
            Text(total, style = MaterialTheme.typography.titleLarge, color = visual.colors.actionPrimary, fontWeight = FontWeight.Black, maxLines = 1)
        }
        HorizontalDivider(color = visual.colors.border)
        content()
    }
}

@Composable
fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Sin datos",
) {
    val visual = rememberLotteryNetVisualSpec()
    SectionCard(modifier = modifier, alt = true) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontWeight = FontWeight.Black)
        Text(message, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun MetricStrip(
    items: List<MetricStripItem>,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    val itemSpacing = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 6.dp else 8.dp
    val itemPadding = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) {
        PaddingValues(horizontal = 8.dp, vertical = 7.dp)
    } else {
        PaddingValues(horizontal = 10.dp, vertical = 9.dp)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        items.forEach { item ->
            CompactPanel(
                modifier = Modifier.weight(1f),
                alt = true,
                contentPadding = itemPadding,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = item.tone,
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = item.tone,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun QuickFilterChips(
    filters: List<QuickFilterChip>,
    selectedId: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { filter ->
            CompactActionButton(
                label = filter.label,
                onClick = { onSelected(filter.id) },
                modifier = Modifier.weight(1f),
                tone = if (filter.id == selectedId) ActionTone.Success else ActionTone.Secondary,
            )
        }
    }
}

@Composable
fun lotteryNetTextFieldColors() = rememberLotteryNetVisualSpec().colors.let { colors ->
    TextFieldDefaults.colors(
        focusedContainerColor = colors.panel,
        unfocusedContainerColor = colors.panel,
        disabledContainerColor = colors.panelAlt,
        focusedIndicatorColor = colors.actionPrimary,
        unfocusedIndicatorColor = colors.border,
        focusedLabelColor = colors.actionPrimary,
        unfocusedLabelColor = colors.muted,
        cursorColor = colors.actionPrimary,
    )
}

@Composable
fun CompactAdaptiveGrid(
    itemCount: Int,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    itemContent: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val safeColumns = columns.coerceAtLeast(1)
    val rows = remember(itemCount, safeColumns) {
        (0 until itemCount).toList().chunked(safeColumns)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                row.forEach { index ->
                    itemContent(index, Modifier.weight(1f))
                }
                repeat(safeColumns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun OperationalListHeader(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = visual.colors.ink,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CollapsibleSectionPanel(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = title, meta = if (expanded) meta else meta)
            Text(
                text = if (expanded) "Ocultar" else "Mostrar",
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    tone: Color? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.merge(
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = visual.colors.ink,
                ),
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        meta?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = it,
                modifier = Modifier.widthIn(max = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 124.dp else 172.dp),
                style = MaterialTheme.typography.labelSmall,
                color = tone ?: visual.colors.muted,
                fontWeight = FontWeight.Bold,
                maxLines = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun ScreenHeaderPanel(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null,
    badgeTone: Color? = null,
    actionIcon: ImageVector? = null,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val compactHeader = visual.windowMode == LotteryNetWindowMode.POS_TIGHT
    CompactPanel(modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                color = visual.colors.panelAlt,
                border = BorderStroke(1.dp, visual.colors.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = visual.colors.ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (actionIcon != null && onAction != null) {
                Surface(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    color = visual.colors.panelAlt,
                    border = BorderStroke(1.dp, visual.colors.border),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            actionIcon,
                            contentDescription = actionContentDescription,
                            tint = visual.colors.admin,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.merge(
                        TextStyle(
                            color = visual.colors.ink,
                            fontWeight = FontWeight.Bold,
                        ),
                    ),
                    maxLines = if (compactHeader) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compactHeader || subtitle.length <= 36) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (compactHeader) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            badgeLabel?.let {
                CompactStatusBadge(
                    label = it,
                    tone = badgeTone ?: visual.colors.neutral,
                )
            }
        }
    }
}

@Composable
fun CompactStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color,
) {
    Row(
        modifier = modifier
            .background(tone.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(tone, CircleShape),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.merge(
                TextStyle(
                    color = tone,
                    fontWeight = FontWeight.Bold,
                ),
            ),
        )
    }
}

@Composable
fun DenseOperationalMetric(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = rememberLotteryNetVisualSpec().colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = tone,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DenseOperationalStatusLine(
    label: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    CompactStatusBadge(
        label = label,
        tone = tone,
        modifier = modifier,
    )
}

@Composable
fun DenseOpenIcon(
    modifier: Modifier = Modifier,
    contentDescription: String = "Abrir",
    tone: Color = rememberLotteryNetVisualSpec().colors.actionPrimary,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
        contentDescription = contentDescription,
        tint = tone,
        modifier = modifier,
    )
}

@Composable
fun CompactTicketSaveSyncStatus(
    contract: TicketSaveSyncUiContract,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    val toneColor = when (contract.tone) {
        ActionTone.Primary -> visual.colors.actionPrimary
        ActionTone.Secondary -> visual.colors.neutral
        ActionTone.Success -> visual.colors.gain
        ActionTone.Warning -> visual.colors.warning
        ActionTone.Danger -> visual.colors.loss
        ActionTone.IntenseBlue -> Color(0xFF005BFF)
        ActionTone.Purple -> Color(0xFF6D28D9)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(toneColor.copy(alpha = 0.10f), RoundedCornerShape(visual.sizes.panelRadius))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactStatusBadge(
            label = contract.label,
            tone = toneColor,
        )
        Text(
            text = contract.message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = toneColor,
            fontWeight = FontWeight.Bold,
            maxLines = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CompactKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    tone: Color? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.95f),
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.ink,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1.05f),
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = tone ?: visual.colors.ink,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Bold,
            maxLines = if (visual.windowMode in setOf(LotteryNetWindowMode.POS_TIGHT, LotteryNetWindowMode.POS)) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun CompactEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    val adaptive = resolveAdaptiveScreenContract(visual.windowMode)
    if (adaptive.preferInlineStatus) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        CompactPanel(modifier = modifier, alt = true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun CompactRecordRow(
    title: String,
    subtitle: String,
    meta: String,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null,
    badgeTone: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val rowModifier = if (onClick != null) modifier
        .fillMaxWidth()
        .clickable(onClick = onClick) else modifier.fillMaxWidth()
    CompactPanel(modifier = rowModifier, alt = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                badgeLabel?.let {
                    CompactStatusBadge(
                        label = it,
                        tone = badgeTone ?: visual.colors.neutral,
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun OperationalIconBadge(
    icon: ImageVector,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Box(
        modifier = modifier
            .size(34.dp)
            .background(tone.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(visual.sizes.actionIcon),
        )
    }
}

@Composable
fun OperationalActionRow(
    title: String,
    subtitle: String,
    meta: String,
    icon: ImageVector,
    tone: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val contract = remember(visual.windowMode) { resolveOperationalUiContract(visual.windowMode) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panelAlt,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(contract.primaryRowHeight)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OperationalIconBadge(icon = icon, tone = tone)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contract.showSupportingText && subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                        maxLines = contract.maxSubtitleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = tone,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OperationalSettingRow(
    title: String,
    subtitle: String,
    meta: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val clickHandler = onClick ?: {}
    OperationalActionRow(
        title = title,
        subtitle = subtitle,
        meta = meta,
        icon = icon,
        tone = tone ?: visual.colors.admin,
        modifier = modifier,
        onClick = clickHandler,
    )
}

@Composable
fun OperationalPrimaryCard(
    title: String,
    value: String,
    meta: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    CompactPanel(
        modifier = modifier,
        alt = true,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = tone,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = tone,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = meta,
            style = MaterialTheme.typography.labelSmall,
            color = rememberLotteryNetVisualSpec().colors.muted,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LotteryLogo(
    assetPath: String?,
    fallback: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFFF7FBFF),
    fillBounds: Boolean = false,
) {
    val bitmap = rememberAssetLogoBitmap(assetPath)
    AssetLogoBox(bitmap = bitmap, fallback = fallback, modifier = modifier, tintColor = tintColor, fillBounds = fillBounds)
}

@Composable
fun ProviderLogo(
    providerId: String?,
    fallback: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFFF7FBFF),
    fillBounds: Boolean = false,
) {
    val bitmap = rememberAssetLogoBitmap(NativeLogoResolver.providerAsset(providerId))
    AssetLogoBox(bitmap = bitmap, fallback = fallback, modifier = modifier, tintColor = tintColor, fillBounds = fillBounds)
}

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFFF7FBFF),
    fillBounds: Boolean = false,
) {
    val bitmap = rememberAssetLogoBitmap(NativeLogoResolver.BRAND_ICON_ASSET)
    AssetLogoBox(bitmap = bitmap, fallback = "LN", modifier = modifier, tintColor = tintColor, fillBounds = fillBounds)
}

@Composable
private fun rememberAssetLogoBitmap(assetPath: String?): Bitmap? {
    val context = LocalContext.current
    val initialBitmap = remember(assetPath) { LotteryLogoBitmapLoader.peek(assetPath) }
    val bitmap by produceState<Bitmap?>(initialValue = initialBitmap, assetPath, context) {
        LotteryLogoBitmapLoader.peek(assetPath)?.let { cached ->
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            LotteryLogoBitmapLoader.load(context, assetPath)
        }
    }
    return bitmap
}

@Composable
private fun AssetLogoBox(
    bitmap: Bitmap?,
    fallback: String,
    modifier: Modifier = Modifier,
    tintColor: Color,
    fillBounds: Boolean,
) {
    val visual = rememberLotteryNetVisualSpec()
    Box(
        modifier = modifier
            .background(tintColor, RoundedCornerShape(visual.sizes.panelRadius))
            .padding(visual.sizes.logoPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = fallback,
                modifier = if (fillBounds) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = fallback.replace(Regex("[^A-Za-z0-9]"), "").take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = rememberLotteryNetVisualSpec().colors.ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun BottomNavBar(
    role: UserRole,
    active: NativeBottomTab?,
    onSelected: (NativeBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!shouldShowBottomNav(role)) return
    val visual = rememberLotteryNetVisualSpec()
    val bottomNavLayout = remember(visual.windowMode) { resolveBottomNavLayout(visual.windowMode) }
    val viewport = rememberAdaptiveViewportContract()
    val items = remember(role) { bottomNavItems(role) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = visual.colors.panel,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(color = visual.colors.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomNavLayout.barHeight)
                    .padding(
                        start = 6.dp,
                        end = 6.dp,
                        top = 2.dp,
                        bottom = 4.dp + bottomNavLayout.extraBottomGap,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.tab == active
                    val iconBox = if (selected) bottomNavLayout.iconBoxSelected else bottomNavLayout.iconBoxIdle
                    val showLabel = bottomNavLayout.showLabels
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelected(item.tab) }
                            .padding(horizontal = 1.dp, vertical = 0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                            .size(iconBox)
                                .background(
                                    if (selected) item.bg else visual.colors.panelAlt.copy(alpha = 0.55f),
                                    RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) item.fg else visual.colors.muted,
                                modifier = Modifier.size(visual.sizes.bottomNavIcon),
                            )
                        }
                        if (showLabel) {
                            Spacer(modifier = Modifier.height(bottomNavLayout.iconLabelGap))
                            Text(
                                text = item.label,
                                modifier = Modifier.padding(top = bottomNavLayout.labelTopPadding),
                                fontSize = visual.sizes.bottomNavLabel,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Bold,
                                color = if (selected) item.fg else visual.colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberAdaptiveViewportContract(): AdaptiveViewportContract {
    val configuration = LocalConfiguration.current
    val widthClass = fallbackWidthSizeClass(configuration.screenWidthDp)
    val heightClass = fallbackHeightSizeClass(configuration.screenHeightDp)
    return remember(widthClass, heightClass) {
        resolveAdaptiveViewportContract(widthClass, heightClass)
    }
}

internal operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    return PaddingValues(
        start = calculateLeftPadding(LayoutDirection.Ltr) + other.calculateLeftPadding(LayoutDirection.Ltr),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateRightPadding(LayoutDirection.Ltr) + other.calculateRightPadding(LayoutDirection.Ltr),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    )
}

fun gainColor(): Color = Color(0xFF0F9D58)

fun lossColor(): Color = Color(0xFFC62828)

fun warningColor(): Color = Color(0xFFEA580C)

fun openShellMenu(context: Context) {
    context.startActivity(
        Intent(context, ShellActivity::class.java).apply {
            putExtra(ShellActivity.EXTRA_FORCE_MENU, true)
        },
    )
}

fun openMasterHome(context: Context) {
    context.startActivity(
        Intent(context, ShellActivity::class.java).apply {
            putExtra(ShellActivity.EXTRA_FORCE_MENU, true)
        },
    )
}

fun openSafeSales(context: Context, role: UserRole) {
    startSafeNativeDestination(context, role, NativeDestination.SALES)
}

fun openSafeResults(context: Context, role: UserRole) {
    startSafeNativeDestination(context, role, NativeDestination.RESULTS)
}

fun openBottomTab(context: Context, role: UserRole, tab: NativeBottomTab) {
    val destination = when (tab) {
        NativeBottomTab.SALE -> return openSafeSales(context, role)
        NativeBottomTab.LIST -> NativeDestination.TICKET_SUMMARY
        NativeBottomTab.DASHBOARD -> when (role) {
            UserRole.ADMIN -> NativeDestination.SALES
            UserRole.CASHIER -> NativeDestination.SALES
            UserRole.MASTER -> NativeDestination.MASTER_DASHBOARD
            else -> NativeDestination.LOGIN
        }
        NativeBottomTab.RESULTS -> return openSafeResults(context, role)
        NativeBottomTab.MENU -> NativeDestination.SHELL_MENU
    }
    startSafeNativeDestination(context, role, destination)
}

internal fun resolveBottomTabActivityClassName(role: UserRole, tab: NativeBottomTab): String {
    return when (tab) {
        NativeBottomTab.SALE -> if (role == UserRole.MASTER) ShellActivity::class.java.name else SalesActivity::class.java.name
        NativeBottomTab.LIST -> if (role == UserRole.MASTER) ShellActivity::class.java.name else TicketSummaryActivity::class.java.name
        NativeBottomTab.DASHBOARD -> when (role) {
            UserRole.ADMIN -> SalesActivity::class.java.name
            UserRole.CASHIER -> SalesActivity::class.java.name
            UserRole.MASTER -> MasterDashboardActivity::class.java.name
            else -> ShellActivity::class.java.name
        }
        NativeBottomTab.RESULTS -> if (role == UserRole.MASTER) ShellActivity::class.java.name else ResultsActivity::class.java.name
        NativeBottomTab.MENU -> ShellActivity::class.java.name
    }
}

private fun bottomNavItems(role: UserRole): List<NativeBottomNavItem> {
    return if (role == UserRole.CASHIER) {
        listOf(
            NativeBottomNavItem(NativeBottomTab.SALE, "Venta", Icons.Rounded.PointOfSale, Color(0xFF0E9B6C), Color(0xFFE8F8F1)),
            NativeBottomNavItem(NativeBottomTab.LIST, "Lista", Icons.AutoMirrored.Rounded.ReceiptLong, Color(0xFF2154D6), Color(0xFFE8F0FF)),
            NativeBottomNavItem(NativeBottomTab.DASHBOARD, "Inicio", Icons.Rounded.Analytics, Color(0xFFC24578), Color(0xFFFFEAF2)),
            NativeBottomNavItem(NativeBottomTab.RESULTS, "Resultados", Icons.Rounded.Today, Color(0xFFC6891A), Color(0xFFFFF5DA)),
            NativeBottomNavItem(NativeBottomTab.MENU, "Más", Icons.Rounded.Tune, Color(0xFF21427A), Color(0xFFE9F0FF)),
        )
    } else {
        listOf(
            NativeBottomNavItem(NativeBottomTab.SALE, "Venta", Icons.Rounded.PointOfSale, Color(0xFF0E9B6C), Color(0xFFE8F8F1)),
            NativeBottomNavItem(NativeBottomTab.LIST, "Tickets", Icons.AutoMirrored.Rounded.ReceiptLong, Color(0xFF2154D6), Color(0xFFE8F0FF)),
            NativeBottomNavItem(NativeBottomTab.DASHBOARD, "Panel", Icons.Rounded.Analytics, Color(0xFFC24578), Color(0xFFFFEAF2)),
            NativeBottomNavItem(NativeBottomTab.RESULTS, "Resultados", Icons.Rounded.Today, Color(0xFFC6891A), Color(0xFFFFF5DA)),
            NativeBottomNavItem(NativeBottomTab.MENU, "Menú", Icons.Rounded.Tune, Color(0xFF21427A), Color(0xFFE9F0FF)),
        )
    }
}
