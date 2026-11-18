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

/**
 * Launches our own voice screen when the cluster asks for it.
 *
 * On the Jupiter 125 the voice/start gesture shows up as bit 7 (128) in byte 6
 * of the 0x11 service frame - the same value seen when start is pressed without
 * the clutch. The cluster then displays its voice symbol and expects the phone
 * to take voice input, exactly as the TVS demo shows.
 */
class VoiceAssistantBridge(private val context: Context) {

    private var armed = true

    /** Call for every decoded telemetry frame. */
    fun onFrame(t: Telemetry) {
        val raw = t.issMode and 0xFF
        if (raw and 0x80 != 0) {
            if (armed) {
                armed = false
                FrameLog.note("VOICE trigger ISS=$raw")
                launch()
            }
        } else {
            // Re-arm only after the button is released, so one press = one launch.
            armed = true
        }
    }

    fun reset() {
        armed = true
    }

    private fun launch() {
        // A foreground service may be blocked from starting an activity
        // directly on newer Android versions. A PendingIntent sent from the
        // service is the supported path; if that is blocked too, a heads-up
        // notification gives the user a one-tap way in.
        val intent = Intent(context, VoiceCommandActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, 71, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val started = runCatching { pending.send() }.isSuccess ||
            runCatching { context.startActivity(intent) }.isSuccess
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
}
