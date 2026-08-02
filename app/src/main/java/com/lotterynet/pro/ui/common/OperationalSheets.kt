package com.lotterynet.pro.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SearchableSheetOption(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val meta: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalModalSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionTone: ActionTone = ActionTone.Primary,
    contentScrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = visual.colors.muted)
                }
            }
            HorizontalDivider(color = visual.colors.border)
            if (contentScrollable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
            if (primaryActionLabel != null && onPrimaryAction != null) {
                HorizontalDivider(color = visual.colors.border)
                CompactActionButton(
                    label = primaryActionLabel,
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    tone = primaryActionTone,
                )
            }
        }
    }
}

@Composable
fun SearchableOptionSheet(
    title: String,
    options: List<SearchableSheetOption>,
    selectedId: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    searchLabel: String = "Buscar",
    searchPlaceholder: String = "Nombre, ID o alias",
    emptyMessage: String = "No hay resultados con ese filtro.",
) {
    val visual = rememberLotteryNetVisualSpec()
    var query by rememberSaveable { mutableStateOf("") }
    val filteredOptions = remember(options, query) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) {
            options
        } else {
            options.filter { option ->
                listOfNotNull(option.title, option.subtitle, option.meta)
                    .any { it.lowercase().contains(cleanQuery) }
            }
        }
    }
    OperationalModalSheet(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        modifier = modifier,
        contentScrollable = false,
    ) {
        CompactTextInput(
            label = searchLabel,
            value = query,
            onValueChange = { query = it.take(80) },
            placeholder = searchPlaceholder,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filteredOptions.isEmpty()) {
                item {
                    CompactEmptyState(message = emptyMessage)
                }
            }
            items(filteredOptions, key = { it.id }) { option ->
                SearchableOptionRow(
                    option = option,
                    selected = option.id == selectedId,
                    onClick = { onSelected(option.id) },
                )
            }
        }
        Text(
            text = "${filteredOptions.size} opciones",
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SearchableOptionRow(
    option: SearchableSheetOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val border = if (selected) visual.colors.actionPrimary.copy(alpha = 0.62f) else visual.colors.border
    val background = if (selected) visual.colors.actionPrimarySurface else visual.colors.panelAlt
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = background,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (selected) visual.colors.actionPrimary.copy(alpha = 0.14f) else visual.colors.neutral.copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.title.firstOrNull()?.uppercase() ?: "#",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) visual.colors.actionPrimary else visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                option.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            option.meta?.takeIf { it.isNotBlank() }?.let {
                CompactStatusBadge(label = it, tone = if (selected) visual.colors.actionPrimary else visual.colors.neutral)
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = visual.colors.actionPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun DangerConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = "Confirma antes de afectar la operación.",
) {
    val visual = rememberLotteryNetVisualSpec()
    OperationalModalSheet(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        modifier = modifier,
        primaryActionLabel = confirmLabel,
        onPrimaryAction = onConfirm,
        primaryActionTone = ActionTone.Danger,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(visual.sizes.panelRadius),
            color = visual.colors.dangerSurface,
            border = BorderStroke(1.dp, Color(0x33DC2626)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = visual.colors.ink,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
