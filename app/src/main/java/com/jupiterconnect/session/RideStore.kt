package com.jupiterconnect.session

import android.content.Context
import android.content.SharedPreferences
import com.jupiterconnect.protocol.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Speed bands used by the ride report: 0-30, 30-50, 50-70, 70+ km/h. */
val SPEED_BAND_LABELS = arrayOf("0-30", "30-50", "50-70", "70+")

/** A completed ride: connect → disconnect. */
data class Ride(
    val startedAt: Long,
    val endedAt: Long,
    val distanceKm: Double,
    val avgSpeedKmh: Double,
    val topSpeedKmh: Int,
    val durationMin: Long,
    /** Milliseconds spent in each speed band (0-30, 30-50, 50-70, 70+). */
    val bandMs: LongArray = LongArray(4),
    /** Milliseconds at (near) zero speed. */
    val idleMs: Long = 0L,
    /** ISS stop events reported by the cluster during this ride. */
    val issStops: Int = 0,
    val fuelBarsStart: Int = 0,
    val fuelBarsEnd: Int = 0,
    val maxRpm: Int = 0,
    /** Speed (km/h) and throttle (%) sampled every 2 s during this ride. */
    val speedSamples: List<Int> = emptyList(),
    val throttleSamples: List<Int> = emptyList(),
) {
    val dateLabel: String
        get() {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = startedAt }
            return "%02d %s · %02d:%02d".format(
                c.get(java.util.Calendar.DAY_OF_MONTH),
                java.text.DateFormatSymbols().shortMonths[c.get(java.util.Calendar.MONTH)],
                c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE),
            )
        }

    /** Fuel bars used, clamped to zero so a refuel mid-ride cannot go negative. */
    val fuelBarsUsed: Int get() = (fuelBarsStart - fuelBarsEnd).coerceAtLeast(0)

    /** Rough economy from the 5.1 L tank across six bars. Only meaningful on long rides. */
    val economyKmPerL: Double
        get() {
            val usedBars = fuelBarsUsed
            return if (usedBars > 0 && distanceKm > 0.5)
                distanceKm / (usedBars * (5.1 / 6.0))
            else 0.0
        }

    val idleMin: Long get() = idleMs / 60_000L

    /** Time-of-day label taken from the ride's actual start, not the clock now. */
    val rideKind: String
        get() {
            val cal = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("Asia/Kolkata"))
            cal.timeInMillis = startedAt
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            return when {
                distanceKm <= 0.3 -> "Short ride"
                hour < 12 -> "Morning ride"
                hour < 16 -> "Afternoon ride"
                hour < 22 -> "Evening ride"
                else -> "Night ride"
            }
        }

    fun bandPct(index: Int): Int {
        val total = bandMs.sum()
        return if (total <= 0) 0 else (bandMs[index] * 100 / total).toInt()
    }
}

/** Where the scooter was last parked, captured when Bluetooth disconnects. */
data class ParkedLocation(
    val lat: Double,
    val lng: Double,
    val atMs: Long,
    /** True when saved from a cached fix rather than a fresh GPS reading. */
    val approximate: Boolean = false,
) {
    val coordinates: String get() = "%.5f, %.5f".format(lat, lng)
    val timeLabel: String
        get() {
            // Always IST, independent of the phone's locale/timezone.
            val fmt = java.text.SimpleDateFormat("dd MMM · HH:mm", java.util.Locale.ENGLISH)
            fmt.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
            return fmt.format(java.util.Date(atMs))
        }
}

/** Last values seen from the cluster, kept for display while disconnected. */
data class LastReading(
    val odometerKm: Double = 0.0,
    val fuelBars: Int = 0,
    val distanceToEmptyKm: Int = 0,
    val averageFuelEconomy: Int = 0,
    val batteryVoltage: Double = 0.0,
    val engineTempC: Int = 0,
    val hasData: Boolean = false,
)

/** Result of folding one cluster ISS-duration reading into the carbon total. */
internal data class IssTotals(val seconds: Long, val lastObserved: Long)

/**
 * The cluster's ECONOMY frame carries `issDuration`, a lifetime cumulative
 * idle-seconds counter. Adopting it as a floor makes carbon saved genuine
 * "from day one" rather than only counting stops seen after install. The
 * per-reading deltas stay as a fallback for clusters that reset the counter
 * each ride, and the total never decreases, so such a reset cannot corrupt a
 * lifetime total.
 */
internal fun foldIssDuration(
    storedSeconds: Long,
    lastObserved: Long,
    observed: Long,
): IssTotals {
    if (observed <= 0) return IssTotals(storedSeconds, lastObserved)
    var seconds = storedSeconds
    if (lastObserved >= 0 && observed > lastObserved) {
        seconds += observed - lastObserved
    }
    // The cluster's lifetime counter includes stops the app never observed;
    // take it as a floor. max() also guards a per-ride reset from lowering it.
    if (observed > seconds) seconds = observed
    return IssTotals(seconds, observed)
}

/**
 * Persists the last cluster reading and records one ride per connect/disconnect
 * cycle. Plain SharedPreferences (no JSON library): a compact delimited format
 * keeps this dependency-free and readable.
 */
object RideStore {
    private const val PREFS = "jupiter_ride_store"
    private const val KEY_LAST_ODO = "last_odo"
    private const val KEY_LAST_FUEL = "last_fuel"
    private const val KEY_LAST_DTE = "last_dte"
    private const val KEY_LAST_ECON = "last_econ"
    private const val KEY_LAST_BATTERY = "last_battery"
    private const val KEY_LAST_TEMP = "last_temp"
    private const val KEY_LAST_HAS = "last_has"
    private const val KEY_RIDES = "rides"
    private const val KEY_SYNCED = "synced_rides"
    private const val KEY_WARMUP_LAST = "warmup_last_run"
    private const val KEY_SAMPLES = "rs_"
    private const val KEY_CARBON_SECONDS = "carbon_iss_seconds"
    private const val KEY_PARK_LAT = "park_lat"
    private const val KEY_PARK_LNG = "park_lng"
    private const val KEY_PARK_MS = "park_ms"
    private const val KEY_PARK_APPROX = "park_approx"
    private const val KEY_LIVE_SPEEDS = "live_speeds"
    private const val KEY_LIVE_THROTTLES = "live_throttles"
    private const val KEY_ALL_TIME_TOP = "all_time_top"

    private var prefs: SharedPreferences? = null
    private val syncedIds = mutableSetOf<Long>()

    private val _lastReading = MutableStateFlow(LastReading())
    val lastReading: StateFlow<LastReading> = _lastReading.asStateFlow()

    private val _rides = MutableStateFlow<List<Ride>>(emptyList())
    val rides: StateFlow<List<Ride>> = _rides.asStateFlow()

    private val _parkedLocation = MutableStateFlow<ParkedLocation?>(null)
    val parkedLocation: StateFlow<ParkedLocation?> = _parkedLocation.asStateFlow()

    // Live ride state. Peak speed is a flow so the UI can show the true top
    // speed (not the current speed, which drops when the accelerator is eased).
    private val _liveTopSpeed = MutableStateFlow(0)
    val liveTopSpeed: StateFlow<Int> = _liveTopSpeed.asStateFlow()

    /** Best speed ever recorded; only ever rises, survives restarts. */
    private val _allTimeTopSpeed = MutableStateFlow(0)
    val allTimeTopSpeed: StateFlow<Int> = _allTimeTopSpeed.asStateFlow()

    // Trip distance derived from the odometer (which the cluster broadcasts
    // correctly). The cluster's "trip meter" byte is not a trip counter - it
    // does not change when the trip is reset - so we compute it ourselves.
    private val _liveTripKm = MutableStateFlow(0.0)
    val liveTripKm: StateFlow<Double> = _liveTripKm.asStateFlow()

    /** Graph samples for the ride in progress; cleared when a new ride starts. */
    private val _liveSpeeds = MutableStateFlow<List<Int>>(emptyList())
    val liveSpeeds: StateFlow<List<Int>> = _liveSpeeds.asStateFlow()
    private val _liveThrottles = MutableStateFlow<List<Int>>(emptyList())
    val liveThrottles: StateFlow<List<Int>> = _liveThrottles.asStateFlow()

    /** Last bar blinking on the cluster (~1.2 L left). */
    private val _lowFuel = MutableStateFlow(false)
    val lowFuel: StateFlow<Boolean> = _lowFuel.asStateFlow()
    /** Distance ridden since low fuel was first detected (Trip F equivalent). */
    private val _tripFKm = MutableStateFlow(0.0)
    val tripFKm: StateFlow<Double> = _tripFKm.asStateFlow()

    /** Cumulative seconds the engine was stopped by ISS; drives CO2 saved. */
    private val _issSavedSeconds = MutableStateFlow(0L)
    val issSavedSeconds: StateFlow<Long> = _issSavedSeconds.asStateFlow()

    /** kg of CO2 not emitted because ISS stopped the engine at signals. */
    val carbonSavedKg: Double
        get() = _issSavedSeconds.value * IDLE_LITRES_PER_SECOND * CO2_KG_PER_LITRE

    // ~0.5 L/hour idle for a 125cc scooter, and ~2.31 kg CO2 per litre petrol.
    const val IDLE_LITRES_PER_SECOND = 0.5 / 3600.0
    const val CO2_KG_PER_LITRE = 2.31

    private var rideStartMs = 0L
    private var rideStartOdo: Double? = null
    private var rideLastOdo: Double? = null
    private var active = false

    // Ride report accumulators. Only the primary 0x10 frame advances the clock
    // so time is not counted once per frame type.
    private val bandMs = LongArray(4)
    private var idleMs = 0L
    private var lastPrimaryAtMs = 0L
    private var startIssCount = -1
    private var lastIssCount = 0
    private var fuelStart = -1
    private var fuelEnd = -1
    private var maxRpm = 0
    private val rideSpeeds = mutableListOf<Int>()
    private val rideThrottles = mutableListOf<Int>()
    private var lastSampleAtMs = 0L
    private var lowFuelStartOdo: Double? = null
    private var lastIssDuration = -1L

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Bundled Meridian fuel export powers the Rides monthly history.
        FuelLogStore.init(context.applicationContext)
        val p = prefs ?: return
        _lastReading.value = LastReading(
            odometerKm = p.getFloat(KEY_LAST_ODO, 0f).toDouble(),
            fuelBars = p.getInt(KEY_LAST_FUEL, 0),
            distanceToEmptyKm = p.getInt(KEY_LAST_DTE, 0),
            averageFuelEconomy = p.getInt(KEY_LAST_ECON, 0),
            batteryVoltage = java.lang.Double.longBitsToDouble(p.getLong(KEY_LAST_BATTERY, 0L)),
            engineTempC = p.getInt(KEY_LAST_TEMP, 0),
            hasData = p.getBoolean(KEY_LAST_HAS, false),
        )
        _issSavedSeconds.value = p.getLong(KEY_CARBON_SECONDS, 0L)
        _allTimeTopSpeed.value = p.getInt(KEY_ALL_TIME_TOP, 0)
        // Keep the last ride's graph visible after a disconnect or a restart.
        // It is only cleared in onConnected(), when the next ride starts.
        _liveSpeeds.value = p.getString(KEY_LIVE_SPEEDS, "").orEmpty().parseSamples()
        _liveThrottles.value = p.getString(KEY_LIVE_THROTTLES, "").orEmpty().parseSamples()
        _rides.value = parseRides(p.getString(KEY_RIDES, "").orEmpty()).map { r ->
            val raw = p.getString(KEY_SAMPLES + r.startedAt, "").orEmpty()
            val parts = raw.split("|")
            if (parts.size == 2) r.copy(
                speedSamples = parts[0].parseSamples(),
                throttleSamples = parts[1].parseSamples(),
            ) else r
        }
        syncedIds.clear()
        p.getString(KEY_SYNCED, "").orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .forEach { syncedIds += it }
        val parkMs = p.getLong(KEY_PARK_MS, 0L)
        if (parkMs > 0) {
            _parkedLocation.value = ParkedLocation(
                lat = java.lang.Double.longBitsToDouble(p.getLong(KEY_PARK_LAT, 0L)),
                lng = java.lang.Double.longBitsToDouble(p.getLong(KEY_PARK_LNG, 0L)),
                atMs = parkMs,
                approximate = p.getBoolean(KEY_PARK_APPROX, false),
            )
        }
    }

    /** Called on GATT connected. */
    fun onConnected() {
        rideStartMs = System.currentTimeMillis()
        rideStartOdo = null
        rideLastOdo = null
        _liveTopSpeed.value = 0
        _liveTripKm.value = 0.0
        rideSpeeds.clear()
        rideThrottles.clear()
        lastSampleAtMs = 0L
        _liveSpeeds.value = emptyList()
        _liveThrottles.value = emptyList()
        lowFuelStartOdo = null
        _lowFuel.value = false
        _tripFKm.value = 0.0
        bandMs.fill(0)
        idleMs = 0L
        lastPrimaryAtMs = 0L
        startIssCount = -1
        lastIssCount = 0
        fuelStart = -1
        fuelEnd = -1
        maxRpm = 0
        active = true
    }

    /**
     * Called for every decoded telemetry frame while connected. [primary] is
     * true only for the 0x10 speedometer frame - the only frame that advances
     * the ride clock, so multiple frame types cannot double-count time.
     */
    fun onFrame(t: Telemetry, primary: Boolean = true) {
        val p = prefs ?: return
        if (t.odometerKm > 0) {
            val reading = _lastReading.value.copy(
                odometerKm = t.odometerKm,
                fuelBars = t.fuelBars,
                distanceToEmptyKm = t.distanceToEmptyKm,
                averageFuelEconomy = if (t.averageFuelEconomy > 0) t.averageFuelEconomy
                    else _lastReading.value.averageFuelEconomy,
                batteryVoltage = if (t.batteryVoltage > 0) t.batteryVoltage
                    else _lastReading.value.batteryVoltage,
                engineTempC = if (t.engineTempC != 0) t.engineTempC
                    else _lastReading.value.engineTempC,
                hasData = true,
            )
            _lastReading.value = reading
            p.edit()
                .putFloat(KEY_LAST_ODO, reading.odometerKm.toFloat())
                .putInt(KEY_LAST_FUEL, reading.fuelBars)
                .putInt(KEY_LAST_DTE, reading.distanceToEmptyKm)
                .putInt(KEY_LAST_ECON, reading.averageFuelEconomy)
                .putLong(KEY_LAST_BATTERY, java.lang.Double.doubleToRawLongBits(reading.batteryVoltage))
                .putInt(KEY_LAST_TEMP, reading.engineTempC)
                .putBoolean(KEY_LAST_HAS, true)
                .apply()
        }
        // Peak speed never decreases when the accelerator is eased.
        if (t.speedKmh > _liveTopSpeed.value) _liveTopSpeed.value = t.speedKmh
        // All-time best only rises; persist it so it is always shown.
        if (t.speedKmh > _allTimeTopSpeed.value) {
            _allTimeTopSpeed.value = t.speedKmh
            p.edit().putInt(KEY_ALL_TIME_TOP, t.speedKmh).apply()
        }
        if (t.rpm > maxRpm) maxRpm = t.rpm
        if (t.issCount > 0) {
            if (startIssCount < 0) startIssCount = t.issCount
            lastIssCount = t.issCount
        }
        // ISS duration is a lifetime cumulative counter on the cluster. Adopt
        // it as a floor (true "from day one" carbon) while keeping app-tracked
        // deltas as a fallback for clusters that reset it per ride.
        if (t.issDuration > 0) {
            val totals = foldIssDuration(
                _issSavedSeconds.value, lastIssDuration, t.issDuration.toLong())
            if (totals.seconds != _issSavedSeconds.value) {
                _issSavedSeconds.value = totals.seconds
                prefs?.edit()?.putLong(KEY_CARBON_SECONDS, totals.seconds)?.apply()
            }
            lastIssDuration = totals.lastObserved
        }
        if (t.fuelBars in 1..6) {
            if (fuelStart < 0) fuelStart = t.fuelBars
            fuelEnd = t.fuelBars
        }
        // Owner's manual: 6 bars above ~5 L, 3 bars at ~3.2 L, and the last
        // bar blinks at ~1.2 L with a "Low fuel" message and Trip F counting.
        if (t.fuelBars in 0..1) {
            if (lowFuelStartOdo == null && t.odometerKm > 0) lowFuelStartOdo = t.odometerKm
            _lowFuel.value = true
        } else if (t.fuelBars >= 2) {
            _lowFuel.value = false
            lowFuelStartOdo = null
            _tripFKm.value = 0.0
        }
        val lowStart = lowFuelStartOdo
        if (lowStart != null && t.odometerKm > lowStart) {
            _tripFKm.value = t.odometerKm - lowStart
        }
        if (active) {
            if (t.odometerKm > 0) {
                if (rideStartOdo == null) rideStartOdo = t.odometerKm
                rideLastOdo = t.odometerKm
                val start = rideStartOdo
                if (start != null) {
                    _liveTripKm.value = (rideLastOdo!! - start).coerceAtLeast(0.0)
                }
            }
            if (primary) {
                val now = System.currentTimeMillis()
                // One sample every 2 s for the per-ride throttle/speed graph.
                if (now - lastSampleAtMs >= 2_000L) {
                    rideSpeeds += t.speedKmh
                    rideThrottles += t.throttlePosition
                    lastSampleAtMs = now
                    _liveSpeeds.value = rideSpeeds.toList()
                    _liveThrottles.value = rideThrottles.toList()
                    p.edit()
                        .putString(KEY_LIVE_SPEEDS, rideSpeeds.joinToString("_"))
                        .putString(KEY_LIVE_THROTTLES, rideThrottles.joinToString("_"))
                        .apply()
                }
                if (lastPrimaryAtMs > 0) {
                    val dt = (now - lastPrimaryAtMs).coerceIn(0L, 2_000L)
                    val idx = when {
                        t.speedKmh < 30 -> 0
                        t.speedKmh < 50 -> 1
                        t.speedKmh < 70 -> 2
                        else -> 3
                    }
                    bandMs[idx] += dt
                    if (t.speedKmh <= 1) idleMs += dt
                }
                lastPrimaryAtMs = now
            }
        }
    }

    /** Called on GATT disconnected (guarded so it only finalises once). */
    fun onDisconnected() {
        if (!active) return
        active = false
        // Freeze the graph on screen after the link drops. Only the next
        // connect/ride start clears it. Persist the last samples as well so a
        // process restart still shows the most recent ride.
        _liveSpeeds.value = rideSpeeds.toList()
        _liveThrottles.value = rideThrottles.toList()
        prefs?.edit()
            ?.putString(KEY_LIVE_SPEEDS, rideSpeeds.joinToString("_"))
            ?.putString(KEY_LIVE_THROTTLES, rideThrottles.joinToString("_"))
            ?.apply()
        val start = rideStartOdo
        val end = rideLastOdo
        if (start == null || end == null || end <= start) return
        val distance = end - start
        val durationMs = (System.currentTimeMillis() - rideStartMs).coerceAtLeast(1000)
        val durationMin = durationMs / 60_000L
        val avg = distance / (durationMs / 3_600_000.0)
        val issStops = if (startIssCount >= 0) (lastIssCount - startIssCount).coerceAtLeast(0) else 0
        val ride = Ride(
            startedAt = rideStartMs,
            endedAt = System.currentTimeMillis(),
            distanceKm = distance,
            avgSpeedKmh = avg,
            topSpeedKmh = _liveTopSpeed.value,
            durationMin = durationMin,
            bandMs = bandMs.copyOf(),
            idleMs = idleMs,
            issStops = issStops,
            fuelBarsStart = fuelStart.coerceAtLeast(0),
            fuelBarsEnd = fuelEnd.coerceAtLeast(0),
            maxRpm = maxRpm,
            speedSamples = rideSpeeds.toList(),
            throttleSamples = rideThrottles.toList(),
        )
        val updated = listOf(ride) + _rides.value
        _rides.value = updated
        prefs?.edit()
            ?.putString(KEY_RIDES, serialiseRides(updated))
            ?.putString(
                KEY_SAMPLES + ride.startedAt,
                ride.speedSamples.joinToString("_") + "|" + ride.throttleSamples.joinToString("_"),
            )
            ?.apply()
    }

    /** Last engine-run timestamp, persisted so warm-up survives restarts. */
    var warmupLastRunMs: Long
        get() = prefs?.getLong(KEY_WARMUP_LAST, 0L) ?: 0L
        set(value) { prefs?.edit()?.putLong(KEY_WARMUP_LAST, value)?.apply() }

    /** Rides not yet confirmed by Supabase, newest first. */
    fun unsyncedRides(): List<Ride> = _rides.value.filter { it.startedAt !in syncedIds }

    /** Mark a ride as successfully uploaded so it is not retried forever. */
    fun markSynced(id: Long) {
        if (!syncedIds.add(id)) return
        prefs?.edit()?.putString(KEY_SYNCED, syncedIds.joinToString(","))?.apply()
    }

    /** Called when Bluetooth disconnects: remember where the scooter is parked. */
    fun saveParkedLocation(
        lat: Double,
        lng: Double,
        atMs: Long = System.currentTimeMillis(),
        approximate: Boolean = false,
    ) {
        val location = ParkedLocation(lat, lng, atMs, approximate)
        _parkedLocation.value = location
        prefs?.edit()
            ?.putLong(KEY_PARK_LAT, java.lang.Double.doubleToRawLongBits(lat))
            ?.putLong(KEY_PARK_LNG, java.lang.Double.doubleToRawLongBits(lng))
            ?.putLong(KEY_PARK_MS, atMs)
            ?.putBoolean(KEY_PARK_APPROX, approximate)
            ?.apply()
    }

    private fun String.parseSamples(): List<Int> =
        if (isBlank()) emptyList() else split("_").mapNotNull { it.toIntOrNull() }

    private fun serialiseRides(list: List<Ride>): String = list.take(50).joinToString(";") { r ->
        listOf(
            r.startedAt, r.endedAt,
            (r.distanceKm * 100).toLong(), (r.avgSpeedKmh * 10).toLong(),
            r.topSpeedKmh.toLong(), r.durationMin,
            r.bandMs[0], r.bandMs[1], r.bandMs[2], r.bandMs[3],
            r.idleMs, r.issStops.toLong(),
            r.fuelBarsStart.toLong(), r.fuelBarsEnd.toLong(), r.maxRpm.toLong(),
        ).joinToString(",")
    }

    private fun parseRides(raw: String): List<Ride> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val f = part.split(",")
            runCatching {
                when {
                    f.size >= 15 -> Ride(
                        startedAt = f[0].toLong(),
                        endedAt = f[1].toLong(),
                        distanceKm = f[2].toLong() / 100.0,
                        avgSpeedKmh = f[3].toLong() / 10.0,
                        topSpeedKmh = f[4].toInt(),
                        durationMin = f[5].toLong(),
                        bandMs = longArrayOf(
                            f[6].toLong(), f[7].toLong(), f[8].toLong(), f[9].toLong(),
                        ),
                        idleMs = f[10].toLong(),
                        issStops = f[11].toInt(),
                        fuelBarsStart = f[12].toInt(),
                        fuelBarsEnd = f[13].toInt(),
                        maxRpm = f[14].toInt(),
                    )
                    f.size == 6 -> Ride(
                        startedAt = f[0].toLong(),
                        endedAt = f[1].toLong(),
                        distanceKm = f[2].toLong() / 100.0,
                        avgSpeedKmh = f[3].toLong() / 10.0,
                        topSpeedKmh = f[4].toInt(),
                        durationMin = f[5].toLong(),
                    )
                    else -> return@mapNotNull null
                }
            }.getOrNull()
        }
    }
}
