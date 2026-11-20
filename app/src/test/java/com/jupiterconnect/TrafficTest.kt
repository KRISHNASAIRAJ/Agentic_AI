package com.jupiterconnect

import com.jupiterconnect.protocol.buildScreenSwitch
import com.jupiterconnect.traffic.Cities
import com.jupiterconnect.traffic.TrafficScreen
import com.jupiterconnect.traffic.buildCricketFrame
import com.jupiterconnect.traffic.buildMarketFrame
import com.jupiterconnect.traffic.buildNewsFrame
import com.jupiterconnect.traffic.buildWeatherFrameWithCode
import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficTest {

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    @Test
    fun screen_switch_frame_matches_apk_traffic_sequence() {
        // createTrafficSequenceCustomFrame: 5B 4C 00 <id> 00*15 FF
        val f = buildScreenSwitch(TrafficScreen.WEATHER)
        assertEquals(20, f.size)
        assertEquals(0x5B, u(f, 0)); assertEquals(0x4C, u(f, 1))
        assertEquals(0x00, u(f, 2)); assertEquals(0x78, u(f, 3))
        assertEquals(0xFF, u(f, 19))
        assertEquals(121, u(buildScreenSwitch(TrafficScreen.CRICKET), 3))
        assertEquals(128, u(buildScreenSwitch(TrafficScreen.MARKET), 3))
    }

    @Test
    fun weather_frame_layout() {
        val f = buildWeatherFrameWithCode(95, 31, "THUNDER", 42, 60)
        assertEquals(20, f.size)
        assertEquals(0x5B, u(f, 0)); assertEquals(0x5D, u(f, 1))
        assertEquals(3, u(f, 2))            // icon: thunderstorm
        assertEquals(0, u(f, 3)); assertEquals(42, u(f, 4)) // AQI big-endian u16
        assertEquals(60, u(f, 5))           // rain probability
        assertEquals(0xFF, u(f, 19))
        assertEquals("31 C THUNDER", String(f, 6, 13, Charsets.US_ASCII).trim())
    }

    @Test
    fun cricket_frame_layout() {
        val f = buildCricketFrame("IND", "AUS", 185, 4, 20, 142, 6, 18)
        assertEquals(20, f.size)
        assertEquals(0x5B, u(f, 0)); assertEquals(0x81, u(f, 1))
        assertEquals(1, u(f, 3))  // IND
        assertEquals(2, u(f, 4))  // AUS
        assertEquals(0, u(f, 7)); assertEquals(185, u(f, 8)) // runs big-endian
        assertEquals(4, u(f, 9))
        assertEquals(0, u(f, 11)); assertEquals(142, u(f, 12))
        assertEquals(6, u(f, 13))
        assertEquals(0xFF, u(f, 19))
    }

    @Test
    fun market_frame_uses_football_scoreboard() {
        val f = buildMarketFrame(0.42, -0.31)
        assertEquals(20, f.size)
        assertEquals(0x5B, u(f, 0)); assertEquals(0x91, u(f, 1))
        assertEquals(4, u(f, 2))   // +0.42% x10
        assertEquals(-3, f[3].toInt()) // -0.31% x10 (signed byte)
        assertEquals("NIFTY", String(f, 8, 5, Charsets.US_ASCII))
        assertEquals("SENSE", String(f, 13, 5, Charsets.US_ASCII))
        assertEquals(0xFF, u(f, 19))
    }

    @Test
    fun city_lookup_defaults_to_auto() {
        val bengaluru = Cities.byName("Bengaluru")
        assertEquals("Bengaluru", bengaluru.name)
        assertEquals(12.9716, bengaluru.lat, 0.0001)
        assertEquals(Cities.AUTO, Cities.byName("Nowhere"))
        assertEquals(Cities.AUTO, Cities.byName(null))
    }

    @Test
    fun news_frame_is_78_bytes_with_75_byte_headline() {
        val f = buildNewsFrame("RBI keeps repo rate unchanged")
        assertEquals(78, f.size)
        assertEquals(0x5B, u(f, 0)); assertEquals(0x96, u(f, 1))
        assertEquals(0xFF, u(f, 77))
        assertEquals("RBI keeps repo rate unchanged",
            String(f, 2, 29, Charsets.US_ASCII).trim())
    }
}
