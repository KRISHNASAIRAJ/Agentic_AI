package com.jupiterconnect.sync

import android.content.Context
import com.jupiterconnect.session.FuelFill
import com.jupiterconnect.session.Ride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Supabase REST + Auth client for Jupiter Connect. Uses the same
 * project and the same Supabase Auth users as Meridian. Only the publishable
 * anon key is embedded - Row Level Security, never a service key, protects data.
 * Plain HttpURLConnection/organization json keeps this dependency-free.
 */
object SupabaseClient {
    private const val URL_BASE = "https://rkmouoglorsnijmemmcd.supabase.co"
    private const val ANON_KEY = "sb_publishable_5QZUkFFNl0fCdj5peFruNA_4NEZ0H7f"

    private const val PREFS = "jupiter_supabase"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER = "user_id"
    private const val KEY_EMAIL = "email"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isSignedIn(): Boolean = !currentToken().isNullOrBlank()
    fun currentToken(): String? = prefs()?.getString(KEY_TOKEN, null)
    fun currentEmail(): String? = prefs()?.getString(KEY_EMAIL, null)

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Returns null on success, or a short error message. */
    suspend fun signIn(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }
        val response = request(
            "$URL_BASE/auth/v1/token?grant_type=password", "POST", body,
            authenticated = false,
        ) ?: return@withContext "Network error"
        val token = response.optString("access_token")
        if (token.isBlank()) {
            return@withContext response.optString("msg").ifBlank { "Sign in failed" }
        }
        prefs()?.edit()
            ?.putString(KEY_TOKEN, token)
            ?.putString(KEY_REFRESH, response.optString("refresh_token"))
            ?.putString(KEY_USER, response.optJSONObject("user")?.optString("id"))
            ?.putString(KEY_EMAIL, email.trim())
            ?.apply()
        null
    }

    suspend fun signUp(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }
        val response = request(
            "$URL_BASE/auth/v1/signup", "POST", body, authenticated = false,
        ) ?: return@withContext "Network error"
        if (response.has("error")) {
            return@withContext response.optString("msg").ifBlank { "Sign up failed" }
        }
        val token = response.optString("access_token")
        if (token.isNotBlank()) {
            prefs()?.edit()
                ?.putString(KEY_TOKEN, token)
                ?.putString(KEY_REFRESH, response.optString("refresh_token"))
                ?.putString(KEY_USER, response.optJSONObject("user")?.optString("id"))
                ?.putString(KEY_EMAIL, email.trim())
                ?.apply()
        }
        null
    }

    fun signOut() {
        prefs()?.edit()?.clear()?.apply()
    }

    /** Upload one ride; the (user, client_id) unique key makes retries safe. */
    suspend fun uploadRide(ride: Ride, token: String): String? = withContext(Dispatchers.IO) {
        val bands = JSONArray()
        ride.bandMs.forEach { bands.put(it) }
        val speeds = JSONArray()
        ride.speedSamples.forEach { speeds.put(it) }
        val throttles = JSONArray()
        ride.throttleSamples.forEach { throttles.put(it) }
        val body = JSONObject().apply {
            put("user_id", prefs()?.getString(KEY_USER, null))
            put("client_id", ride.startedAt.toString())
            put("started_at", java.time.Instant.ofEpochMilli(ride.startedAt).toString())
            put("ended_at", java.time.Instant.ofEpochMilli(ride.endedAt).toString())
            put("distance_km", ride.distanceKm)
            put("avg_speed_kmh", ride.avgSpeedKmh)
            put("top_speed_kmh", ride.topSpeedKmh)
            put("duration_min", ride.durationMin)
            put("band_ms", bands)
            put("idle_ms", ride.idleMs)
            put("max_rpm", ride.maxRpm)
            put("speed_samples", speeds)
            put("throttle_samples", throttles)
            put("maps_eta_min", ride.mapsEtaMinutes)
        }
        val response = request(
            "$URL_BASE/rest/v1/jc_rides", "POST", body,
            authenticated = true, token = token,
            prefer = "return=minimal,resolution=merge-duplicates",
        )
        if (response == null) "Ride upload failed" else null
    }

    /** Upsert every petrol fill from the bundled Meridian export in one call. */
    suspend fun uploadFuelLogs(fills: List<FuelFill>, token: String): String? =
        withContext(Dispatchers.IO) {
            if (fills.isEmpty()) return@withContext null
            val userId = prefs()?.getString(KEY_USER, null)
            val array = JSONArray()
            fills.forEach { f ->
                array.put(JSONObject().apply {
                    put("user_id", userId)
                    put("client_id", "f${f.no}_${f.dateIst}")
                    put("date_ist", f.dateIst)
                    put("odometer_km", f.odometerKm)
                    put("distance_km", f.distanceKm)
                    put("liters", f.liters)
                    put("amount_paise", f.amountPaise)
                    put("mileage_kmpl", f.mileageKmpl)
                    put("station", f.station)
                })
            }
            val response = requestArray(
                "$URL_BASE/rest/v1/jc_fuel_logs", array, token)
            if (response == null) "Fuel log upload failed" else null
        }

    /** Upsert the one JSON document holding settings and cumulative counters. */
    suspend fun uploadAppState(details: JSONObject, token: String): String? =
        withContext(Dispatchers.IO) {
            details.put("user_id", prefs()?.getString(KEY_USER, null))
            val response = request(
                "$URL_BASE/rest/v1/jc_app_state", "POST", details,
                authenticated = true, token = token,
                prefer = "return=minimal,resolution=merge-duplicates",
            )
            if (response == null) "App state upload failed" else null
        }

    /** PostgREST accepts a JSON array for a batched upsert. */
    private fun requestArray(urlString: String, array: JSONArray, token: String): JSONObject? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("apikey", ANON_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Prefer", "return=minimal,resolution=merge-duplicates")
        conn.doOutput = true
        conn.outputStream.use { it.write(array.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) return JSONObject().put("error", code).put("msg", text.take(160))
        return if (text.isBlank()) JSONObject()
        else runCatching { JSONObject(text) }.getOrDefault(JSONObject())
    }

    private fun request(
        urlString: String,
        method: String,
        body: JSONObject? = null,
        authenticated: Boolean,
        token: String? = null,
        prefer: String? = null,
    ): JSONObject? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("apikey", ANON_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (authenticated && !token.isNullOrBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        if (prefer != null) conn.setRequestProperty("Prefer", prefer)
        if (body != null) {
            conn.doOutput = true
            val out: OutputStream = conn.outputStream
            out.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) return JSONObject().put("error", code).put("msg", text.take(160))
        if (text.isBlank()) return JSONObject()
        return runCatching { JSONArray(text).optJSONObject(0) ?: JSONObject(text) }
            .getOrDefault(JSONObject(text))
    }
}
