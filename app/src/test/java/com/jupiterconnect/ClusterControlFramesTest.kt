package com.jupiterconnect

import com.jupiterconnect.nav.Pictograms
import com.jupiterconnect.protocol.ClusterControlFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterControlFramesTest {

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    @Test
    fun vehicle_control_uses_jupiter_illumination_bytes() {
        val f = ClusterControlFrames.buildVehicleControlFrame(
            illumination = 2, tsl = 0, jupiterTheme = true)
        assertEquals(20, f.size)
        assertEquals(0x5A, u(f, 0)); assertEquals(0xF1, u(f, 1))
        assertEquals(0x70, u(f, 2)) // Jupiter U566 theme level 2
        assertEquals(7, u(f, 3)); assertEquals(0, u(f, 5))
        assertEquals(1, u(f, 13)); assertEquals(0xFF, u(f, 19))
        assertEquals(0x60, ClusterControlFrames.illuminationByte(1, true))
        assertEquals(0xA0, ClusterControlFrames.illuminationByte(5, true))
        assertEquals(3, ClusterControlFrames.illuminationByte(3, false))
    }

    @Test
    fun u546_brightness_new_protocol_mapping() {
        val f = ClusterControlFrames.buildU546BrightnessFrame(3, true)
        assertEquals(0x5A, u(f, 0)); assertEquals(0xF1, u(f, 1))
        assertEquals(0x40, u(f, 2))
        assertEquals(2, u(f, 5)); assertEquals(1, u(f, 14))
        assertEquals(6, u(ClusterControlFrames.buildU546BrightnessFrame(3, false), 2))
    }

    @Test
    fun regen_and_ride_mode_frames() {
        val regen = ClusterControlFrames.buildRegenFrame(2)
        assertEquals(0xF2, u(regen, 1)); assertEquals(2, u(regen, 5))
        val mode = ClusterControlFrames.buildRideModeFrame(10, 2, 0)
        assertEquals(0xF1, u(mode, 1)); assertEquals(10, u(mode, 3))
        assertEquals(2, u(mode, 5)); assertEquals(1, u(mode, 12))
    }

    @Test
    fun trip_reset_puts_ff_in_byte_three() {
        val select = ClusterControlFrames.buildTripFrame(1, false)
        assertEquals(0xF1, u(select, 1)); assertEquals(1, u(select, 3))
        assertEquals(2, u(select, 5)); assertEquals(0, u(select, 12))
        val reset = ClusterControlFrames.buildTripFrame(1, true)
        assertEquals(0xFF, u(reset, 3)); assertEquals(1, u(reset, 12))
    }

    @Test
    fun calibration_frames_match_apk_byte_layout() {
        val req = ClusterControlFrames.buildCalibrationRequest()
        assertEquals(0x5B, u(req, 0)); assertEquals(0x73, u(req, 1)); assertEquals(8, u(req, 2))

        val update = ClusterControlFrames.buildCalibrationUpdate(1000, 18, 8)
        assertEquals(0x5B, u(update, 0)); assertEquals(0x73, u(update, 1))
        assertEquals(8, u(update, 2)); assertEquals(18, u(update, 3))
        assertEquals(2, u(update, 4))            // minimal byte count of 1000
        assertEquals(0xE8, u(update, 7))          // reversed 4-byte timer
        assertEquals(0x03, u(update, 8))
        assertEquals(0xF9, u(update, 9))          // 256 - 263 % 256
    }

    @Test
    fun follow_me_headlamp_timer_frame() {
        val f = ClusterControlFrames.buildFollowMeHeadlampFrame(5)
        assertEquals(10, u(f, 2)); assertEquals(18, u(f, 3)); assertEquals(2, u(f, 4))
        assertEquals(0, u(f, 7)); assertEquals(5, u(f, 8))
        assertEquals(0x3D, u(f, 9))
    }

    @Test
    fun road_name_frame() {
        val f = ClusterControlFrames.buildRoadNameFrame("Main St")
        assertEquals(0x5A, u(f, 0)); assertEquals(0x49, u(f, 1))
        assertEquals("Main St", String(f, 2, 7, Charsets.US_ASCII))
        assertEquals(0, u(f, 9)); assertEquals(0xFF, u(f, 19))
        assertTrue(u(ClusterControlFrames.buildRoadNameFrame("X", true), 2 + 1) == 1)
    }

    @Test
    fun social_split_uses_apk_bit_packing() {
        val f = ClusterControlFrames.buildSocialCountsV2(2, 5, 3, 4, 7)
        assertEquals(0x5B, u(f, 0)); assertEquals(0x9C, u(f, 1))
        assertEquals(0x45, u(f, 2)) // 2 chats (3 bits) + 5 msgs (5 bits)
        assertEquals(0x34, u(f, 3)) // 3 chats (4 bits) + 4 msgs (4 bits)
        assertEquals(7, u(f, 4))
        val clamped = ClusterControlFrames.buildSocialCountsV2(9, 99, 99, 99, 300)
        assertEquals(0xFF, u(clamped, 2)) // 7 chats + 31 msgs
        assertEquals(0xFF, u(clamped, 3)) // 15 + 15
        assertEquals(44, u(clamped, 4))   // 300 truncated to a byte
    }

    @Test
    fun keyless_request_uses_random_command_slice() {
        val f = ClusterControlFrames.buildKeylessRequest(
            ClusterControlFrames.KeylessCommand.UNLOCK, randomStart = 1, length = 4)
        assertEquals(20, f.size)
        assertEquals(0x9A, u(f, 0)); assertEquals(0xF1, u(f, 1))
        assertEquals(64, u(f, 2)); assertEquals(1, u(f, 3)); assertEquals(4, u(f, 4))
        assertEquals(0x7C, u(f, 6)); assertEquals(0x26, u(f, 7))
        assertEquals(0x9A, u(f, 8)); assertEquals(0x16, u(f, 9))
        assertEquals(0xFF, u(f, 19))
    }

    @Test
    fun keyless_challenge_is_encrypted_with_apk_cipher() {
        val challenge = ByteArray(20) { (it * 7).toByte() }
        assertTrue(ClusterControlFrames.isKeylessChallenge(challenge.copyOf().also {
            it[0] = 0x5A; it[1] = 0xF2.toByte()
        }))
        assertFalse(ClusterControlFrames.isKeylessChallenge(ByteArray(20)))
        val cipher = ClusterControlFrames.encryptChallenge(challenge)
        assertEquals(16, cipher.size)
        val response = ClusterControlFrames.buildKeylessResponse(challenge)
        assertEquals(80, u(response, 2))
        assertEquals(cipher[1], response[6]) // slice starts at random index 1
        assertEquals(cipher[4], response[9])
    }

    @Test
    fun setup_handshake_frames() {
        val user = ClusterControlFrames.buildUserIdFrame(1)
        assertEquals(0x5B, u(user, 0)); assertEquals(0x22, u(user, 1)); assertEquals(1, u(user, 18))
        val vin = ClusterControlFrames.buildVinFrame()
        assertEquals(0x5A, u(vin, 0)); assertEquals(0x7D, u(vin, 1)); assertEquals(0x4D, u(vin, 2))
        val dob = ClusterControlFrames.buildDobFrame(1, 1, 1900)
        assertEquals(0x80, u(dob, 1)); assertEquals(0x07, u(dob, 4)); assertEquals(0x6C, u(dob, 5))
        val apache = ClusterControlFrames.buildApacheDobFrame(1, 1)
        assertEquals(0x7E, u(apache, 1)); assertEquals(2, u(apache, 4))
    }

    @Test
    fun apk_pictogram_tables_are_transcribed_verbatim() {
        assertEquals(19, Pictograms.fromU725(41))
        assertEquals(7, Pictograms.fromU725(42))
        assertEquals(17, Pictograms.fromU725(43))
        assertEquals(10, Pictograms.fromU725(3)) // case 3/14 keeps default 10
        assertEquals(74, Pictograms.fromMmiV2(42))
        assertEquals(73, Pictograms.fromMmiV2(44))
        assertEquals(57, Pictograms.fromManeuverId(7))
    }
}
