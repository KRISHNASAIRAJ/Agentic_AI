package com.jupiterconnect.transport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jupiterconnect.R
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.protocol.Telemetry
import com.jupiterconnect.voice.VoiceCommandActivity
import com.jupiterconnect.voice.BackgroundVoiceListener

/**
 * Launches our own voice screen when the cluster asks for it.
 *
 * On the Jupiter 125 the voice/start gesture shows up as bit 7 (128) in byte 6
 * of the 0x11 service frame - the same value seen when start is pressed without
 * the clutch. The cluster then displays its voice symbol and expects the phone
 * to take voice input, exactly as the TVS demo shows.
 *
 * Both signals are supported:
 *  - the handlebar voice button (0x10 byte 12 action 4), and
 *  - a short press of the start button without the clutch (0x11 byte 6 bit 7).
 *
 * Both are level-held, so only a rising edge triggers, and the level present
 * when the service starts is primed away so no queued prompt is replayed.
 * Voice input opens only while the app is on screen; background triggers are
 * ignored so the microphone is never started from the service.
 */
class VoiceAssistantBridge(private val context: Context) {

    private val backgroundVoice = BackgroundVoiceListener(context)
    private var launches = 0
    private val seenValues = mutableSetOf<Int>()
    private var lastAction = -1
    // A switch level held when the service starts is stale. Prime from the
    // first frame so opening the app never replays a queued voice prompt.
    private var primed = false
    // Same idea for the start-without-clutch (ISS bit 7) gesture.
    private var issPrimed = false
    private var issWasPressed = false
    private var lastIssLaunchMs = 0L

    /**
     * The cluster reports handlebar switch actions in 0x10 byte 12:
     * 1 accept call, 2 reject, 3 reject + SMS, 4 = voice assistant.
     * It is a level held across frames, so act only on a change.
     */
    fun onSwitchAction(action: Int) {
        if (!primed) {
            // Ignore the level already present when the service/app starts.
            primed = true
            lastAction = action
            return
        }
        // The handlebar voice button (action 4) is one of two triggers; the
        // start-without-clutch gesture arrives separately in 0x11 byte 6 and
        // is handled in onFrame(). Ignore other switch actions here.
        if (action == VOICE_ACTION && lastAction != VOICE_ACTION) {
            launches++
            val note = "VOICE button #$launches (0x10 byte12=$action)"
            FrameLog.note(note)
            ClusterRepository.voiceDebug.value = note
            launch()
        }
        if (action >= 0 && action != lastAction && action != VOICE_ACTION) {
            ClusterRepository.voiceDebug.value = "Switch action $action (voice=4)"
        }
        lastAction = action
    }

    private companion object {
        const val VOICE_ACTION = 4
    }

    /** Call for every decoded telemetry frame. */
    fun onFrame(t: Telemetry) {
        val raw = t.issMode and 0xFF
        val pressed = raw and 0x80 != 0
        if (seenValues.add(raw)) {
            val note = "VOICE byte6 seen: 0x%02X (%d)".format(raw, raw)
            FrameLog.note(note)
            ClusterRepository.voiceDebug.value = "$note | bit7=$pressed"
        }
        // The start button without the clutch is a level held for a moment.
        // Ignore any level already present when the service starts, then act
        // only on a rising edge with a short cooldown so ISS activity cannot
        // fire repeated voice prompts.
        if (!issPrimed) {
            issPrimed = true
            issWasPressed = pressed
            return
        }
        val now = System.currentTimeMillis()
        if (pressed && !issWasPressed && now - lastIssLaunchMs > 2_000L) {
            lastIssLaunchMs = now
            launches++
            val note = "VOICE start button #$launches (0x11 byte6=0x%02X)".format(raw)
            FrameLog.note(note)
            ClusterRepository.voiceDebug.value = note
            launch()
        }
        issWasPressed = pressed
    }

    /** Test hook: behaves exactly like a cluster trigger. */
    fun simulate() {
        launches++
        val note = "VOICE manual test #$launches"
        FrameLog.note(note)
        ClusterRepository.voiceDebug.value = note
        launch()
    }

    /** New BLE link: keep the primed switch level so a held button cannot replay. */
    fun reset() { /* intentionally no-op */ }

    private fun launch() {
        if (!ClusterRepository.uiResumed) {
            // Voice input runs only while the app is on screen. Opening the
            // microphone from the background requires switching the foreground
            // service type, which is unreliable on OEM ROMs and can tear down
            // the BLE link. Ignore the trigger instead of risking the ride.
            FrameLog.note("VOICE trigger ignored - app in background")
            return
        }
        // App in foreground: use the existing VoiceCommandActivity screen.
        // A foreground service can usually start an activity directly, but
        // OEMs block it in some states. Try direct first, then a PendingIntent,
        // then a heads-up notification the user can tap.
        val intent = Intent(context, VoiceCommandActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, 71, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val started = runCatching { context.startActivity(intent) }.isSuccess ||
            runCatching { pending.send() }.isSuccess
        if (!started) {
            FrameLog.note("VOICE direct launch blocked - posting notification")
            postTapNotification(pending)
        }
    }

    private fun postTapNotification(pending: PendingIntent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "voice", "Voice command",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, "voice")
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle("Jupiter voice")
            .setContentText("Tap to speak a command")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { manager.notify(71, notification) }
            .onFailure { FrameLog.note("VOICE notification failed: ${it.message}") }
    }

    /** Release the mic/TTS when the BLE service is destroyed. */
    fun destroy() {
        backgroundVoice.destroy()
    }
}
