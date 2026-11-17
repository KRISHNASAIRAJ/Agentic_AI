package com.jupiterconnect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.transport.ClusterService
import com.jupiterconnect.ui.theme.JupiterColors
import com.jupiterconnect.ui.theme.JupiterConnectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JupiterConnectTheme { JupiterApp() } }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JupiterApp() {
    val context = LocalContext.current
    val telemetry by ClusterRepository.telemetry.collectAsState()
    val devices by ClusterRepository.devices.collectAsState()
    val scanning by ClusterRepository.scanning.collectAsState()
    val navInstruction by ClusterRepository.navInstruction.collectAsState()
    var mirroring by remember { mutableStateOf(ClusterRepository.mirroring.value) }

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("android.permission.BLUETOOTH_SCAN")
            add("android.permission.BLUETOOTH_CONNECT")
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add("android.permission.POST_NOTIFICATIONS")
    }.toTypedArray()
    fun granted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (granted()) ContextCompat.startForegroundService(context, Intent(context, ClusterService::class.java))
    }
    LaunchedEffect(Unit) {
        if (granted()) {
            ContextCompat.startForegroundService(context, Intent(context, ClusterService::class.java))
        } else {
            launcher.launch(permissions)
        }
    }

    val tabs = listOf(
        Tab("Home", Icons.Rounded.Home),
        Tab("Navigate", Icons.Rounded.Navigation),
        Tab("Rides", Icons.Rounded.History),
        Tab("Find", Icons.Rounded.Place),
        Tab("Settings", Icons.Rounded.Settings),
    )
    var selected by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tabs[selected].label,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Rounded.BluetoothConnected,
                            contentDescription = "Connection",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JupiterColors.Primary,
                            selectedTextColor = JupiterColors.Primary,
                            unselectedIconColor = JupiterColors.Secondary,
                            unselectedTextColor = JupiterColors.Secondary,
                            indicatorColor = JupiterColors.PrimaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (selected) {
                0 -> HomeScreen(telemetry, devices, scanning)
                1 -> NavigateScreen(navInstruction, telemetry, mirroring, { mirroring = it })
                2 -> RidesScreen()
                3 -> FindScreen()
                4 -> SettingsScreen()
            }
        }
    }
}

// ---------------------------------------------------------------- components

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    val tint = if (ok) JupiterColors.Tertiary else JupiterColors.Error
    Box(
        Modifier.background(tint.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JupiterColors.Container),
        border = BorderStroke(1.dp, JupiterColors.OutlineVariant),
    ) {
        // Must be a Column: a Box would stack every child on top of the next,
        // which is exactly what made all the labels overlap.
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun MetricTile(icon: ImageVector, tint: Color, label: String, value: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JupiterColors.Container),
        border = BorderStroke(1.dp, JupiterColors.OutlineVariant),
    ) {
        Column(Modifier.padding(13.dp)) {
            Box(
                Modifier.size(18.dp).background(tint, RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RideRow(time: String, distance: String, duration: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = JupiterColors.Container),
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(distance, style = MaterialTheme.typography.titleLarge)
            }
            Text(duration, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = JupiterColors.Secondary)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- screens

@Composable
private fun HomeScreen(
    telemetry: Telemetry,
    devices: List<Pair<String, String>>,
    scanning: Boolean,
) {
    Screen {
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("JUPITER 125 · COPPER MATTE".uppercase(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                StatusPill(
                    if (telemetry.connected) "Connected" else "Disconnected",
                    ok = telemetry.connected,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("${telemetry.speedKmh}", style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (telemetry.connected) "km/h · cluster link active"
                else "km/h · ignition ON, hold MODE 5s to pair",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (telemetry.error != null) {
                Text(telemetry.error, style = MaterialTheme.typography.labelSmall, color = JupiterColors.Error)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                val metrics = listOf(
                    "%.0f".format(telemetry.odometerKm) to "ODOMETER km",
                    "${telemetry.fuelBars} / 6" to "FUEL BARS",
                    "${telemetry.engineTempC}°" to "ENGINE °C",
                )
                metrics.forEachIndexed { i, item ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.first, style = MaterialTheme.typography.titleLarge)
                        Text(item.second, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (i < metrics.size - 1) {
                        Box(Modifier.width(1.dp).height(36.dp).background(JupiterColors.OutlineVariant))
                    }
                }
            }
        }
        SectionLabel("Quick actions")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ClusterRepository.scan() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JupiterColors.Primary, contentColor = JupiterColors.OnPrimary),
                shape = RoundedCornerShape(999.dp),
            ) { Text(if (scanning) "Scanning" else "Find", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { ClusterRepository.disconnect() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                shape = RoundedCornerShape(999.dp),
            ) { Text("Disconnect", fontSize = 11.sp) }
            OutlinedButton(
                onClick = {},
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                shape = RoundedCornerShape(999.dp),
            ) { Text("Mirror", fontSize = 12.sp) }
        }
        if (!telemetry.connected) {
            SectionLabel("Nearby clusters")
            CardBox {
                if (devices.isEmpty()) {
                    Text("No TVSBT cluster found. Switch ignition ON, hold MODE for 5s, then scan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    devices.forEach { (address, name) ->
                        OutlinedButton(
                            onClick = { ClusterRepository.connect(address) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("$name · $address") }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        SectionLabel("Live telemetry")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(Icons.Rounded.Speed, JupiterColors.Primary, "Speed", "${telemetry.speedKmh} km/h")
            MetricTile(Icons.Rounded.BatteryFull, JupiterColors.Tertiary, "Distance to empty",
                "${telemetry.distanceToEmptyKm} km")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(Icons.Rounded.SignalCellular4Bar, JupiterColors.Warning, "Engine temp",
                "${telemetry.engineTempC} °C")
            MetricTile(Icons.Rounded.BluetoothConnected, JupiterColors.Secondary, "Intake / frames",
                "${telemetry.intakeTempC}° · ${telemetry.frameCount}")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(Icons.Rounded.Speed, JupiterColors.Primary, "RPM", "${telemetry.rpm}")
            MetricTile(Icons.Rounded.BatteryFull, JupiterColors.Tertiary, "Battery",
                "%.1f V".format(telemetry.batteryVoltage))
        }
        SectionLabel("Last ride")
        RideRow("16 NOV · 6:42 PM", "18.4 km", "32 min")
    }
}

@Composable
private fun NavigateScreen(
    instruction: NavInstruction?,
    telemetry: Telemetry,
    mirroring: Boolean,
    onMirroringChange: (Boolean) -> Unit,
) {
    Screen {
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    if (instruction != null) "Navigation live" else "Waiting for maps",
                    ok = instruction != null,
                )
                Spacer(Modifier.weight(1f))
                Text((instruction?.provider ?: "no provider").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(52.dp).background(JupiterColors.PrimaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Navigation, contentDescription = "Turn right",
                        tint = JupiterColors.OnPrimaryContainer, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("${instruction?.distanceMeters ?: 0}",
                        style = MaterialTheme.typography.displayLarge)
                    Text(
                        listOfNotNull(instruction?.title, instruction?.street)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { "Start navigation in Google Maps" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = {
                    val d = instruction?.distanceMeters ?: 0
                    if (d <= 0) 0f else (1f - (d % 1000) / 1000f).coerceIn(0.05f, 1f)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(4.dp),
                color = JupiterColors.Primary,
                trackColor = JupiterColors.Highest,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("ETA ${instruction?.etaMinutes ?: 0} MIN".uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("LAST ${telemetry.lastFrame}".uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MIRROR TO CLUSTER".uppercase(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (mirroring) "Sending 5A 4E · 5B 4F" else "Mirror off",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = mirroring,
                    onCheckedChange = {
                        onMirroringChange(it)
                        ClusterRepository.mirroring.value = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JupiterColors.OnPrimary,
                        checkedTrackColor = JupiterColors.Primary,
                    ),
                )
            }
        }
        SectionLabel("Packet stream")
        CardBox {
            listOf("Frames seen" to "${telemetry.frameCount}", "Last frame" to telemetry.lastFrame,
                "Link" to if (telemetry.connected) "Stable" else "Offline",
                "Control cadence" to "1.5 s").forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodyMedium,
                        color = if (v == "Stable") JupiterColors.Tertiary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        SectionLabel("Up next")
        RideRow("IN 850 M", "Keep left", "→")
        RideRow("IN 2.1 KM", "Roundabout, exit 2", "↻")
    }
}

@Composable
private fun RidesScreen() {
    Screen {
        CardBox {
            Text("THIS WEEK".uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("86.2", style = MaterialTheme.typography.displayLarge)
            Text("km · 3 rides · 2 h 14 min moving", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Recent")
        RideRow("TODAY · 9:20 AM", "18.4 km", "32 min")
        RideRow("15 NOV · 6:05 PM", "42.7 km", "1 h 08 m")
        RideRow("14 NOV · 8:15 AM", "25.1 km", "34 min")
        SectionLabel("This month")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(Icons.Rounded.Speed, JupiterColors.Primary, "Distance", "312 km")
            MetricTile(Icons.Rounded.History, JupiterColors.Tertiary, "Rides", "14")
        }
    }
}

@Composable
private fun FindScreen() {
    Screen {
        Box(
            Modifier.fillMaxWidth().height(190.dp).background(JupiterColors.Lowest, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Place, contentDescription = "Last parked",
                tint = JupiterColors.Primary, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(14.dp))
        CardBox {
            Text("LAST PARKED".uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Near MG Road, Bengaluru", style = MaterialTheme.typography.titleLarge)
            Text("16 Nov · 6:42 PM · GPS fix good", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = JupiterColors.Primary, contentColor = JupiterColors.OnPrimary),
            shape = RoundedCornerShape(999.dp),
        ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Locate scooter")
        }
        SectionLabel("Find-me feedback")
        CardBox {
            Text("Sends the find-me flag in the next keepalive frame. The cluster flashes the indicators and beeps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            StatusPill("5B 4A · byte 17", ok = true)
        }
    }
}

@Composable
private fun SettingsScreen() {
    var notificationAccess by remember { mutableStateOf(false) }
    Screen {
        SectionLabel("Permissions")
        CardBox {
            listOf("Bluetooth connect & scan" to true, "Nearby devices" to true,
                "Notification access" to notificationAccess, "Location" to true,
                "Battery optimisation exempt" to true).forEach { (label, granted) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                        contentDescription = if (granted) "Granted" else "Missing",
                        tint = if (granted) JupiterColors.Tertiary else JupiterColors.Error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (!granted) IconButton(onClick = { notificationAccess = true }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Open settings",
                            tint = JupiterColors.Error)
                    }
                }
            }
        }
        SectionLabel("Cluster")
        CardBox {
            listOf("Model" to "Jupiter 125", "Cluster MAC" to "63:5A:0E:••:••:E1",
                "Mirror navigation" to "On", "Protocol" to "SmartXonnect BLE").forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(k, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SectionLabel("About")
        CardBox {
            Text("Jupiter Connect 0.1.0 · debug", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121218)
@Composable
private fun PreviewHome() {
    JupiterConnectTheme { HomeScreen(Telemetry(), emptyList(), false) }
}
