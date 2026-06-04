package com.lotterynet.pro.ui.admin.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.ui.admin.redesign.theme.*
import com.lotterynet.pro.ui.admin.redesign.uiState.BancaDetailState

@Composable
fun UserCard(
    userAccount: UserAccount,
    onEditClick: () -> Unit,
    onToggleActiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SlateCard, RoundedCornerShape(6.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = userAccount.displayName ?: userAccount.user,
                        style = FintechTypography.titleLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = SlateTextInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    StatusBadge(
                        label = if (userAccount.active) "Activo" else "Bloqueado",
                        active = userAccount.active
                    )
                }

                Text(
                    text = "@${userAccount.user} · ${userAccount.role.name}",
                    style = FintechTypography.bodyMedium,
                    color = SlateTextMuted
                )

                if (!userAccount.banca.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = "Banca",
                            tint = SlateTextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = userAccount.banca!!,
                            style = FintechTypography.bodyMedium,
                            color = SlateTextMuted
                        )
                    }
                }
            }

            // Balance & Actions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val balanceColor = if (userAccount.balance >= 0) SuccessGreen else DangerRed
                val prefix = if (userAccount.balance > 0) "+" else ""
                
                Text(
                    text = "$prefix$${String.format("%.2f", userAccount.balance)}",
                    style = FintechTypography.titleSmall.copy(fontWeight = FontWeight.Bold, color = balanceColor),
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar",
                            tint = FintechGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleActiveClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (userAccount.active) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                            contentDescription = if (userAccount.active) "Bloquear" else "Activar",
                            tint = if (userAccount.active) DangerRed else SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BancaCard(
    banca: BancaDetailState,
    onEditClick: () -> Unit,
    onToggleActiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SlateCard, RoundedCornerShape(6.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = banca.name,
                        style = FintechTypography.titleLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = SlateTextInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    StatusBadge(
                        label = if (banca.active) "Abierta" else "Cerrada",
                        active = banca.active
                    )
                }

                Text(
                    text = "Encargado: ${banca.assignedAdmin}",
                    style = FintechTypography.bodyMedium,
                    color = SlateTextMuted
                )

                Text(
                    text = "${banca.activeCajeros} cajeros activos",
                    style = FintechTypography.bodyMedium,
                    color = SlateTextMuted
                )
            }

            // Ventas & Actions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Ventas: $${String.format("%.2f", banca.salesToday)}",
                    style = FintechTypography.titleSmall.copy(fontWeight = FontWeight.Bold, color = FintechGold),
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar",
                            tint = FintechGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleActiveClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (banca.active) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                            contentDescription = if (banca.active) "Bloquear" else "Activar",
                            tint = if (banca.active) DangerRed else SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
