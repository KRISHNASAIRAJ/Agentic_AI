package com.jupiterconnect

import com.jupiterconnect.nav.NavInstruction
import com.jupiterconnect.nav.NavParsing
import com.jupiterconnect.session.ColdStartTracker
import com.jupiterconnect.session.FuelFill
import com.jupiterconnect.session.RideAnalytics
import com.jupiterconnect.session.SpeedFusion
import com.jupiterconnect.session.Ride
import com.jupiterconnect.session.WarmupPhase
import com.jupiterconnect.protocol.buildCallerPackets
import com.jupiterconnect.protocol.buildMessageFrame
import com.jupiterconnect.protocol.buildMessageRow
import com.jupiterconnect.protocol.buildKeepalive
import com.jupiterconnect.protocol.buildNavControl
import com.jupiterconnect.protocol.buildNavText
import com.jupiterconnect.protocol.decodeFrame
import com.jupiterconnect.protocol.sanitize
import com.jupiterconnect.protocol.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun caller_frames_use_sub_ids_and_do_not_touch_navigation_row() {
        val packets = buildCallerPackets("9876543210", "91", "Mom")
        assertEquals(3, packets.size)
        // Stock order: national number (1), country code (3), contact name (2).
        assertEquals(listOf(1, 3, 2), packets.map { it[2].toInt() and 0xFF })
        packets.forEach {
            assertEquals(21, it.size)
            assertEquals(0x5B, it[0].toInt() and 0xFF)
            assertEquals(0x43, it[1].toInt() and 0xFF)
            assertEquals(0xFF, it[20].toInt() and 0xFF)
        }
        assertEquals("9876543210", String(packets[0], 3, 10, Charsets.US_ASCII))
        assertEquals("Mom", String(packets[2], 3, 3, Charsets.US_ASCII))

        // VoIP callers have no phone number: the name fills both slots.
        val voip = buildCallerPackets("Mom", contactName = "Mom")
        assertEquals(2, voip.size)
        assertEquals(listOf(1, 2), voip.map { it[2].toInt() and 0xFF })
    }

    @Test
    fun message_row_uses_the_jupiter_text_channel() {
        val row = buildMessageRow("Mom: Hello")
        val sms = buildMessageFrame("Mom: Hello")
        val nav = buildNavText("Turn left")
        assertEquals(0x4C, row[1].toInt() and 0xFF)
        assertEquals(0x53, sms[1].toInt() and 0xFF)
        assertEquals(0x4F, nav[1].toInt() and 0xFF)
        assertEquals(0xFF, row[19].toInt() and 0xFF)
        assertEquals(0x63, buildMessageRow("Mom: Hello", row2 = true)[1].toInt() and 0xFF)
    }

    @Test
    fun keepalive_carries_the_missed_call_counter() {
        val b = buildKeepalive(missedCalls = 7)
        assertEquals(7, b[10].toInt() and 0xFF)
        assertEquals(0, buildKeepalive()[10].toInt() and 0xFF)
    }

    @Test
    fun keepalive_has_no_checksum_and_12_hour_clock() {
        val b = buildKeepalive()
        assertEquals(20, b.size)
        assertEquals(0x5B, b[0].toInt() and 0xFF)
        assertEquals(0x4A, b[1].toInt() and 0xFF)
        // Byte 18 is ZERO, not a checksum — the mobile-data frame has none.
        assertEquals(0, b[18].toInt() and 0xFF)
        assertEquals(0xFF, b[19].toInt() and 0xFF)
        // 12-hour clock, 1..12; AM/PM flag in byte 9.
        val hour = b[6].toInt() and 0xFF
        assertTrue("hour must be 1..12", hour in 1..12)
        assertTrue("AM/PM must be 0 or 1", (b[9].toInt() and 0xFF) in 0..1)
    }

    @Test
    fun navigation_frames_have_expected_shape() {
        val control = buildNavControl(550, 12, 8400, NavInstruction.RIGHT)
        assertEquals(20, control.size)
        assertEquals(0x5A, control[0].toInt() and 0xFF)
        assertEquals(0x4E, control[1].toInt() and 0xFF)
        assertEquals(550, (control[2].toInt() and 0xFF) shl 8 or (control[3].toInt() and 0xFF))
        assertEquals(NavInstruction.RIGHT, control[9].toInt() and 0xFF)
        assertEquals(0xFF, control[19].toInt() and 0xFF)

        val text = buildNavText("Turn right onto NH44")
        assertEquals(20, text.size)
        assertEquals(0x5B, text[0].toInt() and 0xFF)
        assertEquals(0x4F, text[1].toInt() and 0xFF)
        assertEquals(0xFF, text[19].toInt() and 0xFF)
    }

    @Test
    fun cluster_text_is_ascii_and_capped_at_17() {
        // Punctuation becomes a space, never a stray "X" in the message.
        val cleaned = sanitize("NH-44 தென்")
        assertFalse(cleaned.contains('X'))
        assertTrue(cleaned.startsWith("NH 44"))
        assertEquals(17, sanitize("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length)
    }

    @Test
    fun telemetry_decodes_big_endian_offsets() {
        val speed = "5A 10 2A 00 D6 C8 04 00 00 00 12 40 20 00 00 3B F0 0D FA 00 FF"
            .split(" ").map { it.toInt(16).toByte() }.toByteArray()
        val decoded = decodeFrame(speed, Telemetry())!!
        assertEquals(42, decoded.speedKmh)
        assertEquals(5498.4, decoded.odometerKm, 0.01)
        assertEquals(4, decoded.fuelBars)
        assertEquals(3578, decoded.rpm) // 0x0DFA
        assertEquals(1534.4, decoded.tripMeterKm, 0.01) // 0x003BF0 / 10
        assertEquals(true, decoded.ignitionOn) // 0x80 = vehicle OFF, not on

        val engine = "5A 18 00 00 00 00 5F 4B 4B 00 00 7E 00 00 00 00 00 00 00 FF"
            .split(" ").map { it.toInt(16).toByte() }.toByteArray()
        val withTemp = decodeFrame(engine, decoded)!!
        assertEquals(12.6, withTemp.batteryVoltage, 0.01)
        assertTrue(withTemp.intakeTempC > 0)
        assertEquals(95, withTemp.barometricPressureRaw) // 0x5F
    }

    @Test
    fun simulator_golden_vectors_decode_identically() {
        // These exact frames are generated by simulator/tvs_protocol.py.
        // If a decode offset changes, both this test and the simulator must be
        // updated together, so the Windows tool stays a trustworthy oracle.
        fun frame(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

        val speed = decodeFrame(
            frame("5A 10 2A 00 D6 C8 04 0C 01 44 12 02 40 37 00 3B F0 0D FA FF"),
            Telemetry())!!
        assertEquals(42, speed.speedKmh)
        assertEquals(5498.4, speed.odometerKm, 0.01)
        assertEquals(4, speed.fuelBars)
        assertEquals(3578, speed.rpm)
        assertEquals(12, speed.averageSpeedKmh)
        assertEquals(true, speed.powerMode)
        assertEquals(68, speed.clusterTopSpeedKmh)
        assertEquals(1534.4, speed.tripMeterKm, 0.01)
        assertEquals(true, speed.ignitionOn) // simulator encodes ignition on as 0x40

        val engine = decodeFrame(
            frame("5A 18 1E 00 00 00 5E 4B 74 00 00 7E 00 00 00 00 00 00 00 FF"),
            speed)!!
        assertEquals(35, engine.intakeTempC)
        assertEquals(76, engine.engineTempC)
        assertEquals(12.6, engine.batteryVoltage, 0.01)
        assertEquals(30, engine.engineLoad)
        assertEquals(94, engine.barometricPressureRaw)

        val economy = decodeFrame(
            frame("5A 19 00 00 00 00 00 00 3C 30 00 00 00 00 00 00 00 00 00 FF"),
            engine)!!
        assertEquals(60, economy.averageFuelEconomy)
        assertEquals(48, economy.instantEconomy)

        val service = decodeFrame(
            frame("5A 11 00 00 01 00 00 07 00 A3 04 00 00 00 01 00 00 00 00 FF"),
            economy)!!
        assertEquals(true, service.serviceDue)
        assertEquals(7, service.softwareVersion)
        assertEquals(163, service.vehicleModel)
        assertEquals(1, service.clusterTheme)
        assertEquals(true, service.milOn)
    }

    @Test
    fun parses_maps_notifications() {
        val google = NavParsing.parse("550 m", "Turn right onto NH 44", "", "")
        assertEquals(550, google.distanceMeters)
        assertEquals(NavInstruction.RIGHT, google.pictogram)

        val km = NavParsing.parse("1.2 km", "Turn left", "", "")
        assertEquals(1200, km.distanceMeters)
        assertEquals(NavInstruction.LEFT, km.pictogram)

        val tvs = NavParsing.parse("Turn left", "2.32 km • 3 min • 11:40 AM ETA", "", "")
        assertEquals(3, tvs.etaMinutes)
        assertEquals(NavInstruction.LEFT, tvs.pictogram)

        // Google Maps 2026: distance is prefixed to the title with a dot.
        val modern = NavParsing.parse(
            "120 m · Turn right after Sri Devi Medical", "", "", "Arrive 5:58 pm")
        assertEquals(120, modern.distanceMeters)
        assertEquals(NavInstruction.RIGHT, modern.pictogram)
        assertTrue("maneuver must not start with the distance",
            !modern.maneuver.trimStart().startsWith("120"))

        // Mappls / MapmyIndia: maneuver in the title, distance + ETA in TEXT.
        val mappls = NavParsing.parse("Turn left", "2.32 km • 3 min • 11:40 AM ETA", "", "")
        assertEquals(2320, mappls.distanceMeters)
        assertEquals(3, mappls.etaMinutes)
        assertEquals(NavInstruction.LEFT, mappls.pictogram)

        val mapplsHead = NavParsing.parse("Head towards MG Road", "1.5 km • 4 min", "", "")
        assertEquals(1500, mapplsHead.distanceMeters)
        assertEquals(4, mapplsHead.etaMinutes)
        assertEquals(NavInstruction.STRAIGHT, mapplsHead.pictogram)

        // Distance prefix must be stripped so the cluster never shows "15 m X ...".
        val prefixed = NavParsing.parse("15 m · Turn left", "", "", "")
        assertEquals("Turn left", prefixed.maneuver)

        // Mappls trip total: "15.5 km • 27 min • 11:11 pm ETA" -> 15500 m.
        assertEquals(15500, NavParsing.findTripDistance("5 m", "15.5 km • 27 min • 11:11 pm ETA"))

        // The exact Mappls notification from the frame log: no maneuver word,
        // distance in title, trip total + ETA in text. All three must parse.
        val mapplsReal = NavParsing.parse("5 m", "15.5 km • 27 min • 11:11 pm ETA", "", "")
        assertEquals(5, mapplsReal.distanceMeters)
        assertEquals(27, mapplsReal.etaMinutes)
        // This metrics-only post has no maneuver word, so parsed fresh it would
        // render a straight arrow and a "5 m" text row. The listener reuses the
        // last real turn instead - that is what stops the cluster blinking.
        assertEquals("5 m", mapplsReal.maneuver)
        assertEquals(NavInstruction.STRAIGHT, mapplsReal.pictogram)
    }

    @Test
    fun ride_report_math_uses_5_1_litre_tank() {
        val ride = Ride(
            startedAt = 0L, endedAt = 600_000L, distanceKm = 5.1,
            avgSpeedKmh = 30.0, topSpeedKmh = 50, durationMin = 10L,
            bandMs = longArrayOf(1_000L, 2_000L, 3_000L, 4_000L),
            idleMs = 500L, fuelBarsStart = 6, fuelBarsEnd = 5,
        )
        assertEquals(1, ride.fuelBarsUsed)
        assertEquals(6.0, ride.economyKmPerL, 0.01)
        assertEquals(10, ride.bandPct(0))
        assertEquals(40, ride.bandPct(3))
    }

    @Test
    fun iss_lifetime_counter_becomes_the_carbon_floor() {
        // App stored 120s from stops it observed; the cluster now reports a
        // lifetime total of 1000s that includes earlier ownership history.
        val result = com.jupiterconnect.session.foldIssDuration(120L, -1L, 1000L)
        assertEquals(1000L, result.seconds)
        assertEquals(1000L, result.lastObserved)
    }

    @Test
    fun iss_deltas_keep_counting_after_the_lifetime_floor_is_adopted() {
        val first = com.jupiterconnect.session.foldIssDuration(0L, -1L, 1000L)
        val second = com.jupiterconnect.session.foldIssDuration(
            first.seconds, first.lastObserved, 1010L)
        assertEquals(1010L, second.seconds)
        assertEquals(1010L, second.lastObserved)
    }

    @Test
    fun iss_per_ride_reset_cannot_lower_the_lifetime_total() {
        // Cluster resets to a small per-ride value then climbs; the floor keeps
        // the lifetime total and app-tracked deltas resume from the new baseline.
        val floored = com.jupiterconnect.session.foldIssDuration(0L, -1L, 1000L)
        val afterReset = com.jupiterconnect.session.foldIssDuration(
            floored.seconds, floored.lastObserved, 5L)
        assertEquals(1000L, afterReset.seconds)
        val climbing = com.jupiterconnect.session.foldIssDuration(
            afterReset.seconds, afterReset.lastObserved, 25L)
        assertEquals(1020L, climbing.seconds)
    }

    @Test
    fun iss_zero_reading_is_ignored() {
        val result = com.jupiterconnect.session.foldIssDuration(500L, 400L, 0L)
        assertEquals(500L, result.seconds)
        assertEquals(400L, result.lastObserved)
    }

    @Test
    fun call_shows_contact_name_or_plain_number() {
        // The 0x43 card adds the "Incoming call" label itself, so only the
        // name/number goes on the wire.
        assertEquals("Mom", com.jupiterconnect.nav.PhoneEventFormat.call("Mom"))
        assertEquals("919876543210",
            com.jupiterconnect.nav.PhoneEventFormat.call("+91 98765 43210"))
    }

    @Test
    fun long_directions_scroll_across_the_cluster_row() {
        val line = "Towards Jubilee Hills Check Post"
        val first = com.jupiterconnect.nav.NavScroll.window(line, 0)
        val second = com.jupiterconnect.nav.NavScroll.window(line, 2)
        assertEquals(17, first.length)
        assertEquals(17, second.length)
        assertFalse(first == second)
        assertTrue(first.startsWith("Towards"))
        // Every window must be a real slice of the instruction plus its gap.
        assertTrue((first + second).contains("Jubilee"))
        assertEquals("Turn left", com.jupiterconnect.nav.NavScroll.window("Turn left", 12))
    }

    @Test
    fun newly_catalogued_codes_decode_from_raw_frames() {
        val speedo = ByteArray(20)
        speedo[0] = 0x5A
        speedo[1] = 0x10
        speedo[12] = 0x44 // voice action 4 plus an unrelated high flag
        assertEquals(4, decodeFrame(speedo, Telemetry())!!.switchAction)

        val address = ByteArray(20)
        address[0] = 0x5B
        address[1] = 0x42
        for (i in 7..12) address[i] = i.toByte()
        assertEquals("0708090A0B0C", decodeFrame(address, Telemetry())!!.clusterBleAddress)

        val music = ByteArray(20)
        music[0] = 0x5A
        music[1] = 0x6B
        music[2] = 0x03
        assertEquals(3, decodeFrame(music, Telemetry())!!.mediaKey)
    }

    @Test
    fun frame_catalogue_tracks_both_directions_per_code() {
        com.jupiterconnect.debug.FrameCatalog.reset()
        val f = ByteArray(20)
        f[0] = 0x5A
        f[1] = 0x10
        com.jupiterconnect.debug.FrameCatalog.observe(f, rx = true)
        com.jupiterconnect.debug.FrameCatalog.observe(f, rx = true)
        com.jupiterconnect.debug.FrameCatalog.observe(f, rx = false)
        val seen = com.jupiterconnect.debug.FrameCatalog.byId(0x10)!!
        assertEquals(2L, seen.rx)
        assertEquals(1L, seen.tx)
        assertEquals("speedometer", seen.label)
        assertTrue(seen.hex.startsWith("5A 10"))
        com.jupiterconnect.debug.FrameCatalog.reset()
    }

    @Test
    fun long_messages_are_paged_into_cluster_sized_frames() {
        val text = com.jupiterconnect.nav.PhoneEventFormat.message(
            "Mom", "On my way I am stuck in traffic near the flyover call you later")
        assertTrue(text.length > 17)
        val pages = com.jupiterconnect.nav.PhoneEventFormat.pages(text)
        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.length <= 17 })
        assertEquals(text, pages.joinToString(""))
    }

    @Test
    fun message_shows_sender_and_text_within_seventeen_chars() {
        assertEquals("Mom On my way",
            com.jupiterconnect.nav.PhoneEventFormat.message("Mom", "On my way"))
        val long = com.jupiterconnect.nav.PhoneEventFormat.message(
            "Rahul", "Download the attachment please")
        // The full text is kept; the transport pages it into 17-byte frames.
        assertEquals("Rahul Download the attachment please", long)
        assertTrue(long.startsWith("Rahul "))
    }

    @Test
    fun fuel_log_months_sum_odometer_distance() {
        val fills = listOf(
            com.jupiterconnect.session.FuelFill(1, "2026-05-25", 260.0, 0.0, 2.81, 33037, 0.0, "BPCL"),
            com.jupiterconnect.session.FuelFill(2, "2026-05-31", 418.0, 158.0, 4.08, 47969, 38.73, "BPCL"),
            com.jupiterconnect.session.FuelFill(3, "2026-06-03", 586.0, 168.0, 3.32, 38386, 50.6, "HPCL"),
        )
        val months = com.jupiterconnect.session.aggregateFuelMonths(fills)
        assertEquals(2, months.size)
        // Keys use Calendar.MONTH, so May = 4 and June = 5.
        assertEquals(202604, months[0].key)
        assertEquals(158.0, months[0].km, 0.01)
        assertEquals(6.89, months[0].liters, 0.01)
        assertEquals(202605, months[1].key)
        assertEquals(168.0, months[1].km, 0.01)
        assertEquals(202605, com.jupiterconnect.session.fuelMonthKey("2026-06-30"))
    }

    @Test
    fun cold_start_tracker_moves_through_phases() {
        var now = 1_000_000L
        val tracker = ColdStartTracker { now }
        val base = com.jupiterconnect.protocol.Telemetry(
            rpm = 1200, speedKmh = 0, intakeTempC = 25, engineTempC = 26,
        )
        val start = tracker.update(base)
        assertTrue(start.cold)
        assertEquals(WarmupPhase.STARTING, start.phase)

        now += 31_000L
        assertEquals(WarmupPhase.WARMING, tracker.update(base.copy(engineTempC = 32)).phase)

        now += 70_000L
        val ready = tracker.update(base.copy(engineTempC = 60))
        assertEquals(WarmupPhase.READY, ready.phase)
        assertEquals(60, ready.targetC)
    }

    @Test
    fun no_engine_data_never_claims_ready_with_zero_temps() {
        val tracker = ColdStartTracker { 1_000_000L }
        val zeros = Telemetry(rpm = 1200, speedKmh = 0, intakeTempC = 0, engineTempC = 0)
        val state = tracker.update(zeros)
        assertFalse(state.hasEngineData)
        assertEquals("No engine data", state.message)
        assertEquals(0, state.targetC)

        val off = tracker.update(zeros.copy(rpm = 0))
        assertFalse(off.hasEngineData)
        assertEquals("Engine off", off.message)
    }

    @Test
    fun ride_kind_uses_the_ride_start_hour() {
        // The app labels rides in IST, so build the fixture in IST too. On a
        // UTC CI runner, a default-timezone fixture shifted the hour by +5:30.
        fun at(hour: Int, minute: Int): Long = java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2026, java.util.Calendar.NOVEMBER, 18, hour, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun ride(startedAt: Long) = Ride(
            startedAt = startedAt, endedAt = startedAt + 1_800_000L,
            distanceKm = 4.1, avgSpeedKmh = 24.0, topSpeedKmh = 58, durationMin = 30,
        )
        assertEquals("Morning ride", ride(at(8, 52)).rideKind)
        assertEquals("Evening ride", ride(at(16, 49)).rideKind)
        assertEquals("Night ride", ride(at(23, 15)).rideKind)
        assertEquals("Short ride", ride(at(16, 49)).copy(distanceKm = 0.1).rideKind)
    }

    @Test
    fun warm_start_skips_to_ready() {
        var now = 1_000_000L
        val tracker = ColdStartTracker { now }
        val warm = com.jupiterconnect.protocol.Telemetry(
            rpm = 1200, speedKmh = 0, intakeTempC = 25, engineTempC = 55,
        )
        val state = tracker.update(warm)
        assertFalse(state.cold)
        assertEquals(WarmupPhase.READY, state.phase)
    }

    @Test
    fun personal_eta_uses_median_and_needs_enough_rides() {
        fun ride(minutes: Long) = Ride(
            startedAt = 1_000L, endedAt = 1_000L + minutes * 60_000L,
            distanceKm = 5.0, avgSpeedKmh = 20.0, topSpeedKmh = 50,
            durationMin = minutes, mapsEtaMinutes = 10,
        )
        val rides = listOf(12L, 12L, 12L, 12L, 12L, 40L).map { ride(it) }
        assertEquals(1.2, RideAnalytics.etaFactor(rides), 0.001)
        assertEquals(12, RideAnalytics.adjustedEta(10, 1.2))
        assertEquals(1.0, RideAnalytics.etaFactor(rides.take(3)), 0.001)
    }

    @Test
    fun mileage_trend_flags_a_drop_below_the_band() {
        fun fill(no: Int, kmpl: Double) = FuelFill(
            no, "2026-06-01", 1000.0 + no, 50.0, 5.0, 10_000L, kmpl, "",
        )
        val normal = (1..12).map { fill(it, 50.0 + (it % 3).toDouble()) }
        val trend = RideAnalytics.mileageTrend(normal)!!
        assertFalse(trend.drop)
        assertTrue(trend.high > trend.low)
        assertTrue(RideAnalytics.mileageTrend(normal + fill(13, 30.0))!!.drop)
    }

    @Test
    fun eco_score_penalises_hard_launches() {
        val clean = Ride(
            startedAt = 0L, endedAt = 1_800_000L, distanceKm = 10.0,
            avgSpeedKmh = 30.0, topSpeedKmh = 50, durationMin = 30,
            speedSamples = List(30) { 30 }, throttleSamples = List(30) { 30 },
        )
        val harsh = clean.copy(
            speedSamples = List(30) { 15 }, throttleSamples = List(30) { 85 })
        assertTrue(RideAnalytics.ecoScore(harsh).score < RideAnalytics.ecoScore(clean).score)
        assertEquals(1, RideAnalytics.ecoScore(harsh).hardLaunches)
    }

    @Test
    fun speed_fusion_learns_the_speedometer_bias() {
        val fusion = SpeedFusion()
        repeat(30) { fusion.update(60.0, 55.0, 3.0) }
        val out = fusion.update(60.0, 55.0, 3.0)
        assertTrue(out.errorKmh > 4.0 && out.errorKmh < 6.0)
        assertTrue(out.kmh in 54..60)
        val gpsless = SpeedFusion().update(40.0, null, null)
        assertTrue(gpsless.kmh in 1..40)
    }

    @Test
    fun severe_use_moves_the_service_reminder_earlier() {
        val calm = RideAnalytics.serviceAdvice(emptyList(), 3_000.0)
        assertEquals(5_000, calm.suggestedKm)
        val severe = (1..10).map {
            Ride(
                startedAt = it * 1_000L, endedAt = it * 1_000L + 900_000L,
                distanceKm = 1.0, avgSpeedKmh = 12.0, topSpeedKmh = 35,
                durationMin = 15, idleMs = 10 * 60_000L,
            )
        }
        val advice = RideAnalytics.serviceAdvice(severe, 3_000.0)
        assertTrue(advice.severe)
        assertTrue(advice.suggestedKm < 5_000)
    }

    @Test
    fun personal_eta_uses_navigation_sessions_and_stays_bounded() {
        // Five real routes where Maps is consistently 20% optimistic.
        val sessions = List(5) { 25 to 30 }
        assertEquals(1.2, RideAnalytics.personalEtaFactor(sessions, emptyList()), 0.001)
        // A single freak jam must not move the median.
        val withOutlier = sessions + (10 to 120)
        assertEquals(1.2, RideAnalytics.personalEtaFactor(withOutlier, emptyList()), 0.001)
        // Consistently way-off ratios are clamped so arrival times stay sane.
        val extreme = List(5) { 10 to 30 }
        assertEquals(1.5, RideAnalytics.personalEtaFactor(extreme, emptyList()), 0.001)
        // Too few sessions falls back to the old whole-ride estimate.
        val rides = List(5) {
            Ride(startedAt = it * 1000L, endedAt = it * 1000L + 1_800_000L,
                distanceKm = 10.0, avgSpeedKmh = 30.0, topSpeedKmh = 50,
                durationMin = 30, mapsEtaMinutes = 30)
        }
        assertEquals(1.0, RideAnalytics.personalEtaFactor(emptyList(), rides), 0.001)
    }

    @Test
    fun speed_fusion_keeps_cluster_speed_when_gps_is_absent() {
        val fusion = com.jupiterconnect.session.SpeedFusion()
        repeat(30) { fusion.update(42.0, null) }
        val fused = fusion.update(42.0, null)
        assertEquals(42, fused.kmh)
        assertEquals(0.0, fused.errorKmh, 0.001)
        assertEquals(-1, RideAnalytics.adjustedEta(-1, 1.2))
        assertEquals(30, RideAnalytics.adjustedEta(25, 1.2))
    }

    @Test
    fun phone_frame_debug_decodes_start_phone_payload() {
        val b = ByteArray(19)
        b[0] = 0x5B
        b[1] = 0x53
        "Mom Hi".toByteArray(Charsets.US_ASCII).copyInto(b, 2)
        val hex = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        assertEquals("Mom Hi",
            com.jupiterconnect.debug.PhoneFrameDebug.decode(hex, 0x53))
        // Wrong ID and short frames must not produce a false reading.
        assertEquals("", com.jupiterconnect.debug.PhoneFrameDebug.decode(hex, 0x43))
        assertEquals("", com.jupiterconnect.debug.PhoneFrameDebug.decode("5B 53 41", 0x53))
    }
}
