package com.jupiterconnect.protocol

import java.util.Calendar
import kotlin.math.abs

private fun u8(b: Byte): Int = b.toInt() and 0xFF

private fun u16(b: ByteArray, at: Int): Int = (u8(b[at]) shl 8) or u8(b[at + 1])

private fun u24(b: ByteArray, at: Int): Int =
    (u8(b[at]) shl 16) or (u8(b[at + 1]) shl 8) or u8(b[at + 2])

/** Decode a 20-byte cluster frame into a fresh telemetry snapshot. */
fun decodeFrame(raw: ByteArray, current: Telemetry): Telemetry? {
    if (raw.size < 20) return null
    val start = u8(raw[0])
    if (start != 0x5A && start != 0x5B && start != 0x9B) return null
    val hex = raw.joinToString(" ") { "%02X".format(it) }
    val base = current.copy(frameCount = current.frameCount + 1, lastFrame = hex.take(8))

    return when (u8(raw[1])) {
        InboundId.SPEEDOMETER_1 -> base.copy(
            speedKmh = u8(raw[2]),
            odometerKm = u24(raw, 3) / 10.0,
            // 125-family uses the low nibble of byte 6 for fuel bars.
            fuelBars = u8(raw[6]) and 0x0F,
            rpm = u16(raw, 17),
        )

        InboundId.SERVICE -> base.copy(
            // byte 7 is the software version on Jupiter; 14 is cluster theme.
        )

        InboundId.ENGINE -> base.copy(
            intakeTempC = u8(raw[7]) - 40,
            engineTempC = u8(raw[8]) - 40,
            batteryVoltage = u8(raw[11]) * 0.1,
        )

        InboundId.ECONOMY -> base.copy(
            averageFuelEconomy = u8(raw[8]),
            distanceToEmptyKm = u16(raw, 11),
        )

        else -> base
    }
}

/** Cluster text channel: ASCII only, 17 bytes, 0xFF terminator. */
fun sanitize(input: String): String {
    val sb = StringBuilder()
    for (c in input) {
        if (sb.length >= 17) break
        sb.append(
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ' -> c
                else -> 'X'
            },
        )
    }
    return sb.toString()
}

/**
 * 20-byte `0x5B 0x4A` mobile-data keepalive, matching the layout recovered
 * from the official TVS Connect app (see TVSRide's Keepalive.kt).
 *
 * Two details this fixes versus the earlier version, both visible on the
 * cluster:
 *  - HOUR is 12-hour (Calendar.HOUR, 0..11, with 0 rewritten to 12). Writing
 *    HOUR_OF_DAY put 14 on a cluster expecting 2, so the dash clock was wrong.
 *  - Byte 18 is ZERO, not a checksum. A checksum there makes the cluster read
 *    the keepalive as corrupt, which is what produced the flickering/glitchy
 *    nav area. The mobile-data frame carries no checksum.
 */
fun buildKeepalive(findMy: Boolean = false): ByteArray {
    val b = ByteArray(20)
    b[0] = 0x5B
    b[1] = OutboundId.MOBILE_DATA.toByte()
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    b[6] = hour.toByte()
    b[7] = now.get(Calendar.MINUTE).toByte()
    b[8] = now.get(Calendar.SECOND).toByte()
    b[9] = if (now.get(Calendar.AM_PM) == Calendar.PM) 1 else 0
    b[17] = if (findMy) 1 else 0
    b[12] = now.get(Calendar.DAY_OF_MONTH).toByte()
    b[13] = (now.get(Calendar.MONTH) + 1).toByte()
    b[14] = (now.get(Calendar.YEAR) % 100).toByte()
    // [18] stays zero — no checksum on this frame.
    b[19] = 0xFF.toByte()
    return b
}

/** Navigation control: distance/ETA/remaining big-endian, pictogram, row count. */
fun buildNavControl(distanceMeters: Int, etaMinutes: Int, remainingMeters: Int, pictogram: Int): ByteArray {
    val b = ByteArray(20)
    b[0] = 0x5A
    b[1] = OutboundId.NAVIGATION_CONTROL.toByte()
    val d = distanceMeters.coerceIn(0, 0xFFFF)
    b[2] = (d ushr 8).toByte(); b[3] = d.toByte()
    b[4] = (etaMinutes.coerceIn(0, 0xFFFF) ushr 8).toByte()
    b[5] = etaMinutes.coerceIn(0, 0xFFFF).toByte()
    val r = remainingMeters.coerceIn(0, 0xFFFFFF)
    b[6] = (r ushr 16).toByte(); b[7] = (r ushr 8).toByte(); b[8] = r.toByte()
    b[9] = pictogram.toByte()
    b[10] = 1
    b[11] = 1
    b[19] = 0xFF.toByte()
    return b
}

fun buildNavText(text: String, id: Int = OutboundId.NAVIGATION_TEXT): ByteArray {
    val b = ByteArray(20)
    b[0] = 0x5B
    b[1] = id.toByte()
    val clean = sanitize(text)
    for (i in clean.indices) b[2 + i] = clean[i].code.toByte()
    b[19] = 0xFF.toByte()
    return b
}
