package com.smsdetector.app.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Palette derived from the Stitch designs (OxygenOS-style dark UI)
val Bg = Color(0xFF0B0D0F)
val Surface = Color(0xFF15181B)
val SurfaceHigh = Color(0xFF1D2126)
val SurfaceVariant = Color(0xFF202429)
val Outline = Color(0xFF2B3036)
val Primary = Color(0xFF33A8FF)
val PrimaryDim = Color(0xFF1B6FA8)
val OnPrimary = Color(0xFF04121B)
val TextPrimary = Color(0xFFF2F5F7)
val TextSecondary = Color(0xFF9AA3AC)
val SpamRed = Color(0xFFFF5C66)
val VerifiedGreen = Color(0xFF5DDAC4)
val OtpBlue = Color(0xFF3D9BF5)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFF14435F),
    onPrimaryContainer = Color(0xFFBFE3FF),
    secondary = VerifiedGreen,
    onSecondary = Color(0xFF00201C),
    tertiary = SpamRed,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    error = SpamRed,
    onError = Color(0xFF2A0709)
)

@Composable
fun SmsDetectorTheme(
    darkTheme: Boolean = true, // app ships dark-first to match the design
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Resolve the Activity safely — under instrumentation the view context
            // can be a ContextThemeWrapper, so a hard cast would crash composition.
            var ctx = view.context
            while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
            if (ctx is Activity) {
                val window = ctx.window
                @Suppress("DEPRECATION")
                window.statusBarColor = Bg.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Bg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else DarkColors,
        typography = Typography(),
        content = content
    )
}
