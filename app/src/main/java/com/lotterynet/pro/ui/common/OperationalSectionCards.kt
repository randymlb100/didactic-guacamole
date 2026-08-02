package com.lotterynet.pro.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CurrentScopeCard(
    title: String,
    value: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String = "Cambiar",
    tone: ActionTone = ActionTone.Primary,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
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
            CompactActionButton(
                label = actionLabel,
                onClick = onChange,
                tone = tone,
            )
        }
    }
}

@Composable
fun CurrentScopeDropdownCard(
    title: String,
    value: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String = "Cambiar",
    tone: ActionTone = ActionTone.Primary,
) {
    val visual = rememberLotteryNetVisualSpec()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selectedId }?.second ?: value
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
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
            Box {
                CompactActionButton(
                    label = actionLabel,
                    onClick = { expanded = true },
                    tone = tone,
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.second) },
                            onClick = {
                                expanded = false
                                onSelected(option.first)
                            },
                        )
                    }
                }
            }
        }
    }
}

data class ScopeLegendEntry(
    val label: String,
    val description: String,
    val tone: Color,
)

@Composable
fun ScopeLegendCard(
    title: String,
    subtitle: String,
    entries: List<ScopeLegendEntry>,
    modifier: Modifier = Modifier,
    alt: Boolean = true,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        modifier = modifier,
        alt = alt,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    CompactStatusBadge(label = entry.label, tone = entry.tone)
                    Text(
                        text = entry.description,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
