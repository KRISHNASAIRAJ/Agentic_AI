package com.jupiterconnect

import com.jupiterconnect.session.FuelMonth
import com.jupiterconnect.session.Ride
import com.jupiterconnect.session.lastMonthKeys
import com.jupiterconnect.session.monthKeyFor
import com.jupiterconnect.session.monthlyDistanceKm
import com.jupiterconnect.session.odometerKmSinceLastFill
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MonthlyDistanceTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    private fun istTime(year: Int, month: Int, day: Int, hour: Int = 9): Long {
        val c = Calendar.getInstance(ist)
        c.clear()
        c.set(year, month, day, hour, 0, 0)
        return c.timeInMillis
    }

    private fun ride(ms: Long, km: Double) = Ride(ms, ms + 600_000L, km, 30.0, 50, 10)

    // Keys are YEAR * 100 + Calendar.MONTH (0-based): Nov 2026 = 202610.
    @Test
    fun month_keys_are_ist_and_oldest_first() {
        val now = istTime(2026, Calendar.NOVEMBER, 23)
        assertEquals(202610, monthKeyFor(now))
        val keys = lastMonthKeys(6, now)
        assertEquals(6, keys.size)
        assertEquals(202605, keys.first()) // June 2026
        assertEquals(202610, keys.last())  // November 2026
    }

    @Test
    fun ist_day_boundary_stays_in_november() {
        // 1 Dec 01:00 IST is still 30 Nov in UTC.
        assertEquals(202611, monthKeyFor(istTime(2026, Calendar.DECEMBER, 1, 1)))
    }

    @Test
    fun odometer_tail_is_clamped() {
        assertEquals(36.0, odometerKmSinceLastFill(5564.0, 5600.0), 0.001)
        assertEquals(0.0, odometerKmSinceLastFill(5564.0, 5564.0), 0.001)
        assertEquals(0.0, odometerKmSinceLastFill(5564.0, 5500.0), 0.001) // odo reset
        assertEquals(0.0, odometerKmSinceLastFill(null, 5600.0), 0.001)
    }

    @Test
    fun past_months_are_not_rewritten() {
        val now = istTime(2026, Calendar.NOVEMBER, 23)
        val fuel = listOf(FuelMonth(202609, 674.0, 0.0, 0L, 5)) // October
        val rides = listOf(ride(istTime(2026, Calendar.SEPTEMBER, 5), 900.0))
        val km = monthlyDistanceKm(rides, fuel, 5564.0, 5600.0, now)
        assertEquals(674.0, km[202609]!!, 0.001) // fuel wins over bigger ride total
    }

    @Test
    fun past_month_without_fuel_uses_rides() {
        val now = istTime(2026, Calendar.NOVEMBER, 23)
        val rides = listOf(
            ride(istTime(2026, Calendar.SEPTEMBER, 5), 11.0),
            ride(istTime(2026, Calendar.SEPTEMBER, 15), 22.0),
        )
        val km = monthlyDistanceKm(rides, emptyList(), null, 0.0, now)
        assertEquals(33.0, km[202608]!!, 0.001)
    }

    @Test
    fun current_month_adds_live_odometer_to_fuel_data() {
        val now = istTime(2026, Calendar.NOVEMBER, 23)
        val fuel = listOf(FuelMonth(202610, 918.0, 0.0, 0L, 6)) // November
        val rides = listOf(ride(istTime(2026, Calendar.NOVEMBER, 20), 5.3))
        val km = monthlyDistanceKm(rides, fuel, 5564.0, 5600.0, now)
        assertEquals(954.0, km[202610]!!, 0.001) // 918 + 36, not double counted with 5.3
    }

    @Test
    fun current_month_without_fuel_uses_max_of_odo_tail_and_rides() {
        val now = istTime(2026, Calendar.NOVEMBER, 23)
        val rides = listOf(ride(istTime(2026, Calendar.NOVEMBER, 20), 120.0))
        val km = monthlyDistanceKm(rides, emptyList(), 5564.0, 5600.0, now)
        assertEquals(120.0, km[202610]!!, 0.001) // 36 km odo tail must not shrink it
        val km2 = monthlyDistanceKm(rides, emptyList(), 5564.0, 5800.0, now)
        assertEquals(236.0, km2[202610]!!, 0.001)
    }

    @Test
    fun no_fills_uses_rides_everywhere() {
        val now = istTime(2026, Calendar.NOVEMBER, 23)
        val rides = listOf(ride(istTime(2026, Calendar.NOVEMBER, 20), 7.5))
        val km = monthlyDistanceKm(rides, emptyList(), null, 0.0, now)
        assertEquals(7.5, km[202610]!!, 0.001)
        assertEquals(0.0, km[202605]!!, 0.001)
    }
}
