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
 */
class VoiceAssistantBridge(private val context: Context) {

    private val backgroundVoice = BackgroundVoiceListener(context)
    private var launches = 0
    private val seenValues = mutableSetOf<Int>()
    private var lastAction = -1
    // A switch level held when the service starts is stale. Prime from the
    // first frame so opening the app never replays a queued voice prompt.
    private var primed = false

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
        // ONLY the handlebar voice button (action 4) triggers. The power/
        // start button pressed without the clutch is a different gesture and
        // must never open voice input.
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
        // Bit 7 of the ISS byte is ALSO set by pressing start without the
        // clutch, so it is no longer used as a voice trigger. Keep the trace
        // for diagnostics only — never open input from it.
        val raw = t.issMode and 0xFF
        if (seenValues.add(raw)) {
            val note = "VOICE byte6 seen: 0x%02X (%d)".format(raw, raw)
            FrameLog.note(note)
            ClusterRepository.voiceDebug.value = "$note | bit7=${raw and 0x80 != 0}"
        }
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
            // App in background: the mic turns on from the service and stays
            // on until the rider speaks. No screen, no queued "speak" prompt.
            FrameLog.note("VOICE from background - listening in service")
            backgroundVoice.start()
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
