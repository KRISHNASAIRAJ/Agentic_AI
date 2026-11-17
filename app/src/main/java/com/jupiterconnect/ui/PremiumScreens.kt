package com.jupiterconnect.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.ui.theme.JupiterColors
import com.jupiterconnect.ui.theme.NumberStyle

@Composable
private fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        content = content,
    )
}

// ------------------------------------------------------------------ HOME

@Composable
fun PremiumHomeScreen(
    telemetry: Telemetry,
    devices: List<ScannedDevice>,
    scanning: Boolean,
    permissions: PermissionStatus,
    lastReading: com.jupiterconnect.session.LastReading,
    onGrantPermissions: () -> Unit,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onViewMore: () -> Unit,
) {
    // While disconnected, fall back to the last reading the cluster sent so
    // the odometer and fuel range don't drop to a misleading zero.
    val shownOdo = if (telemetry.odometerKm > 0) telemetry.odometerKm else lastReading.odometerKm
    val shownFuel = if (telemetry.connected) telemetry.fuelBars else lastReading.fuelBars
    val shownDte = if (telemetry.connected) telemetry.distanceToEmptyKm else lastReading.distanceToEmptyKm
    Screen {
        Spacer(Modifier.height(18.dp))

        if (!permissions.bleReady) {
            PremiumCard {
                Column(Modifier.padding(16.dp)) {
                    Caption("PERMISSIONS NEEDED", color = JupiterColors.Amber)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Bluetooth (${if (permissions.bluetooth) "on" else "off"}) and " +
                            "Location (${if (permissions.location) "on" else "off"}) are " +
                            "required to scan for the cluster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onGrantPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JupiterColors.Accent, contentColor = JupiterColors.OnPrimary),
                    ) { Text("Grant permissions") }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Caption("GOOD RIDE, RIDER")
                Spacer(Modifier.height(4.dp))
                Text("Jupiter 125", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(if (telemetry.connected) "Cluster link active" else "Waiting for cluster",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(44.dp).clip(CircleShape)
                .background(JupiterColors.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.TwoWheeler, contentDescription = null,
                    tint = JupiterColors.Accent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(18.dp))

        Surface(
            Modifier.fillMaxWidth().enterAnimated(delayMs = 0),
            shape = RoundedCornerShape(26.dp),
            color = JupiterColors.Container,
            border = androidx.compose.foundation.BorderStroke(1.dp, JupiterColors.Hairline),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Caption("LIVE SPEEDOMETER")
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                if (telemetry.connected) "${telemetry.speedKmh}" else "-",
                                style = NumberStyle, fontSize = 64.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("km/h", style = MaterialTheme.typography.titleMedium,
                                color = JupiterColors.Accent, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                    StatusPill(if (telemetry.connected) "Connected" else "Disconnected", telemetry.connected)
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.radialGradient(listOf(
                            JupiterColors.Accent.copy(alpha = 0.10f), Color.Transparent))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.TwoWheeler, contentDescription = "Scooter",
                        tint = if (telemetry.connected) JupiterColors.Accent else JupiterColors.OnSurfaceVariant,
                        modifier = Modifier.size(88.dp))
                }
                if (telemetry.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(telemetry.error, style = MaterialTheme.typography.labelSmall, color = JupiterColors.Red)
                }
                Spacer(Modifier.height(14.dp))
                if (!telemetry.connected) {
                    Button(
                        onClick = onScan,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JupiterColors.Accent, contentColor = JupiterColors.OnPrimary),
                    ) {
                        Icon(Icons.Rounded.Bluetooth, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (scanning) "Scanning..." else "Find my cluster", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Caption("LINK ACTIVE", color = JupiterColors.Accent)
                        Spacer(Modifier.weight(1f))
                        Text(telemetry.address.ifBlank { "-" }, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Nearby devices - the only way to pick and connect to the cluster.
        SectionHeading(
            "Nearby devices",
            trailing = if (scanning) "SCANNING..." else "${devices.size} FOUND",
        )
        Spacer(Modifier.height(10.dp))
        PremiumCard {
            Column(Modifier.padding(8.dp)) {
                if (devices.isEmpty()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            if (scanning) "Looking for Bluetooth devices..."
                            else "No devices yet. Tap scan, then switch the ignition ON and hold MODE for 5s.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    devices.take(3).forEach { d ->
                        Surface(
                            onClick = { onConnect(d.address) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.BluetoothConnected,
                                    contentDescription = null,
                                    tint = if (d.isCluster) JupiterColors.Accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${if (d.isCluster) "â˜... " else ""}${d.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        d.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "${d.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (devices.size > 3) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onViewMore, modifier = Modifier.fillMaxWidth()) {
                        Text("View more (${devices.size - 3})", color = JupiterColors.Accent)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JupiterColors.High, contentColor = JupiterColors.Accent),
                ) { Text(if (scanning) "Scanning..." else "Scan for devices") }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeading("Ride statistics", trailing = if (telemetry.connected) "LIVE" else null)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.enterAnimated(delayMs = 60)) {
            PremiumStat(Modifier.weight(1f), Icons.Rounded.History,
                "%.1f".format(shownOdo), "km", "Odometer")
            PremiumStat(Modifier.weight(1f), Icons.Rounded.Speed,
                "${telemetry.topSpeed()}", "km/h", "Top speed")
        }
        Spacer(Modifier.height(14.dp))

        SectionHeading("Fuel & range")
        Spacer(Modifier.height(10.dp))
        PremiumCard(modifier = Modifier.enterAnimated(delayMs = 120)) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalGasStation, contentDescription = null,
                        tint = JupiterColors.Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Caption("FUEL LEVEL")
                    Spacer(Modifier.weight(1f))
                    Text("${shownFuel} / 6 bars", style = NumberStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FuelRing(bars = shownFuel)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigReadout("${shownDte}", "km",
                        "Range left", modifier = Modifier.weight(1f), accent = true)
                    BigReadout("${telemetry.engineTempC}", "Â°C", "Engine temp",
                        modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeading("Quick actions")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallAction(Modifier.weight(1f), Icons.Rounded.BluetoothConnected,
                if (telemetry.connected) "Disconnect" else "Connect") {
                if (telemetry.connected) ClusterRepository.disconnect() else onScan()
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun Telemetry.topSpeed(): Int = speedKmh

@Composable
private fun PremiumStat(modifier: Modifier, icon: ImageVector, value: String, unit: String, label: String) {
    PremiumCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = JupiterColors.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyle, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun SmallAction(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = JupiterColors.Container,
        border = androidx.compose.foundation.BorderStroke(1.dp, JupiterColors.Hairline),
        onClick = onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = JupiterColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --------------------------------------------------------------- NAVIGATE

@Composable
fun PremiumNavigateScreen(
    instruction: NavInstruction?,
    telemetry: Telemetry,
    mirroring: Boolean,
    onMirroringChange: (Boolean) -> Unit,
) {
    Screen {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Caption("CURRENT TRIP")
                Spacer(Modifier.height(4.dp))
                Text("Ongoing Ride", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            StatusPill(if (instruction != null) "Nav live" else "Waiting", instruction != null)
        }
        Spacer(Modifier.height(18.dp))

        Surface(
            Modifier.fillMaxWidth().height(220.dp).enterAnimated(delayMs = 0),
            shape = RoundedCornerShape(24.dp), color = JupiterColors.Container,
            border = androidx.compose.foundation.BorderStroke(1.dp, JupiterColors.Hairline),
        ) { RouteCanvas(active = instruction != null) }
        Spacer(Modifier.height(14.dp))

        PremiumCard(modifier = Modifier.enterAnimated(delayMs = 60), color = JupiterColors.High) {
            Column(Modifier.padding(18.dp)) {
                Caption(if (instruction != null) "NEXT MANOEUVRE" else "NO ACTIVE ROUTE",
                    color = if (instruction != null) JupiterColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    instruction?.displayLine ?: "Start navigation in Google Maps",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (instruction?.street?.isNotBlank() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text(instruction.street, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    NavInfo(if (instruction != null) "${instruction.distanceMeters}" else "-", "m", "Distance")
                    NavInfo(if (instruction != null && instruction.etaMinutes > 0) "${instruction.etaMinutes}" else "-",
                        "min", "Est. time")
                    if (!instruction?.provider.isNullOrBlank()) {
                        NavInfo(instruction.provider, "", "Provider")
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeading("Live figures", trailing = if (telemetry.connected) "LIVE" else null)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigReadout("${telemetry.speedKmh}", "km/h", "Speed",
                modifier = Modifier.weight(1f), accent = true)
            BigReadout("${telemetry.distanceToEmptyKm}", "km", "Range left",
                modifier = Modifier.weight(1f))
            BigReadout("${telemetry.fuelBars}", "/6", "Fuel", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        PremiumCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Navigation, contentDescription = null,
                        tint = JupiterColors.Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Navigation mirroring", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Send turn-by-turn to the cluster", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppSwitch(checked = mirroring, onCheckedChange = onMirroringChange)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NavInfo(value: String, unit: String, caption: String) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumberStyle, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.size(3.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RouteCanvas(active: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val grid = JupiterColors.Hairline.copy(alpha = 0.35f)
        for (i in 1..5) {
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, h * i / 6f),
                androidx.compose.ui.geometry.Offset(w, h * i / 6f), strokeWidth = 1.dp.toPx())
            drawLine(grid, androidx.compose.ui.geometry.Offset(w * i / 6f, 0f),
                androidx.compose.ui.geometry.Offset(w * i / 6f, h), strokeWidth = 1.dp.toPx())
        }
        val route = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.22f, h * 0.82f)
            cubicTo(w * 0.30f, h * 0.62f, w * 0.42f, h * 0.66f, w * 0.48f, h * 0.46f)
            cubicTo(w * 0.54f, h * 0.28f, w * 0.68f, h * 0.34f, w * 0.76f, h * 0.18f)
        }
        drawPath(route, Color.Black.copy(alpha = 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(16.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(route, JupiterColors.Accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawCircle(JupiterColors.Accent, radius = 8.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.82f))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(20.dp), shape = RoundedCornerShape(50),
            color = JupiterColors.Accent.copy(alpha = 0.25f)) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Navigation, contentDescription = "You",
                    tint = JupiterColors.Accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ RIDES

@Composable
fun PremiumRidesScreen(
    telemetry: Telemetry,
    rides: List<com.jupiterconnect.session.Ride>,
) {
    Screen {
        Spacer(Modifier.height(18.dp))
        Caption("YOUR RIDES")
        Spacer(Modifier.height(4.dp))
        Text("Ride history", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(18.dp))

        val totalKm = rides.sumOf { it.distanceKm }
        PremiumCard(modifier = Modifier.enterAnimated(delayMs = 0)) {
            Column(Modifier.padding(20.dp)) {
                Caption("RECORDED RIDES")
                Spacer(Modifier.height(8.dp))
                Text("%.1f".format(totalKm), style = NumberStyle,
                    fontSize = 48.sp, fontWeight = FontWeight.Bold, color = JupiterColors.Accent)
                Text("km across ${rides.size} ride${if (rides.size == 1) "" else "s"} Â· saved on this phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))

        SectionHeading("Saved rides", trailing = "NEWEST FIRST")
        Spacer(Modifier.height(10.dp))
        if (rides.isEmpty()) {
            PremiumCard {
                Text("No rides yet. Connect to the cluster, ride, then disconnect - the trip is saved automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
            }
        } else {
            rides.forEach { r ->
                PremiumCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.dateLabel, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f))
                            Text("%.1f km".format(r.distanceKm), style = NumberStyle,
                                color = JupiterColors.Accent, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RideMetric(Modifier.weight(1f), "AVG", "%.0f".format(r.avgSpeedKmh), "km/h")
                            RideMetric(Modifier.weight(1f), "TOP", "${r.topSpeedKmh}", "km/h")
                            RideMetric(Modifier.weight(1f), "TIME", "${r.durationMin}", "min")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RideMetric(modifier: Modifier, label: String, value: String, unit: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = NumberStyle, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------- FIND

@Composable
fun PremiumFindScreen(telemetry: Telemetry, onScan: () -> Unit) {
    Screen {
        Spacer(Modifier.height(18.dp))
        Caption("SCOOTER")
        Spacer(Modifier.height(4.dp))
        Text("Find", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(18.dp))

        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.radialGradient(listOf(
                JupiterColors.Accent.copy(alpha = 0.10f), JupiterColors.Lowest))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Place, contentDescription = "Last parked",
                tint = JupiterColors.Accent, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(14.dp))
        PremiumCard {
            Column(Modifier.padding(18.dp)) {
                Caption("LAST PARKED")
                Spacer(Modifier.height(8.dp))
                Text("Waiting for GPS fix", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Parked location is recorded locally when the cluster is connected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        SmallAction(Modifier.fillMaxWidth(), Icons.Rounded.TwoWheeler,
            if (telemetry.connected) "Connected to cluster" else "Find cluster",
            telemetry.address.ifBlank { null }) {
            if (!telemetry.connected) onScan()
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------- SETTINGS

@Composable
fun PremiumSettingsScreen(
    notificationAccess: Boolean,
    permissions: PermissionStatus = PermissionStatus(),
    onGrantPermissions: () -> Unit = {},
    onOpenNotificationAccess: () -> Unit,
) {
    val context = LocalContext.current
    Screen {
        Spacer(Modifier.height(18.dp))
        Caption("PREFERENCES")
        Spacer(Modifier.height(4.dp))
        Text("Settings", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(18.dp))

        SectionHeading("Permissions")
        Spacer(Modifier.height(10.dp))
        PremiumCard {
            Column(Modifier.padding(8.dp)) {
                PermRow("Bluetooth connect & scan", permissions.bluetooth, onClick = onGrantPermissions)
                PermRow("Location", permissions.location, onClick = onGrantPermissions)
                PermRow("Notifications", permissions.notifications, onClick = onGrantPermissions)
                PermRow("Notification access", notificationAccess, onClick = onOpenNotificationAccess)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onGrantPermissions,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
        ) { Text("Re-request permissions") }
        Spacer(Modifier.height(14.dp))

        SectionHeading("Cluster")
        Spacer(Modifier.height(10.dp))
        PremiumCard {
            Column(Modifier.padding(16.dp)) {
                InfoRow("Model", "Jupiter 125")
                InfoRow("Protocol", "SmartXonnect BLE")
                InfoRow("Navigation", "5A 4E / 5B 4F")
                InfoRow("Link", if (notificationAccess) "Ready" else "Needs notification access")
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeading("About")
        Spacer(Modifier.height(10.dp))
        PremiumCard {
            Column(Modifier.padding(16.dp)) {
                Text("Jupiter Connect", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("BLE companion for the 2026 TVS Jupiter 125",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                ) { Text("Open notification access") }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = if (granted) "Granted" else "Missing",
            tint = if (granted) JupiterColors.Accent else JupiterColors.Red,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (!granted && onClick != null) {
            androidx.compose.material3.IconButton(onClick = onClick) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Open settings", tint = JupiterColors.Red)
            }
        } else if (granted) {
            Caption("OK", color = JupiterColors.Accent)
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ------------------------------------------------------------------ DEVICES

@Composable
fun PremiumDevicesScreen(
    devices: List<ScannedDevice>,
    scanning: Boolean,
    rememberedAddress: String?,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onForget: () -> Unit,
) {
    Screen {
        Spacer(Modifier.height(18.dp))
        Caption("BLUETOOTH")
        Spacer(Modifier.height(4.dp))
        Text("Devices", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(18.dp))

        if (rememberedAddress != null) {
            PremiumCard {
                Column(Modifier.padding(16.dp)) {
                    Caption("LAST CONNECTED", color = JupiterColors.Accent)
                    Spacer(Modifier.height(8.dp))
                    Text(rememberedAddress, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onConnect(rememberedAddress) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JupiterColors.Accent,
                                contentColor = JupiterColors.OnPrimary),
                        ) { Text("Reconnect") }
                        OutlinedButton(
                            onClick = onForget,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                        ) { Text("Forget") }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        SectionHeading("Nearby", trailing = if (scanning) "SCANNING..." else "${devices.size} FOUND")
        Spacer(Modifier.height(10.dp))
        PremiumCard {
            Column(Modifier.padding(8.dp)) {
                if (devices.isEmpty()) {
                    Text("No devices yet. Tap scan below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp))
                } else {
                    devices.forEach { d ->
                        Surface(
                            onClick = { onConnect(d.address) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.BluetoothConnected,
                                    contentDescription = null,
                                    tint = if (d.isCluster) JupiterColors.Accent
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${if (d.isCluster) "â˜... " else ""}${d.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text(d.address, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${d.rssi} dBm", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JupiterColors.High, contentColor = JupiterColors.Accent),
                ) { Text(if (scanning) "Scanning..." else "Scan for devices") }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

