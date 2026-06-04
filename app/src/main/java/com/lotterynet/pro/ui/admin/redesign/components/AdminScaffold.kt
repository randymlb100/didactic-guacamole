package com.lotterynet.pro.ui.admin.redesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.admin.redesign.theme.*

sealed class FintechNavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val roles: List<UserRole>
) {
    object Dashboard : FintechNavigationItem(
        "dashboard", "Dashboard", Icons.Rounded.Dashboard,
        listOf(UserRole.MASTER, UserRole.ADMIN, UserRole.SUPERVISOR)
    )
    object Users : FintechNavigationItem(
        "users", "Usuarios", Icons.Rounded.People,
        listOf(UserRole.MASTER, UserRole.ADMIN, UserRole.SUPERVISOR)
    )
    object Bancas : FintechNavigationItem(
        "bancas", "Bancas", Icons.Rounded.Storefront,
        listOf(UserRole.MASTER)
    )
    object Reports : FintechNavigationItem(
        "reports", "Finanzas", Icons.Rounded.Analytics,
        listOf(UserRole.MASTER, UserRole.ADMIN, UserRole.SUPERVISOR)
    )
    object Limits : FintechNavigationItem(
        "limits", "Límites", Icons.Rounded.Tune,
        listOf(UserRole.MASTER, UserRole.ADMIN)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScaffold(
    role: UserRole,
    currentRoute: String,
    onRouteSelected: (String) -> Unit,
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightAction: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val navItems = remember(role) {
        listOf(
            FintechNavigationItem.Dashboard,
            FintechNavigationItem.Users,
            FintechNavigationItem.Bancas,
            FintechNavigationItem.Reports,
            FintechNavigationItem.Limits
        ).filter { it.roles.contains(role) }
    }

    Row(modifier = Modifier.fillMaxSize().background(SlateBg)) {
        // Sidebar for tablet / wide viewports
        if (isTablet) {
            NavigationRail(
                containerColor = SlateCard,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .border(width = 1.dp, color = SlateBorder),
                header = {
                    Box(
                        modifier = Modifier.padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "LotteryNet",
                            tint = FintechGold,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                navItems.forEach { item ->
                    val isSelected = currentRoute == item.route
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onRouteSelected(item.route) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isSelected) FintechGold else SlateTextMuted
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                style = FintechTypography.labelSmall,
                                maxLines = 1,
                                color = if (isSelected) FintechGold else SlateTextMuted
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = FintechGold,
                            unselectedIconColor = SlateTextMuted,
                            indicatorColor = SlateBg
                        )
                    )
                }
            }
        }

        // Main content area
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                style = FintechTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = SlateTextInk
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = FintechTypography.bodyMedium,
                                    color = SlateTextMuted
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "Regresar",
                                    tint = SlateTextInk
                                )
                            }
                        }
                    },
                    actions = {
                        rightAction?.invoke()
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SlateBg,
                        titleContentColor = SlateTextInk,
                        actionIconContentColor = SlateTextInk
                    )
                )
            },
            bottomBar = {
                // Bottom Nav for mobile screens
                if (!isTablet) {
                    NavigationBar(
                        containerColor = SlateCard,
                        modifier = Modifier
                            .height(58.dp)
                            .border(width = 1.dp, color = SlateBorder),
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { onRouteSelected(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        style = FintechTypography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FintechGold,
                                    selectedTextColor = FintechGold,
                                    unselectedIconColor = SlateTextMuted,
                                    unselectedTextColor = SlateTextMuted,
                                    indicatorColor = SlateBg
                                )
                            )
                        }
                    }
                }
            },
            containerColor = SlateBg,
            content = content
        )
    }
}
