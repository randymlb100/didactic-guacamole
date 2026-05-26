package com.lotterynet.pro.ui.admin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.model.AuditEntry
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.storage.LocalAuditRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme

class AdminAuditActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_AUDIT)) return
        val session = activeSession ?: return
        LocalUsersRepository(this).touchSession(session)
        val entries = LocalAuditRepository(this).getEntries()

        setContent {
            LotteryNetComposeTheme {
                AdminAuditRoute(
                    session = session,
                    bancaName = session.banca ?: "LotteryNet",
                    entries = entries,
                    onBack = { finish() },
                )
            }
        }
    }
}

private enum class AuditFilter {
    ALL,
    CRITICAL,
    USER_CHANGES,
}

@Composable
private fun AdminAuditRoute(
    session: ActiveSession,
    bancaName: String,
    entries: List<AuditEntry>,
    onBack: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(AuditFilter.ALL) }
    val filteredEntries = remember(entries, filter) {
        when (filter) {
            AuditFilter.ALL -> entries
            AuditFilter.CRITICAL -> entries.filter { it.action in criticalActions }
            AuditFilter.USER_CHANGES -> entries.filter { it.action.contains("CAJERO") || it.action.contains("CHANGE") || it.action.contains("BALANCE") }
        }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
                verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = visual.sizes.screenPaddingV),
            ) {
                item {
                    ScreenHeaderPanel(
                        title = "Auditoría",
                        subtitle = bancaName,
                        onBack = onBack,
                        badgeLabel = if (filter == AuditFilter.ALL) "Todo" else if (filter == AuditFilter.CRITICAL) "Críticas" else "Usuarios",
                        badgeTone = if (filter == AuditFilter.CRITICAL) warningColor() else visual.colors.neutral,
                    )
                }
                item {
                    CompactPanel {
                        SectionHeader(title = "Resumen", meta = "${filteredEntries.size} registros")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AuditSummary(modifier = Modifier.weight(1f), icon = Icons.Rounded.QueryStats, label = "Total", value = entries.size.toString(), tone = visual.colors.ink)
                            AuditSummary(modifier = Modifier.weight(1f), icon = Icons.Rounded.WarningAmber, label = "Críticas", value = entries.count { it.action in criticalActions }.toString(), tone = warningColor())
                            AuditSummary(modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.FactCheck, label = "Usuarios", value = entries.count { it.action.contains("CAJERO") || it.action.contains("CHANGE") }.toString(), tone = Color(0xFF4F46E5))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AuditChip("Todo", filter == AuditFilter.ALL) { filter = AuditFilter.ALL }
                            AuditChip("Críticas", filter == AuditFilter.CRITICAL) { filter = AuditFilter.CRITICAL }
                            AuditChip("Usuarios", filter == AuditFilter.USER_CHANGES) { filter = AuditFilter.USER_CHANGES }
                        }
                    }
                }
                if (filteredEntries.isEmpty()) {
                    item {
                        CompactPanel {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                Text("Sin registros de auditoría.", color = visual.colors.muted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(filteredEntries, key = { "${it.timestampLabel}|${it.user}|${it.action}|${it.detail}" }) { entry ->
                        AuditEntryRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditSummary(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tone: Color,
) {
    CompactPanel(modifier = modifier, alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = tone)
            androidx.compose.foundation.layout.Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
                Text(value, style = MaterialTheme.typography.titleSmall, color = tone, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun AuditChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun AuditEntryRow(entry: AuditEntry) {
    val visual = rememberLotteryNetVisualSpec()
    val tone = actionColor(entry.action)
    CompactPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactStatusBadge(
                label = entry.action.ifBlank { "SIN_ACCION" },
                tone = tone,
            )
            Text(
                entry.timestampLabel,
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            "${entry.user.ifBlank { "sistema" }} · ${entry.role.ifBlank { "sin rol" }}",
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.detail.ifBlank { "Sin detalle adicional." },
            style = MaterialTheme.typography.bodyMedium,
            color = visual.colors.ink,
        )
    }
}

private fun actionColor(action: String): Color {
    return when {
        action in criticalActions -> Color(0xFFD97706)
        action.contains("BLOQUEAR") || action.contains("REVOCAR") || action.contains("BORRAR") -> Color(0xFFB91C1C)
        action.contains("ACTIVAR") || action.contains("CREAR") || action.contains("PERMISO") -> Color(0xFF15803D)
        else -> Color(0xFF07111F)
    }
}

private val criticalActions = setOf(
    "TOPE_PAGO_CAJERO",
    "CAMBIAR_TOPE_MASTER_RECARGA",
    "ASIGNAR_BALANCE",
    "CAMBIAR_LIMITE_GLOBAL",
    "CAMBIAR_LIMITE_RECARGA",
    "PERMISO_OFFLINE",
    "PERMISO_OFFLINE_DIRECTO",
    "REVOCAR_PERMISO_OFFLINE",
)
