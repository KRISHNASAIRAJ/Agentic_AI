package com.jupiterconnect.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Jupiter Connect premium palette - same electric-lime automotive system used
 * by the Jupiter Drive branch. Kept under the original name so the rest of the
 * app's references stay valid.
 */
object JupiterColors {
    val Accent = Color(0xFFC8FF3D)
    val AccentSoft = Color(0xFFE6FFB0)
    val AccentDim = Color(0xFF7FA321)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF5A5A)

    val Surface = Color(0xFF08090B)
    val SurfaceDim = Color(0xFF050607)
    val SurfaceBright = Color(0xFF1B1F24)
    val Lowest = Color(0xFF050607)
    val Low = Color(0xFF13161A)
    val Container = Color(0xFF13161A)
    val High = Color(0xFF1B1F24)
    val Highest = Color(0xFF242A2F)
    val Outline = Color(0xFF2E343A)
    val OutlineVariant = Color(0xFF262B31)
    val Hairline = Color(0xFF262B31)

    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceVariant = Color(0xFF8E959E)

    // Retained aliases mapped onto the new system.
    val Primary = Accent
    val OnPrimary = Color(0xFF0A0C07)
    val PrimaryContainer = Color(0xFF1B2410)
    val OnPrimaryContainer = Accent
    val Secondary = Color(0xFFA9B0A0)
    val Tertiary = Accent
    val OnTertiary = Color(0xFF0A0C07)
    val Error = Red
    val Warning = Amber
}
