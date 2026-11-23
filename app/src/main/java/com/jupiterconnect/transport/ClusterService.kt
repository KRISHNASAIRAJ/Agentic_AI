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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.annotation.SuppressLint
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.provider.CallLog
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jupiterconnect.R
import com.jupiterconnect.debug.FeatureVerification
import com.jupiterconnect.debug.FeatureVerification.Status
import com.jupiterconnect.debug.FrameCatalog
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.protocol.ClusterControlFrames
import com.jupiterconnect.protocol.Gatt
import com.jupiterconnect.protocol.InboundId
import com.jupiterconnect.protocol.ScannedDevice
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.session.CloudGarageStore
import com.jupiterconnect.session.ColdStartTracker
import com.jupiterconnect.session.JUPITER_VEHICLE
import com.jupiterconnect.session.RideAnalytics
import com.jupiterconnect.session.SpeedFusion
import com.jupiterconnect.session.WarmupPhase
import com.jupiterconnect.session.WarmupState
import com.jupiterconnect.sync.SupabaseClient
import com.jupiterconnect.sync.shouldReportOdo
import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.profile.ClusterImageTransfer
import com.jupiterconnect.protocol.buildKeepalive
import com.jupiterconnect.protocol.buildBrightnessFrame
import com.jupiterconnect.protocol.buildLocationFrame
import com.jupiterconnect.protocol.buildNavControl
import com.jupiterconnect.protocol.buildScreenSwitch
import com.jupiterconnect.protocol.buildSocialCounts
import com.jupiterconnect.protocol.buildNavText
import com.jupiterconnect.protocol.buildCallerFrame
import com.jupiterconnect.protocol.buildCallerPackets
import com.jupiterconnect.protocol.buildMessageFrame
import com.jupiterconnect.protocol.buildMessageRow
import com.jupiterconnect.protocol.decodeFrame
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.traffic.Cities
import com.jupiterconnect.traffic.TrafficClient
import com.jupiterconnect.traffic.TrafficScreen
import com.jupiterconnect.traffic.buildCricketFrame
import com.jupiterconnect.traffic.buildMarketFrame
import com.jupiterconnect.traffic.buildNewsFrame
import com.jupiterconnect.traffic.buildWeatherFrameWithCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Shared state between the foreground service and Compose UI. */
object ClusterRepository {
    private const val PREFS = "jupiter_prefs"
    private const val KEY_RIDER_NAME = "rider_name"
    private const val KEY_RIDER_NAME_ON = "rider_name_on"
    private const val KEY_WEATHER_CITY = "weather_city"
    private const val KEY_ILLUMINATION = "cluster_illumination"
    private const val KEY_TSL = "cluster_tsl"
    private const val KEY_REGEN = "cluster_regen"
    private const val KEY_RIDE_MODE = "cluster_ride_mode"
    private const val KEY_SPEED_ALERT = "high_speed_alert"
    private const val KEY_SPEED_ALERT_LIMIT = "high_speed_alert_limit"
    private const val KEY_DRIVING_MODE = "driving_mode"
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    /** One-time init from the app or the service; reads persisted settings. */
    fun initPrefs(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        FeatureVerification.init(context)
        riderName.value = prefs?.getString(KEY_RIDER_NAME, "Rider") ?: "Rider"
        riderNameEnabled.value = prefs?.getBoolean(KEY_RIDER_NAME_ON, true) ?: true
        weatherCity.value = prefs?.getString(KEY_WEATHER_CITY, Cities.HYDERABAD.name)
            ?: Cities.HYDERABAD.name
        val applied = AppliedSettings(
            illumination = prefs?.getInt(KEY_ILLUMINATION, 2) ?: 2,
            tsl = prefs?.getInt(KEY_TSL, 0) ?: 0,
            regen = prefs?.getInt(KEY_REGEN, 0) ?: 0,
            rideMode = prefs?.getInt(KEY_RIDE_MODE, 0) ?: 0,
        )
        appliedSettings.value = applied
        // The draft starts equal to what was last applied: no phantom "unsaved"
        // state, and never a chip selection that silently changes the cluster.
        illumination.value = applied.illumination
        tslSetting.value = applied.tsl
        regenSetting.value = applied.regen
        rideMode.value = applied.rideMode
        highSpeedAlert.value = prefs?.getBoolean(KEY_SPEED_ALERT, false) ?: false
        highSpeedAlertSpeed.value = prefs?.getInt(KEY_SPEED_ALERT_LIMIT, 60) ?: 60
        drivingMode.value = prefs?.getBoolean(KEY_DRIVING_MODE, false) ?: false
        ClusterImageTransfer.restore(context)
        riderPhotoReady.value = ClusterImageTransfer.hasImage
    }

    val riderName = MutableStateFlow("Rider")
    val riderNameEnabled = MutableStateFlow(true)
    val weatherCity = MutableStateFlow(Cities.HYDERABAD.name)
    val riderPhotoReady = MutableStateFlow(false)

    fun setRiderPhotoReady(ready: Boolean) { riderPhotoReady.value = ready }

    fun setRiderName(value: String) {
        riderName.value = value.take(17)
        prefs?.edit()?.putString(KEY_RIDER_NAME, riderName.value)?.apply()
    }

    fun setRiderNameEnabled(value: Boolean) {
        riderNameEnabled.value = value
        prefs?.edit()?.putBoolean(KEY_RIDER_NAME_ON, value)?.apply()
    }

    fun setWeatherCity(name: String) {
        weatherCity.value = name
        prefs?.edit()?.putString(KEY_WEATHER_CITY, name)?.apply()
    }

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

    /** Persist the speed-alert switch. Turning it off clears any active alert. */
    fun setHighSpeedAlert(enabled: Boolean) {
        highSpeedAlert.value = enabled
        prefs?.edit()?.putBoolean(KEY_SPEED_ALERT, enabled)?.apply()
        if (!enabled) service?.clearSpeedAlert()
    }

    /** Persist the threshold (20-100 km/h), matching the settings UI range. */
    fun setHighSpeedAlertSpeed(limit: Int) {
        val v = limit.coerceIn(20, 100)
        highSpeedAlertSpeed.value = v
        prefs?.edit()?.putInt(KEY_SPEED_ALERT_LIMIT, v)?.apply()
        // If the rider lowers the limit below the current speed, re-evaluate on
        // the next telemetry frame instead of waiting for another crossing.
        service?.rearmSpeedAlert()
    }

    /** Fire one alert now, for the "Test" button in Settings. */
    fun testSpeedAlert() = service?.testSpeedAlert()

    /**
     * Off by default: BLE scanning, connecting and the foreground notification
     * only happen while the rider explicitly turns on driving mode. Bluetooth
     * can stay on for AirPods and other devices without this app draining the
     * battery.
     */
    val drivingMode = MutableStateFlow(false)

    fun setDrivingMode(enabled: Boolean) {
        if (drivingMode.value == enabled) return
        drivingMode.value = enabled
        prefs?.edit()?.putBoolean(KEY_DRIVING_MODE, enabled)?.apply()
        val ctx = appContext ?: return
        val i = Intent(ctx, ClusterService::class.java)
            .setAction(ClusterService.ACTION_DRIVING_MODE)
            .putExtra(ClusterService.EXTRA_DRIVING_ENABLED, enabled)
        runCatching {
            if (enabled) {
                i.putExtra(ClusterService.EXTRA_FOREGROUND, true)
                ContextCompat.startForegroundService(ctx, i)
            } else {
                ctx.startService(i)
            }
        }
    }

    /** Start the service the right way for the current driving-mode state. */
    fun startService(context: Context, action: String? = null, address: String? = null) {
        val i = Intent(context, ClusterService::class.java)
        if (action != null) i.action = action
        if (address != null) i.putExtra(ClusterService.EXTRA_ADDRESS, address)
        if (drivingMode.value) {
            i.putExtra(ClusterService.EXTRA_FOREGROUND, true)
            ContextCompat.startForegroundService(context, i)
        } else {
            runCatching { context.startService(i) }
        }
    }

    val socialCounts = MutableStateFlow(Triple(0, 0, 0))
    // Genuine APK packs WhatsApp/Facebook as chats + messages (5).
    data class SocialSplit(
        val waChats: Int = 0, val waMsgs: Int = 0,
        val fbChats: Int = 0, val fbMsgs: Int = 0, val instagram: Int = 0,
    )
    val socialSplit = MutableStateFlow(SocialSplit())

    /** Last raw service-reminder byte from the 0x11 frame. */
    val serviceReminderCode = MutableStateFlow(0)

    /** Draft values shown in the More screen. Tapping a chip only changes these. */
    val illumination = MutableStateFlow(2)
    val tslSetting = MutableStateFlow(0)
    val regenSetting = MutableStateFlow(0)
    val rideMode = MutableStateFlow(0)

    /** Values actually sent on the last Apply, re-sent once per ignition. */
    data class AppliedSettings(
        val illumination: Int = 2,
        val tsl: Int = 0,
        val regen: Int = 0,
        val rideMode: Int = 0,
    )
    val appliedSettings = MutableStateFlow(AppliedSettings())
    val selectedTrip = MutableStateFlow(0)
    val settingsStatus = MutableStateFlow("Nothing applied yet")
    /** Feedback for the Find-tab Refresh location button. */
    val parkedStatus = MutableStateFlow("")
    val handshakeStatus = MutableStateFlow("Setup data not sent yet")

    fun addSocialSplit(pkg: String) {
        val s = socialSplit.value
        socialSplit.value = when {
            pkg.startsWith("com.whatsapp") -> s.copy(waMsgs = s.waMsgs + 1)
            pkg.startsWith("com.facebook") || pkg == "com.facebook.orca" ->
                s.copy(fbMsgs = s.fbMsgs + 1)
            pkg.startsWith("com.instagram") -> s.copy(instagram = s.instagram + 1)
            else -> return
        }
    }

    fun clearSocialSplit() { socialSplit.value = SocialSplit() }

    /**
     * Chip taps only change the local draft. No BLE write, no persistence:
     * the cluster keeps showing the last applied values until Apply is pressed.
     */
    fun setIllumination(level: Int) { illumination.value = level.coerceIn(1, 5) }
    fun setTsl(code: Int) { tslSetting.value = code }
    fun setRegen(level: Int) { regenSetting.value = level.coerceIn(0, 3) }
    fun setRideMode(code: Int) { rideMode.value = code }

    fun discardSettingsChanges() {
        val a = appliedSettings.value
        illumination.value = a.illumination
        tslSetting.value = a.tsl
        regenSetting.value = a.regen
        rideMode.value = a.rideMode
        settingsStatus.value = "Unapplied changes discarded"
    }

    /** Called only after Apply has written the frames to the cluster. */
    fun markSettingsApplied() {
        val draft = AppliedSettings(
            illumination.value, tslSetting.value, regenSetting.value, rideMode.value)
        appliedSettings.value = draft
        prefs?.edit()
            ?.putInt(KEY_ILLUMINATION, draft.illumination)
            ?.putInt(KEY_TSL, draft.tsl)
            ?.putInt(KEY_REGEN, draft.regen)
            ?.putInt(KEY_RIDE_MODE, draft.rideMode)
            ?.apply()
    }

    fun setBrightness(level: Int) {
        val next = level.coerceIn(2, 5)
        brightness.value = next
        service?.sendBrightness(next)
    }
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
    /**
     * Debug flag for A/B testing against the dash: send the legacy 21-byte
     * 1/3/2 caller sequence instead of Jupiter's single 20-byte 0x43 frame.
     * Off by default — the single frame is what TVS Connect 8.14.3 uses for
     * Jupiter. No Bluetooth connection behaviour is affected by this flag.
     */
    val legacyCallerPackets = MutableStateFlow(false)

    /** Traffic screens (weather / cricket / market / news) with free APIs. */
    val trafficEnabled = MutableStateFlow(false)
    val trafficStatus = MutableStateFlow("Off")
    val trafficScreen = MutableStateFlow(-1)

    /** Show a short reply on the cluster, overriding the nav text briefly. */
    fun showText(text: String, holdMs: Long = 10_000L) {
        replyText = text
        replyUntilMs = System.currentTimeMillis() + holdMs
        service?.sendTextRow(text)
    }

    /**
     * Incoming calls and messages use their own full-screen cluster cards
     * (DID 0x43 / 0x53). They never touch the navigation row, so a WhatsApp
     * message cannot replace the current direction on the dash.
     */
    fun showPhoneEvent(
        isCall: Boolean,
        text: String,
        holdMs: Long = 5_000L,
        sourcePackage: String? = null,
    ) {
        // Social unread counters use the separate 0x9C frame, never the text row.
        if (!isCall) onSocialNotification(sourcePackage)
        service?.sendPhoneEvent(isCall, text, holdMs)
    }

    /**
     * TVS Connect increments a per-app unread counter for each notification.
     * The wire field widths are applied in buildSocialCounts (WA <=31, FB <=15).
     */
    fun onSocialNotification(sourcePackage: String?) {
        val pkg = sourcePackage.orEmpty()
        val (wa, fb, ig) = socialCounts.value
        val next = when {
            pkg.startsWith("com.whatsapp") -> Triple(wa + 1, fb, ig)
            pkg.startsWith("com.facebook.katana") || pkg == "com.facebook.orca" ->
                Triple(wa, fb + 1, ig)
            pkg.startsWith("com.instagram") -> Triple(wa, fb, ig + 1)
            else -> return
        }
        service?.sendSocialCounts(next.first, next.second, next.third)
        addSocialSplit(pkg)
        service?.sendSocialSplit(socialSplit.value)
    }

    /** Clear all social unread counters (the APK's clearRecords() equivalent). */
    fun clearSocialCounts() {
        socialCounts.value = Triple(0, 0, 0)
        clearSocialSplit()
        service?.sendSocialCounts(0, 0, 0)
        service?.sendSocialSplit(SocialSplit())
    }

    @Volatile private var replyText: String? = null
    @Volatile private var replyUntilMs = 0L

    /** Reply text to show in the nav row while the override is active. */
    fun currentReplyText(): String? =
        if (System.currentTimeMillis() < replyUntilMs) replyText else null

    // UI delegates. All are explicit user actions; none run automatically on
    // connect, so the Bluetooth link setup is unchanged.
    fun applyClusterSettings() = service?.applyClusterSettings()
    fun sendTrip(trip: Int, reset: Boolean) = service?.sendTrip(trip, reset)
    fun sendCalibrationRequest() = service?.sendCalibrationRequest()
    fun sendFollowMeTimer(minutes: Int) = service?.sendFollowMeTimer(minutes)
    fun sendSetupData() = service?.sendSetupData()
    fun sendRoadName(name: String) = service?.sendRoadName(name)
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
    // Last APPLIED settings are re-sent once when the ignition comes on. A
    // chip tap never writes to the cluster; only the Apply button does. Reset
    // on every ignition-off edge.
    @Volatile private var lastIgnitionOn = false
    @Volatile private var settingsAppliedThisIgnition = false
    // The foreground notification is only meaningful while Bluetooth is on.
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateForegroundForBluetooth()
            // Bluetooth was toggled back on - redial the remembered cluster so
            // the user never has to reopen the app to get the link back.
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON &&
                ClusterRepository.drivingMode.value &&
                !manualDisconnect && !lastAddress.isNullOrBlank() && gatt == null
            ) {
                FrameLog.note("BLUETOOTH ON - redial $lastAddress")
                connectTo(lastAddress!!)
            }
        }
    }
    private var bluetoothReceiverRegistered = false

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
    private var trafficJob: Job? = null
    private var mediaKeyJob: Job? = null
    private var phoneEventJob: Job? = null
    private var syncJob: Job? = null
    // Throttle for live odometer reports: re-send after 1 km or 30 s.
    private var lastReportedOdoKm = 0
    private var lastOdoReportAtMs = 0L
    private var reconnectJob: Job? = null
    private var connectWatchdog: Job? = null
    // A GATT connect attempted while bonding is still in progress fails with
    // 147 on some phones - the classic "connects only sometimes" symptom.
    private var bondWaitJob: Job? = null
    private var bondWaiter: BroadcastReceiver? = null
    private var reconnectAttempts = 0
    private var writeFailures = 0
    /** Set by an explicit disconnect so the auto-retry never fights the user. */
    @Volatile private var manualDisconnect = false
    private var disconnectedAtMs = 0L
    // Link diagnostics for the frame log: how long the link stayed up and when
    // the last cluster frame arrived, so a future drop can be explained.
    private var connectedAtMs = 0L
    private var lastFrameAtMs = 0L
    /**
     * Unread missed calls for the 0x4A keepalive (byte 10). TVS Connect reads
     * CallLog.Calls with type=MISSED and is_read=0, requires READ_CALL_LOG and
     * caps the value at 99. Reading it fresh each keepalive means a call the
     * user has since opened no longer shows as missed on the dash.
     */
    @SuppressLint("MissingPermission")
    private fun currentMissedCalls(): Int {
        // Never touch the call-log provider without a live link. This keeps the
        // Bluetooth-off path byte-for-byte identical to the previous build.
        if (gatt == null) return 0
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) return 0
        return runCatching {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.IS_READ}=?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0"),
                null,
            )?.use { it.count.coerceAtMost(99) } ?: 0
        }.getOrDefault(0)
    }
    // Remembered bike survives a service/process restart (OEM battery optimisers
    // love to kill background BLE services). Shares MainActivity's prefs so the
    // UI and the service always agree on the last linked cluster.
    private val linkPrefs by lazy { getSharedPreferences("jupiter_device", MODE_PRIVATE) }
    private var speedAlertActive = false
    private var lastSpeedAlertMs = 0L
    private var lastNotificationKey = ""
    /** True only after a successful startForeground(), so we never stop one we never started. */
    @Volatile private var foregroundActive = false
    private val speedFusion = SpeedFusion()
    @Volatile private var gpsSpeedMps = -1f
    @Volatile private var gpsAccuracyM = -1f
    private var gpsListener: LocationListener? = null
    // BLE callbacks are delivered on the main looper. Keep GPS callbacks on a
    // separate thread so a burst of fixes cannot delay them.
    private var gpsThread: HandlerThread? = null
    private val batteryHistory = ArrayDeque<Double>()
    private var lastAnomalyKey = ""
    @SuppressLint("MissingPermission") private var scanCallback: ScanCallback? = null

    private val adapter: BluetoothAdapter?
        get() = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun onCreate() {
        super.onCreate()
        voiceBridge = VoiceAssistantBridge(this)
        ClusterRepository.initPrefs(applicationContext)
        FrameLog.init(applicationContext)
        RideStore.init(applicationContext)
        SupabaseClient.init(applicationContext)
        coldStartTracker.setLastRunMs(RideStore.warmupLastRunMs)
        createChannel()
        ClusterRepository.service = this
        // Restore the last linked bike so a killed service can redial it on
        // restart instead of sitting idle with no address to connect to.
        lastAddress = linkPrefs.getString(KEY_LINK_ADDRESS, null)
        manualDisconnect = linkPrefs.getBoolean(KEY_USER_DISCONNECTED, false)
        runCatching<Unit> {
            registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            bluetoothReceiverRegistered = true
        }
        // Foreground promotion happens in onStartCommand, only when this start
        // was requested as a foreground service (driving mode on). Calling
        // stopForeground here when we were started with a plain startService()
        // used to kill the app as soon as Bluetooth was off.
        // Keep rides, fuel logs and app state synced even with no bike linked.
        startRideSync()
    }

    /** Foreground only while driving mode is on AND Bluetooth is powered. */
    private fun updateForegroundForBluetooth() {
        val wanted = ClusterRepository.drivingMode.value && adapter?.isEnabled == true
        if (wanted) {
            if (!foregroundActive) startForegroundSafe(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            updateNotification()
        } else if (foregroundActive) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundActive = false
            lastNotificationKey = ""
        }
    }

    /**
     * TVS Connect falls back from a typed foreground service to connectedDevice
     * only when an OEM rejects the requested type. We do the same so the link
     * survives devices that enforce foreground-service types strictly.
     */
    private fun startForegroundSafe(types: Int) {
        try {
            ServiceCompat.startForeground(this, 1, buildNotification(), types)
            foregroundActive = true
        } catch (e: SecurityException) {
            FrameLog.note("FOREGROUND typed start denied - retrying connectedDevice")
            runCatching {
                ServiceCompat.startForeground(
                    this, 1, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            }.onSuccess { foregroundActive = true }
                .onFailure { FrameLog.note("FOREGROUND fallback failed: ${it.message}") }
        } catch (e: Exception) {
            FrameLog.note("FOREGROUND start failed: ${e.message}")
        }
    }

    /** Partial wake lock while linked, matching TVS Connect's BleClusterSync. */
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JupiterConnect::BleClusterSync")
                ?.apply { setReferenceCounted(false) }
        }
        // Renewing the timeout each keepalive makes the lock self-healing.
        runCatching { wakeLock?.acquire(300_000L) }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasBluetoothPermission()) return START_STICKY
        // startForegroundService() REQUIRES a prompt startForeground() call even
        // if Bluetooth is off or driving mode was switched off in the meantime.
        // Skipping it is what made the app disappear the moment Bluetooth went
        // off. A plain startService() start never sets this extra.
        if (intent?.getBooleanExtra(EXTRA_FOREGROUND, false) == true ||
            ClusterRepository.drivingMode.value
        ) {
            if (!foregroundActive) {
                startForegroundSafe(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            }
        }
        // Always handle the requested action HERE, in onStartCommand, which is
        // guaranteed to run after onCreate has set ClusterRepository.service.
        // Launching the service and then posting scan()/connect() to the main
        // thread raced with onCreate and silently dropped the action when the
        // service reference was still null ΓÇö "Bluetooth not connecting".
        when (intent?.action) {
            ACTION_DRIVING_MODE ->
                onDrivingModeChanged(intent.getBooleanExtra(EXTRA_DRIVING_ENABLED, false))
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_ADDRESS)?.let { connectTo(it) }
                ?: startScan()
            ACTION_SCAN -> startScan()
            else -> {
                // Null intent = the system restarted a stuck service. Redial the
                // remembered cluster; a persisted user disconnect is respected.
                if (!manualDisconnect && !lastAddress.isNullOrBlank()) {
                    FrameLog.note("SERVICE RESTART - redial $lastAddress")
                    connectTo(lastAddress!!)
                } else {
                    startScan()
                }
            }
        }
        updateForegroundForBluetooth()
        return START_STICKY
    }

    /** Enter or leave the only state in which this app touches BLE. */
    private fun onDrivingModeChanged(enabled: Boolean) {
        FrameLog.note("DRIVING MODE ${if (enabled) "ON" else "OFF"}")
        if (!enabled) {
            // Park the scooter and stop every BLE loop before hiding the link.
            if (connectionEstablished || gatt != null || ClusterRepository.scanning.value) {
                disconnectNow()
            }
            scanCallback?.let { cb ->
                runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) }
                scanCallback = null
            }
            ClusterRepository.scanning.value = false
            reconnectJob?.cancel()
            reconnectJob = null
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(error = null)
        } else if (adapter?.isEnabled == true) {
            if (!lastAddress.isNullOrBlank()) connectTo(lastAddress!!) else startScan()
        }
        updateForegroundForBluetooth()
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
        if (!ClusterRepository.drivingMode.value) {
            // Bluetooth may be on for AirPods etc. - never scan unless the
            // rider explicitly enabled driving mode.
            FrameLog.note("SCAN skipped: driving mode off")
            return
        }
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
        linkPrefs.edit()
            .putString(KEY_LINK_ADDRESS, address)
            .putBoolean(KEY_USER_DISCONNECTED, false)
            .apply()
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        attemptConnect(address)
    }

    /** Low-level connect used by both user taps and the retry loop. */
    private fun attemptConnect(address: String) {
        if (!ClusterRepository.drivingMode.value) {
            FrameLog.note("CONNECT skipped: driving mode off")
            return
        }
        if (!hasBluetoothPermission()) return
        if (bondWaitJob?.isActive == true) {
            FrameLog.note("CONNECT ignored: waiting for pairing")
            return
        }
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
        // Some clusters refuse GATT until bonded, but a connect issued while
        // pairing is still running fails with 147. Finish pairing first.
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                runCatching { device.createBond() }
                Log.i(TAG, "createBond requested for $address")
            }
            connectWhenBonded(device, address)
            return
        }
        doConnect(device, address)
    }

    private fun doConnect(device: BluetoothDevice, address: String) {
        if (gatt != null) return
        FrameLog.note("CONNECT GATT $address")
        Log.i(TAG, "connectGatt -> $address")
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        armConnectWatchdog()
    }

    /** Wait for the pairing broadcast, with a hard timeout so we never stall. */
    private fun connectWhenBonded(device: BluetoothDevice, address: String) {
        finishBondWait()
        ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
            error = "Pairing with cluster...")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val extra: BluetoothDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent?.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                if (extra?.address != address) return
                when (intent?.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_NONE -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                        FrameLog.note("PAIR state=$state for $address - connecting")
                        finishBondWait()
                        doConnect(device, address)
                    }
                }
            }
        }
        bondWaiter = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
        }
        bondWaitJob = scope.launch {
            delay(15_000L)
            if (bondWaiter === receiver) {
                FrameLog.note("PAIR wait timed out - connecting anyway")
                finishBondWait()
                doConnect(device, address)
            }
        }
    }

    private fun finishBondWait() {
        bondWaitJob?.cancel()
        bondWaitJob = null
        bondWaiter?.let { runCatching { unregisterReceiver(it) } }
        bondWaiter = null
    }

    /**
     * A direct connect (autoConnect = false) gets exactly one attempt. Some
     * phones never deliver a STATE_CONNECTED *or* a STATE_DISCONNECTED callback
     * when the cluster is momentarily unreachable, leaving [gatt] non-null so
     * every later retry bails out on the `gatt != null` guard. This watchdog is
     * armed only while a connect is pending - it can never tear down a live
     * link, unlike the old "no frames for 25s" force-reconnect that was reverted.
     */
    private fun armConnectWatchdog() {
        connectWatchdog?.cancel()
        connectWatchdog = scope.launch {
            delay(CONNECT_WATCHDOG_MS)
            if (connectionEstablished || gatt == null) return@launch
            recoverStalledConnect()
        }
    }

    /**
     * A bare close() on Android can leave a half-open GATT client behind; the
     * next connectGatt then fails with 147 "sometimes, but not always". Always
     * ask the stack to disconnect first, and close only after it has settled.
     * The DISCONNECTED callback still closes its own Gatt immediately - that is
     * the one place where Android has already finished the teardown.
     */
    private fun closeGattSafely(g: BluetoothGatt?, disconnectFirst: Boolean) {
        if (g == null) return
        if (disconnectFirst) runCatching { g.disconnect() }
        main.postDelayed({ runCatching { g.close() } }, if (disconnectFirst) 750L else 0L)
    }

    /** Clear a connect that never came up and hand control back to the backoff loop. */
    private fun recoverStalledConnect() {
        FrameLog.note("CONNECT STALLED ${CONNECT_WATCHDOG_MS / 1000}s - dropping and retrying")
        val g = gatt
        gatt = null
        writeChar = null
        connectionEstablished = false
        closeGattSafely(g, disconnectFirst = true)
        connectWatchdog?.cancel()
        connectWatchdog = null
        keepaliveJob?.cancel()
        keepaliveJob = null
        navJob?.cancel()
        navJob = null
        trafficJob?.cancel()
        trafficJob = null
        mediaKeyJob?.cancel()
        mediaKeyJob = null
        releaseWakeLock()
        ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
            connected = false, error = "Link stalled - reconnecting")
        updateNotification()
        scheduleReconnect()
    }

    /**
     * Retry a lost link with capped exponential backoff. A failed attempt
     * (status 147) and a real drop (status 8) both land here, but a manual
     * disconnect or Bluetooth-off never does - no endless retry loop.
     */
    private fun scheduleReconnect() {
        val address = lastAddress ?: return
        if (!ClusterRepository.drivingMode.value) return
        if (manualDisconnect || reconnectJob?.isActive == true) return
        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(
                error = "Bluetooth is off - turn it on to reconnect")
            return
        }
        // A connect attempt that never reported back leaves gatt non-null and
        // the `gatt != null` guard below would skip every retry forever. Close
        // that stale object, but only when no live link was ever established.
        if (gatt != null && !connectionEstablished) {
            val stale = gatt
            gatt = null
            writeChar = null
            closeGattSafely(stale, disconnectFirst = true)
            FrameLog.note("Dropped stale unconnected GATT before redial")
        }
        connectWatchdog?.cancel()
        connectWatchdog = null
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

    /**
     * Over-speed detection, mirroring TVS Connect: the phone watches the speed
     * reported by the cluster, fires once when it crosses the limit, and only
     * re-arms after the rider drops clearly below it. Genuine APK behaviour is
     * `speed >= limit` with the flag reset below the limit (U368AlertHelper).
     */
    private fun checkSpeedAlert(t: Telemetry) {
        if (!t.connected || !ClusterRepository.highSpeedAlert.value) {
            if (speedAlertActive) clearSpeedAlert()
            return
        }
        val limit = ClusterRepository.highSpeedAlertSpeed.value
        if (speedAlertActive) {
            // One-shot: stay silent until the bike drops below the limit.
            if (t.speedKmh < limit - SPEED_ALERT_HYSTERESIS) speedAlertActive = false
            return
        }
        if (t.speedKmh <= 0 || t.speedKmh < limit) return
        // Debounce BEFORE latching: a skipped frame must stay un-fired so the
        // next frame can alert. Latching first silently swallowed a real
        // re-crossing that happened within 2 s of the previous alert.
        val now = System.currentTimeMillis()
        if (now - lastSpeedAlertMs < 2_000L) return
        speedAlertActive = true
        lastSpeedAlertMs = now
        fireSpeedAlert(t.speedKmh, limit)
    }

    /**
     * Send the genuine `5B 4C` "HIGH SPEED ALERT" message frame, hold the same
     * warning on the cluster's text row, vibrate and post a status-bar alert.
     * The 0x4A keepalive is deliberately untouched - writing its byte 3 used to
     * drop the BLE link, so all detection stays in the app.
     */
    private fun fireSpeedAlert(speed: Int, limit: Int) {
        val frame = ClusterControlFrames.buildHighSpeedAlertFrame()
        scope.launch { write(frame) }
        FrameLog.note("SPEED ALERT $speed >= $limit frame=${hex(frame)}")
        // Held text survives the navigation bridge's next 1.5 s refresh; a bare
        // 0x4F write is overwritten before the rider can read it.
        ClusterRepository.showText("HIGH SPEED $speed km/h", holdMs = 5_000L)
        val vibrator = getSystemService(android.os.Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(180, 180))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(180L)
        }
        postSpeedAlertNotification(speed, limit)
        FeatureVerification.log("Speed alert", Status.APPLIED,
            "$speed >= $limit km/h", hex(frame))
    }

    /** Clear the one-shot flag and dismiss the phone notification. */
    fun clearSpeedAlert() {
        speedAlertActive = false
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIF_SPEED_ALERT)
        }
    }

    /** Re-evaluate immediately after the rider changes the threshold. */
    fun rearmSpeedAlert() {
        speedAlertActive = false
        checkSpeedAlert(ClusterRepository.telemetry.value)
    }

    /** Settings "Test" button: fire the alert without waiting for a crossing. */
    fun testSpeedAlert() {
        val limit = ClusterRepository.highSpeedAlertSpeed.value
        fireSpeedAlert(limit + 1, limit)
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
        val thread = HandlerThread("JupiterGps").also { it.start() }
        gpsThread = thread
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
            runCatching<Unit> {
                manager.requestLocationUpdates(p, 2_000L, 0f, listener, thread.looper)
            }
        }
    }

    private fun stopGpsSpeed() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        gpsListener?.let { runCatching { manager.removeUpdates(it) } }
        gpsListener = null
        gpsThread?.let { runCatching { it.quitSafely() } }
        gpsThread = null
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

    /**
     * Re-send the values from the last Apply once per ignition cycle. Trip
     * resets and one-shot setup frames are deliberately NOT part of this.
     * Unapplied chip selections are ignored here by design.
     */
    private fun applySavedSettingsOnIgnition() {
        settingsAppliedThisIgnition = true
        val a = ClusterRepository.appliedSettings.value
        scope.launch {
            write(ClusterControlFrames.buildVehicleControlFrame(
                illumination = a.illumination, tsl = a.tsl))
            write(ClusterControlFrames.buildRegenFrame(a.regen))
            if (a.rideMode != 0) {
                write(ClusterControlFrames.buildRideModeFrame(
                    a.rideMode, a.illumination, a.tsl))
            }
        }
        FrameLog.note("IGNITION APPLY $a")
        FeatureVerification.log(
            "Ignition auto-apply", Status.APPLIED,
            "illumination ${a.illumination}, TSL ${a.tsl}, " +
                "regen ${a.regen}, mode ${a.rideMode}")
    }

    /** Fan out one decoded frame to rides, voice, speed graph and warm-up. */
    private fun onTelemetryFrame(t: Telemetry, primary: Boolean, raw: ByteArray) {
        if (t.ignitionOn && !lastIgnitionOn && !settingsAppliedThisIgnition) {
            FrameLog.note("IGNITION ON - applying saved cluster settings")
            applySavedSettingsOnIgnition()
        }
        if (!t.ignitionOn) settingsAppliedThisIgnition = false
        lastIgnitionOn = t.ignitionOn
        if ((raw[1].toInt() and 0xFF) == 0x10) {
            voiceBridge.onSwitchAction(raw[12].toInt() and 0x3F)
        }
        if ((raw[1].toInt() and 0xFF) == InboundId.SERVICE) {
            val previous = ClusterRepository.serviceReminderCode.value
            ClusterRepository.serviceReminderCode.value = t.serviceReminderCode
            if (t.serviceReminderCode != previous) {
                FeatureVerification.log(
                    "Service reminder RX", Status.WORKING,
                    if (t.serviceReminderCode == 0) "cleared by cluster"
                    else "cluster reports code ${t.serviceReminderCode}")
            }
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
     * Capture the parking spot the instant the link drops. Save the best cached
     * fix immediately (so a drop always leaves a spot), then try to improve it
     * with a fresh fix for up to 5 s. The saved time is the disconnect time, not
     * the GPS fix time, so "Saved" stays accurate. [connectionEstablished] keeps
     * the spot frozen until the next real connect/disconnect cycle.
     */
    @SuppressLint("MissingPermission")
    private fun saveParkedLocation(
        force: Boolean = false,
        preserveAtMs: Long = 0L,
        reportStatus: Boolean = false,
    ) {
        if (!force && !connectionEstablished) return
        connectionEstablished = false
        val epoch = ++parkingEpoch
        fun report(msg: String) {
            FrameLog.note("PARKED $msg")
            if (reportStatus) ClusterRepository.parkedStatus.value = msg
        }
        val locationAllowed =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!locationAllowed) {
            report("no location permission")
            return
        }
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        // PASSIVE_PROVIDER aggregates fixes other apps (Maps, etc.) already took
        // and is often far fresher than GPS/NETWORK's own last-known values.
        val knownProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val activeProviders = knownProviders.filter {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (activeProviders.isEmpty()) report("no location provider enabled")

        val fallback = knownProviders
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        // Refresh keeps the original parking time: only the coordinates may be
        // corrected, never the moment the scooter was parked.
        val savedAt = if (preserveAtMs > 0) preserveAtMs else System.currentTimeMillis()
        fun store(l: Location, fresh: Boolean) {
            RideStore.saveParkedLocation(l.latitude, l.longitude, savedAt, approximate = !fresh)
            report("${if (fresh) "fresh fix" else "cached fix"} " +
                "${"%.5f,%.5f".format(l.latitude, l.longitude)}")
        }
        // Never wait for a fresh fix before the Find tab has a spot.
        fallback?.let { store(it, fresh = false) }

        var saved = fallback != null
        lateinit var timeout: Runnable
        lateinit var listenerRef: LocationListener
        fun stopUpdates() {
            activeProviders.forEach { p -> runCatching { manager.removeUpdates(listenerRef) } }
        }
        listenerRef = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (saved || epoch != parkingEpoch) return
                // LocationManager can replay a cached fix immediately. Never let
                // an older replay replace the best fix we already captured.
                if (fallback != null && location.time <= fallback.time) return
                saved = true
                main.removeCallbacks(timeout)
                stopUpdates()
                store(location, fresh = savedAt - location.time < 60_000L)
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
            report("no location fix available")
        }
        // requestLocationUpdates is more reliable than requestSingleUpdate on
        // OEM stacks that silently drop one-shot requests. First fix wins.
        activeProviders.forEach { p ->
            runCatching<Unit> {
                manager.requestLocationUpdates(p, 1_000L, 0f, listenerRef, Looper.getMainLooper())
            }
        }
        main.postDelayed(timeout, 5_000L)
    }

    /** Find-tab button: re-capture the coordinates, keeping the parking time. */
    fun refreshParkedLocation() {
        val allowed =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!allowed) {
            val msg = "Location permission needed to save the parking spot"
            ClusterRepository.parkedStatus.value = msg
            ClusterRepository.telemetry.value = ClusterRepository.telemetry.value.copy(error = msg)
            return
        }
        ClusterRepository.parkedStatus.value = "Getting a fresh fix..."
        val originalAt = RideStore.parkedLocation.value?.atMs ?: 0L
        saveParkedLocation(force = true, preserveAtMs = originalAt, reportStatus = true)
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

    /**
     * Mirror a call or message to the cluster without touching the navigation
     * row. Jupiter's own branch in TVS Connect 8.14.3 sends a single 20-byte
     * 0x43 frame; the legacy 1/3/2 sequence is available via a debug flag.
     * Messages use the Jupiter 0x4C text row; 0x53 is a genuine TVS ID whose
     * rendering on this cluster is still unverified.
     */
    fun sendPhoneEvent(isCall: Boolean, text: String, holdMs: Long) {
        phoneEventJob?.cancel()
        phoneEventJob = scope.launch {
            if (isCall) {
                // Missed calls are read from the call log into 0x4A byte 10,
                // not derived from caller cards. Keep the log entry for debugging.
                FrameLog.note("CALL trigger '$text' - missedCalls=${currentMissedCalls()}")
            }
            val pages = if (isCall) listOf(text)
                else com.jupiterconnect.nav.PhoneEventFormat.pages(text)
            val started = System.currentTimeMillis()
            val until = started + holdMs
            if (isCall) {
                // TVS Connect's Jupiter branch writes one 20-byte frame. The
                // legacy 21-byte sub-ID sequence is kept behind a debug flag
                // for A/B testing against the dash.
                val packets = if (ClusterRepository.legacyCallerPackets.value) {
                    buildCallerPackets(text, contactName = text)
                } else listOf(buildCallerFrame(text))
                while (isActive && System.currentTimeMillis() < until) {
                    packets.forEach { write(it) }
                    delay(500L)
                }
            } else {
                while (isActive && System.currentTimeMillis() < until) {
                    val elapsed = System.currentTimeMillis() - started
                    val slot = (elapsed * pages.size / holdMs).toInt().coerceIn(0, pages.lastIndex)
                    val page = pages[slot]
                    // 0x4C is Jupiter's message row; 0x53 covers other clusters.
                    write(buildMessageRow(page))
                    write(buildMessageFrame(page))
                    delay(500L)
                }
            }
            if (!isActive) return@launch
            if (isCall) {
                val calls = currentMissedCalls()
                repeat(3) {
                    write(buildKeepalive(
                        batteryPct = phoneBatteryPct(),
                        signalBars = phoneSignalBars(),
                        missedCalls = calls,
                    ))
                    delay(100L)
                }
            } else {
                write(buildMessageRow(""))
                write(buildMessageFrame(""))
            }
            phoneEventJob = null
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

    /** 5B 9C with the APK's real chats+messages bit packing. */
    fun sendSocialSplit(s: ClusterRepository.SocialSplit) {
        scope.launch {
            write(ClusterControlFrames.buildSocialCountsV2(
                s.waChats, s.waMsgs, s.fbChats, s.fbMsgs, s.instagram))
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }

    /** Cluster-reported value that can confirm a trip command took effect. */
    private fun clusterTripValue(): Int? {
        val t = ClusterRepository.telemetry.value
        return if (t.frameCount == 0L) null else t.tripMeterKm.toInt()
    }

    private fun nowLabel(): String {
        val c = java.util.Calendar.getInstance()
        return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE))
    }

    /**
     * Writes a setting and records whether the cluster reported a matching
     * change. Features with no telemetry counterpart are marked NO_FEEDBACK so
     * we can later tell "not supported" from "not verifiable".
     */
    private fun verifySetting(
        feature: String,
        frame: ByteArray,
        observable: (() -> Int?)? = null,
    ) {
        scope.launch {
            val before = observable?.invoke()
            write(frame)
            FrameLog.note("$feature sent: ${hex(frame)}")
            if (observable == null || before == null) {
                FeatureVerification.unverifiable(
                    feature, "cluster has no reported value for this setting", hex(frame))
            } else {
                delay(1500L)
                val after = observable()
                when {
                    after == null -> FeatureVerification.log(
                        feature, Status.NO_CHANGE, "cluster stopped reporting", hex(frame))
                    after != before -> FeatureVerification.log(
                        feature, Status.WORKING, "cluster value $before -> $after", hex(frame))
                    else -> FeatureVerification.log(
                        feature, Status.NO_CHANGE, "cluster value stayed $before", hex(frame))
                }
            }
            ClusterRepository.settingsStatus.value = "$feature sent at ${nowLabel()}"
        }
    }

    /** Send the More-screen draft to the cluster and remember it as applied. */
    fun applyClusterSettings() {
        scope.launch {
            val r = ClusterRepository
            val draft = ClusterRepository.AppliedSettings(
                r.illumination.value, r.tslSetting.value,
                r.regenSetting.value, r.rideMode.value)
            write(ClusterControlFrames.buildVehicleControlFrame(
                illumination = draft.illumination, tsl = draft.tsl))
            write(ClusterControlFrames.buildRegenFrame(draft.regen))
            if (draft.rideMode != 0) {
                write(ClusterControlFrames.buildRideModeFrame(
                    draft.rideMode, draft.illumination, draft.tsl))
            }
            r.markSettingsApplied()
            r.settingsStatus.value = "Applied at ${nowLabel()}"
            FrameLog.note("SETTINGS APPLIED $draft")
        }
    }

    fun sendTrip(trip: Int, reset: Boolean) {
        val name = "Trip ${if (trip == 0) "A" else "B"} ${if (reset) "reset" else "select"}"
        verifySetting(name, ClusterControlFrames.buildTripFrame(trip, reset), ::clusterTripValue)
        ClusterRepository.selectedTrip.value = trip
    }

    fun sendCalibrationRequest() {
        val frame = ClusterControlFrames.buildCalibrationRequest()
        scope.launch {
            write(frame)
            FeatureVerification.unverifiable(
                "Calibration request", "cluster does not report calibration state", hex(frame))
            ClusterRepository.settingsStatus.value = "Calibration request sent at ${nowLabel()}"
        }
    }

    fun sendFollowMeTimer(minutes: Int) {
        val frame = ClusterControlFrames.buildFollowMeHeadlampFrame(minutes)
        scope.launch {
            write(frame)
            FeatureVerification.unverifiable(
                "Follow-me $minutes min", "cluster does not report this timer", hex(frame))
            ClusterRepository.settingsStatus.value = "Follow-me timer $minutes min sent at ${nowLabel()}"
        }
    }

    fun sendRoadName(name: String, arrived: Boolean = false) {
        val frame = ClusterControlFrames.buildRoadNameFrame(name, arrived)
        scope.launch {
            write(frame)
            FeatureVerification.unverifiable("Road name", "cluster does not echo text rows", hex(frame))
        }
    }

    /** 5B 22 / 5A 7D / 5B 80 setup frames - manual, never sent automatically. */
    fun sendSetupData() {
        scope.launch {
            val frames = listOf(
                ClusterControlFrames.buildUserIdFrame(1),
                ClusterControlFrames.buildVinFrame(),
                ClusterControlFrames.buildDobFrame(1, 1, 1900),
            )
            frames.forEach { write(it) }
            FeatureVerification.unverifiable(
                "Setup data", "user ID / VIN / DOB are write-only", frames.joinToString(" | ", transform = ::hex))
            ClusterRepository.handshakeStatus.value = "Setup data sent at ${nowLabel()}"
        }
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
        }.onFailure {
            FrameLog.note("VOICE foreground type update failed: ${it.message}")
            startForegroundSafe(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }
    }

    fun disconnectNow(fromDestroy: Boolean = false) {
        // Stop the retry loop first: an explicit disconnect must stay off.
        manualDisconnect = true
        // A system teardown (onDestroy) is not a user action - keep the remembered
        // bike so the restarted service can redial it. An explicit Disconnect is
        // persisted so the restart never fights the user's choice.
        if (!fromDestroy) linkPrefs.edit().putBoolean(KEY_USER_DISCONNECTED, true).apply()
        connectWatchdog?.cancel()
        connectWatchdog = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        finishBondWait() // a pending pairing must not reconnect after Disconnect
        // Only a connected bike has a parking spot to save (onDestroy also
        // calls this, with no link at all).
        if (connectionEstablished || gatt != null) saveParkedLocation(force = true)
        stopGpsSpeed()
        // A manual disconnect after a ride must still finalise that ride.
        RideStore.onDisconnected()
        // An explicit Disconnect is the end of the journey - do not let a later
        // reconnect silently merge it back into a new ride.
        if (!fromDestroy) RideStore.discardResumableRide()
        keepaliveJob?.cancel()
        keepaliveJob = null
        navJob?.cancel()
        navJob = null
        trafficJob?.cancel()
        trafficJob = null
        mediaKeyJob?.cancel()
        mediaKeyJob = null
        releaseWakeLock()
        val g = gatt
        if (g != null) {
            writeChar = null
            gatt = null
            closeGattSafely(g, disconnectFirst = true)
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
                    FrameLog.note("LINK UP status=$status addr=${g.device.address}")
                    connectWatchdog?.cancel()
                    connectWatchdog = null
                    connectedAtMs = System.currentTimeMillis()
                    lastFrameAtMs = 0L
                    FrameCatalog.reset()
                    FrameLog.startSession(g.device.address)
                    reconnectJob?.cancel()
                    reconnectJob = null
                    reconnectAttempts = 0
                    finishBondWait()
                    manualDisconnect = false
                    // No requestConnectionPriority(HIGH): it triggers a
                    // parameter update that drops the link on this cluster.
                    // The 9e5e945 behaviour (default params) was stable.
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
                    startRideSync()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT disconnected, status=$status")
                    FrameLog.endSession()
                    connectWatchdog?.cancel()
                    connectWatchdog = null
                    val wasEstablished = connectionEstablished
                    val nowMs = System.currentTimeMillis()
                    val uptimeS = if (connectedAtMs > 0) (nowMs - connectedAtMs) / 1000L else 0L
                    val lastFrameAgeS = if (lastFrameAtMs > 0) (nowMs - lastFrameAtMs) / 1000L else -1L
                    FrameLog.note(
                        "LINK DOWN status=$status established=$wasEstablished " +
                            "uptime=${uptimeS}s lastFrameAge=${lastFrameAgeS}s")
                    connectedAtMs = 0L
                    connectionEstablished = false
                    if (wasEstablished) {
                        // Only a real connect -> disconnect cycle closes a ride
                        // or moves the parking spot. Status 147 means the
                        // connection never happened, so neither may change.
                        stopGpsSpeed()
                        saveParkedLocation(force = true)
                        RideStore.onDisconnected()
                        // No telemetry means no engine data - make that explicit.
                        ClusterRepository.warmup.value = WarmupState(message = "Engine off")
                        // Keep syncing for two minutes after the link drops.
                        disconnectedAtMs = System.currentTimeMillis()
                    }
                    // Keep the foreground service (and its notification) alive
                    // for the whole time Bluetooth is on. Cancelling the FGS
                    // notification used to drop the service into Doze, where the
                    // reconnect delay() timers froze until the user reopened the
                    // app - exactly the "never reconnects while riding" symptom.
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
                    trafficJob?.cancel()
                    trafficJob = null
                    mediaKeyJob?.cancel()
                    mediaKeyJob = null
                    releaseWakeLock()
                    phoneEventJob?.cancel()
                    phoneEventJob = null
                    if (g === gatt) gatt = null
                    // Android has already finished the disconnect here, so a
                    // direct close is safe (no extra disconnect round-trip).
                    closeGattSafely(g, disconnectFirst = false)
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
            startTrafficBridge()
            startMediaKeyBridge()
            sendInitialClusterData()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            FrameLog.record("RX", value)
            FrameCatalog.observe(value, rx = true)
            lastFrameAtMs = System.currentTimeMillis()
            if (value.size > 6 && (value[1].toInt() and 0xFF) == 0x54) {
                ClusterImageTransfer.onRequest(value)?.let { chunk ->
                    scope.launch { write(chunk) }
                }
            }
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
            FrameCatalog.observe(v, rx = true)
            lastFrameAtMs = System.currentTimeMillis()
            if (v.size > 6 && (v[1].toInt() and 0xFF) == 0x54) {
                ClusterImageTransfer.onRequest(v)?.let { chunk ->
                    scope.launch { write(chunk) }
                }
            }
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
                acquireWakeLock()
                // Real phone values stop the cluster flipping between
                // "full signal / full battery" and "no signal / low battery"
                // every time our keepalive arrived with zeros.
                write(buildKeepalive(
                    batteryPct = phoneBatteryPct(),
                    signalBars = phoneSignalBars(),
                    missedCalls = currentMissedCalls(),
                ))
                delay(2000)
            }
        }
    }

    /**
     * Sync with the cloud: push rides (including graphs) and the one JSON
     * document of settings/counters, pull live garage data from Meridian, and
     * report the cluster odometer. Runs at a slow cadence while parked so
     * sign-in without a bike still syncs.
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
                    // Fuel, service records and odometer are read-only here.
                    CloudGarageStore.pull(token)
                    val liveKm = ClusterRepository.telemetry.value.odometerKm
                        .takeIf { it > 0 } ?: RideStore.lastReading.value.odometerKm
                    val km = kotlin.math.round(liveKm).toInt()
                    val now = System.currentTimeMillis()
                    if (km > 0 && shouldReportOdo(km, lastReportedOdoKm, now, lastOdoReportAtMs)) {
                        if (SupabaseClient.reportOdometer(
                                JUPITER_VEHICLE, km, "cluster", token) == null
                        ) {
                            lastReportedOdoKm = km
                            lastOdoReportAtMs = now
                        }
                    }
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
            var scrollOffset = 0
            var lastLine = ""
            while (isActive) {
                // Personal correction from real rides; neutral until 5 rides
                // carry a Maps ETA, and always bounded.
                val etaFactor = RideAnalytics.personalEtaFactor(
                    RideStore.navEtaSamples(), RideStore.rides.value)
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
                        val line = step.displayLine
                        val long = line.length > com.jupiterconnect.nav.NavScroll.WIDTH
                        write(buildNavControl(
                            distance,
                            eta,
                            remaining,
                            step.pictogram,
                            lines = if (long) 2 else 1,
                        ))
                        if (reply == null) {
                            if (line != lastLine) {
                                lastLine = line
                                scrollOffset = 0
                            }
                            val window = com.jupiterconnect.nav.NavScroll.window(line, scrollOffset)
                            // The dash latches the first text of a frame pair and
                            // ignores later writes. A blank frame releases it so
                            // each scrolled window is rendered as a fresh line.
                            if (long && scrollOffset > 0) {
                                write(buildNavText(""))
                                delay(120L)
                            }
                            write(buildNavText(window))
                            if (long) scrollOffset += 2
                        }
                    }
                }
                delay(1500)
            }
        }
    }

    /** Cluster media buttons (0x6B byte 2) drive Android's media session. */
    private fun startMediaKeyBridge() {
        if (mediaKeyJob?.isActive == true) return
        mediaKeyJob = scope.launch {
            var last = 0
            ClusterRepository.telemetry.collect { t ->
                val key = t.mediaKey
                if (key == 0) {
                    last = 0
                } else if (key != last) {
                    last = key
                    dispatchMediaKey(key)
                }
            }
        }
    }

    private fun dispatchMediaKey(key: Int) {
        val code = when (key) {
            0 -> KeyEvent.KEYCODE_MEDIA_PLAY
            1 -> KeyEvent.KEYCODE_MEDIA_PAUSE
            2 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            3 -> KeyEvent.KEYCODE_MEDIA_NEXT
            4 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            5 -> KeyEvent.KEYCODE_VOLUME_UP
            6 -> KeyEvent.KEYCODE_VOLUME_DOWN
            else -> return
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /**
     * Weather follows the scooter's last-parked coordinates. Hyderabad is the
     * only fixed fallback city, used when no parked spot has been saved yet.
     * No active GPS scan, so this costs no extra battery.
     */
    private fun resolveWeatherLocation(): Pair<Double, Double> {
        RideStore.parkedLocation.value?.let { return it.lat to it.lng }
        val city = Cities.byName(ClusterRepository.weatherCity.value)
        return city.lat to city.lon
    }

    /** Rider greeting and last-known place, sent once when the link comes up. */
    private fun sendInitialClusterData() {
        scope.launch {
            val name = ClusterRepository.riderName.value.trim()
            if (ClusterRepository.riderNameEnabled.value && name.isNotEmpty()) {
                write(buildNavText(name, 0x52))
            }
            RideStore.parkedLocation.value?.let { p ->
                write(buildLocationFrame("Near ${p.coordinates}"))
            }
            // Announce the rider photo; chunks are sent when the cluster asks (0x54).
            if (ClusterImageTransfer.hasImage) {
                write(ClusterImageTransfer.initFrame())
            }
        }
    }

    /**
     * Traffic screens need BOTH a screen-select frame (5B 4C 00 <id> ...) and the
     * matching data frame. The previous build only sent the select on a manual
     * button, which is why nothing ever appeared. Runs only while connected,
     * enabled, and no navigation is being mirrored.
     */
    private fun startTrafficBridge() {
        if (trafficJob?.isActive == true) return
        trafficJob = scope.launch {
            var weather: TrafficClient.Weather? = null
            var cricket: TrafficClient.Cricket? = null
            var market: TrafficClient.Market? = null
            var news: TrafficClient.News? = null
            var lastFetch = 0L
            var index = 0
            val screens = intArrayOf(
                TrafficScreen.WEATHER, TrafficScreen.CRICKET,
                TrafficScreen.MARKET, TrafficScreen.DEFAULT,
            )
            while (isActive) {
                if (!ClusterRepository.trafficEnabled.value) {
                    ClusterRepository.trafficStatus.value = "Off"
                    delay(1000L)
                    continue
                }
                // Navigation owns the cluster while a route is active.
                if (ClusterRepository.mirroring.value &&
                    ClusterRepository.navInstruction.value != null
                ) {
                    delay(2000L)
                    continue
                }
                val now = System.currentTimeMillis()
                if (now - lastFetch > 600_000L) {
                    ClusterRepository.trafficStatus.value = "Fetching free data"
                    val (lat, lon) = resolveWeatherLocation()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        weather = runCatching { TrafficClient.weather(lat, lon) }.getOrNull()
                        cricket = runCatching { TrafficClient.cricket() }.getOrNull()
                        market = runCatching { TrafficClient.market() }.getOrNull()
                        news = runCatching { TrafficClient.news() }.getOrNull()
                    }
                    lastFetch = now
                    ClusterRepository.trafficStatus.value = when {
                        weather != null || market != null || news != null -> "Live"
                        else -> "No data - check connection"
                    }
                }
                val id = screens[index % screens.size]
                ClusterRepository.trafficScreen.value = id
                // Select the screen first, then write its data frame.
                write(buildScreenSwitch(id))
                delay(300L)
                when (id) {
                    TrafficScreen.WEATHER -> weather?.let {
                        write(buildWeatherFrameWithCode(it.code, it.tempC, it.desc, it.aqi, it.rainPct))
                    }
                    TrafficScreen.CRICKET -> cricket?.let {
                        write(buildCricketFrame(
                            it.team1, it.team2,
                            it.runs1, it.wickets1, it.overs1,
                            it.runs2, it.wickets2, it.overs2, it.state,
                        ))
                    }
                    TrafficScreen.MARKET -> market?.let {
                        write(buildMarketFrame(it.niftyPct, it.sensexPct))
                        // Full values on text row 2 so the numbers are readable.
                        write(buildNavText(
                            "N %d %+.1f".format(it.nifty.toInt(), it.niftyPct), 0x63))
                        delay(250L)
                        write(buildNavText(
                            "S %d %+.1f".format(it.sensex.toInt(), it.sensexPct), 0x63))
                    }
                    TrafficScreen.DEFAULT -> news?.let { write(buildNewsFrame(it.headline)) }
                }
                index++
                delay(8000L)
            }
        }
    }

    private suspend fun write(bytes: ByteArray) = writeLock.withLock {
        val g = gatt
        val ch = writeChar
        if (g == null || ch == null) return@withLock
        FrameLog.record("TX", bytes)
        FrameCatalog.observe(bytes, rx = false)
        // Stock write type for every frame: no response, as TVS Connect does.
        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, bytes, writeType)
            } else {
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                ch.writeType = writeType
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
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            "cluster", "Cluster link", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alert = NotificationChannel(
                CHANNEL_SPEED_ALERT, "High speed alert", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fires once when the configured speed limit is crossed"
                enableVibration(true)
            }
            manager.createNotificationChannel(alert)
        }
    }

    /** One-shot status-bar alert; mirrors the Raider/U577 variant in TVS Connect. */
    private fun postSpeedAlertNotification(speed: Int, limit: Int) {
        // MainActivity requests POST_NOTIFICATIONS on API 33+. If the rider
        // denied it, notify() simply does nothing - the cluster alert and the
        // vibration still fire, so this is safe to call unconditionally.
        val pending = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, com.jupiterconnect.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_SPEED_ALERT)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("High speed alert")
            .setContentText("$speed km/h - limit $limit km/h")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_SPEED_ALERT, notification)
        }
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
            // Non-dismissible while the service runs (Bluetooth on): a swiped-
            // away FGS notification lets the system kill the service and freeze
            // the reconnect timers in Doze.
            .setOngoing(true)
            .build()
    }

    /** Re-post the notification so its title/text follow the link state. */
    private fun updateNotification() {
        // No notification while not driving, even if Bluetooth is on for AirPods.
        if (!ClusterRepository.drivingMode.value || adapter?.isEnabled != true) return
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
        if (bluetoothReceiverRegistered) {
            runCatching { unregisterReceiver(bluetoothStateReceiver) }
            bluetoothReceiverRegistered = false
        }
        phoneEventJob?.cancel()
        phoneEventJob = null
        releaseWakeLock()
        disconnectNow(fromDestroy = true)
        voiceBridge.destroy()
        ClusterRepository.service = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SCAN = "com.jupiterconnect.action.SCAN"
        const val ACTION_CONNECT = "com.jupiterconnect.action.CONNECT"
        const val ACTION_DRIVING_MODE = "com.jupiterconnect.action.DRIVING_MODE"
        const val EXTRA_ADDRESS = "com.jupiterconnect.extra.ADDRESS"
        const val EXTRA_DRIVING_ENABLED = "com.jupiterconnect.extra.DRIVING_ENABLED"
        const val EXTRA_FOREGROUND = "com.jupiterconnect.extra.FOREGROUND"

        private const val TAG = "JupiterBle"
        private const val CHANNEL_SPEED_ALERT = "speed_alert"
        private const val NOTIF_SPEED_ALERT = 2
        /** Prevent flicker at the limit: re-arm only a few km/h below it. */
        private const val SPEED_ALERT_HYSTERESIS = 3
        private const val SCAN_TIMEOUT_MS = 20_000L
        private const val RUNNING_RPM = 500
        // HCI supervision timeout: an established link really dropped.
        private const val GATT_CONN_TIMEOUT = 8
        // Android 15+: a direct connect attempt timed out (never connected).
        private const val GATT_CONNECTION_TIMEOUT = 147
        private const val GATT_CONN_TERMINATE_PEER_USER = 19
        /** Backoff for auto-reconnect: 5s, 10s, 20s, then 30s between tries. */
        private val RECONNECT_DELAYS = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L)
        /** A direct connect with no callback for this long is stuck, not slow. */
        private const val CONNECT_WATCHDOG_MS = 35_000L
        private const val KEY_LINK_ADDRESS = "address"
        private const val KEY_USER_DISCONNECTED = "user_disconnected"
        /** Matches `TVS BT...` and `TVSBT...` - the advertised name is inferred
         * from community pairing reports, so both spellings are accepted. */
        private val CLUSTER_NAME_RE = Regex("""\bTVS\s*BT""", RegexOption.IGNORE_CASE)
        private const val SCAN_FAILED_ALREADY_STARTED = 1
        private const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        private const val SCAN_FAILED_INTERNAL_ERROR = 3
        private const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
    }
}
