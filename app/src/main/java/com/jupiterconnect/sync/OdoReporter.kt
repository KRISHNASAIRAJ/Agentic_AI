package com.jupiterconnect.sync

/**
 * Pure odometer-reporting decision, kept separate from ClusterService so the
 * throttle can be unit tested. A new report is due when the cluster has moved
 * at least a kilometre, or when 30 seconds have passed since the last report
 * (which keeps the cloud fresh while the bike is idle).
 */
internal fun shouldReportOdo(
    km: Int,
    lastKm: Int,
    nowMs: Long,
    lastMs: Long,
): Boolean {
    if (km <= 0) return false
    if (lastMs <= 0L) return true
    return km >= lastKm + 1 || nowMs - lastMs >= 30_000L
}
