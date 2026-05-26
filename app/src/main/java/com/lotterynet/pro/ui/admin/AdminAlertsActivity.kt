package com.lotterynet.pro.ui.admin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SystemAlert
import com.lotterynet.pro.core.storage.LocalAlertsRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme

class AdminAlertsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_ALERTS)) return
        val session = activeSession ?: return
        LocalUsersRepository(this).touchSession(session)
        val repository = LocalAlertsRepository(this)

        setContent {
            LotteryNetComposeTheme {
                var alerts by remember { mutableStateOf(repository.getAlerts()) }
                AdminAlertsRoute(
                    session = session,
                    bancaName = session.banca ?: "LotteryNet",
                    alerts = alerts,
                    onBack = { finish() },
                    onMarkRead = { alert ->
                        repository.markRead(alert.id)
                        alerts = repository.getAlerts()
                    },
                )
            }
        }
    }
}

@Composable
private fun AdminAlertsRoute(
    session: ActiveSession,
    bancaName: String,
    alerts: List<SystemAlert>,
    onBack: () -> Unit,
    onMarkRead: (SystemAlert) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val unreadCount = remember(alerts) { alerts.count { !it.read } }
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
                        title = "Alertas",
                        subtitle = bancaName,
                        onBack = onBack,
                        badgeLabel = if (unreadCount > 0) "$unreadCount sin leer" else "Todo al día",
                        badgeTone = if (unreadCount > 0) warningColor() else gainColor(),
                    )
                }
                item {
                    CompactPanel {
                        SectionHeader(title = "Resumen", meta = "${alerts.size} registros")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AlertSummary(modifier = Modifier.weight(1f), icon = Icons.Rounded.Notifications, label = "Total", value = alerts.size.toString(), tone = visual.colors.ink)
                            AlertSummary(modifier = Modifier.weight(1f), icon = Icons.Rounded.WarningAmber, label = "Sin leer", value = unreadCount.toString(), tone = warningColor())
                        }
                    }
                }
                if (alerts.isEmpty()) {
                    item {
                        CompactPanel {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                Text("Sin alertas activas.", color = visual.colors.muted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(alerts, key = { it.id }) { alert ->
                        AlertRow(alert = alert, onMarkRead = { onMarkRead(alert) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertSummary(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tone: Color,
) {
    CompactPanel(modifier = modifier, alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tone)
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
                Text(value, style = MaterialTheme.typography.titleSmall, color = tone, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: SystemAlert,
    onMarkRead: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val tone = levelColor(alert.level)
    CompactPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .background(tone.copy(alpha = if (alert.read) 0.14f else 0.9f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = if (alert.read) tone else Color.White)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactStatusBadge(
                        label = alert.type.ifBlank { "alerta" },
                        tone = tone,
                    )
                    Text(
                        alert.timestampLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alert.read) visual.colors.muted else visual.colors.ink,
                )
            }
        }
        if (!alert.read) {
            CompactActionButton(
                label = "Marcar leída",
                onClick = onMarkRead,
                icon = Icons.Rounded.Done,
                active = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun levelColor(level: String): Color = when (level.lowercase()) {
    "warning" -> Color(0xFFD97706)
    "error" -> Color(0xFFDC2626)
    "success" -> Color(0xFF059669)
    else -> Color(0xFF2563EB)
}
