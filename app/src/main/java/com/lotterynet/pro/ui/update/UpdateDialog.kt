package com.lotterynet.pro.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.update.OtaDownloadStatus
import com.lotterynet.pro.core.update.OtaUpdateInfo
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    fallbackInfo: OtaUpdateInfo,
    onUpdateNow: (OtaUpdateInfo) -> Unit,
    onLater: (OtaUpdateInfo) -> Unit,
    onRetry: (OtaUpdateInfo) -> Unit,
) {
    val info = when (state) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.ReadyToInstall -> state.info
        is UpdateUiState.Error -> state.info
        else -> fallbackInfo
    }
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = visual.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompactPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = visual.colors.actionPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, visual.colors.border),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = visual.colors.actionPrimary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.title.ifBlank { "Nueva actualizacion" },
                            style = MaterialTheme.typography.titleLarge,
                            color = visual.colors.ink,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Version ${info.versionName} · ${formatApkSize(info.apkSizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = visual.colors.muted,
                        )
                    }
                    CompactStatusBadge(
                        label = if (info.blocksCurrentBuild) "Obligatoria" else "Opcional",
                        tone = if (info.blocksCurrentBuild) MaterialTheme.colorScheme.error else visual.colors.gain,
                    )
                }

                CompactPanel(alt = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    SectionHeader(title = "Cambios", meta = "Produccion")
                    val lines = info.changelog.ifEmpty { listOf("Mejoras de estabilidad y rendimiento.") }
                    lines.take(6).forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = visual.colors.ink,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = visual.colors.gain)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "La descarga se valida con SHA-256 y firma oficial antes de instalar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                }

                when (state) {
                    is UpdateUiState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = visual.colors.actionPrimary,
                            trackColor = visual.colors.border,
                        )
                        Text(
                            text = "${state.percent}% · ${state.speedLabel} · ${downloadStatusLabel(state.status)}",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = visual.colors.ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        CompactStatusBadge(state.message, tone = visual.colors.gain)
                    }
                    is UpdateUiState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    is UpdateUiState.Offline -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = visual.colors.warning,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    else -> Unit
                }

                AnimatedVisibility(state !is UpdateUiState.Downloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactActionButton(
                            label = when (state) {
                                is UpdateUiState.Error -> "Reintentar"
                                is UpdateUiState.ReadyToInstall -> "Abrir instalador"
                                else -> "Actualizar ahora"
                            },
                            onClick = {
                                if (state is UpdateUiState.Error) onRetry(info) else onUpdateNow(info)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Download,
                            tone = ActionTone.Primary,
                        )
                        if (shouldShowLaterButton(info)) {
                            CompactActionButton(
                                label = "Mas tarde",
                                onClick = { onLater(info) },
                                modifier = Modifier.fillMaxWidth(),
                                tone = ActionTone.Secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun downloadStatusLabel(status: OtaDownloadStatus): String {
    return when (status) {
        OtaDownloadStatus.IDLE -> "Preparando"
        OtaDownloadStatus.QUEUED -> "En cola"
        OtaDownloadStatus.DOWNLOADING -> "Descargando"
        OtaDownloadStatus.COMPLETED -> "Completado"
        OtaDownloadStatus.FAILED -> "Error"
    }
}
