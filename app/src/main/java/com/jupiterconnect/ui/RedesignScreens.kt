package com.jupiterconnect.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiterconnect.R
import com.jupiterconnect.debug.FrameCatalog
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.session.Ride
import com.jupiterconnect.session.RideAnalytics
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.session.WarmupPhase
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.transport.ClusterService
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

/** One-line compact label for the narrow 3-across telemetry tiles. */
@Composable
private fun TileLabel(text: String) = Text(
    text.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Medium,
    color = JupiterColors.OnSurfaceVariant, letterSpacing = 0.5.sp,
    maxLines = 1, softWrap = false,
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

/** Compact horizontal stat row so long live readings cannot overlap neighbours. */
@Composable
private fun IconStatRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(JupiterColors.RedTint),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, modifier = Modifier.size(17.dp), tint = JupiterColors.Accent)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), fontSize = 10.sp,
            color = JupiterColors.OnSurfaceVariant, maxLines = 1, softWrap = false)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = JupiterColors.Accent, maxLines = 1, softWrap = false)
    }
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
        val baseline = RideStore.carbonBaselineKg
        Text(
            if (baseline > 0)
                "Dash %.2f kg + %.2f kg from ISS since".format(
                    baseline, RideStore.carbonAboveBaselineKg)
            else "ISS saved ${seconds / 60} min of idling · ~%.2f L fuel".format(
                seconds * RideStore.IDLE_LITRES_PER_SECOND),
            fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
    }
}

/** Cold-start warm-up with an animated-feeling red ring and phase badge. */
@Composable
private fun ColdStartCardV2() {
    val state by ClusterRepository.warmup.collectAsState()
    val engineOff = state.message == "Engine off"
    val noData = !state.hasEngineData
    val ready = state.phase == WarmupPhase.READY && !noData && !engineOff
    val glow = if (ready) JupiterColors.Red else JupiterColors.Accent
    val phaseIcon = when {
        engineOff -> Icons.Rounded.AccessTime
        noData -> Icons.Rounded.Speed
        state.phase == WarmupPhase.READY -> Icons.Rounded.CheckCircle
        state.phase == WarmupPhase.STARTING -> Icons.Rounded.AccessTime
        else -> Icons.Rounded.Speed
    }
    val phaseSymbol = when {
        engineOff -> "ENGINE OFF"
        noData -> "NO DATA"
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
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(phaseIcon, phaseSymbol,
                        modifier = Modifier.size(18.dp), tint = JupiterColors.Accent)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(JupiterColors.RedTint)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(phaseSymbol, fontSize = 10.sp,
                            color = JupiterColors.Accent, letterSpacing = 1.sp,
                            maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        engineOff -> "Engine off"
                        noData -> "No engine data"
                        else -> state.message
                    },
                    fontSize = 14.sp, color = JupiterColors.OnSurface,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                // Never print 0°C placeholders. With the engine off or before
                // the cluster sends temperature bytes, only show the spec and
                // the fact that no engine data is available.
                Text(
                    if (engineOff || noData) "Air Cooled · No Engine Data"
                    else "target ${state.targetC}°C · start ${state.startTemp}°C · " +
                        "ambient ${state.ambientC}°C",
                    fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * One fixed-width stat line. Both columns are weighted and single-line, so a
 * long live value can never paint over its neighbour (the old free-width rows
 * did exactly that in the STATS card).
 */
@Composable
private fun StatLine(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.42f), fontSize = 11.sp, softWrap = false,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = JupiterColors.OnSurfaceVariant)
        Text(value, Modifier.weight(0.58f), fontSize = 11.sp, softWrap = false,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End, fontWeight = FontWeight.Medium,
            color = if (accent) JupiterColors.Accent else JupiterColors.OnSurface)
    }
}

@Composable
private fun VehicleDetailsRows(
    telemetry: Telemetry,
    lastReading: com.jupiterconnect.session.LastReading,
) {
    StatLine("Speed", "${telemetry.speedKmh} km/h")
    StatLine("RPM", "${telemetry.rpm}")
    StatLine("Odometer", "%.1f km".format(
        if (telemetry.odometerKm > 0) telemetry.odometerKm else lastReading.odometerKm))
    StatLine("Trip (app)", "%.2f km".format(RideStore.liveTripKm.collectAsState().value))
    StatLine("Top speed", "${RideStore.liveTopSpeed.collectAsState().value} km/h")
    StatLine("Fuel", "${if (telemetry.connected) telemetry.fuelBars else lastReading.fuelBars} / 6 bars")
    val lowFuel by RideStore.lowFuel.collectAsState()
    if (lowFuel) {
        StatLine("Trip F", "%.2f km".format(RideStore.tripFKm.collectAsState().value))
    }
    StatLine("Intake temp", "${telemetry.intakeTempC} °C")
    StatLine("Engine load", "${telemetry.engineLoad} %")
    StatLine("Throttle", "${telemetry.throttlePosition} %")
    StatLine("Mode", if (telemetry.powerMode) "POWER" else "ECO")
    StatLine("Ignition", if (telemetry.ignitionOn) "ON" else "OFF")
    StatLine("ISS / start", when {
        telemetry.issMode and 0x80 != 0 -> "START (no clutch)"
        telemetry.issMode and 0x7F == 0 -> "IDLE"
        else -> "ISS ${telemetry.issMode and 0x7F}"
    })
    StatLine("Switch action", when (telemetry.switchAction) {
        0 -> "none"
        1 -> "answer call"
        2 -> "reject call"
        3 -> "reject + SMS"
        4 -> "voice button"
        else -> "0x%02X".format(telemetry.switchAction)
    })
    StatLine("Cluster addr", telemetry.clusterBleAddress.ifEmpty { "--" })
    StatLine("Media key", if (telemetry.mediaKey != 0) "0x%02X".format(telemetry.mediaKey) else "--")
    StatLine("Service due", if (telemetry.serviceDue) "YES" else "NO")
}

/** Passive code discovery: what the cluster emits, what we send, and probes. */
@Composable
private fun ProtocolDiscoveryCard() {
    val frames by FrameCatalog.frames.collectAsState()
    val confirmed by ClusterRepository.confirmedTextWrites.collectAsState()
    val seen = frames.associateBy { it.id }
    val missing = FrameCatalog.CANDIDATES.filter { seen[it.id] == null }

    Label("PROTOCOL DISCOVERY")
    Spacer(Modifier.height(8.dp))
    Text("Every frame is saved to Downloads/JupiterConnect as one file per connection.",
        fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
    FrameLog.currentSession()?.let {
        Spacer(Modifier.height(4.dp))
        Text("Writing: $it", fontSize = 10.sp, color = JupiterColors.Accent)
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Confirmed text writes", fontSize = 12.sp, color = JupiterColors.OnSurface)
            Text("Try if cluster text stays on the first page", fontSize = 10.sp,
                color = JupiterColors.OnSurfaceVariant)
        }
        Switch(checked = confirmed, onCheckedChange = {
            ClusterRepository.confirmedTextWrites.value = it
        }, colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = JupiterColors.Red,
            uncheckedTrackColor = JupiterColors.High))
    }
    Spacer(Modifier.height(10.dp))
    Label("PROBE DISPLAY IDS")
    Spacer(Modifier.height(6.dp))
    Text("Sends one labelled text frame. No vehicle control or calibration.",
        fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        ClusterService.PROBE_IDS.forEach { id ->
            SecondaryButton("0x%02X".format(id), {
                ClusterRepository.service?.sendProbeFrame(id)
            })
            Spacer(Modifier.width(8.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    Label("SEEN ON THIS CLUSTER")
    Spacer(Modifier.height(6.dp))
    if (frames.isEmpty()) {
        Text("No frames yet on this connection.", fontSize = 11.sp,
            color = JupiterColors.OnSurfaceVariant)
    } else {
        frames.forEach { f ->
            StatLine("0x%02X ${f.label}".trim(), "RX ${f.rx} / TX ${f.tx}", accent = f.rx > 0)
            Text(f.hex, fontSize = 9.sp, color = JupiterColors.OnSurfaceVariant,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    Label("NOT SEEN")
    Spacer(Modifier.height(6.dp))
    Text(if (missing.isEmpty()) "All candidate IDs were observed."
        else missing.joinToString("  ") { "0x%02X".format(it.id) },
        fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
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
    onOpenLiveGraph: () -> Unit,
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
    val fills by com.jupiterconnect.session.FuelLogStore.fills.collectAsState()
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
                            val stroke = 9.dp.toPx()
                            val inset = stroke / 2 + 3.dp.toPx()
                            val arc = Size(size.width - inset * 2, size.height - inset * 2)
                            val top = Offset(inset, inset)
                            drawArc(JupiterColors.High, 135f, 270f, false, top, arc,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                            drawArc(ringColor, 135f, 270f * pct / 100f, false, top, arc,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$pct%", fontSize = 26.sp, fontWeight = FontWeight.Medium,
                                color = JupiterColors.OnSurface)
                            Spacer(Modifier.height(4.dp))
                            Text("Capacity 5.1 L", fontSize = 11.sp,
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
                        "%.1f V".format(telemetry.batteryVoltage) else "--"
                    val rpmV = if (telemetry.rpm > 0) "${telemetry.rpm}" else "--"
                    Column(Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconStatRow(Icons.Rounded.DeviceThermostat, "TEMP", engineTempV)
                        IconStatRow(Icons.Rounded.BatteryChargingFull, "BATT", batteryV)
                        IconStatRow(Icons.Rounded.Speed, "RPM", rpmV)
                    }
                }
            }
            // Right column
            Column(Modifier.weight(178f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .height(161.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(JupiterColors.Low)
                        .clickable { onOpenLiveGraph() },
                ) {
                    Column(Modifier.padding(18.dp).fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("LIVE SPEED")
                            Spacer(Modifier.weight(1f))
                            // Small graph marker: tap the whole card to open
                            // the full-screen chart.
                            Box(
                                Modifier.size(22.dp).clip(CircleShape)
                                    .background(JupiterColors.RedTint),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Speed, "Open graph",
                                    modifier = Modifier.size(13.dp),
                                    tint = JupiterColors.Accent)
                            }
                        }
                        val speed = if (telemetry.connected && telemetry.speedKmh > 0)
                            "${telemetry.speedKmh}" else "--"
                        BigValue(speed, "km/h", 36)
                        Spacer(Modifier.weight(1f))
                        // Decorative graph artwork only — NOT the saved ride's
                        // exact plot. Real Speed/Throttle charts live in the
                        // full-screen LiveGraphScreen and in the Rides cards.
                        Image(
                            painterResource(R.drawable.ic_speed_graph),
                            contentDescription = "Speed graph",
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
                Box(Modifier.height(161.dp).clip(RoundedCornerShape(34.dp)).background(JupiterColors.Low)) {
                    Column(Modifier.padding(18.dp).fillMaxSize()) {
                        Label("ODOMETER")
                        val odo = if (telemetry.odometerKm > 0) telemetry.odometerKm else lastReading.odometerKm
                        BigValue("%.1f".format(odo), "km", 30)
                        // All-time best only rises and is always shown; the
                        // current ride's peak is shown underneath it.
                        val bestTop = RideStore.allTimeTopSpeed.collectAsState().value
                            .coerceAtLeast(telemetry.clusterTopSpeedKmh)
                        val rideTop = RideStore.liveTopSpeed.collectAsState().value
                        Text("Best $bestTop km/h", fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, color = JupiterColors.Accent)
                        Text("This ride $rideTop km/h", fontSize = 11.sp,
                            color = JupiterColors.OnSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        // The bar is a speed gauge, not an odometer gauge: label
                        // it so it no longer looks like unexplained progress.
                        Text("BEST / 95 KM/H", fontSize = 9.sp,
                            color = JupiterColors.OnSurfaceVariant,
                            maxLines = 1, softWrap = false)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(JupiterColors.High)) {
                            Box(Modifier.fillMaxWidth((bestTop / 95f).coerceIn(0f, 1f)).height(5.dp)
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
                // Show the cluster's own distance-to-empty — it uses the bike's
                // real fuel level and economy (e.g. 185 km), so the app matches
                // the dash. Fall back to the last known reading before the
                // economy frame arrives; never fabricate a tank-size estimate.
                val dte = if (telemetry.connected && telemetry.distanceToEmptyKm > 0)
                    telemetry.distanceToEmptyKm
                else if (lastReading.distanceToEmptyKm > 0) lastReading.distanceToEmptyKm
                else 0
                BigValue(if (dte > 0) "$dte" else "--", "km", 36)
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
        Spacer(Modifier.height(10.dp))
    }
}

/** Full-screen speed/throttle charts, opened by tapping the LIVE SPEED card. */
@Composable
fun LiveGraphScreen(
    telemetry: Telemetry,
    onBack: () -> Unit,
) {
    val liveSpeeds by RideStore.liveSpeeds.collectAsState()
    val liveThrottles by RideStore.liveThrottles.collectAsState()
    val rides by RideStore.rides.collectAsState()
    val recent = rides.firstOrNull()
    val speeds = if (liveSpeeds.isNotEmpty()) liveSpeeds
        else if (!telemetry.connected) recent?.speedSamples.orEmpty() else emptyList()
    val throttles = if (liveThrottles.isNotEmpty()) liveThrottles
        else if (!telemetry.connected) recent?.throttleSamples.orEmpty() else emptyList()
    val rideTop by RideStore.liveTopSpeed.collectAsState()
    val allTimeTop by RideStore.allTimeTopSpeed.collectAsState()

    Box(Modifier.fillMaxSize().background(JupiterColors.Surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                        tint = JupiterColors.OnSurface)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("SPEED & THROTTLE")
                    Text("Live graph", fontSize = 26.sp, fontWeight = FontWeight.Medium,
                        color = JupiterColors.OnSurface)
                }
                Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                RedCard(bar = true) {
                    Label("CURRENT SPEED")
                    Spacer(Modifier.height(8.dp))
                    BigValue(
                        if (telemetry.connected && telemetry.speedKmh > 0) "${telemetry.speedKmh}"
                        else "--", "km/h", 44)
                    Spacer(Modifier.height(8.dp))
                    Text("Ride top $rideTop km/h · All-time best $allTimeTop km/h",
                        fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                LineGraphCard("SPEED · KM/H", speeds, 120, JupiterColors.Red, 190)
                Spacer(Modifier.height(10.dp))
                LineGraphCard("THROTTLE · %", throttles, 100, JupiterColors.Accent, 150)
                Spacer(Modifier.height(110.dp))
            }
        }
    }
}

@Composable
private fun LineGraphCard(
    title: String,
    samples: List<Int>,
    ceiling: Int,
    color: Color,
    height: Int,
) {
    RedCard(bar = true) {
        Label(title)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            for (i in 1..3) {
                val y = size.height * i / 4f
                drawLine(JupiterColors.High.copy(alpha = 0.4f),
                    Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (samples.size > 1) {
                val max = (samples.maxOrNull() ?: 1).coerceAtLeast(1).coerceAtMost(ceiling).toFloat()
                val step = size.width / (samples.size - 1)
                for (i in 0 until samples.size - 1) {
                    val y0 = size.height - samples[i].coerceAtMost(ceiling) / max * size.height
                    val y1 = size.height - samples[i + 1].coerceAtMost(ceiling) / max * size.height
                    drawLine(color, Offset(i * step, y0), Offset((i + 1) * step, y1),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            } else {
                // A quiet baseline instead of an empty card when no ride yet.
                drawLine(color.copy(alpha = 0.35f),
                    Offset(0f, size.height * 0.85f), Offset(size.width, size.height * 0.85f),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${samples.size} samples · latest ${samples.lastOrNull() ?: 0}",
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
    }
}

// ------------------------------------------------------------------- RIDE

@Composable
fun RideScreenV2(
    telemetry: Telemetry,
    lastReading: com.jupiterconnect.session.LastReading,
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
            "ECON" to "${telemetry.averageFuelEconomy} km/L",
            "ENG TEMP" to "${telemetry.engineTempC} °C",
        )
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    RedCard(modifier = Modifier.weight(1f).height(92.dp)) {
                        TileLabel(label)
                        Spacer(Modifier.height(8.dp))
                        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = JupiterColors.OnSurface)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // Traffic/cluster-info screens moved to future development (PLAN.md).
        Spacer(Modifier.height(18.dp))
        Label("STATS")
        Spacer(Modifier.height(10.dp))
        RedCard { VehicleDetailsRows(telemetry, lastReading) }
        Spacer(Modifier.height(10.dp))
        RedCard { ProtocolDiscoveryCard() }
        Spacer(Modifier.height(10.dp))
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
    onViewAll: () -> Unit,
) {
    val total = rides.sumOf { it.distanceKm }
    val top = rides.maxOfOrNull { it.topSpeedKmh } ?: 0
    val fills by com.jupiterconnect.session.FuelLogStore.fills.collectAsState()
    val fuelMonths = remember(fills) { com.jupiterconnect.session.FuelLogStore.monthly() }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var selectedBar by remember { mutableStateOf<Int?>(null) }
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
                Text("Best recorded", fontSize = 13.sp,
                    color = JupiterColors.OnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (rides.isEmpty()) {
            RedCard { Text("No rides saved yet.", color = JupiterColors.OnSurfaceVariant, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(18.dp))
        Label("HISTORY · LAST 6 MONTHS")
        Spacer(Modifier.height(10.dp))
        RedCard {
            val months = remember(rides, fuelMonths) {
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
                    // Odometer deltas from the fuel log are real distance; use
                    // them for covered months, telemetry rides only elsewhere.
                    val fromFuel = fuelMonths.firstOrNull { it.key == key }?.km
                    val km = fromFuel ?: rides.filter { r ->
                        val c = Calendar.getInstance().apply { timeInMillis = r.startedAt }
                        c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) == key
                    }.sumOf { it.distanceKm }
                    label to km
                }
            }
            val maxKm = months.maxOf { it.second }.coerceAtLeast(0.1)
            // Three fixed-height zones per month: value, bar, label. Because
            // every column is the same height, all bars share one baseline and
            // every month keeps its label — including the tall Jul/Aug bars,
            // which used to push their labels out of the fixed-height row.
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom) {
                months.forEachIndexed { i, (label, km) ->
                    val isSelected = selectedBar == i
                    val barHeight = (maxOf(2.0, km / maxKm * 80.0)).dp
                    // A fixed column width is essential: fillMaxWidth inside
                    // an unbounded Row column made every month take the full
                    // card width, so only the first bar was visible.
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp).clickable {
                            selectedBar = if (isSelected) null else i
                        }) {
                        Box(Modifier.fillMaxWidth().height(18.dp),
                            contentAlignment = Alignment.BottomCenter) {
                            if (isSelected) Text("%.0f km".format(km), fontSize = 10.sp,
                                color = JupiterColors.Accent, maxLines = 1, softWrap = false)
                        }
                        // Bottom-centred inside a full-height slot: the bar
                        // grows upward from a common baseline.
                        Box(Modifier.width(18.dp).height(80.dp),
                            contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height(barHeight)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) JupiterColors.Accent
                                    else if (km > 0) JupiterColors.Red else JupiterColors.High))
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(14.dp),
                            contentAlignment = Alignment.TopCenter) {
                            Text(label, fontSize = 9.sp,
                                color = if (isSelected) JupiterColors.Accent
                                else JupiterColors.OnSurfaceVariant,
                                maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        // The Rides tab shows only the two newest rides; "View all" opens a
        // dedicated screen with the rest.
        rides.take(2).forEach { r ->
            Spacer(Modifier.height(10.dp))
            val expanded = expandedId == r.startedAt
            RedCard(modifier = Modifier.clickable {
                expandedId = if (expanded) null else r.startedAt
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.TwoWheeler, "Ride")
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.dateLabel, fontSize = 15.sp, color = JupiterColors.OnSurface)
                        Text(r.rideKind,
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
                if (expanded) {
                    Spacer(Modifier.height(12.dp))
                    Text("Idle ${r.idleMin} min · ISS ${r.issStops} stops · " +
                        "Fuel ${r.fuelBarsUsed} bars · Max ${r.topSpeedKmh} km/h", fontSize = 12.sp,
                        color = JupiterColors.OnSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    val samples = r.speedSamples
                    val thr = r.throttleSamples
                    if (samples.size > 1) {
                        Label("SPEED")
                        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                            val max = (samples.maxOrNull() ?: 1).coerceAtLeast(1)
                            val step = size.width / (samples.size - 1)
                            for (i in 0 until samples.size - 1) {
                                drawLine(JupiterColors.Red,
                                    Offset(i * step, size.height - samples[i].toFloat() / max * size.height),
                                    Offset((i + 1) * step, size.height - samples[i + 1].toFloat() / max * size.height),
                                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                        if (thr.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Label("THROTTLE")
                            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                                val step = size.width / (thr.size - 1)
                                for (i in 0 until thr.size - 1) {
                                    drawLine(JupiterColors.Accent,
                                        Offset(i * step, size.height - thr[i].toFloat() / 100f * size.height),
                                        Offset((i + 1) * step, size.height - thr[i + 1].toFloat() / 100f * size.height),
                                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                        }
                    } else {
                        Text("No graph data recorded for this ride", fontSize = 12.sp,
                            color = JupiterColors.OnSurfaceVariant)
                    }
                }
            }
        }
        if (rides.size > 2) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("View all ${rides.size} rides", onViewAll, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(18.dp))
        AnalyticsCards(rides, fills)
    }
}

/** One expandable ride card, shared by the Rides tab and the All rides screen. */
@Composable
private fun RideCard(r: Ride, expanded: Boolean, onToggle: () -> Unit) {
    RedCard(modifier = Modifier.clickable { onToggle() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.TwoWheeler, "Ride")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.dateLabel, fontSize = 15.sp, color = JupiterColors.OnSurface)
                Text(r.rideKind, fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
            }
            Text("%.1f km".format(r.distanceKm), fontSize = 18.sp,
                fontWeight = FontWeight.Medium, color = JupiterColors.Accent)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AVG %.0f".format(r.avgSpeedKmh), fontSize = 12.sp,
                color = JupiterColors.OnSurfaceVariant)
            Text("TOP ${r.topSpeedKmh}", fontSize = 12.sp,
                color = JupiterColors.OnSurfaceVariant)
            Text("MIN ${r.durationMin}", fontSize = 12.sp,
                color = JupiterColors.OnSurfaceVariant)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            val eco = RideAnalytics.ecoScore(r)
            Text("Idle ${r.idleMin} min · ISS ${r.issStops} stops · " +
                "Fuel ${r.fuelBarsUsed} bars · Eco ${eco.score}/100", fontSize = 12.sp,
                color = JupiterColors.OnSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val samples = r.speedSamples
            val thr = r.throttleSamples
            if (samples.size > 1) {
                Label("SPEED")
                Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                    val max = (samples.maxOrNull() ?: 1).coerceAtLeast(1)
                    val step = size.width / (samples.size - 1)
                    for (i in 0 until samples.size - 1) {
                        drawLine(JupiterColors.Red,
                            Offset(i * step, size.height - samples[i].toFloat() / max * size.height),
                            Offset((i + 1) * step, size.height - samples[i + 1].toFloat() / max * size.height),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                if (thr.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Label("THROTTLE")
                    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                        val step = size.width / (thr.size - 1).coerceAtLeast(1)
                        for (i in 0 until thr.size - 1) {
                            drawLine(JupiterColors.Accent,
                                Offset(i * step, size.height - thr[i].toFloat() / 100f * size.height),
                                Offset((i + 1) * step, size.height - thr[i + 1].toFloat() / 100f * size.height),
                                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
            } else {
                Text("No graph data recorded for this ride", fontSize = 12.sp,
                    color = JupiterColors.OnSurfaceVariant)
            }
        }
    }
}

/** All the personal analytics live together here, with trend-only caveats. */
@Composable
private fun AnalyticsCards(rides: List<Ride>, fills: List<com.jupiterconnect.session.FuelFill>) {
    val factor = RideAnalytics.etaFactor(rides)
    val etaRides = rides.count { it.mapsEtaMinutes > 0 }
    val trend = remember(fills) { RideAnalytics.mileageTrend(fills) }
    val latest = rides.firstOrNull()
    val eco = remember(rides) { latest?.let { RideAnalytics.ecoScore(it) } }
    val lastReading by RideStore.lastReading.collectAsState()
    val advice = remember(rides, lastReading.odometerKm) {
        RideAnalytics.serviceAdvice(rides, lastReading.odometerKm)
    }
    val fused by ClusterRepository.fusedSpeed.collectAsState()
    val errorKmh by ClusterRepository.speedErrorKmh.collectAsState()
    val fusionSamples by ClusterRepository.speedFusionSamples.collectAsState()
    val anomalies by ClusterRepository.anomalyNotes.collectAsState()

    Label("PERSONAL ETA")
    Spacer(Modifier.height(8.dp))
    RedCard {
        BigValue("%.2f".format(factor), "× Maps", 26)
        Text("From $etaRides rides with a Maps ETA · applied to cluster arrival",
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        Text("Trend from a few rides · not a prediction", fontSize = 9.sp,
            color = JupiterColors.OnSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    Label("MILEAGE TREND")
    Spacer(Modifier.height(8.dp))
    RedCard {
        if (trend == null) {
            Text("Need 8+ filled km/L readings for a trend",
                fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
        } else {
            BigValue("%.1f".format(trend.ewma), "km/L", 26)
            Text("Band %.1f–%.1f km/L · ${trend.samples} real fills (~6 months)".format(
                trend.low, trend.high), fontSize = 11.sp,
                color = JupiterColors.OnSurfaceVariant)
            if (trend.drop) {
                Spacer(Modifier.height(6.dp))
                Text("Latest fill dropped below the band — check tyre pressure or air filter",
                    fontSize = 11.sp, color = JupiterColors.Error)
            }
            Text("Trends and ranges, not predictions", fontSize = 9.sp,
                color = JupiterColors.OnSurfaceVariant)
        }
    }
    Spacer(Modifier.height(10.dp))
    Label("ECO SCORE")
    Spacer(Modifier.height(8.dp))
    RedCard {
        BigValue(if (eco != null) "${eco!!.score}" else "--", "/100", 26)
        if (eco != null) {
            Text("Hard launches ${eco!!.hardLaunches} · heavy throttle %.0f%% · idle %.0f%%".format(
                eco!!.heavyThrottleShare * 100, eco!!.idleShare * 100),
                fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        }
        Text("Latest ride · finer sampling on fast speed changes", fontSize = 9.sp,
            color = JupiterColors.OnSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    Label("SPEED FUSION")
    Spacer(Modifier.height(8.dp))
    RedCard {
        BigValue("$fused", "km/h", 26)
        Text("Cluster + GPS · speedometer error %+.1f km/h · $fusionSamples samples".format(errorKmh),
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        Text("Smoothed speed; distance still comes from the odometer", fontSize = 9.sp,
            color = JupiterColors.OnSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    Label("SERVICE TIMING")
    Spacer(Modifier.height(8.dp))
    RedCard {
        BigValue("${advice.suggestedKm}", "km", 26)
        Text("Odometer interval ${advice.odometerIntervalKm} km" +
            if (advice.severe) " · moved earlier for severe use" else "",
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        if (advice.reasons.isNotEmpty()) {
            Text(advice.reasons.joinToString(" · "), fontSize = 11.sp,
                color = JupiterColors.Accent)
        }
        Text("Heuristic from short trips, cold starts and idling", fontSize = 9.sp,
            color = JupiterColors.OnSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    Label("CHECKS")
    Spacer(Modifier.height(8.dp))
    RedCard {
        if (anomalies.isBlank()) {
            Text("No battery or temperature flags", fontSize = 12.sp,
                color = JupiterColors.OnSurfaceVariant)
        } else {
            Text(anomalies, fontSize = 12.sp, color = JupiterColors.Error)
        }
        Text("Live checks while riding · trends, not diagnoses", fontSize = 9.sp,
            color = JupiterColors.OnSurfaceVariant)
    }
}

/** Full list of saved rides, opened by the Rides tab's "View all" button. */
@Composable
fun AllRidesScreen(rides: List<Ride>, onBack: () -> Unit) {
    var expandedId by remember { mutableStateOf<Long?>(null) }
    Box(Modifier.fillMaxSize().background(JupiterColors.Surface)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                        tint = JupiterColors.OnSurface)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("SAVED ON THIS PHONE")
                    Text("All rides", fontSize = 26.sp, fontWeight = FontWeight.Medium,
                        color = JupiterColors.OnSurface)
                }
                Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("${rides.size} rides · ${"%.1f".format(rides.sumOf { it.distanceKm })} km",
                    fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
                rides.forEach { r ->
                    Spacer(Modifier.height(10.dp))
                    RideCard(r, expandedId == r.startedAt) {
                        expandedId = if (expandedId == r.startedAt) null else r.startedAt
                    }
                }
                Spacer(Modifier.height(110.dp))
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
                else "Saved ${parked!!.timeLabel}" +
                    if (parked!!.approximate) " · approximate fix" else "",
                fontSize = 13.sp, color = JupiterColors.OnSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Refresh location",
                { ClusterRepository.refreshParkedLocation() },
                Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Navigate to scooter", {
                val p = parked ?: return@PrimaryButton
                val dest = "${p.lat},${p.lng}"
                // Walking directions to where the scooter was parked: you are
                // on foot going back to it, and walking routes use footpaths.
                // This is separate from voice/normal navigation, which keeps
                // its own two-wheeler travel mode.
                // Maps URLs: travelmode=walking selects the walking tab.
                val uri = Uri.parse(
                    "https://www.google.com/maps/dir/?api=1" +
                        "&destination=$dest&travelmode=walking",
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                    .setPackage("com.google.android.apps.maps")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isFailure) {
                    // Older Maps builds: google.navigation &mode=w = walking.
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW,
                                Uri.parse("google.navigation:q=$dest&mode=w"))
                                .setPackage("com.google.android.apps.maps")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
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
    bluetoothOn: Boolean,
    onEnableBluetooth: () -> Unit,
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
    // Reflect the real adapter state, refreshed whenever the user taps Scan or
    // Find my cluster. When it is off, tapping the row offers to turn it on.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (bluetoothOn) Modifier else Modifier.clickable { onEnableBluetooth() },
    ) {
        IconCircle(Icons.Rounded.Bluetooth, "Bluetooth status")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (bluetoothOn) "Bluetooth is on" else "Bluetooth is off",
                fontSize = 14.sp, color = JupiterColors.OnSurface)
            if (!bluetoothOn) Text("Tap to turn it on", fontSize = 11.sp,
                color = JupiterColors.Error)
        }
        Icon(
            if (bluetoothOn) Icons.Rounded.Check else Icons.Rounded.ChevronRight,
            "Bluetooth status",
            modifier = Modifier.size(20.dp),
            tint = if (bluetoothOn) JupiterColors.Accent else JupiterColors.Error,
        )
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton("Scan for devices", onScan, Modifier.fillMaxWidth())
}

@Composable
private fun SettingsContent(
    notificationAccess: Boolean,
    bleReady: Boolean,
    contactsGranted: Boolean,
    phoneMirroring: Boolean,
    onPhoneMirroring: (Boolean) -> Unit,
    email: String?,
    onNotificationAccess: () -> Unit,
    onSignOut: () -> Unit,
) {
    RedCard {
        Label("PERMISSIONS")
        Spacer(Modifier.height(12.dp))
        listOf(
            Triple(Icons.Rounded.Bluetooth, "Bluetooth connect and scan", bleReady),
            Triple(Icons.Rounded.LocationOn, "Location", true),
            Triple(Icons.Rounded.Notifications, "Notifications", true),
            Triple(Icons.Rounded.NotificationsActive, "Notification access", notificationAccess),
            Triple(Icons.Rounded.Person, "Contacts (caller names)", contactsGranted),
        ).forEach { (icon, name, ok) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(icon, name)
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
        Label("CALL & MESSAGE ON CLUSTER")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mirror calls & messages", fontSize = 14.sp,
                    color = JupiterColors.OnSurface)
                Text(if (contactsGranted) "Contact names available"
                    else "Numbers only - enable Contacts",
                    fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
            }
            Switch(
                checked = phoneMirroring, onCheckedChange = onPhoneMirroring,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = JupiterColors.Red,
                    uncheckedTrackColor = JupiterColors.High,
                ),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    RedCard(bar = true) {
        Label("CARBON SAVING")
        Spacer(Modifier.height(8.dp))
        Text("Enter the lifetime kg from the dash once. The app then adds CO2 saved by every ISS stop it sees from now on, so the Home value keeps growing.",
            fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        var carbonInput by remember {
            mutableStateOf("%.2f".format(RideStore.carbonBaselineKg))
        }
        OutlinedTextField(
            value = carbonInput,
            onValueChange = { carbonInput = it },
            label = { Text("Cluster CO2 saved (kg)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Save carbon value", {
            carbonInput.toDoubleOrNull()?.let { RideStore.carbonBaselineKg = it.coerceAtLeast(0.0) }
        }, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    RedCard(bar = true) {
        Label("CLOUD SYNC")
        Spacer(Modifier.height(8.dp))
        Text(email ?: "Not signed in", fontSize = 15.sp, color = JupiterColors.OnSurface)
        Spacer(Modifier.height(6.dp))
        Text("Rides, fuel log, settings and stats sync when signed in.",
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Sign out", onSignOut, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("HIGH SPEED ALERT")
        Spacer(Modifier.height(8.dp))
        val alert by ClusterRepository.highSpeedAlert.collectAsState()
        val limit by ClusterRepository.highSpeedAlertSpeed.collectAsState()
        val connected by ClusterRepository.telemetry.collectAsState()
        Text("Active only while the cluster is connected", fontSize = 11.sp,
            color = JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alert at $limit km/h", fontSize = 15.sp, color = JupiterColors.OnSurface)
                Text(if (connected.connected) "Linked" else "Waiting for cluster",
                    fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
            }
            OutlinedButton(
                onClick = { ClusterRepository.highSpeedAlertSpeed.value = (limit - 5).coerceIn(20, 100) },
                modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
            ) { Text("-") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = { ClusterRepository.highSpeedAlertSpeed.value = (limit + 5).coerceIn(20, 100) },
                modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
            ) { Text("+") }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = alert,
                onCheckedChange = { ClusterRepository.highSpeedAlert.value = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = JupiterColors.Red,
                    uncheckedTrackColor = JupiterColors.High,
                ),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("VOICE")
        Spacer(Modifier.height(8.dp))
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
    bluetoothOn: Boolean,
    onEnableBluetooth: () -> Unit,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onForget: () -> Unit,
    notificationAccess: Boolean,
    bleReady: Boolean,
    contactsGranted: Boolean,
    phoneMirroring: Boolean,
    onPhoneMirroring: (Boolean) -> Unit,
    email: String?,
    onNotificationAccess: () -> Unit,
    onSignOut: () -> Unit,
    onTab: (Int) -> Unit,
) {
    JupiterShell("More", "DEVICES & SETTINGS", 4, onTab, {}) {
        Spacer(Modifier.height(4.dp))
        DevicesContent(devices, rememberedAddress, bluetoothOn, onEnableBluetooth,
            onScan, onConnect, onForget)
        Spacer(Modifier.height(22.dp))
        Label("SETTINGS")
        Spacer(Modifier.height(10.dp))
        SettingsContent(notificationAccess, bleReady, contactsGranted,
            phoneMirroring, onPhoneMirroring, email, onNotificationAccess, onSignOut)
    }
}
