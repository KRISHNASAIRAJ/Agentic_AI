package com.jupiterconnect.debug

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records what happened after each optional cluster feature was tried, so
 * features that do nothing on a particular bike can be identified and removed
 * later. The log is persisted locally, mirrored into the per-connection frame
 * capture via [FrameLog], and also written to logcat ("JupiterVerify").
 */
object FeatureVerification {

    enum class Status { WORKING, NO_CHANGE, NO_FEEDBACK, APPLIED }

    data class Result(
        val feature: String,
        val status: Status,
        val detail: String,
        val time: String,
    )

    private const val PREFS = "jupiter_feature_verification"
    private const val KEY = "results"
    private const val MAX = 60
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var prefs: SharedPreferences? = null

    private val _results = MutableStateFlow<List<Result>>(emptyList())
    val results: StateFlow<List<Result>> = _results

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _results.value = (prefs?.getString(KEY, null).orEmpty())
            .split(";")
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("|", limit = 4)
                if (p.size != 4) null else runCatching {
                    Result(p[0], Status.valueOf(p[1]), p[2], p[3])
                }.getOrNull()
            }
            .takeLast(MAX)
    }

    fun log(feature: String, status: Status, detail: String, frameHex: String = "") {
        val entry = Result(feature, status, detail, format.format(Date()))
        _results.value = (_results.value + entry).takeLast(MAX).also { save(it) }
        val wire = if (frameHex.isEmpty()) "" else " frame=[$frameHex]"
        val line = "VERIFY $feature => ${status.name}: $detail$wire"
        FrameLog.note(line)
        Log.i("JupiterVerify", line)
    }

    /** For features the cluster never reports back (calibration, follow-me, ...). */
    fun unverifiable(feature: String, detail: String, frameHex: String = "") =
        log(feature, Status.NO_FEEDBACK, detail, frameHex)

    fun clear() {
        _results.value = emptyList()
        prefs?.edit()?.remove(KEY)?.apply()
        FrameLog.note("VERIFY log cleared")
    }

    private fun save(all: List<Result>) {
        val text = all.joinToString(";") { "${it.feature}|${it.status.name}|${it.detail}|${it.time}" }
        prefs?.edit()?.putString(KEY, text)?.apply()
    }
}
