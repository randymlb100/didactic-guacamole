package com.lotterynet.pro.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LotteryNetLightColors = lightColorScheme(
    primary = Coal,
    onPrimary = Paper,
    secondary = TicketsBlue,
    onSecondary = Paper,
    background = CanvasSand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = MintSurface,
    onSurfaceVariant = InkSoft,
    outline = Line,
    error = Clay,
    errorContainer = ClaySurface,
    onErrorContainer = Clay,
    secondaryContainer = TicketsBlueSurface,
    onSecondaryContainer = TicketsBlue,
    tertiary = ResultsAmber,
    onTertiary = Ink,
    tertiaryContainer = ResultsAmberSurface,
    onTertiaryContainer = ResultsAmber,
)

@Composable
fun LotteryNetComposeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LotteryNetLightColors,
        typography = LotteryNetTypography,
        content = content,
    )
}
