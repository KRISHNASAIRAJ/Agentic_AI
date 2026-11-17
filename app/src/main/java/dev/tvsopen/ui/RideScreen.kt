package dev.tvsopen.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvsopen.session.HudState
import dev.tvsopen.session.Telemetry
import dev.tvsopen.ui.theme.AppSwitch
import dev.tvsopen.ui.theme.NumberStyle
import dev.tvsopen.ui.theme.PremiumColors

/**
 * Ride / navigation screen.
 *
 * A stylised route map drawn with Canvas (no network, no tile dependency) plus
 * the live turn-by-turn instruction mirrored from the rider's maps app. The
 * route marker pulses slowly to signal a live link; everything flows in a
 * single column so nothing can overlap.
 */
@Composable
fun RideScreen(
    hud: HudState,
    telemetry: Telemetry,
    navMirroring: Boolean,
    notificationAccessGranted: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onNavMirrorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Caption("CURRENT TRIP")
                Spacer(Modifier.height(4.dp))
                Text("Ongoing Ride", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            StatusPill(label = if (hud.active) "Nav live" else "Idle", ok = hud.active)
        }
        Spacer(Modifier.height(18.dp))

        if (!notificationAccessGranted) {
            PremiumCard(
                modifier = Modifier.enterAnimated(delayMs = 0),
                color = PremiumColors.Amber.copy(alpha = 0.12f),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = PremiumColors.Amber,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Caption("NOTIFICATION ACCESS OFF", color = PremiumColors.Amber)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Turn-by-turn can't be read until Android allows it. If the " +
                            "toggle is greyed out, open App info → ⋮ → Allow restricted " +
                            "settings first (Android 13+ sideload rule).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenNotificationAccess,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumColors.Amber,
                            contentColor = Color(0xFF0A0C07),
                        ),
                    ) { Text("Open notification access") }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        RouteMap(active = hud.active, modifier = Modifier.enterAnimated(delayMs = 0))
        Spacer(Modifier.height(14.dp))

        InstructionCard(hud = hud, modifier = Modifier.enterAnimated(delayMs = 60))
        Spacer(Modifier.height(14.dp))

        SectionHeading("Live figures", trailing = if (telemetry.speedKmh != null) "LIVE" else null)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigReadout(
                modifier = Modifier
                    .weight(1f)
                    .then(Modifier),
                value = telemetry.speedKmh?.toString() ?: "—",
                unit = "km/h",
                caption = "Speed",
                accent = true,
            )
            BigReadout(
                modifier = Modifier.weight(1f),
                value = telemetry.distanceToEmptyKm?.toString() ?: "—",
                unit = "km",
                caption = "Range left",
            )
            BigReadout(
                modifier = Modifier.weight(1f),
                value = telemetry.fuelBars?.toString() ?: "—",
                unit = "/6",
                caption = "Fuel",
            )
        }
        Spacer(Modifier.height(14.dp))

        PremiumCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Navigation, contentDescription = null,
                        tint = PremiumColors.Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Navigation mirroring", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Send turn-by-turn to the cluster",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppSwitch(checked = navMirroring, onCheckedChange = onNavMirrorChange)
                }
                hud.framesSent.takeIf { it > 0 }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("$it instructions sent this session",
                        style = MaterialTheme.typography.labelSmall,
                        color = PremiumColors.Accent)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RouteMap(active: Boolean, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "marker").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "marker-scale",
    )
    Surface(
        modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(24.dp),
        color = PremiumColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, PremiumColors.Hairline),
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // Faint road grid.
                val grid = PremiumColors.Hairline.copy(alpha = 0.35f)
                for (i in 1..5) {
                    drawLine(grid, Offset(0f, h * i / 6f), Offset(w, h * i / 6f), strokeWidth = 1.dp.toPx())
                    drawLine(grid, Offset(w * i / 6f, 0f), Offset(w * i / 6f, h), strokeWidth = 1.dp.toPx())
                }
                // The route: a smooth polyline from bottom-left to top-right.
                val route = Path().apply {
                    moveTo(w * 0.22f, h * 0.82f)
                    cubicTo(w * 0.30f, h * 0.62f, w * 0.42f, h * 0.66f, w * 0.48f, h * 0.46f)
                    cubicTo(w * 0.54f, h * 0.28f, w * 0.68f, h * 0.34f, w * 0.76f, h * 0.18f)
                }
                drawPath(route, Color.Black.copy(alpha = 0.5f), style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
                drawPath(route, PremiumColors.Accent, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                // Destination marker.
                drawCircle(PremiumColors.Accent, radius = 8.dp.toPx(),
                    center = Offset(w * 0.22f, h * 0.82f))
                drawCircle(PremiumColors.Card, radius = 3.dp.toPx(),
                    center = Offset(w * 0.22f, h * 0.82f))
            }
            // Pulsing current-position marker.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size((26 * pulse).dp)
                        .then(Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        Modifier.size(20.dp),
                        shape = RoundedCornerShape(50),
                        color = PremiumColors.Accent.copy(alpha = 0.25f),
                    ) {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Navigation, contentDescription = "You",
                                tint = PremiumColors.Accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionCard(hud: HudState, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier, color = PremiumColors.CardElevated) {
        Column(Modifier.padding(18.dp)) {
            Caption(if (hud.active) "NEXT MANOEUVRE" else "NO ACTIVE ROUTE",
                color = if (hud.active) PremiumColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(
                hud.instructionTitle ?: "Start navigation in your maps app",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            hud.street?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                InfoCell(value = if (hud.distanceMeters >= 0) "${hud.distanceMeters}" else "—",
                    unit = "m", caption = "Distance")
                InfoCell(value = if (hud.etaMinutes > 0) "${hud.etaMinutes}" else "—",
                    unit = "min", caption = "Est. time")
                hud.provider?.let { InfoCell(value = it, unit = "", caption = "Provider") }
            }
        }
    }
}

@Composable
private fun InfoCell(value: String, unit: String, caption: String) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumberStyle, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.size(3.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

