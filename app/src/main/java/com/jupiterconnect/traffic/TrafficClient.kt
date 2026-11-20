package com.jupiterconnect.traffic

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Free, keyless data sources. No TVS account and no TVS cloud endpoints. */
object TrafficClient {

    data class Weather(val code: Int, val tempC: Int, val desc: String, val aqi: Int, val rainPct: Int)
    data class Cricket(
        val team1: String, val team2: String,
        val runs1: Int, val wickets1: Int, val overs1: Int,
        val runs2: Int, val wickets2: Int, val overs2: Int,
        val state: Int,
    )
    data class Market(val nifty: Double, val niftyPct: Double, val sensex: Double, val sensexPct: Double)
    data class News(val headline: String)

    private fun get(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "JupiterConnect/1.1")
        conn.setRequestProperty("Accept", "application/json,text/xml;q=0.9,*/*;q=0.8")
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        body
    }.getOrNull()

    /** Open-Meteo current weather + US AQI, both keyless. */
    fun weather(lat: Double, lon: Double): Weather? {
        val w = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&timezone=Asia%2FKolkata"
        ) ?: return null
        val aqiJson = get(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                "&current=us_aqi&timezone=Asia%2FKolkata"
        )
        val current = runCatching { JSONObject(w).getJSONObject("current") }.getOrNull() ?: return null
        val code = current.optInt("weather_code", 0)
        val temp = current.optDouble("temperature_2m", 0.0).toInt()
        val aqi = runCatching {
            JSONObject(aqiJson ?: "{}").getJSONObject("current").optInt("us_aqi", 0)
        }.getOrDefault(0)
        return Weather(code, temp, describe(code), aqi, 0)
    }

    /** True when either side is India (by abbreviation, name or series name). */
    private fun involvesIndia(event: JSONObject): Boolean {
        val competitors = runCatching {
            event.getJSONArray("competitions").getJSONObject(0).optJSONArray("competitors")
        }.getOrNull() ?: return false
        for (i in 0 until competitors.length()) {
            val team = runCatching {
                competitors.getJSONObject(i).getJSONObject("team")
            }.getOrNull() ?: continue
            val abbr = team.optString("abbreviation", "")
            val name = team.optString("displayName", "") + " " + team.optString("name", "")
            if (abbr.equals("IND", true) || name.contains("India", true)) return true
        }
        // Some feeds expose the series name but not full team objects.
        val series = event.optString("name", "")
        return series.contains("India", true)
    }

    private fun describe(code: Int) = when (code) {
        0 -> "CLEAR"; 1, 2 -> "PARTLY CLOUDY"; 3 -> "OVERCAST"
        45, 48 -> "FOG"; in 51..57 -> "DRIZZLE"; in 61..67 -> "RAIN"
        in 71..77, 85, 86 -> "SNOW"; in 80..82 -> "SHOWERS"
        95, 96, 99 -> "THUNDER"; else -> "CLEAR"
    }

    /** Wickets from the first statistics entry whose label mentions wickets. */
    private fun wicketsOf(c: JSONObject): Int {
        val direct = c.optInt("wickets", -1)
        if (direct >= 0) return direct
        val stats = c.optJSONArray("statistics") ?: return 0
        for (i in 0 until stats.length()) {
            val s = stats.optJSONObject(i) ?: continue
            val label = (s.optString("label", "") + " " + s.optString("name", "")).lowercase()
            if (label.contains("wicket")) {
                return s.optString("displayValue", "0").trim().toIntOrNull()
                    ?: s.optInt("value", 0)
            }
        }
        return 0
    }

    /**
     * ESPN public cricket scoreboard, restricted to matches involving India.
     * All parsing is defensive; returns null when no India match is live.
     */
    fun cricket(): Cricket? {
        val body = get("https://site.api.espn.com/apis/site/v2/sports/cricket/1000/scoreboard") ?: return null
        val events = runCatching {
            JSONObject(body).getJSONArray("events")
        }.getOrNull() ?: return null
        val event = (0 until events.length()).mapNotNull { events.optJSONObject(it) }
            .firstOrNull { involvesIndia(it) } ?: return null
        val competition = runCatching {
            event.getJSONArray("competitions").getJSONObject(0)
        }.getOrNull() ?: return null
        val competitors = competition.optJSONArray("competitors") ?: return null
        if (competitors.length() < 2) return null
        fun side(i: Int): Triple<String, Int, Int> {
            val c = competitors.getJSONObject(i)
            val team = runCatching {
                c.getJSONObject("team").optString("abbreviation", "")
            }.getOrDefault("")
            // ESPN exposes runs as `score`; wickets live in a statistics entry.
            val runs = runCatching {
                c.optString("score", "0").trim().toIntOrNull() ?: 0
            }.getOrDefault(0)
            val wickets = wicketsOf(c)
            return Triple(team.ifBlank { "IND" }, runs, wickets)
        }
        val a = side(0); val b = side(1)
        val state = 0 // neutral; the cluster only needs a valid state byte
        return Cricket(a.first, b.first, a.second, a.third, 0, b.second, b.third, 0, state)
    }

    /** Yahoo Finance keyless chart endpoint for Nifty 50 and BSE Sensex. */
    fun market(): Market? {
        fun quote(symbol: String): Pair<Double, Double>? {
            val body = get(
                "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=1d"
            ) ?: return null
            val meta = runCatching {
                JSONObject(body).getJSONArray("chart").getJSONObject(0).getJSONObject("meta")
            }.getOrNull() ?: return null
            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            val prev = meta.optDouble("previousClose", Double.NaN)
            if (price.isNaN() || prev <= 0.0) return null
            return price to ((price - prev) / prev) * 100.0
        }
        val nifty = quote("%5ENSEI") ?: return null  // ^NSEI
        val sensex = quote("%5EBSESN") ?: return null // ^BSESN
        return Market(nifty.first, nifty.second, sensex.first, sensex.second)
    }

    /**
     * Stock-market-only headlines via Google News RSS search. Keyless, no API
     * key, India/English, last 24 hours. Covers Nifty, Sensex and Indian equities.
     */
    fun news(): News? {
        val query = "Nifty OR Sensex OR stock market India OR BSE when:1d"
        val xml = get(
            "https://news.google.com/rss/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&hl=en-IN&gl=IN&ceid=IN:en"
        ) ?: return null
        val matcher = Regex("<item>.*?<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(xml) ?: return null
        val title = matcher.groupValues[1]
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
            .trim()
        return if (title.isEmpty()) null else News(title)
    }
}
