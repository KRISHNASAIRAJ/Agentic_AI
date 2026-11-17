package com.jupiterconnect.transport

import android.content.Context
import android.content.Intent
import com.jupiterconnect.debug.FrameLog
import com.jupiterconnect.protocol.Telemetry

/**
 * Launches the phone's voice assistant when the cluster asks for it.
 *
 * On the Jupiter 125 the voice/start gesture shows up as bit 7 (128) in byte 6
 * of the 0x11 service frame - the same value seen when start is pressed without
 * the clutch. The cluster then displays its voice symbol and expects the phone
 * to take voice input (Google Assistant), exactly as the TVS demo shows.
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
        val intent = Intent(Intent.ACTION_ASSIST).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { FrameLog.note("VOICE launch failed: ${it.message}") }
    }
}
