package com.jupiterconnect.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Jupiter Connect palette: black / dark-neutral surfaces and a single red
 * accent system. No green, amber, blue, purple or light backgrounds.
 */
object JupiterColors {
    val Accent = Color(0xFFFF4D66)       // light red - icons, links, active tab
    val AccentSoft = Color(0xFFFF4D66)
    val AccentDim = Color(0xFFEB0028)
    val Red = Color(0xFFEB0028)          // primary red
    val Amber = Color(0xFFFF4D66)        // warnings use light red, never amber
    val RedTint = Color(0xFF2A0E14)      // icon circles
    val ActiveTab = Color(0xFF2A0A10)
    val NavPill = Color(0xFF101012)

    val Surface = Color(0xFF000000)
    val SurfaceDim = Color(0xFF000000)
    val SurfaceBright = Color(0xFF232327)
    val Lowest = Color(0xFF000000)
    val Low = Color(0xFF141416)          // cards
    val Container = Color(0xFF141416)
    val High = Color(0xFF232327)         // raised / secondary
    val Highest = Color(0xFF232327)
    val Outline = Color(0xFF232327)
    val OutlineVariant = Color(0xFF232327)
    val Hairline = Color(0xFF232327)

    val OnSurface = Color(0xFFF2F2F4)
    val OnSurfaceVariant = Color(0xFF9A9AA2)

    val Primary = Red
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = RedTint
    val OnPrimaryContainer = Accent
    val Secondary = Accent
    val Tertiary = Red
    val OnTertiary = Color(0xFFFFFFFF)
    val Error = Color(0xFFFF6F82)
    val Warning = Accent
}
