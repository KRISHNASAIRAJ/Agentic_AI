package com.jupiterconnect.traffic

import com.jupiterconnect.protocol.Gatt

/**
 * Wire builders for the cluster's Traffic screens, recovered from TVS Connect
 * 8.14.3 (com.tvs.ble.feature.utils.BluetoothUtil / TrafficSequence).
 *
 * Two frames are required per screen:
 *   1. screen select  - 5B 4C 00 <id> 00*15 FF   (buildScreenSwitch in protocol)
 *   2. data frame     - one of the builders below
 *
 * The old app sent only (1) on a manual button press and never (2), which is
 * why nothing ever appeared. The traffic bridge sends both in sequence.
 */

/** Screen IDs from TrafficSequence (verified in the decompiled APK). */
object TrafficScreen {
    const val DEFAULT = 0x00
    const val WEATHER = 0x78 // 120
    const val CRICKET = 0x79 // 121
    const val MARKET = 0x80  // 128 - the APK calls this Football
}

private fun u8(v: Int) = v.coerceIn(0, 255).toByte()
private fun u16be(v: Int): ByteArray = byteArrayOf(u8(v ushr 8), u8(v))

private fun ascii(text: String, max: Int, pad: Byte = 0): ByteArray {
    val clean = StringBuilder()
    for (c in text) {
        if (clean.length >= max) break
        clean.append(if (c.code in 0x20..0x7E) c else ' ')
    }
    val bytes = clean.toString().toByteArray(Charsets.US_ASCII)
    return bytes + ByteArray(max - bytes.size) { pad }
}

/** Open-Meteo WMO code -> TVS weather icon byte (getWeatherIcon). */
fun weatherIcon(code: Int): Int = when (code) {
    0, 1 -> 5            // clear / mainly clear
    2 -> 1               // cloudy
    3 -> 1               // overcast
    45, 48 -> 9          // fog
    51, 53, 55, 56, 57 -> 8 // drizzle
    61, 63, 65, 66, 67 -> 8 // rain
    71, 73, 75, 77, 85, 86 -> 11 // snow
    80, 81, 82 -> 8      // showers
    95, 96, 99 -> 3      // thunderstorm
    else -> 5
}

/**
 * Weather: 5B 5D <icon> <AQI:u16be> <rain:u8> <short desc, space padded> FF.
 * TVS `createClimateData`.
 */
fun buildWeatherFrame(tempC: Int, description: String, aqi: Int, rainPct: Int): ByteArray {
    val b = ByteArray(20)
    b[0] = 0x5B; b[1] = 0x5D
    b[2] = u8(weatherIcon(0)) // replaced by caller via buildWeatherFrameWithCode
    val desc = ascii("$tempC C $description", 13, pad = ' '.code.toByte())
    val aqiBytes = u16be(aqi)
    b[3] = aqiBytes[0]; b[4] = aqiBytes[1]
    b[5] = u8(rainPct)
    desc.copyInto(b, 6)
    b[19] = 0xFF.toByte()
    return b
}

/** Same layout with an explicit Open-Meteo WMO code for the icon. */
fun buildWeatherFrameWithCode(code: Int, tempC: Int, desc: String, aqi: Int, rainPct: Int): ByteArray {
    val frame = buildWeatherFrame(tempC, desc, aqi, rainPct)
    frame[2] = u8(weatherIcon(code))
    return frame
}

/** Two-letter team -> TVS team byte, matching the APK's team enum ordering. */
fun teamByte(name: String): Int = when (name.trim().uppercase()) {
    "IND" -> 1; "AUS" -> 2; "ENG" -> 3; "SA" -> 4; "PAK" -> 5
    "NZ" -> 6; "SL" -> 7; "WI" -> 8; "BAN" -> 9; "AFG" -> 10
    "ZIM" -> 11; "IRE" -> 12; "SCO" -> 13; "NL" -> 14
    else -> 0
}

/**
 * Cricket: 5B 81 <tournament> <t1> <t2> <state> <result>
 *          <t1 runs:u16be> <t1 wickets> <t1 overs>
 *          <t2 runs:u16be> <t2 wickets> <t2 overs>
 *          <margin:u16be> <margin wickets> <match no> FF
 * TVS `getCricketData`.
 */
fun buildCricketFrame(
    team1: String, team2: String,
    runs1: Int, wickets1: Int, overs1: Int,
    runs2: Int, wickets2: Int, overs2: Int,
    state: Int = 0, result: Int = 0,
): ByteArray {
    val b = ByteArray(20)
    b[0] = 0x5B; b[1] = 0x81.toByte()
    b[2] = 0                       // tournament (unknown from free APIs)
    b[3] = u8(teamByte(team1))
    b[4] = u8(teamByte(team2))
    b[5] = u8(state)
    b[6] = u8(result)
    val r1 = u16be(runs1); b[7] = r1[0]; b[8] = r1[1]
    b[9] = u8(wickets1)
    b[10] = u8(overs1)
    val r2 = u16be(runs2); b[11] = r2[0]; b[12] = r2[1]
    b[13] = u8(wickets2)
    b[14] = u8(overs2)
    val m = u16be(0); b[15] = m[0]; b[16] = m[1]
    b[17] = 0; b[18] = 0
    b[19] = 0xFF.toByte()
    return b
}

/**
 * Market on the Football scoreboard (0x91), as requested: NIFTY vs SENSEX.
 * 5B 91 <nifty> <sensex> <tourney> <time> <injury> <state>
 *      <"NIFTY"> <"SENSE"> 00 FF
 * The score bytes carry the signed change-percent x10 (clamped), since index
 * values do not fit in one byte. Full values are also sent on text row 2.
 */
fun buildMarketFrame(changeNiftyPct: Double, changeSensexPct: Double): ByteArray {
    val b = ByteArray(20)
    b[0] = 0x5B; b[1] = 0x91.toByte()
    // Score bytes are signed (change can be negative), so do not use u8's 0..255 clamp.
    b[2] = (changeNiftyPct * 10).toInt().coerceIn(-128, 127).toByte()
    b[3] = (changeSensexPct * 10).toInt().coerceIn(-128, 127).toByte()
    b[4] = 0; b[5] = 0; b[6] = 0; b[7] = 0
    ascii("NIFTY", 5).copyInto(b, 8)
    ascii("SENSE", 5).copyInto(b, 13)
    b[18] = 0
    b[19] = 0xFF.toByte()
    return b
}

/** News: 5B 96 <75-byte headline, zero padded> FF (TVS `sendNewsHeadline`). */
fun buildNewsFrame(headline: String): ByteArray {
    val text = ascii(headline, 75)
    val b = ByteArray(2 + text.size + 1)
    b[0] = 0x5B; b[1] = 0x96.toByte()
    text.copyInto(b, 2)
    b[b.lastIndex] = 0xFF.toByte()
    return b
}
