package com.jupiterconnect.protocol

import java.util.Calendar

/**
 * Additional phone -> cluster frames recovered from the genuine TVS Connect
 * 8.14.3 APK. Every layout here is transcribed from the actual array-builder
 * code in the decompiled sources; see docs/RE_PROTOCOL_CATALOG.md for the
 * file/line provenance.
 *
 * These are all LOCAL features - none of them need the TVS cloud.
 */
object ClusterControlFrames {

    // ---------------------------------------------------------------- helpers

    private fun controlFrame(dataId: Int): ByteArray = ByteArray(20).apply {
        this[0] = 0x5A
        this[1] = dataId.toByte()
        this[19] = 0xFF.toByte()
    }

    private fun dataFrame(dataId: Int): ByteArray = ByteArray(20).apply {
        this[0] = 0x5B
        this[1] = dataId.toByte()
        this[19] = 0xFF.toByte()
    }

    private val cal: Calendar get() = Calendar.getInstance()
    private fun b(v: Int) = (v and 0xFF).toByte()
    private fun bytes(vararg values: Int) = ByteArray(values.size) { b(values[it]) }

    /** Big-endian minimal bytes, as BigInteger.toByteArray() in the APK. */
    private fun intBytes(value: Int): ByteArray {
        if (value == 0) return byteArrayOf(0)
        var v = value
        val out = ArrayList<Byte>()
        while (v != 0) {
            out.add(0, (v and 0xFF).toByte())
            v = v shr 8
        }
        // BigInteger.toByteArray() adds a leading zero for values >= 0x80.
        if ((out[0].toInt() and 0x80) != 0) out.add(0, 0)
        return out.toByteArray()
    }

    /** APK getCalibrationData(): BE minimal bytes, reversed, left-padded to 4. */
    private fun calibrationBytes(timeMs: Int): ByteArray {
        val minimal = intBytes(timeMs).reversedArray()
        return ByteArray(4 - minimal.size) + minimal
    }

    /** APK getChecksum(): (256 - sum % 256) as a byte. */
    private fun checksum(vararg values: Int): Byte {
        val sum = values.sum()
        return b(256 - (sum - (sum / 256) * 256))
    }

    /** APK calculateChecksum() used by the follow-me headlamp frame. */
    private fun followMeChecksum(secondByte: Int): Byte = when (secondByte and 0xFF) {
        16 -> 0xAB.toByte()
        32 -> 0x74.toByte()
        else -> 0x3D.toByte() // Base64.padSymbol == '='
    }

    // --------------------------------------------------- cluster settings (2)

    /** Illumination byte for the Jupiter U566 theme (APK sendVehicleControl). */
    fun illuminationByte(level: Int, jupiterTheme: Boolean = true): Int {
        val v = level.coerceIn(1, 5)
        return if (jupiterTheme) when (v) {
            1 -> 0x60
            2 -> 0x70
            3 -> 0x80
            4 -> 0x90
            else -> 0xA0
        } else v
    }

    /**
     * 5A F1 vehicle control: illumination/theme, TSL, clock.
     * BlePacketsProvider.sendVehicleControl (1834-1942).
     */
    fun buildVehicleControlFrame(
        illumination: Int = 2,
        tsl: Int = 0,
        jupiterTheme: Boolean = true,
        clearCallInfo: Boolean = false,
    ): ByteArray {
        val c = cal
        return controlFrame(0xF1).apply {
            this[2] = b(illuminationByte(illumination, jupiterTheme))
            this[3] = if (jupiterTheme) 7 else 9
            this[4] = 0
            this[5] = if (jupiterTheme) 0 else 2
            this[6] = b(tsl)
            this[9] = b(c.get(Calendar.DAY_OF_MONTH))
            this[10] = b(c.get(Calendar.MONTH))
            this[11] = b(c.get(Calendar.YEAR))
            this[12] = if (clearCallInfo) 1 else 1 // APK: 1 for Jupiter family
            this[13] = 1
        }
    }

    /**
     * 5A F1 brightness/regen/auto-brightness settings frame
     * (ble/feature/utils/BluetoothUtil.getVehicleControlDataToWriteToCluster:895).
     */
    fun buildVaSettingsFrame(
        brightness: Int,
        autoBrightness: Int = 0,
        trip: Int = 0,
    ): ByteArray {
        val c = cal
        return controlFrame(0xF1).apply {
            this[2] = b(brightness)
            this[3] = b(trip)
            this[4] = 0
            this[5] = 2
            this[9] = b(c.get(Calendar.DAY_OF_MONTH))
            this[10] = b(c.get(Calendar.MONTH) + 1)
            this[11] = b(c.get(Calendar.YEAR) % 100)
            this[14] = 1
            this[15] = b(autoBrightness)
        }
    }

    /** 5A F1 U546 brightness; new-protocol levels map to 0x20/0x30/0x40/0x50. */
    fun buildU546BrightnessFrame(level: Int, newProtocol: Boolean = true): ByteArray {
        val mapped = if (newProtocol) when (level.coerceIn(1, 4)) {
            1 -> 0x20; 2 -> 0x30; 3 -> 0x40; else -> 0x50
        } else when (level.coerceIn(1, 5)) {
            1 -> 2; 2 -> 4; 3 -> 6; 4 -> 8; else -> 10
        }
        val c = cal
        return controlFrame(0xF1).apply {
            this[2] = b(mapped)
            this[5] = 2
            this[9] = b(c.get(Calendar.DAY_OF_MONTH))
            this[10] = b(c.get(Calendar.MONTH) + 1)
            this[11] = b(c.get(Calendar.YEAR) % 100)
            this[14] = 1
        }
    }

    /** 5A F2 regen level (BluetoothUtil.getRegenDataToWriteToU546Cluster:885). */
    fun buildRegenFrame(value: Int): ByteArray = controlFrame(0xF2).apply { this[5] = b(value) }

    /** 5A F1 ride mode: mode 6 -> 6, 7 -> 7, 10 -> 10 (sendVehicleMode:1944). */
    fun buildRideModeFrame(mode: Int, illumination: Int = 2, tsl: Int = 0): ByteArray {
        val c = cal
        val modeByte = when (mode) { 6 -> 6; 7 -> 7; 10 -> 10; else -> 7 }
        return controlFrame(0xF1).apply {
            this[2] = b(illuminationByte(illumination, false))
            this[3] = b(modeByte)
            this[4] = 0
            this[5] = 2
            this[6] = b(tsl)
            this[9] = b(c.get(Calendar.DAY_OF_MONTH))
            this[10] = b(c.get(Calendar.MONTH))
            this[11] = b(c.get(Calendar.YEAR))
            this[12] = 1
            this[13] = 1
        }
    }

    /** 5B 73 08 calibration write request, sent once during BLE setup. */
    fun buildCalibrationRequest(): ByteArray = dataFrame(0x73).apply { this[2] = 8 }

    /**
     * 5B 73 <cmd> <id> <len> <4-byte reversed timer> <checksum> ... FF
     * BlePacketsProvider.sendCalibrationUpdate (1001-1024).
     */
    fun buildCalibrationUpdate(
        timeMs: Int,
        calibrationId: Int,
        command: Int = 8,
    ): ByteArray {
        val data = calibrationBytes(timeMs)
        val chk = checksum(command, calibrationId, intBytes(timeMs).size,
            data[3].toInt(), data[2].toInt(), data[1].toInt(), data[0].toInt())
        return dataFrame(0x73).apply {
            this[2] = b(command)
            this[3] = b(calibrationId)
            this[4] = b(intBytes(timeMs).size)
            data.copyInto(this, 5)
            this[9] = chk
        }
    }

    /** 5B 73 0A 12 follow-me headlamp timer (updateFollowMeHeadlampTimer:2033). */
    fun buildFollowMeHeadlampFrame(time: Int): ByteArray {
        val hi = b(time ushr 8).toInt()
        val lo = b(time).toInt()
        return dataFrame(0x73).apply {
            this[2] = 10
            this[3] = 18
            this[4] = 2
            this[7] = b(hi)
            this[8] = b(lo)
            this[9] = followMeChecksum(lo)
        }
    }

    // --------------------------------------------------- trip / service (3)

    /** 5A F1 trip select or reset (BluetoothUtil.createVehicleDataArray:1461). */
    fun buildTripFrame(trip: Int, reset: Boolean = false): ByteArray {
        val c = cal
        return controlFrame(0xF1).apply {
            this[2] = 0
            this[3] = if (reset) 0xFF.toByte() else b(trip)
            this[5] = 2
            this[9] = b(c.get(Calendar.DAY_OF_MONTH))
            this[10] = b(c.get(Calendar.MONTH) + 1)
            this[11] = b(c.get(Calendar.YEAR) % 100)
            if (reset) this[12] = b(trip)
            this[14] = 1
        }
    }

    // ------------------------------------------------------ navigation (4)

    /** 5A 49 road name / destination-arrived (BleNavigationSendData:215-233). */
    fun buildRoadNameFrame(name: String, arrived: Boolean = false): ByteArray {
        val b = controlFrame(0x49)
        val clean = StringBuilder()
        for (ch in name) {
            if (clean.length >= 22) break
            clean.append(if (ch.code in 0x20..0x7E) ch else ' ')
        }
        val bytes = clean.toString().toByteArray(Charsets.US_ASCII)
        bytes.copyInto(b, 2)
        b[2 + bytes.size] = if (arrived) 1 else 0
        return b
    }

    // --------------------------------------------------- speed alert

    /**
     * Genuine TVS Connect over-speed alert trigger:
     * `5B 4C` + ASCII "HIGH SPEED ALERT" + 1 zero pad + `FF` = 20 bytes.
     * Transcribed from U368SendDataToCluster.sendHighSpeedMessage() (303-317);
     * the same frame is used by U449/U507. This is a dedicated message frame -
     * it never touches the 0x4A keepalive (whose byte 3 must stay zero).
     */
    fun buildHighSpeedAlertFrame(text: String = "HIGH SPEED ALERT"): ByteArray {
        val b = dataFrame(0x4C) // header + 0xFF postfix, rest zeroed
        val clean = StringBuilder()
        for (ch in text) {
            if (clean.length >= 16) break
            clean.append(if (ch.code in 0x20..0x7E) ch else ' ')
        }
        clean.toString().toByteArray(Charsets.US_ASCII).copyInto(b, 2)
        return b
    }

    // --------------------------------------------------- social counters (5)

    /**
     * 5B 9C with the genuine APK's split chat/message packing:
     * byte2 = WhatsApp (3-bit chats << 5) | 5-bit msgs,
     * byte3 = Facebook (4-bit chats << 4) | 4-bit msgs,
     * byte4 = Instagram raw count.
     */
    fun buildSocialCountsV2(
        whatsappChats: Int,
        whatsappMsgs: Int,
        facebookChats: Int,
        facebookMsgs: Int,
        instagram: Int,
    ): ByteArray = dataFrame(0x9C).apply {
        this[2] = b(((whatsappChats.coerceIn(0, 7) shl 5) or (whatsappMsgs.coerceIn(0, 31))))
        this[3] = b(((facebookChats.coerceIn(0, 15) shl 4) or (facebookMsgs.coerceIn(0, 15))))
        this[4] = b(instagram)
    }

    // --------------------------------------------------- handshake setup (7)

    /** 5B 22 first-connection user ID (BlePacketsProvider.byteArrayForFistConnection:367). */
    fun buildUserIdFrame(userId: Long): ByteArray {
        val b = dataFrame(0x22)
        val bytes = intBytes(userId.toInt())
        bytes.copyInto(b, 2 + (17 - bytes.size))
        return b
    }

    /** 5A 7D VIN (fixed sample bytes in the APK). */
    fun buildVinFrame(vin: ByteArray = byteArrayOf(
        0x4D, 0x44, 0x54, 0x32, 0x36, 0x04, 0x4B, 0x33,
        0x31, 0x32, 0x33, 0x34, 0x35
    )): ByteArray {
        val b = controlFrame(0x7D)
        vin.copyInto(b, 2)
        return b
    }

    /** 5B 80 iQube DOB (BluetoothUtil.createDOBArray:1170). */
    fun buildDobFrame(day: Int, month: Int, year: Int): ByteArray = dataFrame(0x80).apply {
        this[2] = b(day)
        this[3] = b(month)
        this[4] = b(year ushr 8)
        this[5] = b(year)
    }

    /** 5B 7E Apache birth date (ApacheMobileToCluster:257-264). */
    fun buildApacheDobFrame(day: Int, month: Int): ByteArray = dataFrame(0x7E).apply {
        this[2] = 1
        this[3] = b(day)
        this[4] = b(month + 1)
        this[6] = 1
    }
}
