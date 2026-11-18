package com.jupiterconnect.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.session.Ride
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.session.WarmupPhase
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.ui.theme.JupiterColors
import kotlinx.coroutines.launch
import java.util.Calendar

private val TAB_LABELS = listOf("Home", "Ride", "Rides", "Find", "More")
private val TAB_ICONS = listOf(
    Icons.Rounded.Home, Icons.Rounded.Navigation, Icons.Rounded.History,
    Icons.Rounded.Place, Icons.Rounded.Settings,
)

/** Shared shell: 48px settings circle, centred title, 58px red BT circle, pill nav. */
@Composable
fun JupiterShell(
    title: String,
    kicker: String,
    activeTab: Int?,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(JupiterColors.Surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Keep the header out of the status/notification bar.
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left spacer keeps the title centred; settings now lives in
                // the "More" tab instead of a header button.
                Spacer(Modifier.size(48.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label(kicker)
                    Text(title, fontSize = 32.sp, fontWeight = FontWeight.Medium,
                        color = JupiterColors.OnSurface, textAlign = TextAlign.Center)
                }
                // Balance the settings circle so the title stays centred.
                Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp), content = content)
            Spacer(Modifier.height(110.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 14.dp)
                .fillMaxWidth().height(64.dp)
                .clip(RoundedCornerShape(34.dp)).background(JupiterColors.NavPill),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TAB_LABELS.forEachIndexed { i, label ->
                val selected = activeTab == i
                Column(
                    Modifier.width(62.dp).height(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) JupiterColors.ActiveTab else Color.Transparent)
                        .clickable { onTab(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(TAB_ICONS[i], label,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) JupiterColors.Accent else JupiterColors.OnSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 10.sp,
                        color = if (selected) JupiterColors.Accent else JupiterColors.OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(
    text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium,
    color = JupiterColors.OnSurfaceVariant, letterSpacing = 2.sp,
)

@Composable
private fun RedCard(bar: Boolean = false, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(JupiterColors.Low),
    ) {
        if (bar) Box(Modifier.width(5.dp).fillMaxHeight().background(JupiterColors.Red))
        Column(Modifier.padding(18.dp).weight(1f), content = content)
    }
}

@Composable
private fun IconCircle(icon: ImageVector, label: String) = Box(
    Modifier.size(42.dp).clip(CircleShape).background(JupiterColors.RedTint),
    contentAlignment = Alignment.Center,
) { Icon(icon, label, modifier = Modifier.size(21.dp), tint = JupiterColors.Accent) }

/** Icon circle with a short label and live value beneath it. */
@Composable
private fun IconStat(icon: ImageVector, label: String, value: String) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    IconCircle(icon, label)
    Spacer(Modifier.height(4.dp))
    Text(label, fontSize = 9.sp, color = JupiterColors.OnSurfaceVariant)
    Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        color = JupiterColors.Accent)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp,
            color = JupiterColors.OnSurfaceVariant)
        Text(value, fontSize = 12.sp, color = JupiterColors.OnSurface)
    }
}

/** Carbon saved by ISS idle stops, with the same numbers sent to the cluster. */
@Composable
private fun CarbonCard() {
    val seconds by RideStore.issSavedSeconds.collectAsState()
    val kg = RideStore.carbonSavedKg
    RedCard(bar = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.Eco, "Carbon saved")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Label("CARBON SAVED")
                Spacer(Modifier.height(4.dp))
                BigValue("%.3f".format(kg), "kg CO2", 32)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("ISS saved ${seconds / 60} min of idling · ~%.2f L fuel".format(
            seconds * RideStore.IDLE_LITRES_PER_SECOND),
            fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
    }
}

/** Cold-start warm-up with an animated-feeling red ring and phase badge. */
@Composable
private fun ColdStartCardV2() {
    val state by ClusterRepository.warmup.collectAsState()
    val ready = state.phase == WarmupPhase.READY
    val engineOff = state.message == "Engine off"
    val glow = if (ready) JupiterColors.Red else JupiterColors.Accent
    val phaseIcon = when {
        engineOff -> Icons.Rounded.AccessTime
        state.phase == WarmupPhase.READY -> Icons.Rounded.CheckCircle
        state.phase == WarmupPhase.STARTING -> Icons.Rounded.AccessTime
        else -> Icons.Rounded.Speed
    }
    val phaseSymbol = when {
        engineOff -> "ENGINE OFF"
        state.phase == WarmupPhase.STARTING -> "IDLE 30s"
        state.phase == WarmupPhase.WARMING -> "RIDE GENTLY"
        else -> "READY"
    }
    RedCard(bar = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(92.dp)) {
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.35f), Color.Transparent)),
                        radius = size.minDimension * 0.5f,
                    )
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2 + 3.dp.toPx()
                    val arc = Size(size.width - inset * 2, size.height - inset * 2)
                    val top = Offset(inset, inset)
                    drawArc(JupiterColors.High, -90f, 360f, false, top, arc,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(glow, -90f, 360f * state.progress / 100f, false, top, arc,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${state.progress}%", fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    color = JupiterColors.OnSurface)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(phaseIcon, phaseSymbol)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(JupiterColors.RedTint)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(phaseSymbol, fontSize = 10.sp,
                            color = JupiterColors.Accent, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (engineOff) "AIR-COOLED 124.8cc"
                        else if (state.cold) "COLD START" else "WARM START",
                        fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant,
                        letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (engineOff) "Engine off · idle 30-60 s on cold start"
                    else state.message,
                    fontSize = 15.sp, color = JupiterColors.OnSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (engineOff) "No engine data while disconnected"
                    else "target ${state.targetC}°C · start ${state.startTemp}°C · " +
                        "ambient ${state.ambientC}°C",
                    fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VehicleDetailsRows(
    telemetry: Telemetry,
    lastReading: com.jupiterconnect.session.LastReading,
) {
    InfoRow("Speed", "${telemetry.speedKmh} km/h")
    InfoRow("RPM", "${telemetry.rpm}")
    InfoRow("Odometer", "%.1f km".format(
        if (telemetry.odometerKm > 0) telemetry.odometerKm else lastReading.odometerKm))
    InfoRow("Trip (app)", "%.2f km".format(RideStore.liveTripKm.collectAsState().value))
    InfoRow("Top speed", "${RideStore.liveTopSpeed.collectAsState().value} km/h")
    InfoRow("Fuel", "${if (telemetry.connected) telemetry.fuelBars else lastReading.fuelBars} / 6 bars")
    val lowFuel by RideStore.lowFuel.collectAsState()
    if (lowFuel) {
        InfoRow("Trip F", "%.2f km".format(RideStore.tripFKm.collectAsState().value))
    }
    InfoRow("Intake temp", "${telemetry.intakeTempC} °C")
    InfoRow("Engine load", "${telemetry.engineLoad} %")
    InfoRow("Throttle", "${telemetry.throttlePosition} %")
    InfoRow("Mode", if (telemetry.powerMode) "POWER" else "ECO")
    InfoRow("Ignition", if (telemetry.ignitionOn) "ON" else "OFF")
    InfoRow("ISS mode", "${telemetry.issMode}")
    InfoRow("Service due", if (telemetry.serviceDue) "YES" else "NO")
}

@Composable
private fun BigValue(value: String, unit: String, size: Int = 40) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = size.sp, fontWeight = FontWeight.Medium,
            color = JupiterColors.OnSurface)
        Spacer(Modifier.width(4.dp))
        Text(unit, fontSize = 13.sp, color = JupiterColors.OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = Button(
    onClick = onClick, modifier = modifier.height(46.dp),
    shape = RoundedCornerShape(26.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = JupiterColors.Red, contentColor = Color.White),
) { Text(text, fontSize = 15.sp) }

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = OutlinedButton(
    onClick = onClick, modifier = modifier.height(46.dp),
    shape = RoundedCornerShape(26.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = JupiterColors.Accent),
) { Text(text, fontSize = 15.sp) }

// ------------------------------------------------------------------- HOME

@Composable
fun HomeScreenV2(
    telemetry: Telemetry,
    lastReading: com.jupiterconnect.session.LastReading,
    onFindCluster: () -> Unit,
    activeTab: Int?,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    val kicker = run {
        val c = Calendar.getInstance()
        val days = arrayOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
        val months = arrayOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")
        "${days[c.get(Calendar.DAY_OF_WEEK) - 1]}, ${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]}"
    }
    JupiterShell("Jupiter 125", kicker, activeTab, onTab, onSettings) {
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // FUEL - tall left card
            Box(Modifier.weight(170f).height(332.dp)
                .clip(RoundedCornerShape(34.dp)).background(JupiterColors.Low)) {
                Column(Modifier.padding(18.dp).fillMaxSize()) {
                    Label("FUEL")
                    val fuel = if (telemetry.connected) telemetry.fuelBars else lastReading.fuelBars
                    val pct = (fuel.coerceIn(0, 6) * 100 / 6)
                    val ringColor = JupiterColors.Red
                    Box(Modifier.size(120.dp).align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(120.dp)) {
                            val stroke = 12.dp.toPx()
                            val inset = stroke / 2 + 4.dp.toPx()
                            val arc = Size(size.width - inset * 2, size.height - inset * 2)
                            val top = Offset(inset, inset)
                            drawArc(JupiterColors.High, 135f, 270f, false, top, arc,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                            drawArc(ringColor, 135f, 270f * pct / 100f, false, top, arc,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$pct%", fontSize = 28.sp, fontWeight = FontWeight.Medium,
                                color = JupiterColors.OnSurface)
                            Text("Capacity 5.1 L", fontSize = 12.sp,
                                color = JupiterColors.OnSurfaceVariant)
                        }
                    }
                    val lowFuel by RideStore.lowFuel.collectAsState()
                    if (lowFuel) {
                        Spacer(Modifier.height(8.dp))
                        val blink by rememberInfiniteTransition().animateFloat(
                            initialValue = 1f, targetValue = 0.25f,
                            animationSpec = infiniteRepeatable(
                                tween(500), RepeatMode.Reverse,
                            ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(JupiterColors.Error.copy(alpha = blink)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("LOW FUEL · ~1.2 L", fontSize = 11.sp,
                                color = JupiterColors.Error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    val engineTempV = if (telemetry.engineTempC != 0) "${telemetry.engineTempC}°" else "--"
                    val batteryV = if (telemetry.batteryVoltage > 0)
                        "%.1f".format(telemetry.batteryVoltage) else "--"
                    val rpmV = if (telemetry.rpm > 0) "${telemetry.rpm}" else "--"
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        IconStat(Icons.Rounded.WaterDrop, "ENGINE TEMP", engineTempV)
                        IconStat(Icons.Rounded.BatteryChargingFull, "BATTERY", batteryV)
                        IconStat(Icons.Rounded.Speed, "RPM", rpmV)
                    }
                }
            }
            // Right column
            Column(Modifier.weight(178f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.height(161.dp).clip(RoundedCornerShape(34.dp)).background(JupiterColors.Low)) {
                    Column(Modifier.padding(18.dp).fillMaxSize()) {
                        Label("LIVE SPEED")
                        val speed = if (telemetry.connected && telemetry.speedKmh > 0)
                            "${telemetry.speedKmh}" else "--"
                        BigValue(speed, "km/h", 36)
                        Spacer(Modifier.weight(1f))
                        // Current ride grows a fresh graph; when disconnected
                        // we show the most recent saved ride until a new one starts.
                        val liveSpeeds = RideStore.liveSpeeds.collectAsState().value
                        val liveThrottles = RideStore.liveThrottles.collectAsState().value
                        val recent = RideStore.rides.collectAsState().value.firstOrNull()
                        val graph = if (liveSpeeds.isNotEmpty()) liveSpeeds
                            else recent?.speedSamples.orEmpty()
                        val throttles = if (liveThrottles.isNotEmpty()) liveThrottles
                            else recent?.throttleSamples.orEmpty()
                        Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                            if (graph.size > 1) {
                                val max = (graph.maxOrNull() ?: 1).coerceAtLeast(1)
                                val step = size.width / (graph.size - 1)
                                for (i in 0 until graph.size - 1) {
                                    drawLine(JupiterColors.Red,
                                        Offset(i * step, size.height - graph[i].toFloat() / max * size.height),
                                        Offset((i + 1) * step, size.height - graph[i + 1].toFloat() / max * size.height),
                                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                            if (throttles.size > 1) {
                                val step = size.width / (throttles.size - 1)
                                for (i in 0 until throttles.size - 1) {
                                    drawLine(JupiterColors.Accent.copy(alpha = 0.5f),
                                        Offset(i * step, size.height - throttles[i].toFloat() / 100f * size.height),
                                        Offset((i + 1) * step, size.height - throttles[i + 1].toFloat() / 100f * size.height),
                                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.height(161.dp).clip(RoundedCornerShape(34.dp)).background(JupiterColors.Low)) {
                    Column(Modifier.padding(18.dp).fillMaxSize()) {
                        Label("ODOMETER")
                        val odo = if (telemetry.odometerKm > 0) telemetry.odometerKm else lastReading.odometerKm
                        BigValue("%.1f".format(odo), "km", 30)
                        val top = RideStore.liveTopSpeed.collectAsState().value
                            .coerceAtLeast(telemetry.clusterTopSpeedKmh)
                        Text("Top $top km/h", fontSize = 13.sp, color = JupiterColors.OnSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(JupiterColors.High)) {
                            Box(Modifier.fillMaxWidth((top / 95f).coerceIn(0f, 1f)).height(5.dp)
                                .clip(RoundedCornerShape(3.dp)).background(JupiterColors.Red))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RedCard(bar = true) {
            Label("CLUSTER LINK")
            Spacer(Modifier.height(8.dp))
            Text(if (telemetry.connected) "Connected" else "Disconnected",
                fontSize = 18.sp, color = JupiterColors.OnSurface)
            if (telemetry.connected && telemetry.address.isNotBlank()) {
                Text(telemetry.address, fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
            }
            val err = telemetry.error
            if (!err.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(err, fontSize = 13.sp, color = JupiterColors.Error)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Find my cluster", onFindCluster, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RedCard(modifier = Modifier.weight(1f)) {
                Label("ENGINE TEMP")
                BigValue(if (telemetry.engineTempC != 0) "${telemetry.engineTempC}" else "--", "°C", 36)
            }
            RedCard(modifier = Modifier.weight(1f)) {
                Label("BATTERY")
                val volt = if (telemetry.batteryVoltage > 0) telemetry.batteryVoltage
                    else lastReading.batteryVoltage
                BigValue(if (volt > 0) "%.1f".format(volt) else "--", "V", 36)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RedCard(modifier = Modifier.weight(1f)) {
                Label("RANGE")
                val dte = if (telemetry.connected) telemetry.distanceToEmptyKm
                    else lastReading.distanceToEmptyKm
                val assumeFull = (telemetry.connected && telemetry.fuelBars >= 6 && dte <= 0) ||
                    (!telemetry.connected && lastReading.hasData &&
                        lastReading.fuelBars >= 6 && dte <= 0)
                val range = when {
                    dte > 0 -> "$dte"
                    assumeFull -> "240"
                    else -> "--"
                }
                BigValue(range, "km", 36)
            }
            RedCard(modifier = Modifier.weight(1f)) {
                Label("AVG ECONOMY")
                val econ = if (telemetry.averageFuelEconomy > 0) telemetry.averageFuelEconomy
                    else lastReading.averageFuelEconomy
                BigValue(if (econ > 0) "$econ" else "--", "km/L", 36)
            }
        }
        Spacer(Modifier.height(10.dp))
        ColdStartCardV2()
        Spacer(Modifier.height(10.dp))
        CarbonCard()
        Spacer(Modifier.height(18.dp))
        Label("VEHICLE DETAILS")
        Spacer(Modifier.height(10.dp))
        RedCard {
            // Live details scroll inside a fixed-height card while connected.
            // Offline, the last known values are shown as a static list and the
            // page itself still scrolls normally.
            if (telemetry.connected) {
                Box(Modifier.height(220.dp).verticalScroll(rememberScrollState())) {
                    VehicleDetailsRows(telemetry, lastReading)
                }
            } else {
                VehicleDetailsRows(telemetry, lastReading)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ------------------------------------------------------------------- RIDE

@Composable
fun RideScreenV2(
    telemetry: Telemetry,
    instruction: NavInstruction?,
    mirroring: Boolean,
    onMirroring: (Boolean) -> Unit,
    activeTab: Int?,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    JupiterShell("Ride", "CURRENT TRIP", activeTab, onTab, onSettings) {
        Spacer(Modifier.height(4.dp))
        RedCard(bar = true) {
            Label(if (instruction == null) "NO ACTIVE ROUTE" else "NAVIGATION")
            Spacer(Modifier.height(8.dp))
            Text(if (instruction == null)
                "Start navigation in Google Maps or Mappls"
                else instruction.displayLine,
                fontSize = 15.sp, color = JupiterColors.OnSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Label("DISTANCE")
                    BigValue(if (instruction != null && instruction.distanceMeters >= 0)
                        "${instruction.distanceMeters}" else "--", "m", 30)
                }
                Column {
                    Label("EST. TIME")
                    BigValue(if (instruction != null && instruction.etaMinutes > 0)
                        "${instruction.etaMinutes}" else "--", "min", 30)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val tiles = listOf(
            "SPEED" to "${telemetry.speedKmh} km/h",
            "RPM" to "${telemetry.rpm}",
            "RANGE" to "${telemetry.distanceToEmptyKm} km",
            "FUEL" to "${telemetry.fuelBars}/6",
            "AVG ECON" to "${telemetry.averageFuelEconomy} km/L",
            "ENGINE TEMP" to "${telemetry.engineTempC} °C",
        )
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    RedCard(modifier = Modifier.weight(1f)) {
                        Label(label)
                        Spacer(Modifier.height(6.dp))
                        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                            color = JupiterColors.OnSurface)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        RedCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Navigation, "Navigation mirroring")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Navigation mirroring", fontSize = 15.sp, color = JupiterColors.OnSurface)
                    Text("Send turn-by-turn to the cluster", fontSize = 12.sp,
                        color = JupiterColors.OnSurfaceVariant)
                }
                Switch(
                    checked = mirroring, onCheckedChange = onMirroring,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = JupiterColors.Red,
                        uncheckedTrackColor = JupiterColors.High,
                    ),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ RIDES

@Composable
fun RidesScreenV2(
    rides: List<Ride>,
    activeTab: Int?,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    val total = rides.sumOf { it.distanceKm }
    val top = rides.maxOfOrNull { it.topSpeedKmh } ?: 0
    JupiterShell("Rides", "SAVED ON THIS PHONE", activeTab, onTab, onSettings) {
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RedCard(modifier = Modifier.weight(1f)) {
                Label("RECORDED")
                BigValue("%.1f".format(total), "km", 32)
                Text("${rides.size} rides", fontSize = 13.sp, color = JupiterColors.OnSurfaceVariant)
            }
            RedCard(modifier = Modifier.weight(1f)) {
                Label("TOP")
                BigValue("$top", "km/h", 32)
            }
        }
        Spacer(Modifier.height(18.dp))
        Label("NEWEST FIRST")
        Spacer(Modifier.height(10.dp))
        if (rides.isEmpty()) {
            RedCard { Text("No rides saved yet.", color = JupiterColors.OnSurfaceVariant, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(18.dp))
        Label("HISTORY · LAST 6 MONTHS")
        Spacer(Modifier.height(10.dp))
        RedCard {
            val months = remember(rides) {
                val fmt = java.text.DateFormatSymbols().shortMonths
                val keys = mutableListOf<Pair<Int, String>>()
                repeat(6) { i ->
                    val c = Calendar.getInstance()
                    c.set(Calendar.DAY_OF_MONTH, 1)
                    c.add(Calendar.MONTH, -i)
                    keys += (c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)) to fmt[c.get(Calendar.MONTH)]
                }
                keys.reverse()
                keys.map { (key, label) ->
                    val km = rides.filter { r ->
                        val c = Calendar.getInstance().apply { timeInMillis = r.startedAt }
                        c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) == key
                    }.sumOf { it.distanceKm }
                    label to km
                }
            }
            val maxKm = months.maxOf { it.second }.coerceAtLeast(0.1)
            Row(Modifier.fillMaxWidth().height(90.dp), Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom) {
                months.forEach { (label, km) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(18.dp)
                            .height((maxOf(2.0, km / maxKm * 80.0)).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (km > 0) JupiterColors.Red else JupiterColors.High))
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
                    }
                }
            }
        }
        rides.forEach { r ->
            Spacer(Modifier.height(10.dp))
            var expanded by remember { mutableStateOf(false) }
            RedCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.TwoWheeler, "Ride")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.dateLabel, fontSize = 15.sp, color = JupiterColors.OnSurface)
                        Text(if (r.distanceKm > 0.3) "Morning ride" else "Short ride",
                            fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
                    }
                    Text("%.1f km".format(r.distanceKm), fontSize = 18.sp,
                        fontWeight = FontWeight.Medium, color = JupiterColors.Accent)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AVG %.0f".format(r.avgSpeedKmh), fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
                    Text("TOP ${r.topSpeedKmh}", fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
                    Text("MIN ${r.durationMin}", fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(if (expanded) "Hide report" else "Report >", fontSize = 13.sp,
                    color = JupiterColors.Accent, modifier = Modifier.clickable { expanded = !expanded })
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    Text("Idle ${r.idleMin} min · ISS ${r.issStops} stops · " +
                        "Fuel ${r.fuelBarsUsed} bars · Max ${r.topSpeedKmh} km/h", fontSize = 12.sp,
                        color = JupiterColors.OnSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val samples = r.speedSamples
                    val thr = r.throttleSamples
                    if (samples.size > 1) {
                        Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                            val max = (samples.maxOrNull() ?: 1).coerceAtLeast(1)
                            val step = size.width / (samples.size - 1)
                            for (i in 0 until samples.size - 1) {
                                drawLine(JupiterColors.Red,
                                    Offset(i * step, size.height - samples[i].toFloat() / max * size.height),
                                    Offset((i + 1) * step, size.height - samples[i + 1].toFloat() / max * size.height),
                                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                            if (thr.size > 1) {
                                val tStep = size.width / (thr.size - 1)
                                for (i in 0 until thr.size - 1) {
                                    drawLine(JupiterColors.Accent.copy(alpha = 0.5f),
                                        Offset(i * tStep, size.height - thr[i].toFloat() / 100f * size.height),
                                        Offset((i + 1) * tStep, size.height - thr[i + 1].toFloat() / 100f * size.height),
                                        strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------- FIND

@Composable
fun FindScreenV2(
    activeTab: Int?,
    onTab: (Int) -> Unit,
    onSettings: () -> Unit,
    onFindCluster: () -> Unit,
    rememberedAddress: String?,
) {
    val context = LocalContext.current
    val parked by RideStore.parkedLocation.collectAsState()
    JupiterShell("Find", "YOUR SCOOTER", activeTab, onTab, onSettings) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(34.dp))
            .background(JupiterColors.Low), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(
                        JupiterColors.Red.copy(alpha = 0.30f), Color.Transparent)),
                    radius = size.minDimension * 0.48f,
                )
            }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(JupiterColors.Red),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocationOn, "Parked location",
                    modifier = Modifier.size(38.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
        RedCard(bar = true) {
            Label("LAST PARKED")
            Spacer(Modifier.height(8.dp))
            Text(parked?.coordinates ?: "--, --", fontSize = 20.sp,
                fontWeight = FontWeight.Medium, color = JupiterColors.OnSurface)
            Text(if (parked == null) "No location saved yet"
                else "Saved ${parked!!.timeLabel}", fontSize = 13.sp,
                color = JupiterColors.OnSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Navigate to scooter", {
                val p = parked ?: return@PrimaryButton
                val uri = Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(Scooter)")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        RedCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.NotificationsActive, "Find my cluster")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Find my cluster", fontSize = 15.sp, color = JupiterColors.OnSurface)
                    Text(rememberedAddress ?: "No remembered cluster", fontSize = 12.sp,
                        color = JupiterColors.OnSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, "Open devices",
                    modifier = Modifier.size(22.dp).clickable { onFindCluster() },
                    tint = JupiterColors.Accent)
            }
        }
    }
}

// ---------------------------------------------------------------- DEVICES

// ------------------------------------------------------------------- MORE

@Composable
private fun DevicesContent(
    devices: List<ScannedDevice>,
    rememberedAddress: String?,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onForget: () -> Unit,
) {
    RedCard(bar = true) {
        Label("LAST CONNECTED")
        Spacer(Modifier.height(8.dp))
        Text(rememberedAddress ?: "No device remembered", fontSize = 18.sp,
            fontWeight = FontWeight.Medium, color = JupiterColors.OnSurface)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Reconnect", {
                if (rememberedAddress != null) onConnect(rememberedAddress)
            }, Modifier.weight(1f))
            SecondaryButton("Forget", onForget, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Label("NEARBY")
        Text("${devices.size} FOUND", fontSize = 11.sp, color = JupiterColors.Accent,
            letterSpacing = 2.sp)
    }
    Spacer(Modifier.height(10.dp))
    RedCard {
        if (devices.isEmpty()) {
            Text("No devices found. Tap Scan.", fontSize = 13.sp,
                color = JupiterColors.OnSurfaceVariant)
        }
        devices.take(8).forEachIndexed { i, d ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(
                    if (d.name.contains("JBL", true)) Icons.Rounded.Headphones
                    else if (d.isCluster) Icons.Rounded.TwoWheeler
                    else Icons.Rounded.Bluetooth,
                    "Device",
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontSize = 14.sp, color = JupiterColors.OnSurface)
                    Text(d.address, fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
                }
                Text("${d.rssi} dBm", fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton("Scan for devices", onScan, Modifier.fillMaxWidth())
}

@Composable
private fun SettingsContent(
    notificationAccess: Boolean,
    bleReady: Boolean,
    email: String?,
    onNotificationAccess: () -> Unit,
    onSignOut: () -> Unit,
) {
    RedCard {
        Label("PERMISSIONS")
        Spacer(Modifier.height(12.dp))
        listOf(
            "Bluetooth connect and scan" to bleReady,
            "Location" to true,
            "Notifications" to true,
            "Notification access" to notificationAccess,
        ).forEach { (name, ok) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Bluetooth, name)
                Spacer(Modifier.width(12.dp))
                Text(name, Modifier.weight(1f), fontSize = 14.sp,
                    color = JupiterColors.OnSurface)
                if (ok) Icon(Icons.Rounded.Check, "Granted",
                    modifier = Modifier.size(20.dp), tint = JupiterColors.Accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Notification access", fontSize = 12.sp, color = JupiterColors.Accent,
            modifier = Modifier.clickable { onNotificationAccess() })
    }
    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("CLUSTER")
        Spacer(Modifier.height(10.dp))
        listOf("Model" to "Jupiter 125", "Protocol" to "SmartXonnect BLE",
            "Link" to if (notificationAccess) "Ready" else "Needs access").forEach { (k, v) ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(k, Modifier.weight(1f), fontSize = 13.sp, color = JupiterColors.OnSurfaceVariant)
                Text(v, fontSize = 13.sp,
                    color = if (k == "Link") JupiterColors.Accent else JupiterColors.OnSurface)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    RedCard(bar = true) {
        Label("CLOUD SYNC")
        Spacer(Modifier.height(8.dp))
        Text(email ?: "Not signed in", fontSize = 15.sp, color = JupiterColors.OnSurface)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Sign out", onSignOut, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("VOICE")
        Spacer(Modifier.height(8.dp))
        val voice by ClusterRepository.voiceDebug.collectAsState()
        Text(voice, fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Test voice trigger",
            { ClusterRepository.service?.simulateVoiceTrigger() },
            Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("ENGINE FRAME")
        Spacer(Modifier.height(8.dp))
        val engine by ClusterRepository.engineDebug.collectAsState()
        Text(engine, fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
    }
}

/** Devices at the top, all settings below, in one scrollable "More" tab. */
@Composable
fun MoreScreenV2(
    devices: List<ScannedDevice>,
    rememberedAddress: String?,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onForget: () -> Unit,
    notificationAccess: Boolean,
    bleReady: Boolean,
    email: String?,
    onNotificationAccess: () -> Unit,
    onSignOut: () -> Unit,
    onTab: (Int) -> Unit,
) {
    JupiterShell("More", "DEVICES & SETTINGS", 4, onTab, {}) {
        Spacer(Modifier.height(4.dp))
        DevicesContent(devices, rememberedAddress, onScan, onConnect, onForget)
        Spacer(Modifier.height(22.dp))
        Label("SETTINGS")
        Spacer(Modifier.height(10.dp))
        SettingsContent(notificationAccess, bleReady, email, onNotificationAccess, onSignOut)
    }
}
