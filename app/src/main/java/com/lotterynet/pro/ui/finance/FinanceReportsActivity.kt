package com.lotterynet.pro.ui.finance

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.finance.FinanceActorPeriodRow
import com.lotterynet.pro.core.finance.FinancePeriodPreset
import com.lotterynet.pro.core.finance.FinancePeriodReport
import com.lotterynet.pro.core.finance.FinancePeriodRow
import com.lotterynet.pro.core.finance.FinanceScope
import com.lotterynet.pro.core.finance.FinanceScopeType
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.finance.resolveOperationalReportNet
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.printing.ThermalTicketRenderer
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalThermalPrinterRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.ActionFeedbackKind
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.resolveFintechDropdownContract
import com.lotterynet.pro.ui.common.resolveOverflowLayoutContract
import com.lotterynet.pro.ui.common.resolveActionFeedbackMessage
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.printer.PrinterActivity
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.time.LocalDate
import java.time.format.DateTimeParseException

class FinanceReportsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PRESET = "finance_preset"
        const val EXTRA_ANCHOR_DAY = "finance_anchor_day"
        const val EXTRA_FROM_DAY = "finance_from_day"
        const val EXTRA_TO_DAY = "finance_to_day"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.FINANCE)) return
        val session = activeSession ?: return
        LocalUsersRepository(this).touchSession(session)
        val repository = LocalFinanceRepository(
            salesRepository = LocalSalesRepository(this),
            rechargeRepository = LocalRechargeRepository(this),
            usersRepository = LocalUsersRepository(this),
        )
        val availableDays = repository.getAvailableDayKeys()
        val latest = availableDays.lastOrNull() ?: LocalDate.now().toString()
        val scope = repository.resolveScope(session)
        val anchorDay = intent?.getStringExtra(EXTRA_ANCHOR_DAY)?.takeIf { it.isNotBlank() } ?: latest
        val fromDay = intent?.getStringExtra(EXTRA_FROM_DAY)?.takeIf { it.isNotBlank() }
        val toDay = intent?.getStringExtra(EXTRA_TO_DAY)?.takeIf { it.isNotBlank() }
        val launchState = buildSafeFinanceReportLaunch(
            repository = repository,
            scope = scope,
            role = session.role,
            latestDayKey = latest,
            requestedPresetRaw = intent?.getStringExtra(EXTRA_PRESET),
            requestedAnchorDay = anchorDay,
            fromDay = fromDay,
            toDay = toDay,
        )

        setContent {
            LotteryNetComposeTheme {
                FinanceReportsRoute(
                    session = session,
                    scope = scope,
                    initialReport = launchState.report,
                    initialAnchorDay = launchState.anchorDay,
                    recoveredFromLaunchError = launchState.recovered,
                    onBack = { finish() },
                    onGenerate = { preset, anchorDay, from, to ->
                        runCatching {
                            repository.getScopedPeriodReport(
                                scope = scope,
                                preset = preset,
                                anchorDayKey = anchorDay,
                                fromDayKey = from,
                                toDayKey = to,
                            )
                        }.getOrElse {
                            repository.getScopedPeriodReport(
                                scope = scope,
                                preset = if (session.role == UserRole.ADMIN) FinancePeriodPreset.WEEK else FinancePeriodPreset.DAY,
                                anchorDayKey = latest,
                            )
                        }
                    },
                    onShare = { report, whatsappOnly -> shareReportText(report, session, whatsappOnly) },
                    onPrint = { report ->
                        val bitmap = NativeBitmapExport.renderFinancePeriodBitmap(
                            bancaName = session.banca ?: "LotteryNet",
                            report = report,
                            actorLabel = report.scope.actorDisplay.takeIf { report.scope.type == FinanceScopeType.ACTOR },
                        )
                        NativeBitmapExport.printBitmap(this, bitmap, "reporte-periodo-${report.toDayKey}")
                    },
                    onSave = { report ->
                        val bitmap = NativeBitmapExport.renderFinancePeriodBitmap(
                            bancaName = session.banca ?: "LotteryNet",
                            report = report,
                            actorLabel = report.scope.actorDisplay.takeIf { report.scope.type == FinanceScopeType.ACTOR },
                        )
                        NativeBitmapExport.saveBitmapToDownloads(this, bitmap, "reporte-periodo-${report.toDayKey}.png")
                    },
                    onThermal = { report ->
                        val prefs = LocalThermalPrinterRepository(this).getPrefs()
                        val text = ThermalTicketRenderer().renderFinanceSummary(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = report.range.label,
                            summary = report.summary,
                            prefs = prefs,
                            actorLabel = when (report.scope.type) {
                                FinanceScopeType.ACTOR -> report.scope.actorDisplay
                                FinanceScopeType.BANK -> session.banca ?: "Banca completa"
                            },
                        )
                        startActivity(
                            Intent(this, PrinterActivity::class.java).apply {
                                putExtra(PrinterActivity.EXTRA_THERMAL_TITLE, "Reporte térmico")
                                putExtra(PrinterActivity.EXTRA_THERMAL_TEXT, text)
                            },
                        )
                    },
                )
            }
        }
    }

    private fun shareReportText(
        report: FinancePeriodReport,
        session: ActiveSession,
        whatsappOnly: Boolean,
    ) {
        val text = buildFinancePeriodShareText(report, session)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (whatsappOnly) `package` = "com.whatsapp"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            startActivity(
                if (whatsappOnly) intent else Intent.createChooser(intent, "Compartir reporte").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.recoverCatching {
            if (whatsappOnly) {
                startActivity(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        `package` = "com.whatsapp.w4b"
                        putExtra(Intent.EXTRA_TEXT, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } else {
                throw it
            }
        }.onFailure {
            Toast.makeText(
                this,
                if (whatsappOnly) "WhatsApp no esta disponible." else "No se pudo compartir el reporte.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

internal enum class FinanceReportScrollContainer {
    SCREEN,
}

internal fun resolveFinanceReportScrollContainers(): List<FinanceReportScrollContainer> =
    listOf(FinanceReportScrollContainer.SCREEN)

private enum class FinanceReportCompactSection(val id: String, val label: String) {
    SUMMARY("summary", "Resumen"),
    PERIOD("period", "Periodo"),
    DAYS("days", "Días"),
    CASHIERS("cashiers", "Cajeros"),
}

private fun financeReportCompactSectionOptions(): List<QuickFilterChip> =
    FinanceReportCompactSection.entries.map { QuickFilterChip(it.id, it.label) }

@Composable
private fun FinanceReportsRoute(
    session: ActiveSession,
    scope: FinanceScope,
    initialReport: FinancePeriodReport,
    initialAnchorDay: String,
    recoveredFromLaunchError: Boolean,
    onBack: () -> Unit,
    onGenerate: (FinancePeriodPreset, String, String?, String?) -> FinancePeriodReport,
    onShare: (FinancePeriodReport, Boolean) -> Unit,
    onPrint: (FinancePeriodReport) -> Boolean,
    onSave: (FinancePeriodReport) -> Boolean,
    onThermal: (FinancePeriodReport) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = androidx.compose.ui.platform.LocalContext.current
    var anchorDay by rememberSaveable { mutableStateOf(initialAnchorDay) }
    var fromDay by rememberSaveable { mutableStateOf(initialReport.range.fromDayKey) }
    var toDay by rememberSaveable { mutableStateOf(initialReport.range.toDayKey) }
    var preset by rememberSaveable { mutableStateOf(initialReport.preset) }
    var report by remember { mutableStateOf(initialReport) }
    var selectedReportSectionId by rememberSaveable { mutableStateOf(FinanceReportCompactSection.SUMMARY.id) }
    var statusMessage by rememberSaveable {
        mutableStateOf(
            if (recoveredFromLaunchError) {
                "El periodo solicitado falló y se abrió un rango seguro para seguir trabajando."
            } else {
                "Consolidado ${initialReport.range.label.lowercase()} listo para ${scope.actorDisplay ?: session.banca ?: "la banca"}."
            },
        )
    }

    fun refreshReport(nextPreset: FinancePeriodPreset) {
        report = onGenerate(nextPreset, anchorDay, fromDay, toDay)
        preset = nextPreset
        fromDay = report.range.fromDayKey
        toDay = report.range.toDayKey
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = session.role,
                active = NativeBottomTab.MENU,
                onSelected = { tab -> openBottomTab(context, session.role, tab) },
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = visual.colors.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            ) {
                FinanceReportCompactHeader(
                    title = "Reporte",
                    subtitle = "${session.banca ?: "LotteryNet"} · ${scope.actorDisplay ?: "banca completa"}",
                    onMenu = { com.lotterynet.pro.ui.common.openShellMenu(context) },
                    onBack = onBack,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CompactStatusBadge(label = "Sincronizado", tone = gainColor())
                }
                Spacer(modifier = Modifier.height(8.dp))
                CompactSegmentedSelector(
                    options = financeReportCompactSectionOptions(),
                    selectedId = selectedReportSectionId,
                    onSelected = { selectedReportSectionId = it },
                    columns = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(visual.sizes.sectionGap))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
                    contentPadding = PaddingValues(bottom = visual.sizes.screenPaddingV),
                ) {
                    if (selectedReportSectionId == FinanceReportCompactSection.PERIOD.id) item {
                        FinanceReportPanel {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE1F8F1), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                ) {
                                    Text("Periodo", style = MaterialTheme.typography.labelMedium, color = visual.colors.ink)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${report.rows.size} días", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.QueryStats, contentDescription = null, tint = visual.colors.muted)
                            }
                            SectionHeader(title = "Contexto del reporte", meta = report.range.label)
                            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                            Text(
                                "Activo: ${report.range.label} · ${report.fromDayKey} a ${report.toDayKey}",
                                style = MaterialTheme.typography.labelMedium,
                                color = visual.colors.ink,
                            )
                            financePeriodOptionRows(visual.windowMode).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { option ->
                                        CompactActionButton(
                                            label = financePeriodButtonLabel(option, visual.windowMode),
                                            onClick = {
                                                refreshReport(option.preset)
                                                statusMessage = "Preset ${option.label.lowercase()} aplicado."
                                            },
                                            active = preset == option.preset,
                                            modifier = Modifier.weight(1f),
                                            tone = option.tone,
                                        )
                                    }
                                    repeat(maxOf(0, 3 - row.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            FinanceReportMonthDropdown(
                                anchorDayKey = anchorDay,
                                onSelect = { month ->
                                    anchorDay = month.anchorDayKey
                                    fromDay = month.fromDayKey
                                    toDay = month.toDayKey
                                    report = onGenerate(FinancePeriodPreset.MONTH, month.anchorDayKey, month.fromDayKey, month.toDayKey)
                                    preset = FinancePeriodPreset.MONTH
                                    fromDay = report.range.fromDayKey
                                    toDay = report.range.toDayKey
                                    statusMessage = "${month.label} aplicado completo."
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = anchorDay,
                                    onValueChange = { anchorDay = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Base") },
                                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                OutlinedTextField(
                                    value = fromDay,
                                    onValueChange = { fromDay = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Desde") },
                                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                OutlinedTextField(
                                    value = toDay,
                                    onValueChange = { toDay = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Hasta") },
                                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                CompactActionButton(
                                    label = "Aplicar",
                                    onClick = {
                                        refreshReport(preset)
                                        statusMessage = "Reporte recalculado para ${report.range.label.lowercase()}."
                                    },
                                    icon = Icons.Rounded.QueryStats,
                                    active = true,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactActionButton(
                                    label = "Imprimir",
                                    onClick = {
                                        val opened = onPrint(report)
                                        statusMessage = if (opened) "Flujo de impresión abierto." else "No se pudo abrir impresión."
                                    },
                                    icon = Icons.Rounded.Print,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactActionButton(
                                    label = "Térmico",
                                    onClick = { onThermal(report) },
                                    icon = Icons.Rounded.PointOfSale,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                CompactActionButton(
                                    label = "WhatsApp",
                                    onClick = { onShare(report, true) },
                                    icon = Icons.Rounded.Whatsapp,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactActionButton(
                                    label = "Compartir",
                                    onClick = { onShare(report, false) },
                                    icon = Icons.Rounded.Share,
                                    active = true,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactActionButton(
                                    label = "Guardar",
                                    onClick = {
                                        val saved = onSave(report)
                                        statusMessage = resolveActionFeedbackMessage(ActionFeedbackKind.SAVE, saved)
                                    },
                                    icon = Icons.Rounded.Download,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (selectedReportSectionId == FinanceReportCompactSection.SUMMARY.id) item { FinancePeriodHero(report) }
                    if (selectedReportSectionId == FinanceReportCompactSection.DAYS.id) item {
                        FinanceReportPanel {
                            if (report.rows.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 30.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No hay movimientos locales en ese rango.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = visual.colors.muted,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                SectionHeader(title = "Detalle por día", meta = "${report.rows.size} filas")
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    report.rows.forEach { row -> FinancePeriodRowCard(row) }
                                }
                            }
                        }
                    }
                    if (selectedReportSectionId == FinanceReportCompactSection.CASHIERS.id && report.actorRows.isNotEmpty()) {
                        item {
                            FinanceReportPanel {
                                SectionHeader(title = "Detalle por cajero", meta = "${report.actorRows.size} actores")
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    report.actorRows.forEach { row -> FinanceActorRowCard(row) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceReportCompactHeader(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF062A57),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("☰", modifier = Modifier.clickable(onClick = onMenu), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
            }
            Text("↩", modifier = Modifier.clickable(onClick = onBack), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

@Composable
private fun FinanceReportPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panel,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun FinanceReportMonthDropdown(
    anchorDayKey: String,
    onSelect: (FinanceMonthUiOption) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val dropdown = resolveFintechDropdownContract(visual.colors)
    val overflow = remember(visual.windowMode) { resolveOverflowLayoutContract(visual.windowMode) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val months = remember(anchorDayKey) { financeMonthOptions(anchorDayKey) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = dropdown.background,
            border = BorderStroke(1.dp, dropdown.border),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = dropdown.foreground)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mes histórico",
                        style = MaterialTheme.typography.labelSmall,
                        color = dropdown.foreground.copy(alpha = 0.86f),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Cargar mes completo",
                        style = MaterialTheme.typography.labelLarge,
                        color = dropdown.foreground,
                        fontWeight = dropdown.valueWeight,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = dropdown.foreground)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(max = overflow.dropdownMaxWidth)
                .heightIn(max = overflow.dropdownMaxHeight),
        ) {
            months.forEach { month ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = month.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = visual.colors.ink,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(month)
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Color(0xFF1D4ED8))
                    },
                )
            }
        }
    }
}

@Composable
private fun FinancePeriodHero(report: FinancePeriodReport) {
    val visual = rememberLotteryNetVisualSpec()
    val periodNet = resolveOperationalReportNet(report.summary)
    val resultTone = financeResultTone(periodNet)
    FinanceReportPanel {
        Text(
            text = "Resumen ${report.range.label}",
            style = MaterialTheme.typography.titleMedium,
            color = visual.colors.ink,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PeriodMetric(modifier = Modifier.weight(1f), label = financeResultLabel(periodNet), value = formatReportSignedMoney(periodNet), accent = resultTone, tint = resultTone.copy(alpha = 0.08f), valueColor = resultTone)
            PeriodMetric(modifier = Modifier.weight(1f), label = "Ventas", value = formatReportMoney(report.summary.ventas), accent = gainColor(), tint = visual.colors.financeSurface, valueColor = gainColor())
            PeriodMetric(modifier = Modifier.weight(1f), label = "Premios", value = formatReportMoney(report.summary.premiosPagados), accent = warningColor(), tint = Color(0xFFFFFBEB), valueColor = warningColor())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PeriodMetric(modifier = Modifier.weight(1f), label = "Caja", value = formatReportMoney(report.summary.cajaDisponible), accent = visual.colors.ink, tint = visual.colors.panelAlt)
            PeriodMetric(modifier = Modifier.weight(1f), label = "Recargas", value = formatReportMoney(report.summary.recargas), accent = visual.colors.recharge, tint = visual.colors.rechargeSurface, valueColor = visual.colors.recharge)
            PeriodMetric(modifier = Modifier.weight(1f), label = "Comisión", value = formatReportMoney(report.summary.comision), accent = visual.colors.loss, tint = Color(0xFFFEF2F2), valueColor = visual.colors.loss)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PeriodMetric(
                modifier = Modifier.weight(1f),
                label = if (report.summary.supervisorComision > 0.0) "Com. sup." else "Tickets",
                value = if (report.summary.supervisorComision > 0.0) {
                    formatReportMoney(report.summary.supervisorComision)
                } else {
                    report.summary.ticketsCount.toString()
                },
                accent = if (report.summary.supervisorComision > 0.0) visual.colors.loss else visual.colors.ink,
                tint = visual.colors.panelAlt,
                valueColor = if (report.summary.supervisorComision > 0.0) visual.colors.loss else visual.colors.ink,
            )
            PeriodMetric(modifier = Modifier.weight(1f), label = "Pendiente", value = formatReportMoney(report.summary.premiosPendientes), accent = warningColor(), tint = Color(0xFFFFFBEB), valueColor = warningColor())
            PeriodMetric(modifier = Modifier.weight(1f), label = "Fuera", value = formatReportMoney(report.summary.fueraDeFinanzaMonto), accent = visual.colors.ink, tint = visual.colors.panelAlt)
        }
    }
}

@Composable
private fun PeriodMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
    tint: Color,
    valueColor: Color? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = tint,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = accent)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = valueColor ?: visual.colors.ink,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun FinancePeriodRowCard(row: FinancePeriodRow) {
    val visual = rememberLotteryNetVisualSpec()
    val net = resolveOperationalReportNet(row.summary)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panelAlt,
        border = BorderStroke(1.dp, visual.colors.border.copy(alpha = 0.9f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = row.dayKey, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink)
            FinanceCompactMetricRow(row.summary, net)
        }
    }
}

@Composable
private fun FinanceActorRowCard(row: FinanceActorPeriodRow) {
    val visual = rememberLotteryNetVisualSpec()
    val net = resolveOperationalReportNet(row.summary)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panelAlt,
        border = BorderStroke(1.dp, visual.colors.border.copy(alpha = 0.9f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = row.actorDisplay, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink)
            FinanceCompactMetricRow(row.summary, net)
        }
    }
}

@Composable
private fun FinanceCompactMetricRow(summary: FinanceSummary, net: Double) {
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FinanceTinyMetric("Venta", formatReportMoney(summary.ventas), gainColor(), Modifier.weight(1f))
            FinanceTinyMetric("Premio", formatReportMoney(summary.premiosPagados), warningColor(), Modifier.weight(1f))
            FinanceTinyMetric(financeResultLabel(net), formatReportSignedMoney(net), financeResultTone(net), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FinanceTinyMetric("Caja", formatReportMoney(summary.cajaDisponible), visual.colors.ink, Modifier.weight(1f))
            FinanceTinyMetric("Recarga", formatReportMoney(summary.recargas), visual.colors.recharge, Modifier.weight(1f))
            FinanceTinyMetric("Tickets", summary.ticketsCount.toString(), visual.colors.ink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FinanceTinyMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val visual = rememberLotteryNetVisualSpec()
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, maxLines = 1)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun parseLocalDateOrNow(value: String): LocalDate {
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        LocalDate.now()
    }
}

private fun buildFinancePeriodShareText(
    report: FinancePeriodReport,
    session: ActiveSession,
): String {
    val header = buildString {
        append("Reporte por periodo")
        session.banca?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
        append('\n')
        append("Periodo: ${report.range.label}")
        append('\n')
        append("Rango: ${report.fromDayKey} a ${report.toDayKey}")
        append('\n')
        append("Ventas: ${formatReportMoney(report.summary.ventas)} · Caja: ${formatReportMoney(report.summary.cajaDisponible)}")
        append('\n')
        append("Recargas: ${formatReportMoney(report.summary.recargas)} · Comisión: ${formatReportMoney(report.summary.comision)}${formatSupervisorCommissionSuffix(report.summary)} · Tickets: ${report.summary.ticketsCount}")
    }
    val lines = report.rows.take(12).mapIndexed { index, row ->
        "${index + 1}. ${row.dayKey} · Ventas ${formatReportMoney(row.summary.ventas)} · Caja ${formatReportMoney(row.summary.cajaDisponible)} · Tickets ${row.summary.ticketsCount}"
    }
    val overflow = if (report.rows.size > 12) "\n... y ${report.rows.size - 12} días más" else ""
    return buildString {
        append(header)
        if (lines.isNotEmpty()) {
            append("\n\n")
            append(lines.joinToString("\n"))
            append(overflow)
        }
    }
}

private fun formatReportMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

private fun formatReportSignedMoney(value: Double): String {
    val prefix = when {
        value > 0.0 -> "+"
        value < 0.0 -> "-"
        else -> ""
    }
    return "$prefix${formatReportMoney(kotlin.math.abs(value))}"
}

@Composable
private fun financeResultTone(value: Double): Color {
    val visual = rememberLotteryNetVisualSpec()
    return when {
        value > 0.0 -> gainColor()
        value < 0.0 -> visual.colors.loss
        else -> visual.colors.ink
    }
}

private fun financeResultLabel(value: Double): String {
    return when {
        value > 0.0 -> "Beneficio"
        value < 0.0 -> "Pérdida"
        else -> "Neutro"
    }
}

private fun formatSupervisorCommissionSuffix(summary: FinanceSummary): String {
    return if (summary.supervisorComision > 0.0) {
        " · Sup. ${formatReportMoney(summary.supervisorComision)}"
    } else {
        ""
    }
}
