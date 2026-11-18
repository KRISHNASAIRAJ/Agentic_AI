package com.jupiterconnect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.transport.ClusterRepository
import com.jupiterconnect.transport.ClusterService
import com.jupiterconnect.ui.FindScreenV2
import com.jupiterconnect.ui.MoreScreenV2
import com.jupiterconnect.ui.HomeScreenV2
import com.jupiterconnect.ui.LiveGraphScreen
import com.jupiterconnect.ui.PermissionStatus
import com.jupiterconnect.ui.RideScreenV2
import com.jupiterconnect.ui.RidesScreenV2

import com.jupiterconnect.ui.PremiumDevicesScreen
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
    val lastReading by RideStore.lastReading.collectAsState()
    val rides by RideStore.rides.collectAsState()
    var mirroring by remember { mutableStateOf(ClusterRepository.mirroring.value) }
    var phoneMirroring by remember { mutableStateOf(loadPhoneMirroring(context)) }

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
        }
        // Location is needed for last-parked capture on every Android version.
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        // Contact names for callers/message senders on the cluster; denial just
        // falls back to showing the raw number.
        add(Manifest.permission.READ_CONTACTS)
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
            contacts = isGranted(Manifest.permission.READ_CONTACTS),
        )
    }

    /** Permission is not enough - the adapter must also be powered on. */
    @SuppressLint("MissingPermission")
    fun isBluetoothOn(): Boolean {
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter?.isEnabled == true
    }

    // Remember the last connected cluster so reconnecting is one tap (or auto).
    val devicePrefs = remember {
        context.getSharedPreferences("jupiter_device", android.content.Context.MODE_PRIVATE)
    }
    var rememberedAddress by remember {
        mutableStateOf(devicePrefs.getString("address", null))
    }
    fun connectToDevice(address: String) {
        devicePrefs.edit().putString("address", address).apply()
        rememberedAddress = address
        // Send the action with the Intent; onStartCommand performs the connect
        // after onCreate has initialised the service. No main-thread race.
        val i = Intent(context, ClusterService::class.java).setAction(ClusterService.ACTION_CONNECT)
        i.putExtra(ClusterService.EXTRA_ADDRESS, address)
        ContextCompat.startForegroundService(context, i)
    }
    fun connectAndRemember(address: String) = connectToDevice(address)
    fun forgetDevice() {
        devicePrefs.edit().remove("address").apply()
        rememberedAddress = null
    }

    var permStatus by remember { mutableStateOf(currentStatus()) }
    var bluetoothOn by remember { mutableStateOf(isBluetoothOn()) }

    // ACTION_REQUEST_ENABLE shows the system "Turn on Bluetooth?" dialog. Some
    // phones block it by policy, so keep a plain message as the fallback.
    var resumeScanAfterEnable by remember { mutableStateOf(false) }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothOn = isBluetoothOn()
        if (bluetoothOn && resumeScanAfterEnable) {
            resumeScanAfterEnable = false
            ContextCompat.startForegroundService(
                context, Intent(context, ClusterService::class.java)
                    .setAction(ClusterService.ACTION_SCAN))
        }
    }

    fun requestEnableBluetooth(thenScan: Boolean = false, purpose: String = "use Bluetooth") {
        bluetoothOn = isBluetoothOn()
        if (bluetoothOn) return
        Toast.makeText(context, "Bluetooth is off - turn it on to $purpose", Toast.LENGTH_LONG).show()
        resumeScanAfterEnable = thenScan
        val launched = runCatching {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            true
        }.getOrDefault(false)
        if (!launched) Toast.makeText(context,
            "Enable Bluetooth from Quick Settings, then try again", Toast.LENGTH_LONG).show()
    }

    /** Returns false (and prompts) when the adapter exists but is powered off. */
    fun bluetoothReady(purpose: String, thenScan: Boolean = false): Boolean {
        bluetoothOn = isBluetoothOn()
        if (bluetoothOn) return true
        requestEnableBluetooth(thenScan = thenScan, purpose = purpose)
        return false
    }

    fun startServiceIfReady() {
        permStatus = currentStatus()
        bluetoothOn = isBluetoothOn()
        if (permStatus.bleReady) {
            rememberedAddress?.let { addr ->
                connectToDevice(addr)
            } ?: ContextCompat.startForegroundService(
                context, Intent(context, ClusterService::class.java))
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startServiceIfReady() }

    // Scan must work from any screen: it re-checks permissions AND the real
    // adapter state, starts the BLE service if it is not running yet, and only
    // then asks it to scan. Without this, tapping Scan while the service is
    // null or Bluetooth is off silently does nothing.
    fun scanForDevices() {
        permStatus = currentStatus()
        if (!permStatus.bleReady) {
            launcher.launch(permissions)
            return
        }
        if (!bluetoothReady("scan for your cluster", thenScan = true)) return
        // Send SCAN as an Intent action. onStartCommand runs after onCreate,
        // so the service reference is guaranteed ready when it calls startScan.
        ContextCompat.startForegroundService(
            context, Intent(context, ClusterService::class.java).setAction(ClusterService.ACTION_SCAN))
    }

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

    var selected by remember { mutableIntStateOf(0) }
    val selectTab = { i: Int -> selected = i }
    // The LIVE SPEED card opens a dedicated full-screen chart instead of
    // expanding inside the card.
    var showLiveGraph by remember { mutableStateOf(false) }

    // "Find my cluster" opens the devices list. Check the adapter first so an
    // off phone is told why scanning would not work, but still show the list -
    // its Bluetooth row can then be tapped to turn it on.
    fun findCluster(tab: Int) {
        bluetoothReady("find your cluster")
        selected = tab
    }

    Box(Modifier.fillMaxSize()) {
        if (showLiveGraph) {
            LiveGraphScreen(
                telemetry = telemetry,
                onBack = { showLiveGraph = false },
            )
        } else {
        when (selected) {
            0 -> HomeScreenV2(
                telemetry = telemetry,
                lastReading = lastReading,
                onFindCluster = { findCluster(3) },
                onOpenLiveGraph = { showLiveGraph = true },
                activeTab = 0,
                onTab = selectTab,
                onSettings = { selected = 4 },
            )
            1 -> RideScreenV2(
                telemetry = telemetry,
                lastReading = lastReading,
                instruction = navInstruction,
                mirroring = mirroring,
                onMirroring = {
                    mirroring = it
                    ClusterRepository.mirroring.value = it
                },
                activeTab = 1,
                onTab = selectTab,
                onSettings = { selected = 4 },
            )
            2 -> RidesScreenV2(
                rides = rides,
                activeTab = 2,
                onTab = selectTab,
                onSettings = { selected = 4 },
            )
            3 -> FindScreenV2(
                activeTab = 3,
                onTab = selectTab,
                onSettings = { selected = 4 },
                onFindCluster = { findCluster(4) },
                rememberedAddress = rememberedAddress,
            )
            4 -> MoreScreenV2(
                devices = devices,
                rememberedAddress = rememberedAddress,
                bluetoothOn = bluetoothOn,
                onEnableBluetooth = { requestEnableBluetooth() },
                onScan = { scanForDevices() },
                onConnect = { address -> connectAndRemember(address) },
                onForget = { forgetDevice() },
                notificationAccess = notificationAccess,
                bleReady = permStatus.bluetooth,
                contactsGranted = permStatus.contacts,
                phoneMirroring = phoneMirroring,
                onPhoneMirroring = { enabled ->
                    phoneMirroring = enabled
                    ClusterRepository.phoneMirroring.value = enabled
                    devicePrefs.edit().putBoolean("phone_mirroring", enabled).apply()
                    if (enabled) launcher.launch(permissions)
                },
                email = com.jupiterconnect.sync.SupabaseClient.currentEmail(),
                onNotificationAccess = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onSignOut = { com.jupiterconnect.sync.SupabaseClient.signOut() },
                onTab = selectTab,
            )
        }
        }
    }
}

/** Persisted across restarts and mirrored into the notification listener. */
private fun loadPhoneMirroring(context: android.content.Context): Boolean {
    val enabled = context.getSharedPreferences("jupiter_device", android.content.Context.MODE_PRIVATE)
        .getBoolean("phone_mirroring", true)
    ClusterRepository.phoneMirroring.value = enabled
    return enabled
}

private fun hasNotificationAccess(context: android.content.Context): Boolean =
    runCatching {
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true
    }.getOrDefault(false)
