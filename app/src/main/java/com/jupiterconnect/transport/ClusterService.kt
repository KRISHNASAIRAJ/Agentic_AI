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
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jupiterconnect.R
import com.jupiterconnect.protocol.Gatt
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.protocol.buildKeepalive
import com.jupiterconnect.protocol.buildNavControl
import com.jupiterconnect.protocol.buildNavText
import com.jupiterconnect.protocol.decodeFrame
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
    val devices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val scanning = MutableStateFlow(false)
    val navInstruction = MutableStateFlow<NavInstruction?>(null)
    val mirroring = MutableStateFlow(true)
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
        createChannel()
        ClusterRepository.service = this
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (hasBluetoothPermission()) startScan()
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
        if (!hasBluetoothPermission()) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        ClusterRepository.scanning.value = true
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val r = result ?: return
                val name = r.device.name ?: return
                if (!name.startsWith("TVSBT", ignoreCase = true)) return
                val entry = r.device.address to name
                val current = ClusterRepository.devices.value.toMutableList()
                if (current.none { it.first == entry.first }) {
                    current.add(entry)
                    ClusterRepository.devices.value = current
                }
            }

            override fun onScanFailed(errorCode: Int) {
                ClusterRepository.scanning.value = false
                ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                    error = "Scan failed: $errorCode")
            }
        }
        scanCallback?.let { scanner.startScan(it) }
    }

    fun connectTo(address: String) {
        if (!hasBluetoothPermission()) return
        lastAddress = address
        scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        ClusterRepository.scanning.value = false
        if (gatt != null) return
        val device: BluetoothDevice = try {
            adapter?.getRemoteDevice(address) ?: return
        } catch (e: IllegalArgumentException) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = e.message)
            return
        }
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnectNow() {
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
                    gatt = g
                    ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                        connected = true, address = g.device.address, error = null)
                    if (!g.requestMtu(Gatt.REQUESTED_MTU)) startDiscovery(g)
                    main.postDelayed({ startDiscovery(g) }, 2500L)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
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
            startDiscovery(g)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(UUID.fromString(Gatt.SERVICE))
                ?: g.services?.firstOrNull {
                    it.getCharacteristic(UUID.fromString(Gatt.WRITE)) != null &&
                        it.getCharacteristic(UUID.fromString(Gatt.READ)) != null
                }
            val ch = service?.getCharacteristic(UUID.fromString(Gatt.WRITE))
            if (ch == null) {
                ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                    error = "No SmartXonnect service on cluster")
                return
            }
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
            }
        }

        @Deprecated("API 33+ overload used above")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val v = characteristic.value ?: return
            decodeFrame(v, ClusterRepository.telemetry.value)?.let {
                ClusterRepository.telemetry.value = it
            }
        }
    }

    private fun startKeepalive() {
        if (keepaliveJob?.isActive == true) return
        keepaliveJob = scope.launch {
            while (isActive) {
                write(buildKeepalive())
                delay(2000)
            }
        }
    }

    /**
     * navigation bridge: one instruction in, two cluster frames out.
     * The control frame is a liveness signal, so it is re-sent every 1.5 s
     * for as long as a route is active. The text row alternates nothing —
     * the cluster renders distance itself from the control frame.
     */
    private fun startNavigationBridge() {
        if (navJob?.isActive == true) return
        navJob = scope.launch {
            while (isActive) {
                if (ClusterRepository.mirroring.value) {
                    ClusterRepository.navInstruction.value?.let { step ->
                        write(buildNavControl(
                            step.distanceMeters.coerceAtLeast(0),
                            step.etaMinutes,
                            0,
                            step.pictogram,
                        ))
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
}
