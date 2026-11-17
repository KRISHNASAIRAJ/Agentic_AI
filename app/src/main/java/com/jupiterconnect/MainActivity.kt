package com.jupiterconnect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.transport.ClusterService
import com.jupiterconnect.ui.PermissionStatus
import com.jupiterconnect.ui.PremiumFindScreen
import com.jupiterconnect.ui.PremiumHomeScreen
import com.jupiterconnect.ui.PremiumNavigateScreen
import com.jupiterconnect.ui.PremiumRidesScreen
import com.jupiterconnect.ui.PremiumSettingsScreen
import com.jupiterconnect.ui.theme.JupiterColors
import com.jupiterconnect.ui.theme.JupiterConnectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JupiterConnectTheme { JupiterApp() } }
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun JupiterApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val telemetry by ClusterRepository.telemetry.collectAsState()
    val devices by ClusterRepository.devices.collectAsState()
    val scanning by ClusterRepository.scanning.collectAsState()
    val navInstruction by ClusterRepository.navInstruction.collectAsState()
    var mirroring by remember { mutableStateOf(ClusterRepository.mirroring.value) }

    // Notification-listener access is a Settings toggle, not a runtime
    // permission. Read the real value and refresh it whenever the app resumes
    // from the Settings screen, rather than faking it with local state.
    var notificationAccess by remember {
        mutableStateOf(hasNotificationAccess(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun isGranted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    fun currentStatus(): PermissionStatus {
        val bluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isGranted(Manifest.permission.BLUETOOTH_SCAN) &&
                isGranted(Manifest.permission.BLUETOOTH_CONNECT)
        } else isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        return PermissionStatus(
            bluetooth = bluetooth,
            location = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
            notifications = if (Build.VERSION.SDK_INT >= 33)
                isGranted(Manifest.permission.POST_NOTIFICATIONS) else true,
        )
    }

    var permStatus by remember { mutableStateOf(currentStatus()) }
    fun startServiceIfReady() {
        permStatus = currentStatus()
        if (permStatus.bleReady) {
            ContextCompat.startForegroundService(context, Intent(context, ClusterService::class.java))
        }
    }

    // Scan must work from any screen: it re-checks permissions, starts the BLE
    // service if it is not running yet, and only then asks it to scan. Without
    // this, tapping Scan while the service is null silently does nothing.
    fun scanForDevices() {
        permStatus = currentStatus()
        if (!permStatus.bleReady) {
            launcher.launch(permissions)
            return
        }
        ContextCompat.startForegroundService(context, Intent(context, ClusterService::class.java))
        // The service sets ClusterRepository.service in onCreate; posting the
        // scan to the main thread lets that happen first.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            ClusterRepository.scan()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startServiceIfReady() }

    // Ask on first launch AND refresh the displayed state every time the app
    // resumes (the user may have changed permissions in system Settings).
    LaunchedEffect(Unit) {
        permStatus = currentStatus()
        if (!permStatus.bleReady || !permStatus.notifications) {
            launcher.launch(permissions)
        } else {
            startServiceIfReady()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) startServiceIfReady()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs = listOf(
        Tab("Home", Icons.Rounded.Home),
        Tab("Ride", Icons.Rounded.Navigation),
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
                    IconButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Icon(
                            Icons.Rounded.BluetoothConnected,
                            contentDescription = "Notification access",
                            tint = if (notificationAccess) JupiterColors.Accent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            selectedIconColor = JupiterColors.Accent,
                            selectedTextColor = JupiterColors.Accent,
                            unselectedIconColor = JupiterColors.OnSurfaceVariant,
                            unselectedTextColor = JupiterColors.OnSurfaceVariant,
                            indicatorColor = JupiterColors.PrimaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                0 -> PremiumHomeScreen(
                    telemetry = telemetry,
                    devices = devices,
                    scanning = scanning,
                    permissions = permStatus,
                    onGrantPermissions = { launcher.launch(permissions) },
                    onScan = { scanForDevices() },
                )
                1 -> PremiumNavigateScreen(
                    instruction = navInstruction,
                    telemetry = telemetry,
                    mirroring = mirroring,
                    onMirroringChange = {
                        mirroring = it
                        ClusterRepository.mirroring.value = it
                    },
                )
                2 -> PremiumRidesScreen(telemetry)
                3 -> PremiumFindScreen(telemetry)
                4 -> PremiumSettingsScreen(
                    notificationAccess = notificationAccess,
                    permissions = permStatus,
                    onGrantPermissions = { launcher.launch(permissions) },
                    onOpenNotificationAccess = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
            }
        }
    }
}

private fun hasNotificationAccess(context: android.content.Context): Boolean =
    runCatching {
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true
    }.getOrDefault(false)
