package com.jupiterconnect.session

import com.jupiterconnect.protocol.Telemetry

/** Cold-start warm-up phases shown on the Home tab. */
enum class WarmupPhase { STARTING, WARMING, READY }

data class WarmupState(
    val phase: WarmupPhase = WarmupPhase.READY,
    val cold: Boolean = false,
    val progress: Int = 100,
    val targetC: Int = 0,
    val startTemp: Int = 0,
    val ambientC: Int = 0,
    val slopeCPerMin: Double = 0.0,
    val message: String = "Engine off",
    /** False while the cluster sends no temperature data (all readings 0). */
    val hasEngineData: Boolean = false,
)

/**
 * Decides whether a run is a cold start from engine vs intake temperature,
 * then tracks warm-up until the target, a plateau, or an 8 minute timeout.
 *
 * Temperature is used instead of "first start of the day" so it still works
 * when the app missed the previous shutdown. All thresholds are starting
 * guesses and should be calibrated from real logs.
 */
class ColdStartTracker(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var phase = WarmupPhase.READY
    private var cold = false
    private var runStartMs = 0L
    private var lastRunMs = 0L
    private var startTemp = 0
    private var ambient = 0
    private var target = 0
    private var lastSampleMs = 0L
    private var lastSlope = 0.0
    private val samples = ArrayDeque<Pair<Long, Int>>()

    /** Loaded from / saved to prefs so a missed shutdown does not reset a session. */
    fun setLastRunMs(value: Long) { lastRunMs = value }
    fun getLastRunMs(): Long = lastRunMs

    fun update(t: Telemetry): WarmupState {
        val now = nowMs()
        val engineOn = t.rpm >= RUN_RPM || t.speedKmh >= 1
        // The cluster reports every temperature as 0 until it has sent a real
        // engine frame. Starting a session from those zeros produced
        // "Warm start, ready · target 0°C start 0°C ambient 0°C", which is
        // misleading. Wait for real readings before claiming a phase.
        if (t.engineTempC <= 0 || t.intakeTempC <= 0) {
            return WarmupState(
                message = if (engineOn) "No engine data" else "Engine off",
                hasEngineData = false,
            )
        }
        if (!engineOn) return snapshot(t, engineOn = false)

        val gap = if (lastRunMs == 0L) null else now - lastRunMs
        if (gap == null || gap > SESSION_GAP_MS) startSession(t, now, gap)
        lastRunMs = now

        if (cold && phase != WarmupPhase.READY) advance(t, now)
        return snapshot(t, engineOn = true)
    }

    private fun startSession(t: Telemetry, now: Long, gapMs: Long?) {
        ambient = t.intakeTempC
        startTemp = t.engineTempC
        runStartMs = now
        samples.clear()
        lastSampleMs = 0L
        lastSlope = 0.0
        // Unknown gap (first run or app missed shutdown): trust temperature only.
        cold = (startTemp - ambient) <= COLD_DELTA_C &&
            (gapMs == null || gapMs >= MIN_COLD_GAP_MS)
        target = (ambient + TARGET_ABOVE_AMBIENT).coerceIn(TARGET_MIN, TARGET_MAX)
        phase = if (cold) WarmupPhase.STARTING else WarmupPhase.READY
    }

    private fun advance(t: Telemetry, now: Long) {
        val elapsed = now - runStartMs
        if (now - lastSampleMs >= SAMPLE_MS) {
            samples.addLast(now to t.engineTempC)
            lastSampleMs = now
            while (samples.isNotEmpty() && now - samples.first().first > SLOPE_WINDOW_MS) {
                samples.removeFirst()
            }
        }
        lastSlope = slopeCPerMin()
        phase = if (elapsed < STARTING_MS) WarmupPhase.STARTING else WarmupPhase.WARMING

        val reached = t.engineTempC >= target
        val plateau = elapsed >= MIN_WARM_MS &&
            t.engineTempC - startTemp >= MIN_RISE_C &&
            lastSlope < PLATEAU_C_PER_MIN
        if (elapsed >= MIN_WARM_MS && (reached || plateau) || elapsed >= MAX_WARM_MS) {
            phase = WarmupPhase.READY
        }
    }

    private fun slopeCPerMin(): Double {
        if (samples.size < 4) return Double.MAX_VALUE // not enough data yet
        val (t0, c0) = samples.first()
        val (t1, c1) = samples.last()
        val mins = (t1 - t0) / 60_000.0
        return if (mins <= 0) Double.MAX_VALUE else (c1 - c0) / mins
    }

    private fun snapshot(t: Telemetry, engineOn: Boolean): WarmupState {
        val span = (target - startTemp).coerceAtLeast(1)
        val progress = when {
            phase == WarmupPhase.READY -> 100
            else -> ((t.engineTempC - startTemp) * 100 / span).coerceIn(0, 99)
        }
        val gentleBreach = engineOn && phase != WarmupPhase.READY &&
            (t.rpm > HARD_RPM || t.speedKmh > GENTLE_KMH)
        val msg = when {
            !engineOn -> "Engine off"
            gentleBreach -> "Ease off, still warming"
            phase == WarmupPhase.STARTING -> "Let it idle"
            phase == WarmupPhase.WARMING -> "Ride gently, $progress% warm"
            cold -> "Ready to go"
            else -> "Warm start, ready"
        }
        return WarmupState(
            phase = phase, cold = cold, progress = progress, targetC = target,
            startTemp = startTemp, ambientC = ambient,
            slopeCPerMin = if (lastSlope == Double.MAX_VALUE) 0.0 else lastSlope,
            message = msg,
            hasEngineData = true,
        )
    }

    private companion object {
        const val RUN_RPM = 500
        const val SESSION_GAP_MS = 10 * 60_000L
        const val MIN_COLD_GAP_MS = 3 * 60 * 60_000L
        const val COLD_DELTA_C = 12
        const val TARGET_ABOVE_AMBIENT = 35
        const val TARGET_MIN = 55
        const val TARGET_MAX = 70
        // Air-cooled 124.8 cc ETFi: 30 s idle, then ride gently for ~30 s.
        const val STARTING_MS = 30_000L
        const val MIN_WARM_MS = 60_000L
        const val MAX_WARM_MS = 3 * 60_000L
        const val SAMPLE_MS = 15_000L
        const val SLOPE_WINDOW_MS = 2 * 60_000L
        const val PLATEAU_C_PER_MIN = 0.5
        const val MIN_RISE_C = 8
        const val HARD_RPM = 6000
        const val GENTLE_KMH = 40
    }
}
