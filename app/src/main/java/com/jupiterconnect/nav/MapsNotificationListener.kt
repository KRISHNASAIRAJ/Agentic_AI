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
        // Mappls sometimes puts the maneuver in TEXT while Google Maps puts it
        // in TITLE. Since only known maps apps can reach this point, accept
        // either field - this keeps both providers working.
        val hasManeuver = MANEUVER_WORDS.any { title.contains(it, true) || text.contains(it, true) }
        // Mappls' real navigation notification is often just "5 m" +
        // "15.5 km • 27 min • 11:11 pm ETA" - no maneuver word at all. Accept
        // it when it carries both a distance and a time/trip signal. A junk
        // distance-only notification (e.g. bare "0 m") still fails this test.
        val hasDistance = NavParsing.findDistance(title, text, bigText, subText) >= 0
        val hasTime = text.contains("min", true) || subText.contains("arrive", true) ||
            NavParsing.findTripDistance(title, text, bigText, subText) >= 0
        val hasNavMetrics = hasDistance && hasTime
        if (!hasManeuver && !hasNavMetrics) return

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
        // Total remaining distance. Try explicit "remaining" phrases first,
        // then Mappls' "15.5 km • 27 min • ETA" format, then all extras.
        val remainingCandidates = buildList { addAll(listOf(title, text, bigText, subText)); addAll(extraTexts) }
        val foundRemaining = NavParsing.findRemaining(*remainingCandidates.toTypedArray()).let { r ->
            if (r >= 0) r else NavParsing.findTripDistance(*remainingCandidates.toTypedArray())
        }
        lastManeuver = parsed.maneuver
        // Maps apps post several notifications and each update can omit some
        // fields. Hold each last real value for a short window so one partial
        // update cannot blank the distance, ETA or trip total - that flip is
        // what produced the blinking and the "0.0 km" remaining field.
        val now = System.currentTimeMillis()
        fun keep(found: Int, last: Int, at: Long): Int =
            if (found >= 0) found else if (last >= 0 && now - at <= 10_000L) last else found

        val effectiveDistance = if (distance >= 0) {
            lastKnownDistance = distance
            lastDistanceAtMs = now
            distance
        } else keep(distance, lastKnownDistance, lastDistanceAtMs)

        val foundEta = parsed.etaMinutes
        val effectiveEta = if (foundEta > 0) {
            lastEta = foundEta
            lastEtaAtMs = now
            foundEta
        } else keep(foundEta, lastEta, lastEtaAtMs).coerceAtLeast(0)

        val effectiveRemaining = if (foundRemaining >= 0) {
            lastRemaining = foundRemaining
            lastRemainingAtMs = now
            foundRemaining
        } else keep(foundRemaining, lastRemaining, lastRemainingAtMs)

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
            etaMinutes = effectiveEta,
            pictogram = parsed.pictogram,
            provider = n.packageName,
            remainingMeters = effectiveRemaining,
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
            lastDistanceAtMs = 0L
            lastEta = -1
            lastEtaAtMs = 0L
            lastRemaining = -1
            lastRemainingAtMs = 0L
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
    private var lastDistanceAtMs: Long = 0L
    private var lastEta: Int = -1
    private var lastEtaAtMs: Long = 0L
    private var lastRemaining: Int = -1
    private var lastRemainingAtMs: Long = 0L
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
            // Mappls (MapmyIndia) ships as com.mmi.maps on current Play builds.
            "com.mmi.maps",
            "com.mmi.android.mmaps",
            "org.organicmaps",
            "com.here.app.maps",
            "com.waze",
            "com.tomtom.gotcha",
        )
    }
}
