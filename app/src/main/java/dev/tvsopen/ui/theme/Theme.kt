package dev.tvsopen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Jupiter Connect premium palette.
 *
 * The visual language comes from the automotive dashboards used as the design
 * reference: a near-black canvas, a single electric-lime accent, large tabular
 * numbers, and softly elevated cards with hairline borders. Lime is reserved
 * for the primary action, active states and the live route — it is never used
 * for body text, which keeps the screen calm.
 *
 * A light scheme is provided because the reference set also contains light
 * dashboards and TVSRide exposes a Light/Dark/Auto setting. The accent stays
 * lime in both; only the canvas and text invert.
 */
object PremiumColors {
    val Accent = Color(0xFFC8FF3D)
    val AccentSoft = Color(0xFFE6FFB0)
    val AccentDim = Color(0xFF7FA321)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF5A5A)
    val Canvas = Color(0xFF08090B)
    val Card = Color(0xFF13161A)
    val CardElevated = Color(0xFF1B1F24)
    val Muted = Color(0xFF8E959E)
    val Hairline = Color(0xFF262B31)
    val LightCanvas = Color(0xFFF3F5F0)
    val LightCard = Color(0xFFFFFFFF)
    val LightInk = Color(0xFF12140F)
    val LightMuted = Color(0xFF6B7067)
    val LightHairline = Color(0xFFE1E5DA)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF6FA300),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF12140F),
    secondary = Color(0xFF6B7067),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F5F0),
    onSecondaryContainer = Color(0xFF12140F),
    tertiary = Color(0xFFB27A00),
    tertiaryContainer = Color(0xFFF3F5F0),
    onTertiaryContainer = Color(0xFF12140F),
    error = Color(0xFFC6372F),
    onError = Color.White,
    background = Color(0xFFF3F5F0),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12140F),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6B7067),
    outline = Color(0xFFE1E5DA),
)

private val DarkColors = darkColorScheme(
    primary = PremiumColors.Accent,
    onPrimary = Color(0xFF0A0C07),
    primaryContainer = Color(0xFF13161A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFA9B0A0),
    onSecondary = Color(0xFF0A0C07),
    secondaryContainer = Color(0xFF1B1F24),
    onSecondaryContainer = Color.White,
    tertiary = PremiumColors.Amber,
    tertiaryContainer = Color(0xFF1B1F24),
    onTertiaryContainer = Color.White,
    error = PremiumColors.Red,
    onError = Color(0xFF0A0C07),
    background = PremiumColors.Canvas,
    surface = PremiumColors.Canvas,
    onSurface = Color.White,
    surfaceVariant = PremiumColors.Card,
    onSurfaceVariant = PremiumColors.Muted,
    outline = PremiumColors.Hairline,
)

/** Retained because ScanScreen and OverviewScreen read tile fills from it. */
@Immutable
data class TilePalette(
    val largeLeft: Color,
    val largeRight: Color,
    val small1: Color,
    val small2: Color,
    val small3: Color,
    val neutral: Color,
    val accent: Color,
    val connected: Color,
)

private val DarkTiles = TilePalette(
    largeLeft = PremiumColors.Card,
    largeRight = PremiumColors.CardElevated,
    small1 = PremiumColors.Card,
    small2 = PremiumColors.CardElevated,
    small3 = PremiumColors.Card,
    neutral = PremiumColors.Card,
    accent = PremiumColors.Accent,
    connected = PremiumColors.Accent,
)

private val LightTiles = TilePalette(
    largeLeft = PremiumColors.LightCard,
    largeRight = PremiumColors.LightCard,
    small1 = PremiumColors.LightCard,
    small2 = PremiumColors.LightCard,
    small3 = PremiumColors.LightCard,
    neutral = PremiumColors.LightCard,
    accent = PremiumColors.Accent,
    connected = Color(0xFF34A853),
)

val LocalTilePalette = staticCompositionLocalOf { DarkTiles }

/** One switch style everywhere — lime track when on. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = PremiumColors.Accent,
            checkedThumbColor = Color(0xFF0A0C07),
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = if (dark) PremiumColors.Hairline else PremiumColors.LightHairline,
            uncheckedThumbColor = if (dark) PremiumColors.Muted else Color.White,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
)

/** Tabular numerals for telemetry readouts only. */
val NumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
)

@Composable
fun TVSRideTheme(
    mode: ThemeMode = ThemeMode.AUTO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val useDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> darkTheme
    }
    CompositionLocalProvider(
        LocalTilePalette provides if (useDark) DarkTiles else LightTiles,
    ) {
        MaterialTheme(
            colorScheme = if (useDark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
