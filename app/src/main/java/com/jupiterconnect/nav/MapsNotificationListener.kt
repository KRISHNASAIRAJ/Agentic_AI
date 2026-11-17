package com.jupiterconnect.nav

import android.app.Notification
import android.os.Bundle
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
        // the real turn instruction AND a near-empty one whose title is only a
        // distance ("0 m") but whose subText says "Arrive 5:49 pm". Taking that
        // junk one made the cluster flicker between the real instruction and a
        // bare "0m". Require the TITLE itself to contain a real maneuver word
        // (turn/head/continue/left/right/...) and to not be distance-only.
        val titleHasManeuver = MANEUVER_WORDS.any { title.contains(it, true) }
        val titleIsDistanceOnly = NavParsing.findDistance(title) >= 0 &&
            title.replace(Regex("""[\d.,\s]|(km|mi|ft|m)\b""", RegexOption.IGNORE_CASE), "").isBlank()
        if (!titleHasManeuver || titleIsDistanceOnly) return

        // This is the notification we accepted - remember its key so a junk
        // Maps notification being removed cannot clear the real instruction.
        activeKey = n.key

        val parsed = NavParsing.parse(title, text, bigText, subText)

        // Android 16 Maps uses a promoted "Live Update" notification. Its chip
        // shows "40 m" from android.shortCriticalText, which is NOT one of
        // TITLE/TEXT/BIG/SUB. Scan every extra value (not just CharSequences)
        // so that chip distance reaches the cluster.
        val extraTexts = collectExtraTexts(extras)
        var distance = parsed.distanceMeters
        if (distance < 0) distance = NavParsing.findDistance(*extraTexts)
        // Total remaining distance, if Maps publishes it anywhere.
        val remaining = NavParsing.findRemaining(title, text, bigText, subText).let { r ->
            if (r >= 0) r else NavParsing.findRemaining(*extraTexts)
        }
        // A different maneuver is a different step - never reuse the previous
        // step's distance. Only hold the last value across re-posts of the SAME
        // step (Maps removes and re-posts its notification on every update).
        if (parsed.maneuver != lastManeuver) {
            lastKnownDistance = -1
            lastManeuver = parsed.maneuver
        }
        // When Maps publishes no distance at all (e.g. the start-of-route
        // "Head towards ..." step), keep -1 as "unknown". Coercing it to 0
        // made the cluster blink "0m" even though no distance was known.
        val effectiveDistance = if (distance >= 0) {
            lastKnownDistance = distance
            distance
        } else lastKnownDistance

        // Diagnostic snapshot so one screenshot on the Rides tab shows exactly
        // what Maps published and what we parsed/sent - no more guessing.
        ClusterRepository.navDebug.value = buildString {
            appendLine("TITLE: $title")
            appendLine("TEXT: $text")
            appendLine("BIG: $bigText")
            appendLine("SUB: $subText")
            appendLine("parsed.dist=${parsed.distanceMeters} fallback=$distance")
            appendLine("eta=${parsed.etaMinutes} pic=${parsed.pictogram}")
            appendLine("lastKnown=$lastKnownDistance sent=$effectiveDistance")
            appendLine("shortCritical=${runCatching { extras.get("android.shortCriticalText")?.toString() }.getOrNull()}")
            appendLine("info=${runCatching { extras.get("android.infoText")?.toString() }.getOrNull()}")
            appendLine("progress=${extras.getInt("android.progress")}/${extras.getInt("android.progressMax")}")
            appendLine("segments=${runCatching { extras.get("android.progressSegments")?.toString() }.getOrNull()}")
            appendLine("EXTRAS: ${extras.keySet().joinToString { k -> "$k=${extras.get(k)}" }}")
            appendLine("hasContentView=${n.notification.contentView != null}")
        }

        // Cancel a pending clear: Maps updates its notification by remove + re-post.
        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
        ClusterRepository.navInstruction.value = NavInstruction(
            title = parsed.maneuver,
            street = parsed.street,
            distanceMeters = effectiveDistance,
            etaMinutes = parsed.etaMinutes,
            pictogram = parsed.pictogram,
            provider = n.packageName,
            remainingMeters = remaining,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName !in MAP_PACKAGES) return
        // Maps posts junk notifications too. Only the removal of the one we
        // actually accepted may clear the instruction.
        if (sbn?.key != activeKey) return
        // Maps remove+reposts its notification on every update. Clearing
        // immediately blanked the cluster text between remove and repost —
        // that is the flicker. Delay the clear so a quick repost cancels it.
        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = Runnable {
            ClusterRepository.navInstruction.value = null
            pendingClear = null
            lastKnownDistance = -1 // new route must not inherit the old distance
            lastManeuver = ""
            activeKey = null
        }.also { handler.postDelayed(it, CLEAR_GRACE_MS) }
    }

    /** Every readable string from a notification's extras, chip text first. */
    private fun collectExtraTexts(extras: Bundle): Array<String> {
        val values = mutableListOf<String>()
        for (key in arrayOf("android.shortCriticalText", "shortCriticalText")) {
            val v = runCatching { extras.get(key)?.toString() }.getOrNull()
            if (!v.isNullOrBlank()) values += v
        }
        for (key in extras.keySet()) {
            val v = runCatching { extras.get(key)?.toString() }.getOrNull()
            if (!v.isNullOrBlank()) values += v
        }
        return values.distinct().toTypedArray()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingClear: Runnable? = null
    // Last distance Maps actually published; reused when a notification omits it.
    private var lastKnownDistance: Int = -1
    // Key of the Maps notification we accepted; only its removal clears nav.
    private var activeKey: String? = null
    // Maneuver of the last accepted step, to detect a genuine step change.
    private var lastManeuver: String = ""

    companion object {
        private const val CLEAR_GRACE_MS = 4000L
        private val MANEUVER_WORDS = listOf(
            "turn", "head", "continue", "keep", "merge", "fork",
            "roundabout", "exit", "u-turn", "arrive", "destination", "onto",
            "left", "right", "straight", "north", "south", "east", "west",
        )
        val MAP_PACKAGES = setOf(
            "com.google.android.apps.maps",
            // Mappls / MapmyIndia ship under several package names. All are
            // accepted: the title guard and strict whitelist still keep out
            // calls, messages and arbitrary notifications.
            "com.mappls.app",
            "com.mappls.app.pro",
            "com.mapmyindia.android.app",
            "com.mmi.android.mmaps",
            "org.organicmaps",
            "com.here.app.maps",
            "com.waze",
            "com.tomtom.gotcha",
        )
    }
}
