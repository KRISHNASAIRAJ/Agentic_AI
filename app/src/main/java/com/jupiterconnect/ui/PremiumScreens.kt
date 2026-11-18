package com.jupiterconnect.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.session.Ride
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.session.SPEED_BAND_LABELS
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.ui.theme.JupiterColors
import com.jupiterconnect.ui.theme.NumberStyle
import kotlinx.coroutines.launch

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
    scanning: Boolean,
    permissions: PermissionStatus,
    lastReading: com.jupiterconnect.session.LastReading,
    onGrantPermissions: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    // While disconnected, fall back to the last reading the cluster sent so
    // the odometer and fuel range don't drop to a misleading zero.
    val shownOdo = if (telemetry.odometerKm > 0) telemetry.odometerKm else lastReading.odometerKm
    val shownFuel = if (telemetry.connected) telemetry.fuelBars else lastReading.fuelBars
    val shownDte = if (telemetry.connected) telemetry.distanceToEmptyKm else lastReading.distanceToEmptyKm
    val offline = !telemetry.connected
    // Never show a fake 0 for range/economy when the cluster is offline. A
    // cached value is shown greyed; with no cached value, show "--".
    val rangeText = when {
        telemetry.connected -> "$shownDte"
        lastReading.hasData && shownDte > 0 -> "$shownDte"
        else -> "--"
    }
    val econText = when {
        telemetry.connected && telemetry.averageFuelEconomy > 0 -> "${telemetry.averageFuelEconomy}"
        !telemetry.connected && lastReading.averageFuelEconomy > 0 -> "${lastReading.averageFuelEconomy}"
        else -> "--"
    }
    // Peak speed this ride - never drops when the accelerator is eased.
    val liveTop = RideStore.liveTopSpeed.collectAsState().value
    val speedHistory = ClusterRepository.speedHistory.collectAsState().value
    val appTripKm = RideStore.liveTripKm.collectAsState().value
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
                ) {
                    SpeedGraph(values = speedHistory, active = telemetry.connected)
                }
                if (telemetry.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(telemetry.error, style = MaterialTheme.typography.labelSmall, color = JupiterColors.Red)
                }
                Spacer(Modifier.height(14.dp))
                if (!telemetry.connected) {
                    Button(
                        onClick = onOpenDevices,
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

        SectionHeading("Ride statistics", trailing = if (telemetry.connected) "LIVE" else null)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.enterAnimated(delayMs = 60)) {
            PremiumStat(Modifier.weight(1.4f), Icons.Rounded.History,
                "%.1f".format(shownOdo), "km", "Odometer")
            PremiumStat(Modifier.weight(1f), Icons.Rounded.Speed,
                "$liveTop", "km/h", "Top speed")
            PremiumStat(Modifier.weight(1f), Icons.Rounded.Bolt,
                "${telemetry.rpm}", "rpm", "Engine RPM")
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
                    BigReadout(rangeText, "km", "Range",
                        modifier = Modifier.weight(1f), accent = !offline, muted = offline)
                    BigReadout("${telemetry.engineTempC}", "°C", "Coolant",
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigReadout(econText, "km/L", "Avg fuel econ",
                        modifier = Modifier.weight(1f), muted = !telemetry.connected ||
                            telemetry.averageFuelEconomy <= 0)
                    BigReadout("%.1f".format(telemetry.batteryVoltage), "V", "Battery",
                        modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeading("Vehicle details", trailing = if (telemetry.connected) "LIVE" else null)
        Spacer(Modifier.height(10.dp))
        PremiumCard(modifier = Modifier.enterAnimated(delayMs = 90)) {
            Column(Modifier.padding(16.dp)) {
                MiniRow(Triple("AVG SPEED", "${telemetry.averageSpeedKmh}", "km/h"),
                    Triple("CLUSTER TOP", "${telemetry.clusterTopSpeedKmh}", "km/h"))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("TRIP (APP)", "%.1f".format(appTripKm), "km"),
                    Triple("MODE", if (telemetry.powerMode) "POWER" else "ECO", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("ENGINE LOAD", "${telemetry.engineLoad}", "%"),
                    Triple("INST ECONOMY", "${telemetry.instantEconomy}", "km/L"))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("SERVICE", if (telemetry.serviceDue) "DUE" else "OK", ""),
                    Triple("CHECK ENGINE", if (telemetry.milOn) "ON" else "OK", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("THROTTLE", "${telemetry.throttlePosition}", "%"),
                    Triple("ISS / START", issLabel(telemetry.issMode), ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("MAP", "${telemetry.manifoldPressure}", ""),
                    Triple("BARO", "${telemetry.barometricPressure}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("ENGINE RUN", "${telemetry.engineRunTime}", ""),
                    Triple("INJ VOLUME", "${telemetry.fuelInjectionVolume}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("INJ TIME", "${telemetry.fuelInjectionTime}", ""),
                    Triple("DIST TRAVELLED", "${telemetry.distanceTravelledKm}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("ISS TIME", "${telemetry.issDuration}", ""),
                    Triple("ISS COUNT", "${telemetry.issCount}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("ISS MODE", "${telemetry.issMode}", ""),
                    Triple("OTA STATUS", "${telemetry.otaStatus}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("SOFTWARE", "${telemetry.softwareVersion}", ""),
                    Triple("VEH MODEL", "${telemetry.vehicleModel}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("VEH STATE", "${telemetry.vehicleState}", ""),
                    Triple("CLUSTER THEME", "${telemetry.clusterTheme}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("FUEL SENSOR", if (telemetry.fuelSensorFault) "FAULT" else "OK", ""),
                    Triple("CONNECTOR", "${telemetry.connectorStatus}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("TURN SIGNAL", "${telemetry.turnIndicator}", ""),
                    Triple("MIL BLINK", "${telemetry.milBlinkCode}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("ISG BLINK", "${telemetry.isgBlinkCode}", ""),
                    Triple("BACKLIGHT", "${telemetry.backlightMode}", ""))
                Spacer(Modifier.height(14.dp))
                MiniRow(Triple("0-60 RAW", "${telemetry.zeroToSixtyRaw}", ""),
                    Triple("BARO RAW", "${telemetry.barometricPressureRaw}", ""))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MiniRow(left: Triple<String, String, String>, right: Triple<String, String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniCell(Modifier.weight(1f), left.first, left.second, left.third)
        MiniCell(Modifier.weight(1f), right.first, right.second, right.third)
    }
}

@Composable
private fun MiniCell(modifier: Modifier, label: String, value: String, unit: String) {
    Column(modifier) {
        Caption(label)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumberStyle, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Live speed trace drawn from the speeds actually received from the cluster. */
@Composable
private fun SpeedGraph(values: List<Int>, active: Boolean) {
    val accent = if (active) JupiterColors.Accent else JupiterColors.OnSurfaceVariant
    val grid = JupiterColors.Hairline.copy(alpha = 0.35f)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (i in 1..3) {
            drawLine(grid, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), strokeWidth = 1.dp.toPx())
        }
        if (values.size >= 2) {
            val ceiling = (values.maxOrNull() ?: 0).coerceAtLeast(20).toFloat() * 1.15f
            val step = w / (values.size - 1)
            val path = Path().apply {
                values.forEachIndexed { i, speed ->
                    val x = i * step
                    val y = h - (speed / ceiling) * h * 0.9f - h * 0.05f
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun PremiumStat(modifier: Modifier, icon: ImageVector, value: String, unit: String, label: String) {
    PremiumCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = JupiterColors.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                // maxLines/softWrap keep a long odometer like "16593.2" on a
                // single line instead of dropping the decimal to a second row.
                Text(value, style = NumberStyle, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(3.dp))
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

        PremiumCard(modifier = Modifier.enterAnimated(delayMs = 60), color = JupiterColors.High) {
            Column(Modifier.padding(18.dp)) {
                Caption(if (instruction != null) "NEXT MANOEUVRE" else "NO ACTIVE ROUTE",
                    color = if (instruction != null) JupiterColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    instruction?.displayLine ?: "Start navigation in Google Maps or Mappls",
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
                    NavInfo(
                        if (instruction != null && instruction.distanceMeters >= 0)
                            "${instruction.distanceMeters}" else "-",
                        "m", "Distance",
                    )
                    NavInfo(if (instruction != null && instruction.etaMinutes > 0) "${instruction.etaMinutes}" else "-",
                        "min", "Est. time")
                    if (!instruction?.provider.isNullOrBlank()) {
                        NavInfo(providerLabel(instruction.provider), "", "Provider")
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
            BigReadout("${telemetry.distanceToEmptyKm}", "km", "Range",
                modifier = Modifier.weight(1f))
            BigReadout("${telemetry.rpm}", "rpm", "Engine RPM",
                modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigReadout("${telemetry.fuelBars}", "/6", "Fuel", modifier = Modifier.weight(1f))
            BigReadout("${telemetry.averageFuelEconomy}", "km/L", "Avg fuel econ",
                modifier = Modifier.weight(1f))
            BigReadout("${telemetry.engineTempC}", "°C", "Coolant", modifier = Modifier.weight(1f))
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

/** 0x11 byte 6: bit 7 (128) appears when start is pressed without clutch. */
private fun issLabel(value: Int): String = when {
    value and 0x80 != 0 -> "START LOCK"
    value == 0 -> "IDLE"
    else -> "$value"
}

private fun providerLabel(pkg: String): String = when {
    pkg.contains("google") -> "Google Maps"
    pkg.contains("mappls") || pkg.contains("mapmyindia") || pkg.contains("mmi.android") -> "Mappls"
    pkg.contains("organicmaps") -> "Organic Maps"
    pkg.contains("here.app") -> "HERE"
    pkg.contains("waze") -> "Waze"
    pkg.contains("tomtom") -> "TomTom"
    else -> pkg
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
                Text("km across ${rides.size} ride${if (rides.size == 1) "" else "s"} · saved on this phone",
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
            var expandedId by remember { mutableStateOf<Long?>(null) }
            rides.forEach { r ->
                val expanded = expandedId == r.startedAt
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
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (expanded) "HIDE REPORT" else "VIEW REPORT",
                            style = MaterialTheme.typography.labelSmall,
                            color = JupiterColors.Accent,
                            modifier = Modifier.clickable {
                                expandedId = if (expanded) null else r.startedAt
                            },
                        )
                        if (expanded) {
                            Spacer(Modifier.height(12.dp))
                            RideReportDetail(r)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Diagnostic card: shows the raw Maps notification fields and what we
        // parsed/sent. Screenshot this while "0m" blinks to see where Maps hides
        // the distance, so the next fix is targeted rather than a guess.
        val dbg = ClusterRepository.navDebug.collectAsState().value
        if (dbg.isNotBlank()) {
            SectionHeading("Nav diagnostic", trailing = "DEBUG")
            Spacer(Modifier.height(10.dp))
            PremiumCard {
                Text(
                    dbg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** Expandable per-ride breakdown: idle, ISS, fuel and time in each speed band. */
@Composable
private fun RideReportDetail(ride: Ride) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RideMetric(Modifier.weight(1f), "IDLE", "${ride.idleMin}", "min")
            RideMetric(Modifier.weight(1f), "ISS", "${ride.issStops}", "stops")
            val econ = ride.economyKmPerL
            RideMetric(
                Modifier.weight(1f), "ECON",
                if (econ > 0) "%.1f".format(econ) else "—", "km/L",
            )
        }
        Spacer(Modifier.height(12.dp))
        Caption("TIME IN SPEED BANDS")
        Spacer(Modifier.height(8.dp))
        ride.bandMs.indices.forEach { i ->
            val pct = ride.bandPct(i)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(SPEED_BAND_LABELS[i], style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp))
                Box(
                    Modifier.weight(1f).height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                ) {
                    if (pct > 0) {
                        Box(
                            Modifier.fillMaxWidth(pct / 100f).height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(JupiterColors.Accent),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("$pct%", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (ride.fuelBarsUsed > 0)
                "Fuel ${ride.fuelBarsStart} → ${ride.fuelBarsEnd} bars (%.1f L)".format(
                    ride.fuelBarsUsed * (5.1 / 6.0))
            else "Fuel: no net change recorded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
fun PremiumFindScreen(telemetry: Telemetry, onOpenDevices: () -> Unit) {
    val parked = RideStore.parkedLocation.collectAsState().value
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
                if (parked != null) {
                    Text(parked.coordinates, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text("Saved ${parked.timeLabel}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Waiting for GPS fix", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Parked location is saved on this phone when the cluster disconnects.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SmallAction(Modifier.fillMaxWidth(), Icons.Rounded.TwoWheeler,
            if (telemetry.connected) "Connected to cluster" else "Find my cluster",
            telemetry.address.ifBlank { null }) {
            if (!telemetry.connected) onOpenDevices()
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
    val clipboard = LocalClipboardManager.current
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

        Spacer(Modifier.height(14.dp))

        // Cloud sync uses the same Supabase Auth users as Meridian.
        SectionHeading("Cloud sync", trailing = "MERIDIAN DB")
        Spacer(Modifier.height(10.dp))
        PremiumCard {
            Column(Modifier.padding(16.dp)) {
                val scope = rememberCoroutineScope()
                var signedIn by remember { mutableStateOf(com.jupiterconnect.sync.SupabaseClient.isSignedIn()) }
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var message by remember { mutableStateOf("") }
                if (signedIn) {
                    InfoRow("Signed in as", com.jupiterconnect.sync.SupabaseClient.currentEmail() ?: "-")
                    Text(
                        "Rides sync to Supabase while the cluster is connected and for 2 minutes after it disconnects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            com.jupiterconnect.sync.SupabaseClient.signOut()
                            signedIn = false
                            message = "Signed out"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                    ) { Text("Sign out") }
                } else {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text("Email") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    message = com.jupiterconnect.sync.SupabaseClient.signIn(email, password)
                                        ?: "Signed in".also { signedIn = true }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                        ) { Text("Sign in") }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    message = com.jupiterconnect.sync.SupabaseClient.signUp(email, password)
                                        ?: "Account created".also { signedIn = com.jupiterconnect.sync.SupabaseClient.isSignedIn() }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                        ) { Text("Sign up") }
                    }
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionHeading("Frame log", trailing = "DEVELOPER")
        Spacer(Modifier.height(10.dp))
        PremiumCard {
            Column(Modifier.padding(16.dp)) {
                val entries = FrameLog.entries.collectAsState().value
                Text(
                    if (entries.isEmpty())
                        "No frames captured yet. Connect to the cluster to start logging."
                    else entries.takeLast(12).joinToString("\n") { it.line },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val text = FrameLog.exportText()
                            if (text.isNotBlank()) clipboard.setText(AnnotatedString(text))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy") }
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, FrameLog.exportText())
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Share frame log"))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Share") }
                    OutlinedButton(
                        onClick = { FrameLog.clear() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Clear") }
                }
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

