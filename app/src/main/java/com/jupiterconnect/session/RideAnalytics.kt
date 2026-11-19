package com.jupiterconnect.session

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lightweight, explainable analytics built from data the app already stores.
 *
 * With roughly six months of fills/rides these are TRENDS AND RANGES, not
 * predictions. No regressions, no precise forecasts: every figure is bounded
 * and every estimate needs a minimum sample count before it is used.
 */
object RideAnalytics {

    /** Minimum rides before a personal correction is trusted. */
    private const val MIN_ETA_RIDES = 5
    private const val ETA_MIN_FACTOR = 0.8
    private const val ETA_MAX_FACTOR = 1.5

    /**
     * Ratio of real ride time to the ETA Maps published at ride start.
     * The median is used (one traffic-jam outlier cannot skew it), and the
     * result is clamped so a weird ride never moves arrival times wildly.
     */
    fun etaFactor(rides: List<Ride>): Double {
        val ratios = rides.mapNotNull { ride ->
            if (ride.mapsEtaMinutes > 0 && ride.durationMin > 0)
                ride.durationMin.toDouble() / ride.mapsEtaMinutes
            else null
        }.sorted()
        if (ratios.size < MIN_ETA_RIDES) return 1.0
        val median = ratios[ratios.size / 2]
        return median.coerceIn(ETA_MIN_FACTOR, ETA_MAX_FACTOR)
    }

    fun adjustedEta(mapsEtaMinutes: Int, factor: Double): Int =
        if (mapsEtaMinutes <= 0) -1
        else max(1, (mapsEtaMinutes * factor).roundToInt())

    /**
     * Personal correction from real navigation sessions. Each sample pairs the
     * ETA Maps published at route start with the minutes it actually took, so
     * a route that begins mid-ride still counts. Falls back to whole-ride data
     * from older builds until enough sessions have been recorded.
     */
    fun personalEtaFactor(
        sessions: List<Pair<Int, Int>>,
        rides: List<Ride>,
    ): Double {
        if (sessions.size >= MIN_ETA_RIDES) {
            val ratios = sessions.map { (eta, actual) -> actual.toDouble() / eta }.sorted()
            return ratios[ratios.size / 2].coerceIn(ETA_MIN_FACTOR, ETA_MAX_FACTOR)
        }
        return etaFactor(rides)
    }

    data class MileageTrend(
        val ewma: Double,
        val mean: Double,
        val sd: Double,
        val latest: Double,
        val low: Double,
        val high: Double,
        /** True only when the newest fill is >2 SD below the historical band. */
        val drop: Boolean,
        val samples: Int,
    )

    /** Exponentially weighted km/L plus a wide band from all real fills. */
    fun mileageTrend(fills: List<FuelFill>): MileageTrend? {
        val values = fills.map { it.mileageKmpl }.filter { it > 0 }
        if (values.size < 8) return null
        var ewma = values.first()
        for (v in values.drop(1)) ewma = 0.25 * v + 0.75 * ewma // alpha = 0.25
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        val latest = values.last()
        return MileageTrend(
            ewma = ewma, mean = mean, sd = sd, latest = latest,
            low = max(0.0, mean - 2 * sd), high = mean + 2 * sd,
            drop = latest < mean - 2 * sd && sd > 0,
            samples = values.size,
        )
    }

    data class EcoScore(
        val score: Int,
        val hardLaunches: Int,
        val heavyThrottleShare: Double,
        val idleShare: Double,
        val highRpm: Boolean,
        val samples: Int,
    )

    /** 0-100 style score: fewer hard launches, heavy throttle, high RPM and idle. */
    fun ecoScore(ride: Ride): EcoScore {
        val speeds = ride.speedSamples
        val throttles = ride.throttleSamples
        val n = minOf(speeds.size, throttles.size)
        var hard = 0
        var heavy = 0
        for (i in 0 until n) {
            if (throttles[i] > 70) heavy++
            val previous = if (i > 0) speeds[i - 1] else 0
            if (throttles[i] > 70 && speeds[i] < 20 && previous < 12) hard++
        }
        val heavyShare = if (n > 0) heavy.toDouble() / n else 0.0
        val durationMs = (ride.durationMin * 60_000L).coerceAtLeast(1L)
        val idleShare = (ride.idleMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        val highRpm = ride.maxRpm >= 5500
        val penalty = maxOf(0.0, heavyShare * 30 + idleShare * 25 +
            minOf(20.0, hard * 2.0) + if (highRpm) 12.0 else 0.0)
        return EcoScore(
            score = (100 - penalty).roundToInt().coerceIn(0, 100),
            hardLaunches = hard, heavyThrottleShare = heavyShare,
            idleShare = idleShare, highRpm = highRpm, samples = n,
        )
    }

    data class ServiceAdvice(
        val odometerIntervalKm: Int,
        val suggestedKm: Int,
        val severe: Boolean,
        val reasons: List<String>,
    )

    /**
     * Severe use (short trips, cold-ish starts, long idle) advances the service
     * reminder. This is a heuristic nudge, never a mileage prediction.
     */
    fun serviceAdvice(rides: List<Ride>, currentOdometerKm: Double): ServiceAdvice {
        val shortTrips = rides.count { it.distanceKm in 0.31..3.0 }
        val longIdle = rides.count { it.idleMin >= 10 }
        val coldish = rides.count { it.rideKind == "Morning ride" && it.distanceKm > 0.3 }
        val reasons = buildList {
            if (shortTrips >= 6) add("$shortTrips short trips under 3 km")
            if (longIdle >= 4) add("$longIdle rides with 10+ min idling")
            if (coldish >= 8) add("$coldish morning rides likely from cold")
        }
        val reduction = (0.04 * shortTrips + 0.05 * longIdle + 0.02 * coldish)
            .coerceAtMost(0.4)
        val interval = 5_000
        return ServiceAdvice(
            odometerIntervalKm = interval,
            suggestedKm = (interval * (1 - reduction)).roundToInt(),
            severe = reasons.isNotEmpty(),
            reasons = reasons,
        )
    }
}

/**
 * One-dimensional Kalman fusion of cluster speed with phone GPS speed.
 * Produces a smoother speed and a slowly learned cluster-vs-GPS offset (the
 * speedometer error). GPS readings are optional: with none, the cluster value
 * passes through smoothly.
 */
class SpeedFusion(
    private val processNoise: Double = 0.25,
    private val clusterVariance: Double = 1.0,
) {
    private var estimate = 0.0
    private var variance = 4.0
    private var biasSum = 0.0
    private var biasCount = 0
    private var samples = 0

    data class Fused(val kmh: Int, val errorKmh: Double, val samples: Int)

    fun reset() {
        estimate = 0.0
        variance = 4.0
        biasSum = 0.0
        biasCount = 0
        samples = 0
    }

    fun update(clusterKmh: Double, gpsKmh: Double?, gpsAccuracyM: Double? = null): Fused {
        // Predict.
        variance += processNoise
        // GPS is trusted in proportion to its reported accuracy.
        if (gpsKmh != null && gpsKmh >= 0) {
            val r = max(2.0, (gpsAccuracyM ?: 5.0)) * max(2.0, (gpsAccuracyM ?: 5.0))
            val k = variance / (variance + r)
            estimate += k * (gpsKmh - estimate)
            variance *= (1 - k)
            if (clusterKmh >= 3 && gpsKmh >= 3) {
                biasSum += clusterKmh - gpsKmh
                biasCount++
            }
        }
        // Cluster reading: cheap, frequent, but may be biased (speedometer error).
        val kc = variance / (variance + clusterVariance)
        estimate += kc * (clusterKmh - estimate)
        variance *= (1 - kc)
        samples++
        return Fused(
            kmh = estimate.coerceAtLeast(0.0).roundToInt(),
            errorKmh = if (biasCount >= 10) biasSum / biasCount else 0.0,
            samples = samples,
        )
    }
}
