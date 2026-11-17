package com.jupiterconnect.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import com.jupiterconnect.session.RideStore
import com.jupiterconnect.transport.ClusterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Turns a spoken command into an action and a short reply. The reply is both
 * spoken (by the activity) and sent to the cluster's text row.
 */
class VoiceCommandProcessor(private val context: Context) {

    suspend fun process(spoken: String): String {
        val text = spoken.trim().lowercase()
        return when {
            isWeather(text) -> weatherReply()
            isNavigation(text) -> navigationReply(spoken)
            isPauseNav(text) -> {
                ClusterRepository.mirroring.value = false
                "Navigation mirroring paused"
            }
            isResumeNav(text) -> {
                ClusterRepository.mirroring.value = true
                "Navigation mirroring resumed"
            }
            isNextTrack(text) -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                "Playing next song"
            }
            isPreviousTrack(text) -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "Playing previous song"
            }
            isPlayMusic(text) -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                "Playing music"
            }
            else -> "Sorry, I did not understand that command"
        }
    }

    private fun isWeather(t: String) =
        t.contains("weather") || t.contains("temperature") || t.contains("will it rain")

    private fun isNavigation(t: String) =
        t.contains("take me to") || t.contains("navigate to") || t.contains("go to") ||
            t.contains("directions to") || t.contains("navigate")

    private fun isPauseNav(t: String) =
        t.contains("pause") && (t.contains("nav") || t.contains("navigation"))

    private fun isResumeNav(t: String) =
        (t.contains("resume") || t.contains("play")) && t.contains("nav")

    private fun isNextTrack(t: String) = t.contains("next song") || t.contains("next track")
    private fun isPreviousTrack(t: String) =
        t.contains("previous song") || t.contains("previous track") || t.contains("last song")
    private fun isPlayMusic(t: String) =
        t.contains("play song") || t.contains("play music") || t.contains("resume music")

    private fun navigationReply(spoken: String): String {
        val lower = spoken.lowercase()
        val place = listOf("take me to", "navigate to", "go to", "directions to")
            .firstNotNullOfOrNull { marker ->
                val i = lower.indexOf(marker)
                if (i >= 0) spoken.substring(i + marker.length).trim().ifBlank { null } else null
            } ?: return "Tell me where to navigate"

        // Prefer Mappls; fall back to whatever maps app handles geo: Uris.
        val encoded = URLEncoder.encode(place, "UTF-8")
        val mappls = Intent(Intent.ACTION_VIEW, Uri.parse("mappls://search?q=$encoded")).apply {
            setPackage("com.mmi.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = runCatching { context.startActivity(mappls) }.isSuccess
        if (!ok) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        return "Opening $place in maps"
    }

    private fun mediaKey(code: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private suspend fun weatherReply(): String = withContext(Dispatchers.IO) {
        val parked = RideStore.parkedLocation.value
            ?: return@withContext "No parked location saved yet"
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=${parked.lat}&longitude=${parked.lng}" +
                "&current=temperature_2m,weather_code&timezone=Asia%2FKolkata"
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val code = current.getInt("weather_code")
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
            "Weather $temp degrees, $desc"
        }.getOrDefault("Weather information unavailable")
    }
}
