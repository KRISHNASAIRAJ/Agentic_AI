package com.jupiterconnect.session

import android.content.Context
import android.content.SharedPreferences
import com.jupiterconnect.sync.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The one vehicle both apps agree on; Meridian seeds the same name. */
const val JUPITER_VEHICLE = "Jupiter 125"

/** A service record pulled from Meridian's maintenance_logs (amount in paise). */
data class ServiceRecord(
    val dateIst: String,
    val serviceType: String,
    val odometerKm: Int?,
    val amountPaise: Long,
    val notes: String,
)

/** An open/reported service issue pulled from vehicle_service_issues. */
data class ServiceIssue(
    val title: String,
    val notes: String,
    val severity: String,
    val status: String,
    val odometerKm: Int?,
    val atLabel: String,
)

/** Result of the general-service interval calculation (all values nullable). */
data class NextService(val lastKm: Int?, val dueKm: Int?, val kmLeft: Int?)

/** One cloud fuel row, reduced to primitives so mapping is pure and testable. */
internal data class FillRow(
    val date: String,
    val odometerKm: Double,
    val liters: Double,
    val amountPaise: Long,
    val station: String,
)

private val GENERAL_SERVICE_TYPES = setOf(
    "General Service", "General service", "general service", "general",
)

/**
 * Map cloud fuel rows into [FuelFill]s. Distance and mileage come from
 * consecutive readings in odometer order (not date order); the returned list
 * keeps the cloud's date-ascending order. Non-positive deltas clamp to zero so
 * a null/zero odometer or a repeated reading cannot produce negative distance.
 */
internal fun mapFillRows(rows: List<FillRow>): List<FuelFill> {
    val distance = DoubleArray(rows.size)
    val mileage = DoubleArray(rows.size)
    val byOdometer = rows.indices.sortedBy { rows[it].odometerKm }
    for (i in 1 until byOdometer.size) {
        val curr = byOdometer[i]
        val prev = byOdometer[i - 1]
        val delta = rows[curr].odometerKm - rows[prev].odometerKm
        if (delta > 0) {
            distance[curr] = delta
            val liters = rows[curr].liters
            if (liters > 0) mileage[curr] = delta / liters
        }
    }
    return rows.mapIndexed { index, row ->
        FuelFill(
            no = index + 1,
            dateIst = row.date.take(10),
            odometerKm = row.odometerKm,
            distanceKm = distance[index],
            liters = row.liters,
            amountPaise = row.amountPaise,
            mileageKmpl = mileage[index],
            station = row.station,
        )
    }
}

/**
 * Next general service from real cloud records. Only general-service rows
 * count (specialist work does not reset the 3000 km interval); the most recent
 * by IST date wins.
 */
internal fun nextServiceFromRecords(
    records: List<ServiceRecord>,
    currentKm: Double,
    intervalKm: Int = 3000,
): NextService {
    val last = records
        .filter { it.serviceType.trim() in GENERAL_SERVICE_TYPES }
        .maxByOrNull { it.dateIst }
    val lastKm = last?.odometerKm
    val dueKm = lastKm?.let { it + intervalKm }
    val kmLeft = dueKm?.let { (it - currentKm).toInt() }
    return NextService(lastKm = lastKm, dueKm = dueKm, kmLeft = kmLeft)
}

/** Total lifetime service spend in paise. */
internal fun totalServicePaise(records: List<ServiceRecord>): Long =
    records.sumOf { it.amountPaise }

/**
 * Read-only mirror of Meridian's garage tables. Supabase is the source of
 * truth: [pull] overwrites the local cache on every reconnect, while [init]
 * restores the last successful pull for offline viewing. Nothing here writes
 * fuel or service rows back to the cloud.
 */
object CloudGarageStore {
    private const val PREFS = "jupiter_cloud_garage"
    private const val KEY_FILLS = "fills"
    private const val KEY_MAINTENANCE = "maintenance"
    private const val KEY_ISSUES = "issues"
    private const val KEY_ODO = "odometer_km"
    private const val KEY_LABEL = "sync_label"

    private const val FILLS_QUERY =
        "select=id,date,odometer,liters,amount,price_per_liter,station,note&order=date.asc"
    private const val MAINTENANCE_QUERY =
        "select=id,date,service_type,odometer,amount,notes&order=date.desc"
    private const val ISSUES_QUERY =
        "select=title,notes,severity,status,odometer_km,created_at,fixed_at&order=created_at.desc"
    private val odoQuery =
        "select=vehicle,current_km,recorded_at,source&vehicle=eq." +
            JUPITER_VEHICLE.replace(" ", "%20")

    private var prefs: SharedPreferences? = null

    /** Fuel is owned by FuelLogStore, which the Rides screens already collect. */
    val fills: StateFlow<List<FuelFill>> get() = FuelLogStore.fills

    private val _maintenance = MutableStateFlow<List<ServiceRecord>>(emptyList())
    val maintenance: StateFlow<List<ServiceRecord>> = _maintenance.asStateFlow()

    private val _issues = MutableStateFlow<List<ServiceIssue>>(emptyList())
    val issues: StateFlow<List<ServiceIssue>> = _issues.asStateFlow()

    private val _odometerKm = MutableStateFlow(0.0)
    val odometerKm: StateFlow<Double> = _odometerKm.asStateFlow()

    private val _lastSyncLabel = MutableStateFlow("Not synced yet")
    val lastSyncLabel: StateFlow<String> = _lastSyncLabel.asStateFlow()

    /** Restore the last good pull so the UI has data before a network round-trip. */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs ?: return
        runCatching {
            p.getString(KEY_FILLS, null)?.let {
                FuelLogStore.setFills(mapFillRows(fillRows(JSONArray(it))))
            }
            p.getString(KEY_MAINTENANCE, null)?.let { _maintenance.value = serviceRecords(JSONArray(it)) }
            p.getString(KEY_ISSUES, null)?.let { _issues.value = serviceIssues(JSONArray(it)) }
            if (p.contains(KEY_ODO)) _odometerKm.value = p.getFloat(KEY_ODO, 0f).toDouble()
            p.getString(KEY_LABEL, null)?.let { _lastSyncLabel.value = it }
        }
    }

    /** Pull all four garage tables. Never throws; failure is a short label. */
    suspend fun pull(token: String) {
        val failed = mutableListOf<String>()
        try {
            val fillJson = SupabaseClient.getTable("fuel_fills", FILLS_QUERY, token)
            if (fillJson == null) failed += "fuel"
            val maintJson = SupabaseClient.getTable("maintenance_logs", MAINTENANCE_QUERY, token)
            if (maintJson == null) failed += "service"
            val issueJson = SupabaseClient.getTable("vehicle_service_issues", ISSUES_QUERY, token)
            if (issueJson == null) failed += "issues"
            val odoJson = SupabaseClient.getTable("vehicle_odometer", odoQuery, token)
            if (odoJson == null) failed += "odometer"

            // A failed fetch must never wipe the last good offline cache: keep
            // the existing value for that table and persist only real results.
            val fills = if (fillJson != null) mapFillRows(fillRows(fillJson))
            else FuelLogStore.fills.value
            FuelLogStore.setFills(fills)
            val records = if (maintJson != null) serviceRecords(maintJson).also {
                _maintenance.value = it
            } else _maintenance.value
            val issues = if (issueJson != null) serviceIssues(issueJson).also {
                _issues.value = it
            } else _issues.value
            if (odoJson != null) {
                _odometerKm.value = odoJson.optJSONObject(0)?.optDouble("current_km", 0.0) ?: 0.0
            }

            val label = if (failed.isEmpty()) "Synced ${timeLabel()}"
            else "Sync failed: ${failed.joinToString(", ")}"
            _lastSyncLabel.value = label
            persist(fills, records, issues, _odometerKm.value, label)
        } catch (e: Exception) {
            _lastSyncLabel.value = "Sync error: ${e.message?.take(48) ?: "unknown"}"
        }
    }

    private fun persist(
        fills: List<FuelFill>,
        records: List<ServiceRecord>,
        issues: List<ServiceIssue>,
        km: Double,
        label: String,
    ) {
        val p = prefs ?: return
        p.edit()
            .putString(KEY_FILLS, writeFills(fills))
            .putString(KEY_MAINTENANCE, writeRecords(records))
            .putString(KEY_ISSUES, writeIssues(issues))
            .putFloat(KEY_ODO, km.toFloat())
            .putString(KEY_LABEL, label)
            .apply()
    }

    private fun timeLabel(): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        fmt.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return fmt.format(Date())
    }

    private fun fillRows(array: JSONArray): List<FillRow> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(FillRow(
                date = o.optString("date"),
                odometerKm = o.optDouble("odometer", 0.0),
                liters = o.optDouble("liters", 0.0),
                amountPaise = o.optLong("amount", 0L),
                station = o.optString("station"),
            ))
        }
    }

    private fun writeFills(fills: List<FuelFill>): String {
        val array = JSONArray()
        fills.forEach { f ->
            array.put(JSONObject().apply {
                put("date", f.dateIst)
                put("odometer", f.odometerKm)
                put("liters", f.liters)
                put("amount", f.amountPaise)
                put("station", f.station)
            })
        }
        return array.toString()
    }

    private fun serviceRecords(array: JSONArray): List<ServiceRecord> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(ServiceRecord(
                dateIst = o.optString("date").take(10),
                serviceType = o.optString("service_type"),
                odometerKm = if (o.isNull("odometer")) null
                    else o.optDouble("odometer", 0.0).toInt(),
                amountPaise = o.optLong("amount", 0L),
                notes = o.optString("notes"),
            ))
        }
    }

    private fun writeRecords(records: List<ServiceRecord>): String {
        val array = JSONArray()
        records.forEach { r ->
            array.put(JSONObject().apply {
                put("date", r.dateIst)
                put("service_type", r.serviceType)
                put("odometer", r.odometerKm ?: JSONObject.NULL)
                put("amount", r.amountPaise)
                put("notes", r.notes)
            })
        }
        return array.toString()
    }

    private fun serviceIssues(array: JSONArray): List<ServiceIssue> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(ServiceIssue(
                title = o.optString("title"),
                notes = o.optString("notes"),
                severity = o.optString("severity"),
                status = o.optString("status"),
                odometerKm = if (o.isNull("odometer_km")) null
                    else o.optDouble("odometer_km", 0.0).toInt(),
                atLabel = o.optString("created_at").take(10),
            ))
        }
    }

    private fun writeIssues(issues: List<ServiceIssue>): String {
        val array = JSONArray()
        issues.forEach { i ->
            array.put(JSONObject().apply {
                put("title", i.title)
                put("notes", i.notes)
                put("severity", i.severity)
                put("status", i.status)
                put("odometer_km", i.odometerKm ?: JSONObject.NULL)
                put("created_at", i.atLabel)
            })
        }
        return array.toString()
    }
}
