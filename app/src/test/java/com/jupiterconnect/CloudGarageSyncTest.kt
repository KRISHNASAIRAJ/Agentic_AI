package com.jupiterconnect

import com.jupiterconnect.session.FillRow
import com.jupiterconnect.session.ServiceRecord
import com.jupiterconnect.session.mapFillRows
import com.jupiterconnect.session.nextServiceFromRecords
import com.jupiterconnect.session.totalServicePaise
import com.jupiterconnect.sync.shouldReportOdo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure mapping/throttle logic only - no Android classes are touched. */
class CloudGarageSyncTest {

    @Test
    fun maps_cloud_rows_and_derives_distance_from_odometer_order() {
        // Rows arrive date-ascending but are not odometer-ascending.
        val rows = listOf(
            FillRow("2026-01-01T09:10:00+05:30", 1000.0, 5.0, 50_000L, "HP"),
            FillRow("2026-01-02T09:10:00+05:30", 900.0, 4.0, 40_000L, "BPCL"),
            FillRow("2026-01-03T09:10:00+05:30", 800.0, 4.0, 40_000L, "IOCL"),
        )
        val fills = mapFillRows(rows)

        assertEquals(3, fills.size)
        // Date order and numbering are preserved from the cloud rows.
        assertEquals(1, fills[0].no)
        assertEquals("2026-01-01", fills[0].dateIst)
        assertEquals("HP", fills[0].station)
        // Lowest odometer has no previous reading.
        assertEquals(0.0, fills[2].distanceKm, 0.001)
        assertEquals(0.0, fills[2].mileageKmpl, 0.001)
        // 900 -> 1000 = 100 km over 5 L.
        assertEquals(100.0, fills[0].distanceKm, 0.001)
        assertEquals(20.0, fills[0].mileageKmpl, 0.001)
        // 800 -> 900 = 100 km over 4 L.
        assertEquals(100.0, fills[1].distanceKm, 0.001)
        assertEquals(25.0, fills[1].mileageKmpl, 0.001)
    }

    @Test
    fun clamps_non_positive_distance_and_zero_liters() {
        val rows = listOf(
            FillRow("2026-02-01", 500.0, 4.0, 100L, ""),
            FillRow("2026-02-02", 500.0, 4.0, 100L, ""), // repeated odometer
            FillRow("2026-02-03", 0.0, 5.0, 100L, ""),   // missing odometer
            FillRow("2026-02-04", 600.0, 0.0, 100L, ""), // no litres
        )
        val fills = mapFillRows(rows)
        // A zero/missing odometer sorts first; it cannot yield a negative delta.
        assertEquals(0.0, fills[2].distanceKm, 0.001)
        assertEquals(500.0, fills[0].distanceKm, 0.001) // 0 -> 500
        assertEquals(0.0, fills[1].distanceKm, 0.001)  // duplicate clamps
        assertEquals(0.0, fills[1].mileageKmpl, 0.001)
        assertEquals(100.0, fills[3].distanceKm, 0.001)
        assertEquals(0.0, fills[3].mileageKmpl, 0.001) // divide-by-zero guarded
    }

    @Test
    fun next_service_uses_only_general_service_records() {
        val records = listOf(
            ServiceRecord("2026-01-10", "Oil change", 20_000, 50_000L, ""),
            ServiceRecord("2026-02-01", "General service", 22_000, 110_000L, ""),
            ServiceRecord("2026-03-01", "General Service", 25_000, 120_000L, ""),
        )
        val next = nextServiceFromRecords(records, 26_000.0)
        assertEquals(25_000, next.lastKm)
        assertEquals(28_000, next.dueKm)
        assertEquals(2_000, next.kmLeft)
    }

    @Test
    fun next_service_is_empty_without_a_general_service() {
        val next = nextServiceFromRecords(
            listOf(ServiceRecord("2026-03-01", "Brake pads", 25_000, 90_000L, "")),
            26_000.0,
        )
        assertNull(next.lastKm)
        assertNull(next.dueKm)
        assertNull(next.kmLeft)
    }

    @Test
    fun total_service_paise_sums_all_records() {
        val records = listOf(
            ServiceRecord("2026-01-10", "Oil change", 20_000, 50_000L, ""),
            ServiceRecord("2026-03-01", "General Service", 25_000, 120_000L, ""),
        )
        assertEquals(170_000L, totalServicePaise(records))
        assertEquals(0L, totalServicePaise(emptyList()))
    }

    @Test
    fun odo_report_uses_one_kilometre_or_thirty_second_threshold() {
        assertFalse(shouldReportOdo(0, 0, 100_000L, 0L))       // no reading
        assertTrue(shouldReportOdo(100, 0, 100_000L, 0L))     // never reported
        assertTrue(shouldReportOdo(101, 100, 129_999L, 100_000L)) // 1 km
        assertFalse(shouldReportOdo(100, 100, 129_999L, 100_000L))
        assertTrue(shouldReportOdo(100, 100, 130_000L, 100_000L)) // 30 s
        assertFalse(shouldReportOdo(99, 100, 129_999L, 100_000L)) // no movement yet
    }
}
