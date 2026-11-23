package com.jupiterconnect.session

import java.util.Calendar
import java.util.TimeZone

/** IST calendar key for an epoch millisecond: YEAR * 100 + MONTH (0-based). */
internal fun monthKeyFor(timeMs: Long): Int {
    val c = Calendar.getInstance(IST)
    c.timeInMillis = timeMs
    return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
}

/** The last [count] month keys, oldest first, in IST. */
internal fun lastMonthKeys(count: Int, nowMs: Long): List<Int> {
    val c = Calendar.getInstance(IST)
    c.timeInMillis = nowMs
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return buildList {
        repeat(count) {
            add(c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH))
            c.add(Calendar.MONTH, -1)
        }
        reverse()
    }
}

/**
 * Distance ridden since the last fuel fill, taken from the live odometer. The
 * imported fuel log stops at its last fill date; the cluster keeps reporting
 * odometer every day we connect, so this is the missing tail for the current
 * month. Never negative (an odo reset or stale reading must not shrink a bar).
 */
internal fun odometerKmSinceLastFill(
    lastFillOdometerKm: Double?,
    currentOdometerKm: Double,
): Double = if (lastFillOdometerKm != null && currentOdometerKm > lastFillOdometerKm)
    currentOdometerKm - lastFillOdometerKm else 0.0

/**
 * Monthly kilometres for the history bars. Past months are deliberately left
 * exactly as they were: fuel-log odo deltas where the import covers the month,
 * otherwise the sum of connected rides. Only the CURRENT month is refreshed
 * from both sources - imported fuel kilometres plus the live odometer tail
 * since the last fill (and never less than the connected rides already known).
 *
 * No double counting: the odometer tail starts at the last fill's odometer,
 * which is already the end of the imported fuel coverage.
 *
 * @return month key (YEAR*100+MONTH) to kilometres, for all six keys.
 */
internal fun monthlyDistanceKm(
    rides: List<Ride>,
    fuelMonths: List<FuelMonth>,
    lastFillOdometerKm: Double?,
    currentOdometerKm: Double,
    nowMs: Long,
): Map<Int, Double> {
    val keys = lastMonthKeys(6, nowMs)
    val currentKey = monthKeyFor(nowMs)
    val fuelByKey = fuelMonths.associateBy { it.key }
    val rideByKey = rides.groupingBy { monthKeyFor(it.startedAt) }
        .fold(0.0) { sum, ride -> sum + ride.distanceKm }
    val odoTail = odometerKmSinceLastFill(lastFillOdometerKm, currentOdometerKm)

    return keys.associateWith { key ->
        val fromFuel = fuelByKey[key]?.km
        val fromRides = rideByKey[key] ?: 0.0
        if (key != currentKey) {
            // Frozen history - do not rewrite past months.
            fromFuel ?: fromRides
        } else {
            // Current month: fuel base + odo since the last fill, floored by
            // the rides we actually recorded so an incomplete import cannot
            // shrink the bar.
            maxOf((fromFuel ?: 0.0) + odoTail, fromRides)
        }
    }
}

private val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
