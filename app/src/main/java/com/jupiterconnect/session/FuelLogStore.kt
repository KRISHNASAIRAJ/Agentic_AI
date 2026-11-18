package com.jupiterconnect.session

import android.content.Context
import com.jupiterconnect.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** One petrol fill from the Meridian fuel-log export (IST dates, paise amounts). */
data class FuelFill(
    val no: Int,
    val dateIst: String,
    val odometerKm: Double,
    val distanceKm: Double,
    val liters: Double,
    val amountPaise: Long,
    val mileageKmpl: Double,
    val station: String,
)

/** One calendar month of fuel-log data, keyed like Calendar.YEAR * 100 + MONTH. */
data class FuelMonth(
    val key: Int,
    val km: Double,
    val liters: Double,
    val paise: Long,
    val fills: Int,
)

/** Calendar.YEAR * 100 + MONTH for an IST "yyyy-MM-dd" string. */
internal fun fuelMonthKey(dateIst: String): Int? {
    val parts = dateIst.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    return year * 100 + (month - 1)
}

/** Sum fills into calendar months, oldest first. Pure so it can be unit tested. */
internal fun aggregateFuelMonths(fills: List<FuelFill>): List<FuelMonth> {
    val acc = linkedMapOf<Int, FuelMonth>()
    for (f in fills) {
        val key = fuelMonthKey(f.dateIst) ?: continue
        val prev = acc[key] ?: FuelMonth(key, 0.0, 0.0, 0L, 0)
        acc[key] = prev.copy(
            km = prev.km + f.distanceKm,
            liters = prev.liters + f.liters,
            paise = prev.paise + f.amountPaise,
            fills = prev.fills + 1,
        )
    }
    return acc.values.sortedBy { it.key }
}

/**
 * Reads the bundled Meridian export so the Rides history can show real monthly
 * distance from odometer readings, not fabricated ride cards. Telemetry rides
 * remain the only source of ride cards with speed/duration data.
 */
object FuelLogStore {
    private val _fills = MutableStateFlow<List<FuelFill>>(emptyList())
    val fills: StateFlow<List<FuelFill>> = _fills.asStateFlow()

    val totalKm: Double get() = _fills.value.sumOf { it.distanceKm }
    val totalLiters: Double get() = _fills.value.sumOf { it.liters }
    val totalPaise: Long get() = _fills.value.sumOf { it.amountPaise }
    val averageMileage: Double get() = if (totalLiters > 0) totalKm / totalLiters else 0.0

    fun init(context: Context) {
        if (_fills.value.isNotEmpty()) return
        val raw = runCatching {
            context.resources.openRawResource(R.raw.fuel_logs).bufferedReader().use { it.readText() }
        }.getOrElse { return }
        val parsed = runCatching {
            val array = JSONObject(raw).getJSONArray("fuel_logs")
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(FuelFill(
                        no = o.getInt("fill_no"),
                        dateIst = o.getString("date_ist"),
                        odometerKm = o.getDouble("odometer_km"),
                        distanceKm = if (o.isNull("distance_since_prev_km")) 0.0
                            else o.getDouble("distance_since_prev_km"),
                        liters = o.getDouble("liters"),
                        amountPaise = o.getLong("amount_paise"),
                        mileageKmpl = if (o.isNull("mileage_kmpl_since_prev")) 0.0
                            else o.getDouble("mileage_kmpl_since_prev"),
                        station = if (o.isNull("station")) "" else o.getString("station"),
                    ))
                }
            }
        }.getOrElse { return }
        _fills.value = parsed
    }

    fun monthly(): List<FuelMonth> = aggregateFuelMonths(_fills.value)

    /** Odometer-derived km for a Calendar.YEAR * 100 + MONTH key, or null. */
    fun kmFor(key: Int): Double? = monthly().firstOrNull { it.key == key }?.km
}
