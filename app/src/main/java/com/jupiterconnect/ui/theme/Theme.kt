package com.jupiterconnect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = JupiterColors.Primary,
    onPrimary = JupiterColors.OnPrimary,
    primaryContainer = JupiterColors.PrimaryContainer,
    onPrimaryContainer = JupiterColors.OnPrimaryContainer,
    secondary = JupiterColors.Secondary,
    onSecondary = JupiterColors.OnSurface,
    tertiary = JupiterColors.Tertiary,
    onTertiary = JupiterColors.OnTertiary,
    background = JupiterColors.Surface,
    onBackground = JupiterColors.OnSurface,
    surface = JupiterColors.Surface,
    onSurface = JupiterColors.OnSurface,
    surfaceVariant = JupiterColors.Low,
    onSurfaceVariant = JupiterColors.OnSurfaceVariant,
    surfaceTint = JupiterColors.Accent,
    error = JupiterColors.Error,
    onError = JupiterColors.OnSurface,
    outline = JupiterColors.Outline,
    outlineVariant = JupiterColors.OutlineVariant,
)

/** Dark mode only at launch — the palette is locked to the Meridian tokens. */
@Composable
fun JupiterConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = JupiterTypography,
        content = content,
    )
}
