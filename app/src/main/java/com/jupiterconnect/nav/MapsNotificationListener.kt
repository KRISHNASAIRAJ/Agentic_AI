package com.jupiterconnect.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jupiterconnect.transport.ClusterRepository

/**
 * Reads turn-by-turn from any maps app. This is the same approach the official
 * TVS app uses: the cluster does not know what a maps app is, so we take the
 * instruction from the notification and forward it over BLE.
 *
 * Notification access is a Settings toggle, not a runtime permission -
 * without it this service never receives anything and mirroring silently
 * does nothing.
 */
class MapsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        // STRICT whitelist only. No heuristic or flag-based detection: the
        // cluster must show ONLY navigation from a known maps app, never a
        // call, message, or any other ongoing notification that happens to
        // carry a title and text.
        if (n.packageName !in MAP_PACKAGES) return
        if (!n.isOngoing) return

        // Google Maps posts multiple ongoing notifications while navigating:
        // the real turn instruction AND a near-empty one ("0 m" / distance
        // only). Taking whichever arrives last made the cluster flicker
        // between "Head east on" and a bare "0m". Require a real instruction:
        // at least one field must contain a maneuver word or a street name.
        val hasInstruction = listOf(title, text, bigText, subText).any { field ->
            field.length > 3 && MANEUVER_WORDS.any { field.contains(it, true) }
        }
        if (!hasInstruction) return

        val parsed = NavParsing.parse(title, text, bigText, subText)

        // Google Maps sometimes puts the distance ("40 m") in a non-standard
        // extra rather than the four fields above. Scan every CharSequence
        // extra as a fallback so the cluster shows the real distance.
        var distance = parsed.distanceMeters
        if (distance < 0) {
            val allText = extras.keySet().mapNotNull { key ->
                runCatching { extras.getCharSequence(key)?.toString() }.getOrNull()
            }.filter { it.isNotBlank() }.toTypedArray()
            distance = NavParsing.findDistance(*allText)
        }
        // Total remaining distance, if Maps publishes it anywhere.
        val remaining = NavParsing.findRemaining(title, text, bigText, subText).let { r ->
            if (r >= 0) r else {
                val allText = extras.keySet().mapNotNull { key ->
                    runCatching { extras.getCharSequence(key)?.toString() }.getOrNull()
                }.toTypedArray()
                NavParsing.findRemaining(*allText)
            }
        }
        ClusterRepository.navInstruction.value = NavInstruction(
            title = parsed.maneuver,
            street = parsed.street,
            distanceMeters = distance.coerceAtLeast(0),
            etaMinutes = parsed.etaMinutes,
            pictogram = parsed.pictogram,
            provider = n.packageName,
            remainingMeters = remaining,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName in MAP_PACKAGES) {
            ClusterRepository.navInstruction.value = null
        }
    }

    companion object {
        private val MANEUVER_WORDS = listOf(
            "turn", "head", "continue", "keep", "merge", "fork",
            "roundabout", "exit", "u-turn", "arrive", "destination", "onto",
            "left", "right", "straight", "north", "south", "east", "west",
        )
        val MAP_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.mappls.app",
            "com.mmi.android.mmaps",
            "org.organicmaps",
            "com.here.app.maps",
            "com.waze",
            "com.tomtom.gotcha",
        )
    }
}
