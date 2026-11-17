package com.jupiterconnect.session

import android.content.Context
import android.content.SharedPreferences
import com.jupiterconnect.protocol.Telemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A completed ride: connect → disconnect. */
data class Ride(
    val startedAt: Long,
    val endedAt: Long,
    val distanceKm: Double,
    val avgSpeedKmh: Double,
    val topSpeedKmh: Int,
    val durationMin: Long,
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
}

/** Last values seen from the cluster, kept for display while disconnected. */
data class LastReading(
    val odometerKm: Double = 0.0,
    val fuelBars: Int = 0,
    val distanceToEmptyKm: Int = 0,
    val hasData: Boolean = false,
)

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
    private const val KEY_LAST_HAS = "last_has"
    private const val KEY_RIDES = "rides"

    private var prefs: SharedPreferences? = null

    private val _lastReading = MutableStateFlow(LastReading())
    val lastReading: StateFlow<LastReading> = _lastReading.asStateFlow()

    private val _rides = MutableStateFlow<List<Ride>>(emptyList())
    val rides: StateFlow<List<Ride>> = _rides.asStateFlow()

    // Live ride state.
    private var rideStartMs = 0L
    private var rideStartOdo: Double? = null
    private var rideLastOdo: Double? = null
    private var rideTopSpeed = 0
    private var active = false

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs ?: return
        _lastReading.value = LastReading(
            odometerKm = p.getFloat(KEY_LAST_ODO, 0f).toDouble(),
            fuelBars = p.getInt(KEY_LAST_FUEL, 0),
            distanceToEmptyKm = p.getInt(KEY_LAST_DTE, 0),
            hasData = p.getBoolean(KEY_LAST_HAS, false),
        )
        _rides.value = parseRides(p.getString(KEY_RIDES, "").orEmpty())
    }

    /** Called on GATT connected. */
    fun onConnected() {
        rideStartMs = System.currentTimeMillis()
        rideStartOdo = null
        rideLastOdo = null
        rideTopSpeed = 0
        active = true
    }

    /** Called for every decoded telemetry frame while connected. */
    fun onFrame(t: Telemetry) {
        val p = prefs ?: return
        if (t.odometerKm > 0) {
            val reading = _lastReading.value.copy(
                odometerKm = t.odometerKm,
                fuelBars = t.fuelBars,
                distanceToEmptyKm = t.distanceToEmptyKm,
                hasData = true,
            )
            _lastReading.value = reading
            p.edit()
                .putFloat(KEY_LAST_ODO, reading.odometerKm.toFloat())
                .putInt(KEY_LAST_FUEL, reading.fuelBars)
                .putInt(KEY_LAST_DTE, reading.distanceToEmptyKm)
                .putBoolean(KEY_LAST_HAS, true)
                .apply()
        }
        if (active) {
            if (t.odometerKm > 0) {
                if (rideStartOdo == null) rideStartOdo = t.odometerKm
                rideLastOdo = t.odometerKm
            }
            if (t.speedKmh > rideTopSpeed) rideTopSpeed = t.speedKmh
        }
    }

    /** Called on GATT disconnected (guarded so it only finalises once). */
    fun onDisconnected() {
        if (!active) return
        active = false
        val start = rideStartOdo
        val end = rideLastOdo
        if (start == null || end == null || end <= start) return
        val distance = end - start
        val durationMs = (System.currentTimeMillis() - rideStartMs).coerceAtLeast(1000)
        val durationMin = durationMs / 60_000L
        val avg = distance / (durationMs / 3_600_000.0)
        val ride = Ride(
            startedAt = rideStartMs,
            endedAt = System.currentTimeMillis(),
            distanceKm = distance,
            avgSpeedKmh = avg,
            topSpeedKmh = rideTopSpeed,
            durationMin = durationMin,
        )
        val updated = listOf(ride) + _rides.value
        _rides.value = updated
        prefs?.edit()?.putString(KEY_RIDES, serialiseRides(updated))?.apply()
    }

    private fun serialiseRides(list: List<Ride>): String = list.take(50).joinToString(";") { r ->
        listOf(
            r.startedAt, r.endedAt,
            (r.distanceKm * 100).toLong(), (r.avgSpeedKmh * 10).toLong(),
            r.topSpeedKmh.toLong(), r.durationMin,
        ).joinToString(",")
    }

    private fun parseRides(raw: String): List<Ride> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val f = part.split(",")
            if (f.size != 6) return@mapNotNull null
            runCatching {
                Ride(
                    startedAt = f[0].toLong(),
                    endedAt = f[1].toLong(),
                    distanceKm = f[2].toLong() / 100.0,
                    avgSpeedKmh = f[3].toLong() / 10.0,
                    topSpeedKmh = f[4].toInt(),
                    durationMin = f[5].toLong(),
                )
            }.getOrNull()
        }
    }
}
