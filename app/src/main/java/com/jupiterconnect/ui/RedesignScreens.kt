package com.jupiterconnect.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Call
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
import com.jupiterconnect.debug.FeatureVerification
import com.jupiterconnect.debug.FeatureVerification.Status
import com.jupiterconnect.debug.FrameCatalog
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.debug.PhoneFrameDebug
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.ClusterControlFrames
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.session.FuelLogStore
import com.jupiterconnect.session.Ride
import com.jupiterconnect.session.RideAnalytics
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.session.WarmupPhase
import com.jupiterconnect.session.lastMonthKeys
import com.jupiterconnect.session.monthlyDistanceKm
import com.jupiterconnect.session.odometerKmSinceLastFill
import com.jupiterconnect.traffic.Cities
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

/** Passive code discovery: what the cluster emits and what the app sends. */
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
    Spacer(Modifier.height(12.dp))
    Label("SEEN ON THIS CLUSTER")
    Spacer(Modifier.height(6.dp))
    if (frames.isEmpty()) {
        Text("No frames yet on this connection.", fontSize = 11.sp,
            color = JupiterColors.OnSurfaceVariant)
    } else {
        frames.forEach { f ->
            StatLine("0x%02X %s".format(f.id, f.label), "RX ${f.rx} / TX ${f.tx}", accent = f.rx > 0)
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
    val fusedSpeed by ClusterRepository.fusedSpeed.collectAsState()
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
                        val speed = if (telemetry.connected && fusedSpeed > 0)
                            "$fusedSpeed" else "--"
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
    val fusedSpeed by ClusterRepository.fusedSpeed.collectAsState()

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
                        if (telemetry.connected && fusedSpeed > 0) "$fusedSpeed"
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
    val fills by FuelLogStore.fills.collectAsState()
    val fuelMonths = remember(fills) { FuelLogStore.monthly() }
    val lastOdo by RideStore.lastReading.collectAsState()
    val lastFillOdo = remember(fills) { fills.maxByOrNull { it.dateIst }?.odometerKm }
    val odoTailKm = odometerKmSinceLastFill(lastFillOdo, lastOdo.odometerKm)
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
            val months = remember(rides, fuelMonths, lastFillOdo, lastOdo.odometerKm) {
                val fmt = java.text.DateFormatSymbols().shortMonths
                val now = System.currentTimeMillis()
                val kmByKey = monthlyDistanceKm(
                    rides = rides,
                    fuelMonths = fuelMonths,
                    lastFillOdometerKm = lastFillOdo,
                    currentOdometerKm = lastOdo.odometerKm,
                    nowMs = now,
                )
                // Past months keep their imported/locked values; only this
                // month also includes kilometres ridden since the last fill.
                lastMonthKeys(6, now).map { key -> fmt[key % 100] to (kmByKey[key] ?: 0.0) }
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
            if (odoTailKm > 0.5) {
                Spacer(Modifier.height(6.dp))
                Text("+%.0f km since last fill, from live odometer".format(odoTailKm),
                    fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
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
                    Text("Idle ${r.idleMin} min · Max ${r.topSpeedKmh} km/h",
                        fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
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
            Text("Idle ${r.idleMin} min · Eco ${eco.score}/100",
                fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
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
    val etaSessions = RideStore.navEtaSamples().size
    val factor = RideAnalytics.personalEtaFactor(RideStore.navEtaSamples(), rides)
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
        Text(if (etaSessions >= 5)
                "From $etaSessions completed navigation sessions · applied to cluster arrival"
             else "From $etaSessions sessions · $etaRides older rides · need 5 sessions",
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
            val parkedNote by ClusterRepository.parkedStatus.collectAsState()
            if (parkedNote.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(parkedNote, fontSize = 11.sp, color = JupiterColors.Accent)
            }
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
    callLogGranted: Boolean,
    phoneMirroring: Boolean,
    onPhoneMirroring: (Boolean) -> Unit,
    email: String?,
    onNotificationAccess: () -> Unit,
    onSignOut: () -> Unit,
    onPickPhoto: () -> Unit,
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
            Triple(Icons.Rounded.Call, "Call log (missed calls)", callLogGranted),
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
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Clear social counters",
            { ClusterRepository.clearSocialCounts() }, Modifier.fillMaxWidth())
        Text("WhatsApp / Facebook / Instagram unread counts are mirrored on 0x9C.",
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
    }

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
                onClick = { ClusterRepository.setHighSpeedAlertSpeed(limit - 5) },
                modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
            ) { Text("-") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = { ClusterRepository.setHighSpeedAlertSpeed(limit + 5) },
                modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
            ) { Text("+") }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = alert,
                onCheckedChange = { ClusterRepository.setHighSpeedAlert(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = JupiterColors.Red,
                    uncheckedTrackColor = JupiterColors.High,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Fires once when speed crosses the limit, then re-arms after you slow " +
                "down. Sends the cluster's HIGH SPEED ALERT frame and vibrates the phone.",
            fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Test alert now", { ClusterRepository.testSpeedAlert() },
            Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    RedCard(bar = true) {
        Label("TRAFFIC SCREENS")
        Spacer(Modifier.height(8.dp))
        val enabled by ClusterRepository.trafficEnabled.collectAsState()
        val status by ClusterRepository.trafficStatus.collectAsState()
        val screen by ClusterRepository.trafficScreen.collectAsState()
        val city by ClusterRepository.weatherCity.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weather / cricket / market / news", fontSize = 14.sp,
                    color = JupiterColors.OnSurface)
                Text("$status · screen 0x${"%02X".format(screen)}",
                    fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
                Text("Free APIs only - no TVS cloud or account",
                    fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { ClusterRepository.trafficEnabled.value = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = JupiterColors.Red,
                    uncheckedTrackColor = JupiterColors.High,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("WEATHER LOCATION: $city", fontSize = 12.sp, color = JupiterColors.Accent)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Cities.list.forEach { c ->
                OutlinedButton(
                    onClick = { ClusterRepository.setWeatherCity(c.name) },
                    modifier = Modifier.padding(end = 6.dp),
                ) { Text(c.name, fontSize = 11.sp) }
            }
        }
        Text("Weather uses last-parked coordinates; Hyderabad is the fallback.",
            fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    RedCard(bar = true) {
        Label("RIDER GREETING")
        Spacer(Modifier.height(8.dp))
        val name by ClusterRepository.riderName.collectAsState()
        val showName by ClusterRepository.riderNameEnabled.collectAsState()
        val photoReady by ClusterRepository.riderPhotoReady.collectAsState()
        OutlinedTextField(
            value = name,
            onValueChange = { ClusterRepository.setRiderName(it) },
            label = { Text("Name shown on the dash") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            if (photoReady) "Change rider photo" else "Add rider photo",
            onPickPhoto, Modifier.fillMaxWidth())
        Text("Photo is sent to the dash on ignition with your name.",
            fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Send greeting on connect", Modifier.weight(1f), fontSize = 13.sp,
                color = JupiterColors.OnSurface)
            Switch(
                checked = showName,
                onCheckedChange = { ClusterRepository.setRiderNameEnabled(it) },
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
        Label("NAV DIAGNOSTICS")
        Spacer(Modifier.height(8.dp))
        val probe by ClusterRepository.debugRemainingMeters.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remaining-distance probe", fontSize = 14.sp,
                    color = JupiterColors.OnSurface)
                Text("Sends 1500 m in 0x4E bytes 6-8 to identify the arrow symbol",
                    fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
            }
            Switch(
                checked = probe >= 0,
                onCheckedChange = {
                    ClusterRepository.debugRemainingMeters.value = if (it) 1500 else -1
                },
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

/**
 * Passive debug for the call/message channels. Shows whether any START_PHONE
 * frame (0x43 caller / 0x53 message) was ever seen from the cluster, with the
 * ASCII payload decoded exactly like the community protocol note. Sends nothing.
 */
@Composable
private fun PhoneFrameDebugCard() {
    val frames by FrameCatalog.frames.collectAsState()
    Label("PHONE FRAME DEBUG")
    Spacer(Modifier.height(8.dp))
    PhoneFrameDebug.PHONE_IDS.forEach { id ->
        val seen = frames.firstOrNull { it.id == id }
        val rxHex = seen?.lastRxHex.orEmpty()
        val txHex = seen?.lastTxHex.orEmpty()
        RedCard {
            StatLine(
                if (id == 0x43) "0x43 caller" else "0x53 message",
                "RX ${seen?.rx ?: 0} / TX ${seen?.tx ?: 0}",
                accent = (seen?.rx ?: 0) > 0,
            )
            Spacer(Modifier.height(4.dp))
            Text("RX text: ${if (rxHex.isEmpty()) "-" else PhoneFrameDebug.decode(rxHex, id).ifEmpty { "(no ASCII)" }}",
                fontSize = 11.sp, color = JupiterColors.OnSurface)
            Text("TX text: ${if (txHex.isEmpty()) "-" else PhoneFrameDebug.decode(txHex, id).ifEmpty { "(no ASCII)" }}",
                fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
            if (rxHex.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(rxHex, fontSize = 9.sp, color = JupiterColors.OnSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Text("Bytes 2-18 of START_PHONE frames as ASCII. Observation only - no frames are sent.",
        fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    val carbonKg = RideStore.carbonSavedKg
    Text("CARBON ESTIMATE  ${"%.1f".format(carbonKg)} kg",
        fontSize = 12.sp, color = JupiterColors.OnSurface)
    Text(RideStore.carbonEstimateSource,
        fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
    Text("The dash computes its own value from idle-stop (ISS) data - no phone " +
        "frame can write it. This is our independent estimate (TVS formats its " +
        "own figure with a x0.8 factor), shown in the app only.",
        fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
}

/** Persisted record of which optional features produced a visible dash change. */
@Composable
private fun FeatureVerificationCard() {
    val results by FeatureVerification.results.collectAsState()
    val ok = Color(0xFF66D19E)
    val warn = Color(0xFFFFC266)
    RedCard {
        Label("FEATURE VERIFICATION")
        Spacer(Modifier.height(8.dp))
        Text(
            "Every command is checked against cluster telemetry. NO_FEEDBACK means " +
                "the dash has no value we can compare, not that the command failed.",
            fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (results.isEmpty()) {
            Text("Nothing logged yet. Tap a setting or command to test it.",
                fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        } else {
            results.takeLast(12).reversed().forEach { r ->
                val colour = when (r.status) {
                    Status.WORKING -> ok
                    Status.NO_CHANGE -> warn
                    Status.NO_FEEDBACK -> JupiterColors.OnSurfaceVariant
                    Status.APPLIED -> JupiterColors.Accent
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text("${r.time}  ", fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text("${r.feature}  ${r.status.name}", fontSize = 11.sp, color = colour)
                        Text(r.detail, fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        SecondaryButton("Clear verification log", { FeatureVerification.clear() },
            Modifier.fillMaxWidth())
    }
}

/**
 * Compact single-line option button. The previous bracket-in-label approach
 * ("[2]") wrapped inside the narrow button and looked broken; selection is
 * shown with border/background colour instead.
 */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(34.dp).background(
            if (selected) JupiterColors.Accent.copy(alpha = 0.12f) else Color.Transparent,
            RoundedCornerShape(8.dp),
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) JupiterColors.Accent
                    else JupiterColors.OnSurfaceVariant.copy(alpha = 0.35f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (selected) JupiterColors.Accent else JupiterColors.OnSurface,
        ),
    ) {
        Text(label, fontSize = 10.sp, maxLines = 1, softWrap = false)
    }
}

/**
 * Cluster-side controls recovered from TVS Connect 8.14.3. Everything here is
 * an explicit user action - nothing is sent automatically during connect, so
 * Bluetooth link behaviour is unchanged.
 */
@Composable
private fun ClusterControlsCard() {
    val illumination by ClusterRepository.illumination.collectAsState()
    val tsl by ClusterRepository.tslSetting.collectAsState()
    val regen by ClusterRepository.regenSetting.collectAsState()
    val mode by ClusterRepository.rideMode.collectAsState()
    val applied by ClusterRepository.appliedSettings.collectAsState()
    val dirty = illumination != applied.illumination || tsl != applied.tsl ||
        regen != applied.regen || mode != applied.rideMode
    val status by ClusterRepository.settingsStatus.collectAsState()
    val handshake by ClusterRepository.handshakeStatus.collectAsState()
    val reminder by ClusterRepository.serviceReminderCode.collectAsState()
    val keylessOn by ClusterRepository.keylessEnabled.collectAsState()
    val keylessStatus by ClusterRepository.keylessStatus.collectAsState()
    var confirm by remember { mutableStateOf<ClusterControlFrames.KeylessCommand?>(null) }
    var roadName by remember { mutableStateOf("") }

    RedCard {
        Label("CLUSTER SETTINGS")
        Spacer(Modifier.height(8.dp))
        Text(if (dirty) "Unapplied changes - tap Apply to send them. The cluster still shows the last applied values."
            else "Only applied values reach the cluster. They are remembered and re-sent when the ignition comes on.",
            fontSize = 10.sp, color = if (dirty) JupiterColors.Red else JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Illumination / theme", fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { level ->
                ChoiceChip("$level", illumination == level,
                    { ClusterRepository.setIllumination(level) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Turn signal lamp", fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Off" to 0, "Left" to 26, "Right" to 27, "Hazard" to 0xAA).forEach { (name, code) ->
                ChoiceChip(name, tsl == code,
                    { ClusterRepository.setTsl(code) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Regen level", fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..3).forEach { level ->
                ChoiceChip("$level", regen == level,
                    { ClusterRepository.setRegen(level) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Ride mode", fontSize = 12.sp, color = JupiterColors.OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Auto" to 0, "M6" to 6, "M7" to 7, "M10" to 10).forEach { (name, code) ->
                ChoiceChip(name, mode == code,
                    { ClusterRepository.setRideMode(code) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SecondaryButton("Apply settings to cluster",
                { ClusterRepository.applyClusterSettings() }, Modifier.weight(2f))
            if (dirty) {
                SecondaryButton("Revert", { ClusterRepository.discardSettingsChanges() },
                    Modifier.weight(1f))
            }
        }
        Text(status, fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SecondaryButton("Trip A", { ClusterRepository.sendTrip(0, false) }, Modifier.weight(1f))
            SecondaryButton("Trip B", { ClusterRepository.sendTrip(1, false) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SecondaryButton("Reset A", { ClusterRepository.sendTrip(0, true) }, Modifier.weight(1f))
            SecondaryButton("Reset B", { ClusterRepository.sendTrip(1, true) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SecondaryButton("Calibrate", { ClusterRepository.sendCalibrationRequest() }, Modifier.weight(1f))
            SecondaryButton("Follow-me", { ClusterRepository.sendFollowMeTimer(5) }, Modifier.weight(1f))
        }
        InfoRow("Service reminder", if (reminder == 0) "none" else "code $reminder")
    }

    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("NAVIGATION EXTRAS")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = roadName,
            onValueChange = { roadName = it },
            label = { Text("Road name on dash") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        SecondaryButton("Send road name", { ClusterRepository.sendRoadName(roadName) },
            Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("SETUP DATA")
        Spacer(Modifier.height(8.dp))
        Text("Sends user ID, VIN and DOB frames exactly as TVS Connect does at setup.",
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SecondaryButton("Send setup data", { ClusterRepository.sendSetupData() },
            Modifier.fillMaxWidth())
        Text(handshake, fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
    }

    Spacer(Modifier.height(10.dp))
    RedCard {
        Label("KEYLESS ENTRY")
        Spacer(Modifier.height(8.dp))
        Text("Experimental. These frames can lock/unlock the bike. Disabled by default.",
            fontSize = 11.sp, color = JupiterColors.OnSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable keyless controls", Modifier.weight(1f), fontSize = 13.sp,
                color = JupiterColors.OnSurface)
            Switch(
                checked = keylessOn,
                onCheckedChange = {
                    ClusterRepository.keylessEnabled.value = it
                    ClusterRepository.keylessStatus.value =
                        if (it) "Enabled - commands need confirmation" else "Keyless controls disabled"
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = JupiterColors.Accent,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        listOf(
            "Unlock" to ClusterControlFrames.KeylessCommand.UNLOCK,
            "Lock" to ClusterControlFrames.KeylessCommand.LOCK,
            "Open trunk" to ClusterControlFrames.KeylessCommand.OPEN_TRUNK,
            "Find vehicle" to ClusterControlFrames.KeylessCommand.FIND_VEHICLE,
        ).forEach { (name, command) ->
            SecondaryButton(
                if (confirm == command) "Confirm $name?" else name,
                {
                    if (confirm == command) {
                        ClusterRepository.sendKeyless(command)
                        confirm = null
                    } else confirm = command
                },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(keylessStatus, fontSize = 10.sp, color = JupiterColors.OnSurfaceVariant)
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
    callLogGranted: Boolean,
    phoneMirroring: Boolean,
    onPhoneMirroring: (Boolean) -> Unit,
    email: String?,
    onNotificationAccess: () -> Unit,
    onSignOut: () -> Unit,
    onPickPhoto: () -> Unit,
    onTab: (Int) -> Unit,
) {
    JupiterShell("More", "DEVICES & SETTINGS", 4, onTab, {}) {
        Spacer(Modifier.height(4.dp))
        DevicesContent(devices, rememberedAddress, bluetoothOn, onEnableBluetooth,
            onScan, onConnect, onForget)
        Spacer(Modifier.height(22.dp))
        Label("SETTINGS")
        Spacer(Modifier.height(10.dp))
        SettingsContent(notificationAccess, bleReady, contactsGranted, callLogGranted,
            phoneMirroring, onPhoneMirroring, email, onNotificationAccess, onSignOut, onPickPhoto)
        Spacer(Modifier.height(18.dp))
        ClusterControlsCard()
        Spacer(Modifier.height(18.dp))
        FeatureVerificationCard()
        Spacer(Modifier.height(18.dp))
        RedCard { ProtocolDiscoveryCard() }
        Spacer(Modifier.height(10.dp))
        PhoneFrameDebugCard()
    }
}
