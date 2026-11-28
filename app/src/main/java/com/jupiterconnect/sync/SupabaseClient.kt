package com.jupiterconnect.sync

import android.content.Context
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

    /** Last REST failure, surfaced in the Rides header so sync errors are diagnosable. */
    @Volatile var lastError: String? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isSignedIn(): Boolean = !currentToken().isNullOrBlank()
    fun currentToken(): String? = prefs()?.getString(KEY_TOKEN, null)
    fun currentEmail(): String? = prefs()?.getString(KEY_EMAIL, null)

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Supabase access tokens expire after about an hour. Sign-in saved a refresh
     * token but nothing ever used it, so every REST call started failing with
     * 401 hours later. Blocking is safe here - all callers use Dispatchers.IO.
     */
    private fun refreshTokenBlocking(): String? {
        val refresh = prefs()?.getString(KEY_REFRESH, null) ?: return null
        val body = JSONObject().put("refresh_token", refresh)
        val response = request(
            "$URL_BASE/auth/v1/token?grant_type=refresh_token", "POST", body,
            authenticated = false,
        ) ?: return null
        if (response.has("error")) return null
        val token = response.optString("access_token")
        if (token.isBlank()) return null
        prefs()?.edit()
            ?.putString(KEY_TOKEN, token)
            ?.putString(KEY_REFRESH, response.optString("refresh_token").ifBlank { refresh })
            ?.putString(KEY_USER, response.optJSONObject("user")?.optString("id")
                ?: prefs()?.getString(KEY_USER, null))
            ?.apply()
        return token
    }

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
        // request() reports transport failures and HTTP errors as an "error"
        // object. Treating those as success would mark the ride synced and
        // permanently drop it from the retry queue.
        if (response == null || response.has("error")) {
            lastError?.let { "Ride upload failed: $it" } ?: "Ride upload failed"
        } else null
    }

    /**
     * Pull this user's ride history back from `jc_rides`. Rides are uploaded on
     * the bike, but a phone reset/reinstall would otherwise lose every trip —
     * the cloud is the source of truth, so restore before the next upload pass.
     * Returns null only on a transport/HTTP failure, so callers can retry.
     */
    suspend fun downloadRides(token: String): List<Ride>? = withContext(Dispatchers.IO) {
        val rows = getTable(
            "jc_rides", "select=*&order=started_at.desc&limit=200", token,
        ) ?: return@withContext null
        (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                // PostgREST emits "+00:00" offsets; older Android java.time
                // builds reject those in Instant.parse, so use OffsetDateTime.
                val started = java.time.OffsetDateTime.parse(o.optString("started_at"))
                    .toInstant().toEpochMilli()
                val ended = java.time.OffsetDateTime.parse(o.optString("ended_at"))
                    .toInstant().toEpochMilli()
                fun intArray(key: String): List<Int> {
                    val a = o.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).mapNotNull { j -> a.opt(j)?.toString()?.toIntOrNull() }
                }
                val bands = o.optJSONArray("band_ms")
                Ride(
                    // client_id is the original epoch millis used to dedupe.
                    startedAt = o.optString("client_id").toLongOrNull() ?: started,
                    endedAt = ended,
                    distanceKm = o.optDouble("distance_km", 0.0),
                    avgSpeedKmh = o.optDouble("avg_speed_kmh", 0.0),
                    topSpeedKmh = o.optInt("top_speed_kmh", 0),
                    durationMin = o.optLong("duration_min", 0L),
                    bandMs = longArrayOf(
                        bands?.optLong(0) ?: 0L, bands?.optLong(1) ?: 0L,
                        bands?.optLong(2) ?: 0L, bands?.optLong(3) ?: 0L,
                    ),
                    idleMs = o.optLong("idle_ms", 0L),
                    maxRpm = o.optInt("max_rpm", 0),
                    speedSamples = intArray("speed_samples"),
                    throttleSamples = intArray("throttle_samples"),
                    mapsEtaMinutes = if (o.has("maps_eta_min")) o.optInt("maps_eta_min") else -1,
                )
            }.getOrNull()
        }
    }

    /** Read a table through PostgREST. RLS scopes rows to the signed-in user. */
    suspend fun getTable(
        table: String,
        query: String,
        token: String,
        retried: Boolean = false,
    ): JSONArray? = withContext(Dispatchers.IO) {
            val conn = URL("$URL_BASE/rest/v1/$table?$query").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("apikey", ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            try {
                val code = conn.responseCode
                if (code == 401 && !retried) {
                    val fresh = refreshTokenBlocking()
                    if (fresh != null) {
                        return@withContext getTable(table, query, fresh, retried = true)
                    }
                }
                if (code !in 200..299) {
                    val msg = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    lastError = "HTTP $code ${msg.take(90)}"
                    return@withContext null
                }
                lastError = null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) return@withContext JSONArray()
                runCatching { JSONArray(text) }.getOrElse {
                    lastError = "parse: ${it.message?.take(90)}"
                    null
                }
            } catch (e: Exception) {
                lastError = e.message?.take(90) ?: "network error"
                null
            } finally {
                conn.disconnect()
            }
        }

    /**
     * Report a live cluster odometer reading through the RPC. The cloud keeps
     * the monotonic greatest value and ignores manual reports shortly after a
     * cluster report, so retries are harmless.
     */
    suspend fun reportOdometer(
        vehicle: String,
        km: Int,
        source: String,
        token: String,
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("p_vehicle", vehicle)
            put("p_km", km)
            put("p_source", source)
            put("p_device_id", JSONObject.NULL)
        }
        val response = runCatching {
            request(
                "$URL_BASE/rest/v1/rpc/jc_report_odometer", "POST", body,
                authenticated = true, token = token,
            )
        }.getOrNull()
        if (response == null || response.has("error")) {
            response?.optString("msg").takeUnless { it.isNullOrBlank() }
                ?: "Odometer report failed"
        } else null
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
            if (response == null || response.has("error")) {
                lastError?.let { "App state upload failed: $it" } ?: "App state upload failed"
            } else null
        }

    /** Read this user's single app-state document (settings + counters). */
    suspend fun downloadAppState(token: String): JSONObject? = withContext(Dispatchers.IO) {
        val rows = getTable("jc_app_state", "select=*&limit=1", token) ?: return@withContext null
        rows.optJSONObject(0)
    }

    /** PostgREST accepts a JSON array for a batched upsert. */
    private fun requestArray(urlString: String, array: JSONArray, token: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlString).openConnection() as HttpURLConnection
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
            if (code !in 200..299) {
                lastError = "HTTP $code ${text.take(90)}"
                return JSONObject().put("error", code).put("msg", text.take(160))
            }
            lastError = null
            if (text.isBlank()) JSONObject()
            else runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        } catch (e: Exception) {
            lastError = "Offline: ${e.message?.take(60) ?: "no connection"}"
            JSONObject().put("error", -1).put("msg", lastError)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun request(
        urlString: String,
        method: String,
        body: JSONObject? = null,
        authenticated: Boolean,
        token: String? = null,
        prefer: String? = null,
        retried: Boolean = false,
    ): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlString).openConnection() as HttpURLConnection
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
            if (code == 401 && authenticated && !retried) {
                val fresh = refreshTokenBlocking()
                if (fresh != null) {
                    return request(urlString, method, body, authenticated, fresh, prefer, true)
                }
            }
            if (code !in 200..299) {
                lastError = "HTTP $code ${text.take(90)}"
                return JSONObject().put("error", code).put("msg", text.take(160))
            }
            lastError = null
            if (text.isBlank()) JSONObject()
            else runCatching { JSONArray(text).optJSONObject(0) ?: JSONObject(text) }
                .getOrDefault(JSONObject(text))
        } catch (e: Exception) {
            // Offline / timeout / DNS failures must never escape into a caller.
            // Callers see an ordinary error object and keep their cached data.
            lastError = "Offline: ${e.message?.take(60) ?: "no connection"}"
            JSONObject().put("error", -1).put("msg", lastError)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
