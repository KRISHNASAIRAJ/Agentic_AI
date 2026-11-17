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
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jupiterconnect.R
import com.jupiterconnect.protocol.Gatt
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.buildKeepalive
import com.jupiterconnect.protocol.buildNavControl
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
    // Recent live speeds for the speedometer graph (oldest first).
    val speedHistory = MutableStateFlow<List<Int>>(emptyList())
    val mirroring = MutableStateFlow(true)

    private const val SPEED_HISTORY_SIZE = 48
    fun recordSpeed(speedKmh: Int) {
        val current = speedHistory.value
        val next = if (current.lastOrNull() == speedKmh) current else (current + speedKmh).takeLast(SPEED_HISTORY_SIZE)
        speedHistory.value = next
    }
    var service: ClusterService? = null

    fun scan() = service?.startScan()
    fun connect(address: String) = service?.connectTo(address)
    fun disconnect() = service?.disconnectNow()
}

/** All BLE entry points are guarded by [hasBluetoothPermission] at runtime. */
@SuppressLint("MissingPermission")
class ClusterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeLock = Mutex()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var discoveryStarted = false
    @Volatile private var lastAddress: String? = null
    private var keepaliveJob: Job? = null
    private var navJob: Job? = null
    @SuppressLint("MissingPermission") private var scanCallback: ScanCallback? = null

    private val adapter: BluetoothAdapter?
        get() = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun onCreate() {
        super.onCreate()
        RideStore.init(applicationContext)
        createChannel()
        ClusterRepository.service = this
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasBluetoothPermission()) return START_STICKY
        // Always handle the requested action HERE, in onStartCommand, which is
        // guaranteed to run after onCreate has set ClusterRepository.service.
        // Launching the service and then posting scan()/connect() to the main
        // thread raced with onCreate and silently dropped the action when the
        // service reference was still null — "Bluetooth not connecting".
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

    fun connectTo(address: String) {
        if (!hasBluetoothPermission()) return
        lastAddress = address
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

    /** Save the last known GPS fix as the parking spot when the link drops. */
    @SuppressLint("MissingPermission")
    private fun saveParkedLocation() {
        val locationAllowed =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!locationAllowed) return
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
        best?.let { RideStore.saveParkedLocation(it.latitude, it.longitude, it.time) }
    }

    fun disconnectNow() {
        saveParkedLocation()
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
                    RideStore.onConnected()
                    gatt = g
                    ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                        connected = true, address = g.device.address, error = null)
                    if (!g.requestMtu(Gatt.REQUESTED_MTU)) startDiscovery(g)
                    main.postDelayed({ startDiscovery(g) }, 2500L)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT disconnected, status=$status")
                    saveParkedLocation()
                    RideStore.onDisconnected()
                    ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                        connected = false,
                        error = if (status != 0) "Link lost (status $status)" else null,
                    )
                    writeChar = null
                    discoveryStarted = false
                    keepaliveJob?.cancel()
                    keepaliveJob = null
                    navJob?.cancel()
                    navJob = null
                    if (g === gatt) gatt = null
                    runCatching { g.close() }
                    lastAddress?.let { addr ->
                        scope.launch {
                            delay(5000)
                            if (gatt == null) connectTo(addr)
                        }
                    }
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
            decodeFrame(value, ClusterRepository.telemetry.value)?.let {
                ClusterRepository.telemetry.value = it
                RideStore.onFrame(it)
                ClusterRepository.recordSpeed(it.speedKmh)
            }
        }

        @Deprecated("API 33+ overload used above")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val v = characteristic.value ?: return
            decodeFrame(v, ClusterRepository.telemetry.value)?.let {
                ClusterRepository.telemetry.value = it
                RideStore.onFrame(it)
                ClusterRepository.recordSpeed(it.speedKmh)
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
        if (!allowed) return -1
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return -1
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return -1
        val dbm = runCatching {
            tm.allCellInfo?.mapNotNull { it.cellSignalStrength?.dbm }?.filter { it > -120 }?.maxOrNull()
        }.getOrNull() ?: return -1
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
                ))
                delay(2000)
            }
        }
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
                if (ClusterRepository.mirroring.value) {
                    ClusterRepository.navInstruction.value?.let { step ->
                        // The cluster treats this field as plain metres: 0xFFFF
                        // rendered as "65.5km". There is no "unknown" sentinel.
                        // When Maps publishes no distance, send 0 so the arrow
                        // and ETA keep updating; the chip-text fallback in the
                        // listener should normally supply the real distance.
                        val distance = step.distanceMeters.coerceAtLeast(0)
                        // Remaining distance (the cluster's "km left" field).
                        val remaining = step.remainingMeters.coerceAtLeast(0)
                        write(buildNavControl(
                            distance,
                            step.etaMinutes,
                            remaining,
                            step.pictogram,
                        ))
                        // Send only the instruction text. The cluster renders
                        // the distance from the control frame; prefixing "0 m "
                        // here produced the wrong "0 m Head east on" row.
                        write(buildNavText(step.displayLine))
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
        return NotificationCompat.Builder(this, "cluster")
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("Jupiter Connect")
            .setContentText("Cluster link active")
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        disconnectNow()
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
        /** Matches `TVS BT...` and `TVSBT...` - the advertised name is inferred
         * from community pairing reports, so both spellings are accepted. */
        private val CLUSTER_NAME_RE = Regex("""\bTVS\s*BT""", RegexOption.IGNORE_CASE)
        private const val SCAN_FAILED_ALREADY_STARTED = 1
        private const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        private const val SCAN_FAILED_INTERNAL_ERROR = 3
        private const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
    }
}
