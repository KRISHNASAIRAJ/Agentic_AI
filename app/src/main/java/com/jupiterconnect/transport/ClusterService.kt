package com.jupiterconnect.transport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.annotation.SuppressLint
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jupiterconnect.R
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.protocol.Gatt
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.session.ColdStartTracker
import com.jupiterconnect.session.FuelLogStore
import com.jupiterconnect.session.RideAnalytics
import com.jupiterconnect.session.SpeedFusion
import com.jupiterconnect.session.WarmupPhase
import com.jupiterconnect.session.WarmupState
import com.jupiterconnect.sync.SupabaseClient
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.buildKeepalive
import com.jupiterconnect.protocol.buildBrightnessFrame
import com.jupiterconnect.protocol.buildCarbonFrame
import com.jupiterconnect.protocol.buildLocationFrame
import com.jupiterconnect.protocol.buildNavControl
import com.jupiterconnect.protocol.buildScreenSwitch
import com.jupiterconnect.protocol.buildSocialCounts
import com.jupiterconnect.protocol.buildNavText
import com.jupiterconnect.protocol.decodeFrame
import com.jupiterconnect.session.RideStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Shared state between the foreground service and Compose UI. */
object ClusterRepository {
    val telemetry = MutableStateFlow(Telemetry())
    val devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scanning = MutableStateFlow(false)
    val navInstruction = MutableStateFlow<NavInstruction?>(null)
    // Diagnostic snapshot of the last Maps notification and how it was parsed.
    val navDebug = MutableStateFlow("")
    // Last ISS/voice byte seen from the cluster, for trigger debugging.
    val voiceDebug = MutableStateFlow("No 0x11 service frame seen yet")
    // Cold-start warm-up state, updated from telemetry frames.
    val warmup = MutableStateFlow(WarmupState())
    // Rider preferences driven by voice commands.
    val brightness = MutableStateFlow(3)
    val autoSms = MutableStateFlow(false)
    val dnd = MutableStateFlow(false)
    val highSpeedAlert = MutableStateFlow(false)
    val highSpeedAlertSpeed = MutableStateFlow(60)
    val socialCounts = MutableStateFlow(Triple(0, 0, 0))

    fun setBrightness(level: Int) {
        val next = level.coerceIn(2, 5)
        brightness.value = next
        service?.sendBrightness(next)
    }
    // Raw 0x18 engine frame + decoded values, for mapping verification.
    val engineDebug = MutableStateFlow("No 0x18 frame seen yet")
    // Recent live speeds for the speedometer graph (oldest first).
    val speedHistory = MutableStateFlow<List<Int>>(emptyList())
    // Cluster + GPS fusion: smoothed speed and learned speedometer error.
    val fusedSpeed = MutableStateFlow(0)
    val speedErrorKmh = MutableStateFlow(0.0)
    val speedFusionSamples = MutableStateFlow(0)
    // Live checks (battery trend, engine temp vs ambient).
    val anomalyNotes = MutableStateFlow("")
    val mirroring = MutableStateFlow(true)

    private const val SPEED_HISTORY_SIZE = 48
    fun recordSpeed(speedKmh: Int) {
        val current = speedHistory.value
        val next = if (current.lastOrNull() == speedKmh) current else (current + speedKmh).takeLast(SPEED_HISTORY_SIZE)
        speedHistory.value = next
    }
    var service: ClusterService? = null
    /** True while the app UI is on screen; decides screen vs service voice input. */
    @Volatile var uiResumed = false

    fun scan() = service?.startScan()
    fun connect(address: String) = service?.connectTo(address)
    fun disconnect() = service?.disconnectNow()
    /** Manually re-capture the parking spot (Find tab). */
    fun refreshParkedLocation() = service?.refreshParkedLocation()
    /** Caller/message mirroring is independent of turn-by-turn mirroring. */
    val phoneMirroring = MutableStateFlow(true)

    /** Show a short reply on the cluster, overriding the nav text briefly. */
    fun showText(text: String, holdMs: Long = 10_000L) {
        replyText = text
        replyUntilMs = System.currentTimeMillis() + holdMs
        service?.sendTextRow(text)
    }

    @Volatile private var replyText: String? = null
    @Volatile private var replyUntilMs = 0L

    /** Reply text to show in the nav row while the override is active. */
    fun currentReplyText(): String? =
        if (System.currentTimeMillis() < replyUntilMs) replyText else null
}

/** All BLE entry points are guarded by [hasBluetoothPermission] at runtime. */
@SuppressLint("MissingPermission")
class ClusterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // applicationContext is null in a field initializer (before attachBaseContext),
    // so create the bridge in onCreate once the Service has a real Context.
    private lateinit var voiceBridge: VoiceAssistantBridge
    private val coldStartTracker = ColdStartTracker()
    private val writeLock = Mutex()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var gatt: BluetoothGatt? = null
    // Guards parking capture: a fresh spot is saved once per connect/disconnect
    // cycle, never from repeated disconnect callbacks or background location.
    @Volatile private var connectionEstablished = false
    // Invalidates an in-flight parking fix if the bike reconnects before it lands.
    @Volatile private var parkingEpoch = 0
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var discoveryStarted = false
    @Volatile private var lastAddress: String? = null
    private var keepaliveJob: Job? = null
    private var navJob: Job? = null
    private var syncJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var writeFailures = 0
    /** Set by an explicit disconnect so the auto-retry never fights the user. */
    @Volatile private var manualDisconnect = false
    private var disconnectedAtMs = 0L
    private var clearNotification: Runnable? = null
    private var speedAlertActive = false
    private var lastNotificationKey = ""
    private val speedFusion = SpeedFusion()
    private var gpsSpeedMps = -1f
    private var gpsAccuracyM = -1f
    private var gpsListener: LocationListener? = null
    private val batteryHistory = ArrayDeque<Double>()
    private var lastAnomalyKey = ""
    @SuppressLint("MissingPermission") private var scanCallback: ScanCallback? = null

    private val adapter: BluetoothAdapter?
        get() = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun onCreate() {
        super.onCreate()
        voiceBridge = VoiceAssistantBridge(this)
        RideStore.init(applicationContext)
        SupabaseClient.init(applicationContext)
        coldStartTracker.setLastRunMs(RideStore.warmupLastRunMs)
        createChannel()
        ClusterRepository.service = this
        // Start with the connected-device type only: microphone permission may
        // not be granted yet at boot. The mic type is added only while listening.
        ServiceCompat.startForeground(
            this, 1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        // Keep rides, fuel logs and app state synced even with no bike linked.
        startRideSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasBluetoothPermission()) return START_STICKY
        // Always handle the requested action HERE, in onStartCommand, which is
        // guaranteed to run after onCreate has set ClusterRepository.service.
        // Launching the service and then posting scan()/connect() to the main
        // thread raced with onCreate and silently dropped the action when the
        // service reference was still null ΓÇö "Bluetooth not connecting".
        when (intent?.action) {
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_ADDRESS)?.let { connectTo(it) }
                ?: startScan()
            ACTION_SCAN -> startScan()
            else -> startScan()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasBluetoothPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun startScan() {
        if (!hasBluetoothPermission()) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = "Bluetooth permission not granted - grant it in Settings")
            Log.w(TAG, "scan refused: no bluetooth permission")
            return
        }
        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = "Bluetooth is off - turn it on and scan again")
            Log.w(TAG, "scan refused: bluetooth off")
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = "BLE scanner unavailable on this device")
            return
        }
        // Stop any previous scan so repeated taps never stack callbacks.
        scanCallback?.let { old -> runCatching { scanner.stopScan(old) } }
        ClusterRepository.devices.value = emptyList()
        ClusterRepository.scanning.value = true
        Log.i(TAG, "scan started")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                record(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { record(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                ClusterRepository.scanning.value = false
                val msg = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "A scan is already running"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App could not register for scanning"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "This phone does not support BLE scanning"
                    SCAN_FAILED_INTERNAL_ERROR -> "Bluetooth stack error - try again"
                    else -> "Scan failed (code $errorCode)"
                }
                ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(error = msg)
                Log.w(TAG, "scan failed: $errorCode - $msg")
            }
        }
        scanCallback = callback
        try {
            scanner.startScan(callback)
        } catch (e: SecurityException) {
            ClusterRepository.scanning.value = false
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(error = e.message)
            Log.w(TAG, "startScan threw: ${e.message}")
            return
        }
        // Time-box the scan: it is the most battery-hungry thing the app does.
        main.postDelayed({
            if (ClusterRepository.scanning.value) {
                runCatching { scanner.stopScan(callback) }
                ClusterRepository.scanning.value = false
                Log.i(TAG, "scan timed out")
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun record(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        // Advertised name first: `device.name` is frequently null until bonded.
        val advertised = runCatching { result.scanRecord?.deviceName }.getOrNull()
        val name = advertised ?: device.name ?: "Unknown ${address.takeLast(5)}"
        val byName = CLUSTER_NAME_RE.containsMatchIn(name)
        val byUuid = runCatching {
            result.scanRecord?.serviceUuids?.any {
                it.uuid?.toString()?.equals(Gatt.SERVICE, ignoreCase = true) == true
            } == true
        }.getOrDefault(false)
        val isCluster = byName || byUuid

        // Keep every device - an already-paired sleeping cluster may advertise
        // nothing recognisable, and hiding it made the scan look broken.
        val entry = ScannedDevice(address, name, result.rssi, isCluster)
        val current = ClusterRepository.devices.value.toMutableList()
        val idx = current.indexOfFirst { it.address == address }
        if (idx < 0) current.add(entry)
        else if (result.rssi > current[idx].rssi) current[idx] = entry
        ClusterRepository.devices.value = current.sortedWith(
            compareByDescending<ScannedDevice> { it.isCluster }
                .thenByDescending { it.rssi })
        Log.d(TAG, "seen: $name [$address] rssi=${result.rssi} cluster=$isCluster")
    }

    /** User/service initiated connect: clear any retry backoff first. */
    fun connectTo(address: String) {
        if (!hasBluetoothPermission()) return
        lastAddress = address
        manualDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        attemptConnect(address)
    }

    /** Low-level connect used by both user taps and the retry loop. */
    private fun attemptConnect(address: String) {
        if (!hasBluetoothPermission()) return
        scanCallback?.let { cb -> runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) } }
        ClusterRepository.scanning.value = false
        if (gatt != null) {
            Log.w(TAG, "connect ignored: gatt already active")
            return
        }
        val device: BluetoothDevice = try {
            adapter?.getRemoteDevice(address) ?: return
        } catch (e: IllegalArgumentException) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = e.message)
            return
        }
        // Bond first when needed - some clusters refuse GATT until bonded.
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            runCatching { device.createBond() }
            Log.i(TAG, "createBond requested for $address")
        }
        Log.i(TAG, "connectGatt -> $address")
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /**
     * Retry a lost link with capped exponential backoff. A failed attempt
     * (status 147) and a real drop (status 8) both land here, but a manual
     * disconnect or Bluetooth-off never does - no endless retry loop.
     */
    private fun scheduleReconnect() {
        val address = lastAddress ?: return
        if (manualDisconnect || reconnectJob?.isActive == true) return
        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = "Bluetooth is off - turn it on to reconnect")
            return
        }
        val idx = reconnectAttempts.coerceIn(0, RECONNECT_DELAYS.lastIndex)
        val delayMs = RECONNECT_DELAYS[idx]
        reconnectAttempts++
        FrameLog.note("RECONNECT in ${delayMs / 1000}s (attempt $reconnectAttempts)")
        reconnectJob = scope.launch {
            delay(delayMs)
            reconnectJob = null
            if (manualDisconnect || gatt != null) return@launch
            if (adapter?.isEnabled != true) return@launch
            attemptConnect(address)
        }
    }

    /** Honest wording: status 147 is a failed attempt, not a lost connection. */
    private fun describeLinkError(status: Int, wasEstablished: Boolean): String? = when {
        status == 0 -> "Disconnected - searching"
        !wasEstablished && status == GATT_CONNECTION_TIMEOUT ->
            "Out of range - searching for cluster"
        status == GATT_CONN_TIMEOUT -> "Link lost - reconnecting"
        status == GATT_CONN_TERMINATE_PEER_USER -> "Cluster switched off - searching"
        else -> "Link lost (status $status) - reconnecting"
    }

    /** Active only while the cluster is linked, like TVS Connect. */
    private fun checkSpeedAlert(t: Telemetry) {
        if (!t.connected) {
            speedAlertActive = false
            return
        }
        val over = ClusterRepository.highSpeedAlert.value &&
            t.speedKmh > ClusterRepository.highSpeedAlertSpeed.value
        if (over == speedAlertActive) return
        speedAlertActive = over
        if (over) {
            FrameLog.note("SPEED ALERT ${t.speedKmh} > ${ClusterRepository.highSpeedAlertSpeed.value}")
            scope.launch { write(buildNavText("OVER SPEED ${t.speedKmh}")) }
            val vibrator = getSystemService(android.os.Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(180, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(180L)
            }
        }
    }

    /** GPS speed for the Kalman fusion with the cluster speedometer. */
    @SuppressLint("MissingPermission")
    private fun startGpsSpeed() {
        if (gpsListener != null) return
        val allowed =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsSpeedMps = if (location.hasSpeed()) location.speed else -1f
                gpsAccuracyM = location.accuracy
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        gpsListener = listener
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
            runCatching<Unit> {
                manager.requestLocationUpdates(p, 1_000L, 0f, listener, Looper.getMainLooper())
            }
        }
    }

    private fun stopGpsSpeed() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        gpsListener?.let { runCatching { manager.removeUpdates(it) } }
        gpsListener = null
        gpsSpeedMps = -1f
        gpsAccuracyM = -1f
    }

    /** Live battery/temperature checks. Trends, not diagnoses. */
    private fun checkAnomalies(t: Telemetry) {
        if (t.rpm < RUNNING_RPM && t.speedKmh < 1) return
        if (t.batteryVoltage > 0) {
            batteryHistory.addLast(t.batteryVoltage)
            while (batteryHistory.size > 30) batteryHistory.removeFirst()
        }
        val notes = mutableListOf<String>()
        val v = t.batteryVoltage
        if (v > 0 && v < 12.2) notes += "Battery low: %.1f V".format(v)
        if (batteryHistory.size >= 10 && v > 0) {
            val avg = batteryHistory.average()
            if (v < avg - 0.4) notes += "Battery dropping: %.1f V vs %.1f V trend".format(v, avg)
        }
        if (t.engineTempC > 0 && t.intakeTempC > 0 && t.speedKmh > 10) {
            val delta = t.engineTempC - t.intakeTempC
            if (delta >= 40) notes += "Engine hot vs ambient: ╬ö$delta┬░C"
        }
        val key = notes.joinToString("|")
        if (key != lastAnomalyKey) {
            lastAnomalyKey = key
            ClusterRepository.anomalyNotes.value = notes.joinToString("\n")
        }
    }

    /** Fan out one decoded frame to rides, voice, speed graph and warm-up. */
    private fun onTelemetryFrame(t: Telemetry, primary: Boolean, raw: ByteArray) {
        if ((raw[1].toInt() and 0xFF) == 0x18) {
            val hex = raw.joinToString(" ") { "%02X".format(it) }
            ClusterRepository.engineDebug.value =
                "RAW: $hex\n" +
                "byte11=${"%02X".format(raw[11])} (${raw[11].toInt() and 0xFF}) " +
                "ΓåÆ ${"%.1f".format(t.batteryVoltage)} V\n" +
                "load=${t.engineLoad} intake=${t.intakeTempC}C " +
                "coolant=${t.engineTempC}C"
        }
        if ((raw[1].toInt() and 0xFF) == 0x10) {
            voiceBridge.onSwitchAction(raw[12].toInt() and 0x3F)
        }
        checkSpeedAlert(t)
        val fused = speedFusion.update(
            t.speedKmh.toDouble(),
            if (gpsSpeedMps >= 0) gpsSpeedMps * 3.6 else null,
            if (gpsAccuracyM >= 0) gpsAccuracyM.toDouble() else null,
        )
        ClusterRepository.fusedSpeed.value = fused.kmh
        ClusterRepository.speedErrorKmh.value = fused.errorKmh
        ClusterRepository.speedFusionSamples.value = fused.samples
        // Rides use the fused, cleaner speed; distance still comes from the
        // odometer, which the cluster broadcasts correctly.
        RideStore.onFrame(t.copy(speedKmh = fused.kmh), primary)
        voiceBridge.onFrame(t)
        ClusterRepository.recordSpeed(fused.kmh)
        checkAnomalies(t)
        val previous = ClusterRepository.warmup.value.phase
        val state = coldStartTracker.update(t)
        ClusterRepository.warmup.value = state
        RideStore.warmupLastRunMs = coldStartTracker.getLastRunMs()
        if (previous != WarmupPhase.READY && state.phase == WarmupPhase.READY) {
            notifyWarmupReady()
        }
    }

    private fun notifyWarmupReady() {
        val s = ClusterRepository.warmup.value
        FrameLog.note(
            "WARMUP ready target=${s.targetC}C start=${s.startTemp}C " +
                "ambient=${s.ambientC}C slope=${String.format("%.2f", s.slopeCPerMin)}",
        )
        val vibrator = getSystemService(android.os.Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(120, 120))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(120L)
        }
    }

    /**
     * Capture the parking spot the instant the link drops. Ask for a fresh fix
     * (the scooter is parked now, not where the phone last had a cached fix),
     * and fall back to the best cached fix if a fresh one takes too long. The
     * saved time is the disconnect time, never the GPS fix time, so "Saved" is
     * accurate. [connectionEstablished] keeps the spot frozen until the next
     * real connect/disconnect cycle.
     */
    @SuppressLint("MissingPermission")
    private fun saveParkedLocation(force: Boolean = false) {
        if (!force && !connectionEstablished) return
        connectionEstablished = false
        val epoch = ++parkingEpoch
        val locationAllowed =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!locationAllowed) {
            FrameLog.note("PARKED skipped: no location permission")
            return
        }
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            FrameLog.note("PARKED skipped: no location provider enabled")
            return
        }

        val fallback = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val savedAt = System.currentTimeMillis()
        var saved = false
        lateinit var timeout: Runnable
        lateinit var listenerRef: LocationListener
        fun stopUpdates() {
            providers.forEach { p -> runCatching { manager.removeUpdates(listenerRef) } }
        }
        // Local holder so the timeout can clear the same listener instance.
        listenerRef = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (saved || epoch != parkingEpoch) return
                saved = true
                main.removeCallbacks(timeout)
                stopUpdates()
                // LocationManager can replay a cached fix immediately; only
                // call it fresh when it was obtained at disconnect time.
                val isFresh = savedAt - location.time < 60_000L
                RideStore.saveParkedLocation(location.latitude, location.longitude, savedAt,
                    approximate = !isFresh)
                FrameLog.note("PARKED ${if (isFresh) "fresh" else "replayed"} " +
                    "${"%.5f,%.5f".format(location.latitude, location.longitude)}")
            }
            override fun onStatusChanged(provider: String?, status: Int,
                                         extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        timeout = Runnable {
            if (saved || epoch != parkingEpoch) return@Runnable
            saved = true
            stopUpdates()
            val f = fallback
            if (f != null) {
                // A cached fix is better than leaving last ride's spot on screen.
                RideStore.saveParkedLocation(f.latitude, f.longitude, savedAt,
                    approximate = true)
                FrameLog.note("PARKED cached ${"%.5f,%.5f".format(f.latitude, f.longitude)}")
            } else {
                FrameLog.note("PARKED no fresh fix and no cached fix available")
            }
        }
        // requestLocationUpdates is more reliable than requestSingleUpdate on
        // OEM stacks that silently drop one-shot requests. First fix wins.
        providers.forEach { p ->
            runCatching<Unit> {
                manager.requestLocationUpdates(p, 1_000L, 0f, listenerRef, Looper.getMainLooper())
            }
        }
        main.postDelayed(timeout, 5_000L)
    }

    /** Find-tab button: re-capture the spot with the user explicitly present. */
    fun refreshParkedLocation() {
        val allowed =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = "Location permission needed to save the parking spot")
            return
        }
        saveParkedLocation(force = true)
        ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(error = null)
    }

    /** Write a short text reply to the cluster's navigation text row (0x5B 0x4F). */
    fun sendTextRow(text: String) {
        scope.launch {
            // The cluster can drop a single write; repeat briefly so the reply
            // actually lands instead of only appearing inside the app.
            repeat(3) {
                write(buildNavText(text))
                delay(250L)
            }
        }
    }

    fun sendBrightness(level: Int) {
        scope.launch { write(buildBrightnessFrame(level)) }
    }

    fun showScreen(id: Int) {
        scope.launch { write(buildScreenSwitch(id)) }
    }

    fun sendPlace(name: String) {
        scope.launch { write(buildLocationFrame(name)) }
    }

    fun sendSocialCounts(wa: Int, fb: Int, ig: Int) {
        ClusterRepository.socialCounts.value = Triple(wa, fb, ig)
        scope.launch { write(buildSocialCounts(wa, fb, ig)) }
    }

    /** Manual trigger so the voice flow can be tested without the bike. */
    fun simulateVoiceTrigger() {
        voiceBridge.simulate()
    }

    /** Add the microphone FGS type only while actually listening (Android 14+). */
    fun useMicrophoneForeground() = setForegroundTypes(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

    fun releaseMicrophoneForeground() = setForegroundTypes(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

    private fun setForegroundTypes(types: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return
        runCatching {
            ServiceCompat.startForeground(this, 1, buildNotification(), types)
        }.onFailure { FrameLog.note("VOICE foreground type update failed: ${it.message}") }
    }

    fun disconnectNow() {
        // Stop the retry loop first: an explicit disconnect must stay off.
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        // Only a connected bike has a parking spot to save (onDestroy also
        // calls this, with no link at all).
        if (connectionEstablished || gatt != null) saveParkedLocation(force = true)
        stopGpsSpeed()
        // A manual disconnect after a ride must still finalise that ride.
        RideStore.onDisconnected()
        keepaliveJob?.cancel()
        keepaliveJob = null
        navJob?.cancel()
        navJob = null
        val g = gatt
        if (g != null) {
            writeChar = null
            gatt = null
            runCatching { g.close() }
        }
        scope.launch { writeLock.withLock { } }
        ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
            connected = false, error = null)
    }

    private fun startDiscovery(g: BluetoothGatt) {
        if (g !== gatt || discoveryStarted) return
        discoveryStarted = true
        if (!g.discoverServices()) discoveryStarted = false
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected, status=$status - requesting MTU")
                    FrameLog.note("GATT connected status=$status")
                    reconnectJob?.cancel()
                    reconnectJob = null
                    reconnectAttempts = 0
                    manualDisconnect = false
                    // Low latency while riding; Android restores the default
                    // connection parameters when this GATT is closed.
                    runCatching {
                        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    }
                    voiceBridge.reset()
                    connectionEstablished = true
                    parkingEpoch++
                    speedFusion.reset()
                    batteryHistory.clear()
                    lastAnomalyKey = ""
                    ClusterRepository.anomalyNotes.value = ""
                    startGpsSpeed()
                    RideStore.pendingMapsEtaMinutes =
                        ClusterRepository.navInstruction.value?.etaMinutes?.takeIf { it > 0 } ?: -1
                    RideStore.onConnected()
                    gatt = g
                    ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                        connected = true, address = g.device.address, error = null)
                    updateNotification()
                    if (!g.requestMtu(Gatt.REQUESTED_MTU)) startDiscovery(g)
                    main.postDelayed({ startDiscovery(g) }, 2500L)
                    disconnectedAtMs = 0L
                    clearNotification?.let { main.removeCallbacks(it) }
                    clearNotification = null
                    startRideSync()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT disconnected, status=$status")
                    FrameLog.note("GATT disconnected status=$status")
                    val wasEstablished = connectionEstablished
                    connectionEstablished = false
                    if (wasEstablished) {
                        // Only a real connect -> disconnect cycle closes a ride
                        // or moves the parking spot. Status 147 means the
                        // connection never happened, so neither may change.
                        stopGpsSpeed()
                        saveParkedLocation(force = true)
                        RideStore.onDisconnected()
                        // Show the accumulated carbon saving on the cluster.
                        scope.launch { write(buildCarbonFrame(RideStore.carbonSavedKg.toInt())) }
                        // No telemetry means no engine data - make that explicit.
                        ClusterRepository.warmup.value = WarmupState(message = "Engine off")
                        // Keep syncing for two minutes after the link drops.
                        disconnectedAtMs = System.currentTimeMillis()
                    }
                    if (!manualDisconnect) {
                        // Clear the foreground notification only after a long
                        // disconnect, so a brief drop does not lose the link UI.
                        clearNotification?.let { main.removeCallbacks(it) }
                        clearNotification = Runnable {
                            runCatching {
                                getSystemService(NotificationManager::class.java)?.cancel(1)
                            }
                            clearNotification = null
                        }.also { main.postDelayed(it, 5 * 60_000L) }
                    }
                    startRideSync()
                    ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                        connected = false,
                        error = if (manualDisconnect) null
                            else describeLinkError(status, wasEstablished),
                    )
                    updateNotification()
                    writeChar = null
                    discoveryStarted = false
                    keepaliveJob?.cancel()
                    keepaliveJob = null
                    navJob?.cancel()
                    navJob = null
                    if (g === gatt) gatt = null
                    runCatching { g.close() }
                    if (!manualDisconnect) scheduleReconnect()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU=$mtu status=$status")
            startDiscovery(g)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.i(TAG, "services discovered, status=$status, count=${g.services?.size ?: 0}")
            g.services?.forEach { s ->
                Log.d(TAG, "  service ${s.uuid} chars=${s.characteristics.size}")
            }
            val service = g.getService(UUID.fromString(Gatt.SERVICE))
                ?: g.services?.firstOrNull {
                    it.getCharacteristic(UUID.fromString(Gatt.WRITE)) != null &&
                        it.getCharacteristic(UUID.fromString(Gatt.READ)) != null
                }
            val ch = service?.getCharacteristic(UUID.fromString(Gatt.WRITE))
            if (ch == null) {
                ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                    error = "No SmartXonnect service on cluster")
                Log.w(TAG, "write characteristic ${Gatt.WRITE} not found")
                return
            }
            Log.i(TAG, "write characteristic found - starting keepalive")
            writeChar = ch
            val notify = service.getCharacteristic(UUID.fromString(Gatt.READ))
            if (notify != null) {
                g.setCharacteristicNotification(notify, true)
                val cccd = notify.getDescriptor(UUID.fromString(Gatt.CCCD))
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }
            }
            startKeepalive()
            startNavigationBridge()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            FrameLog.record("RX", value)
            decodeFrame(value, ClusterRepository.telemetry.value)?.let {
                ClusterRepository.telemetry.value = it
                onTelemetryFrame(it, primary = (value[1].toInt() and 0xFF) == 0x10, raw = value)
            }
        }

        @Deprecated("API 33+ overload used above")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val v = characteristic.value ?: return
            FrameLog.record("RX", v)
            decodeFrame(v, ClusterRepository.telemetry.value)?.let {
                ClusterRepository.telemetry.value = it
                onTelemetryFrame(it, primary = (v[1].toInt() and 0xFF) == 0x10, raw = v)
            }
        }
    }

    /** Phone battery percentage, or -1 when unavailable. */
    private fun phoneBatteryPct(): Int {
        val i = runCatching {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    /** Cellular signal as 0..4 bars, or -1 when unavailable. */
    @SuppressLint("MissingPermission")
    private fun phoneSignalBars(): Int {
        val allowed =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!allowed) return 4 // phone has service; avoid sending an empty bar
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 4
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return 4
        val dbm = runCatching {
            tm.allCellInfo?.mapNotNull { it.cellSignalStrength?.dbm }?.filter { it > -120 }?.maxOrNull()
        }.getOrNull() ?: return 4
        return when {
            dbm >= -80 -> 4
            dbm >= -95 -> 3
            dbm >= -105 -> 2
            dbm >= -115 -> 1
            else -> 0
        }
    }

    private fun startKeepalive() {
        if (keepaliveJob?.isActive == true) return
        keepaliveJob = scope.launch {
            while (isActive) {
                // Real phone values stop the cluster flipping between
                // "full signal / full battery" and "no signal / low battery"
                // every time our keepalive arrived with zeros.
                write(buildKeepalive(
                    batteryPct = phoneBatteryPct(),
                    signalBars = phoneSignalBars(),
                    overspeedLimit = if (ClusterRepository.highSpeedAlert.value)
                        ClusterRepository.highSpeedAlertSpeed.value else 120,
                ))
                delay(2000)
            }
        }
    }

    /**
     * Keep every app detail in the cloud: rides (including graphs), all petrol
     * fills, and the one JSON document of settings and cumulative counters.
     * Runs at a slow cadence while parked so sign-in without a bike still syncs.
     */
    private fun startRideSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                val token = SupabaseClient.currentToken()
                if (!token.isNullOrBlank()) {
                    RideStore.unsyncedRides().take(5).forEach { ride ->
                        if (SupabaseClient.uploadRide(ride, token) == null) {
                            RideStore.markSynced(ride.startedAt)
                        }
                    }
                    SupabaseClient.uploadFuelLogs(FuelLogStore.fills.value, token)
                    SupabaseClient.uploadAppState(buildAppStateJson(), token)
                }
                delay(if (ClusterRepository.telemetry.value.connected) 30_000L else 120_000L)
            }
            syncJob = null
        }
    }

    /** Settings, cumulative counters and last-known readings sent as JSONB. */
    private fun buildAppStateJson(): JSONObject {
        val o = JSONObject()
        o.put("carbon_seconds", RideStore.issSavedSeconds.value)
        o.put("all_time_top_kmh", RideStore.allTimeTopSpeed.value)
        val last = RideStore.lastReading.value
        o.put("odometer_km", last.odometerKm)
        o.put("fuel_bars", last.fuelBars)
        o.put("range_km", last.distanceToEmptyKm)
        o.put("economy_kmpl", last.averageFuelEconomy)
        o.put("battery_voltage", last.batteryVoltage)
        o.put("engine_temp_c", last.engineTempC)
        RideStore.parkedLocation.value?.let { p ->
            o.put("parked_lat", p.lat)
            o.put("parked_lng", p.lng)
            o.put("parked_at_ms", p.atMs)
        }
        o.put("navigation_mirroring", ClusterRepository.mirroring.value)
        o.put("phone_mirroring", ClusterRepository.phoneMirroring.value)
        o.put("high_speed_alert", ClusterRepository.highSpeedAlert.value)
        o.put("high_speed_limit", ClusterRepository.highSpeedAlertSpeed.value)
        return o
    }

    /**
     * navigation bridge: one instruction in, two cluster frames out.
     * The control frame is a liveness signal, so it is re-sent every 1.5 s
     * for as long as a route is active. The text row alternates nothing -
     * the cluster renders distance itself from the control frame.
     */
    private fun startNavigationBridge() {
        if (navJob?.isActive == true) return
        navJob = scope.launch {
            while (isActive) {
                // Personal correction from real rides; neutral until 5 rides
                // carry a Maps ETA, and always bounded.
                val etaFactor = RideAnalytics.etaFactor(RideStore.rides.value)
                // A voice reply owns the cluster text row even when no route is
                // active, so answers appear on the bike and not only on the phone.
                val reply = ClusterRepository.currentReplyText()
                if (reply != null) write(buildNavText(reply))
                if (ClusterRepository.mirroring.value) {
                    ClusterRepository.navInstruction.value?.let { step ->
                        val distance = step.distanceMeters.coerceAtLeast(0)
                        val remaining = step.remainingMeters.coerceAtLeast(0)
                        val eta = if (step.etaMinutes > 0)
                            RideAnalytics.adjustedEta(step.etaMinutes, etaFactor)
                        else step.etaMinutes
                        write(buildNavControl(
                            distance,
                            eta,
                            remaining,
                            step.pictogram,
                        ))
                        if (reply == null) write(buildNavText(step.displayLine))
                    }
                }
                delay(1500)
            }
        }
    }

    private suspend fun write(bytes: ByteArray) = writeLock.withLock {
        val g = gatt
        val ch = writeChar
        if (g == null || ch == null) return@withLock
        FrameLog.record("TX", bytes)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        } catch (e: SecurityException) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(error = e.message)
        } catch (e: Exception) {
            // A no-response write can fail transiently while the link is being
            // torn down. Log a pattern instead of crashing the keepalive loop.
            writeFailures++
            if (writeFailures % 5 == 0) {
                FrameLog.note("TX failures=$writeFailures (${e.message})")
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            "cluster", "Cluster link", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pending = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, com.jupiterconnect.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val t = ClusterRepository.telemetry.value
        val err = t.error
        val (title, text) = if (t.connected) {
            "Jupiter Connect" to if (t.address.isNotBlank())
                "Connected ┬╖ ${t.address}" else "Connected to cluster"
        } else {
            "Jupiter Connect" to if (!err.isNullOrBlank()) err
            else "Disconnected ┬╖ waiting for cluster"
        }
        return NotificationCompat.Builder(this, "cluster")
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(t.connected)
            .build()
    }

    /** Re-post the notification so its title/text follow the link state. */
    private fun updateNotification() {
        val t = ClusterRepository.telemetry.value
        val key = "${t.connected}|${t.address}|${t.error}"
        // Only post when the state actually changes - never spam the shade.
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(1, buildNotification())
        }
    }

    override fun onDestroy() {
        clearNotification?.let { main.removeCallbacks(it) }
        disconnectNow()
        voiceBridge.destroy()
        ClusterRepository.service = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SCAN = "com.jupiterconnect.action.SCAN"
        const val ACTION_CONNECT = "com.jupiterconnect.action.CONNECT"
        const val EXTRA_ADDRESS = "com.jupiterconnect.extra.ADDRESS"

        private const val TAG = "JupiterBle"
        private const val SCAN_TIMEOUT_MS = 20_000L
        private const val RUNNING_RPM = 500
        // HCI supervision timeout: an established link really dropped.
        private const val GATT_CONN_TIMEOUT = 8
        // Android 15+: a direct connect attempt timed out (never connected).
        private const val GATT_CONNECTION_TIMEOUT = 147
        private const val GATT_CONN_TERMINATE_PEER_USER = 19
        /** Backoff for auto-reconnect: 5s, 10s, 20s, then 30s between tries. */
        private val RECONNECT_DELAYS = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L)
        /** Matches `TVS BT...` and `TVSBT...` - the advertised name is inferred
         * from community pairing reports, so both spellings are accepted. */
        private val CLUSTER_NAME_RE = Regex("""\bTVS\s*BT""", RegexOption.IGNORE_CASE)
        private const val SCAN_FAILED_ALREADY_STARTED = 1
        private const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        private const val SCAN_FAILED_INTERNAL_ERROR = 3
        private const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
    }
}
