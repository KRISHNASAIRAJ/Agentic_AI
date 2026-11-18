package com.jupiterconnect.voice

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import com.jupiterconnect.protocol.sanitize
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.transport.ClusterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turns a spoken command into an action and a short reply. Every reply is
 * spoken by the activity and written to the cluster's text row.
 */
class VoiceCommandProcessor(private val context: Context) {

    suspend fun process(spoken: String): String {
        val text = spoken.trim().lowercase(Locale.getDefault())
        return when {
            isGreeting(text) -> "Hello rider"
            isNavigation(text) -> navigationReply(spoken)
            isPoi(text) -> poiReply(spoken)
            isPauseNav(text) -> {
                ClusterRepository.mirroring.value = false
                "Navigation paused"
            }
            isResumeNav(text) -> {
                ClusterRepository.mirroring.value = true
                "Navigation resumed"
            }
            isBrightnessUp(text) -> {
                ClusterRepository.setBrightness(ClusterRepository.brightness.value + 1)
                "Brightness increased"
            }
            isBrightnessDown(text) -> {
                ClusterRepository.setBrightness(ClusterRepository.brightness.value - 1)
                "Brightness decreased"
            }
            isVolumeUp(text) -> adjustVolume(AudioManager.ADJUST_RAISE, "Volume increased")
            isVolumeDown(text) -> adjustVolume(AudioManager.ADJUST_LOWER, "Volume decreased")
            isCurrentLocation(text) -> currentLocationReply()
            isSaveLocation(text) -> saveLocationReply()
            isAutoSms(text) -> toggleReply(text, "auto sms", ClusterRepository.autoSms,
                "Auto SMS reply enabled", "Auto SMS reply disabled")
            isDnd(text) -> toggleReply(text, "dnd", ClusterRepository.dnd,
                "Do not disturb enabled", "Do not disturb disabled")
            isSpeedAlert(text) -> toggleReply(text, "alert", ClusterRepository.highSpeedAlert,
                "High speed alert enabled", "High speed alert disabled")
            isEta(text) -> etaReply()
            isWeather(text) -> weatherReply()
            isNews(text) -> newsReply()
            isCricket(text) -> {
                ClusterRepository.service?.showScreen(0x79)
                scoreReply("cricket", "https://site.api.espn.com/apis/site/v2/sports/cricket/1000/scoreboard")
            }
            isFootball(text) -> {
                ClusterRepository.service?.showScreen(0x80)
                scoreReply("football", "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard")
            }
            isRain(text) -> rainReply()
            isNextTrack(text) -> { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); "Playing next song" }
            isPreviousTrack(text) -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); "Playing previous song" }
            isPlayMusic(text) -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY); "Playing music" }
            else -> "Sorry, I did not understand that command"
        }
    }

    private fun isGreeting(t: String) =
        t.startsWith("hello") || t.contains("how are you") || t.contains("good morning")
    private fun isEta(t: String) =
        t.contains("estimated time") || t.contains("arrival time") || t.contains("eta")
    private fun isWeather(t: String) =
        t.contains("weather") || t.contains("temperature")
    private fun isRain(t: String) =
        t.contains("rain") || t.contains("raincoat") || t.contains("rain coat")
    private fun isNavigation(t: String) = listOf(
        "take me to", "navigate to", "navigate me to", "go to", "directions to",
        "guide me to", "let's go to", "show me", "take to", "navigate",
    ).any { t.contains(it) }
    private fun isPoi(t: String) = listOf("nearest", "nearby", "near to", "near ").any { t.contains(it) }
    private fun isPauseNav(t: String) =
        t.contains("pause") && (t.contains("nav") || t.contains("navigation"))
    private fun isResumeNav(t: String) =
        (t.contains("resume") || t.contains("start") || t.contains("play")) && t.contains("nav")
    private fun isBrightnessUp(t: String) =
        t.contains("brightness") && (t.contains("increase") || t.contains("up") ||
            t.contains("high") || t.contains("turn up"))
    private fun isBrightnessDown(t: String) =
        t.contains("brightness") && (t.contains("decrease") || t.contains("down") ||
            t.contains("low") || t.contains("turn down"))
    private fun isVolumeUp(t: String) =
        t.contains("volume") && (t.contains("up") || t.contains("increase"))
    private fun isVolumeDown(t: String) =
        t.contains("volume") && (t.contains("down") || t.contains("decrease") || t.contains("reduce"))
    private fun isCurrentLocation(t: String) =
        t.contains("my location") || t.contains("where am i") || t.contains("current location")
    private fun isSaveLocation(t: String) =
        t.contains("save location") || t.contains("save current location")
    private fun isAutoSms(t: String) = t.contains("auto sms") || t.contains("auto reply")
    private fun isDnd(t: String) = t.contains("do not disturb") || t.contains("dnd")
    private fun isSpeedAlert(t: String) =
        t.contains("speed alert") || t.contains("high speed")
    private fun isNews(t: String) =
        t.contains("news") || t.contains("headlines")
    private fun isCricket(t: String) = t.contains("cricket")
    private fun isFootball(t: String) = t.contains("football")
    private fun isNextTrack(t: String) = t.contains("next song") || t.contains("next track")
    private fun isPreviousTrack(t: String) =
        t.contains("previous song") || t.contains("previous track") || t.contains("last song")
    private fun isPlayMusic(t: String) =
        t.contains("play song") || t.contains("play music") || t.contains("resume music")

    private fun toggleReply(
        text: String,
        key: String,
        flow: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
        on: String,
        off: String,
    ): String {
        val disable = text.contains("disable") || text.contains("off") || text.contains("stop")
        flow.value = !disable
        if (key == "dnd") applyDnd(!disable)
        return if (disable) off else on
    }

    private fun applyDnd(enable: Boolean) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                nm?.isNotificationPolicyAccessGranted == true
            ) {
                nm.setInterruptionFilter(
                    if (enable) NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL,
                )
            }
        }
    }

    private fun adjustVolume(direction: Int, reply: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return reply
        runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
        return reply
    }

    private fun navigationReply(spoken: String): String {
        val lower = spoken.lowercase(Locale.getDefault())
        val markers = listOf("take me to", "navigate me to", "navigate to", "directions to",
            "guide me to", "let's go to", "show me directions to", "show me", "take to", "go to")
        val place = markers.firstNotNullOfOrNull { marker ->
            val i = lower.indexOf(marker)
            if (i >= 0) spoken.substring(i + marker.length).trim().ifBlank { null } else null
        } ?: return "Tell me where to navigate"
        return openBikeRoute(place, "Navigating to $place")
    }

    private fun poiReply(spoken: String): String {
        val place = when {
            spoken.contains("petrol", true) || spoken.contains("fuel", true) ||
                spoken.contains("gas station", true) -> "petrol pump"
            spoken.contains("hospital", true) -> "hospital"
            spoken.contains("restaurant", true) || spoken.contains("food", true) -> "restaurant"
            else -> spoken.replace(Regex("nearest|nearby|near to|near|take me to", RegexOption.IGNORE_CASE), "")
                .trim().ifBlank { "place" }
        }
        return openBikeRoute(place, "Taking you to nearest $place")
    }

    private fun openBikeRoute(place: String, reply: String): String {
        val encoded = URLEncoder.encode(place, "UTF-8")
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=bicycling"))
                .setPackage("com.google.android.apps.maps"),
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded&mode=b"))
                .setPackage("com.google.android.apps.maps"),
            Intent(Intent.ACTION_VIEW, Uri.parse("mappls://navigate?q=$encoded"))
                .setPackage("com.mmi.maps"),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
                .setPackage("com.google.android.apps.maps"),
        )
        for (candidate in candidates) {
            if (runCatching {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess) return reply
        }
        return "No maps app could open $place"
    }

    private fun currentLocationReply(): String {
        val p = RideStore.parkedLocation.value
            ?: return "No saved location yet"
        ClusterRepository.service?.sendPlace("Near ${p.coordinates}")
        return "You are around ${p.coordinates}"
    }

    private fun saveLocationReply(): String {
        val p = RideStore.parkedLocation.value ?: return "No location saved yet"
        ClusterRepository.service?.sendPlace("Saved ${p.coordinates}")
        return "Location saved at ${p.coordinates}"
    }

    private fun mediaKey(code: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun etaReply(): String {
        val step = ClusterRepository.navInstruction.value
            ?: return "No active route"
        return if (step.etaMinutes > 0)
            "About ${step.etaMinutes} minutes to destination"
        else "Arrival time not available"
    }

    private suspend fun weatherReply(): String = withContext(Dispatchers.IO) {
        val parked = RideStore.parkedLocation.value
            ?: return@withContext "No parked location saved yet"
        val temp = getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=${parked.lat}&longitude=${parked.lng}" +
                "&current=temperature_2m,weather_code&timezone=Asia%2FKolkata",
        )?.optJSONObject("current")
        val aqi = getJson(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${parked.lat}" +
                "&longitude=${parked.lng}&current=us_aqi&timezone=Asia%2FKolkata",
        )?.optJSONObject("current")?.optInt("us_aqi", -1) ?: -1
        if (temp == null) return@withContext "Weather information unavailable"
        val c = temp.getDouble("temperature_2m").roundToInt()
        val code = temp.getInt("weather_code")
        val desc = when (code) {
            0 -> "clear sky"
            1, 2 -> "partly cloudy"
            3 -> "overcast"
            45, 48 -> "foggy"
            in 51..57 -> "drizzling"
            in 61..67 -> "rainy"
            in 71..77 -> "snowy"
            in 80..82 -> "showers"
            95 -> "thunderstorms"
            else -> "changeable"
        }
        val aqiText = if (aqi >= 0) ", AQI $aqi" else ""
        "Weather $c degrees, $desc$aqiText"
    }

    private suspend fun rainReply(): String = withContext(Dispatchers.IO) {
        val parked = RideStore.parkedLocation.value
            ?: return@withContext "No parked location saved yet"
        val current = getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=${parked.lat}&longitude=${parked.lng}" +
                "&current=weather_code&timezone=Asia%2FKolkata",
        )?.optJSONObject("current") ?: return@withContext "Rain status unavailable"
        val code = current.getInt("weather_code")
        if (code in 51..82 || code == 95) "Take a rain coat" else "No rain expected"
    }

    private suspend fun newsReply(): String = withContext(Dispatchers.IO) {
        val xml = getText("https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en")
            ?: return@withContext "News unavailable"
        val title = firstXmlText(xml, "title")
        if (title.isNullOrBlank()) "News unavailable" else title.take(120)
    }

    private suspend fun scoreReply(sport: String, url: String): String = withContext(Dispatchers.IO) {
        val json = getJson(url) ?: return@withContext "$sport scores unavailable"
        val events = json.optJSONArray("events") ?: return@withContext "No live $sport matches"
        if (events.length() == 0) return@withContext "No live $sport matches"
        val e = events.getJSONObject(0)
        val name = e.optString("name", "$sport match")
        val status = e.optJSONObject("status")?.optString("type", "") ?: ""
        "$name: $status".take(120)
    }

    private fun getJson(url: String): JSONObject? =
        getText(url)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun getText(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "JupiterConnect/1.0")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        body
    }.getOrNull()

    private fun firstXmlText(xml: String, tag: String): String? = runCatching {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        var found: String? = null
        while (event != XmlPullParser.END_DOCUMENT && found == null) {
            if (event == XmlPullParser.START_TAG && parser.name == tag) {
                found = parser.nextText()
            }
            event = parser.next()
        }
        found
    }.getOrNull()
}
