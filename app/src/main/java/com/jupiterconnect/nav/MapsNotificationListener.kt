package com.jupiterconnect.nav

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jupiterconnect.protocol.sanitize
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

        // Maps remove + re-post their ongoing notification on every update.
        // ANY post from a maps app proves the route is still alive - cancel a
        // pending clear even when this particular post is a junk/partial one
        // the guard below rejects. Without this, a rejected repost leaves the
        // clear armed and the cluster goes blank until the next accepted post.
        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null

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
        // Stale guard: a different maneuver is a different step. Clear the
        // cached distance so this step cannot inherit the previous step's
        // distance when its own notification carries none.
        if (parsed.maneuver != lastManeuver) {
            lastKnownDistance = -1
            lastManeuver = parsed.maneuver
        }

        // Maps apps post several notifications and each update can omit some
        // fields. Hold each last real value so one partial update cannot blank
        // the distance, ETA or trip total. The hold is long because Google Maps
        // can go many seconds between real updates - a short hold made the
        // cluster flip back to "0m"/"0.0 km" between them.
        val now = System.currentTimeMillis()
        fun keep(found: Int, last: Int, at: Long): Int =
            if (found >= 0) found else if (last >= 0 && now - at <= VALUE_HOLD_MS) last else found

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

        // Google Maps never publishes the trip total in its notification. When
        // a total IS seen once (Mappls, or Maps' route-start notification),
        // anchor it to the odometer and decrease it as the bike moves - that
        // gives Google Maps a live "km left" instead of a stuck 0.0 km.
        val odoKm = runCatching { ClusterRepository.telemetry.value.odometerKm }.getOrDefault(0.0)
        if (foundRemaining >= 0) {
            remainingAnchorMeters = foundRemaining
            remainingAnchorOdoKm = odoKm
        }
        val effectiveRemaining = when {
            foundRemaining >= 0 -> {
                lastRemaining = foundRemaining
                lastRemainingAtMs = now
                foundRemaining
            }
            remainingAnchorMeters >= 0 && remainingAnchorOdoKm > 0 && odoKm > remainingAnchorOdoKm ->
                (remainingAnchorMeters - ((odoKm - remainingAnchorOdoKm) * 1000).toInt()).coerceAtLeast(0)
            else -> keep(foundRemaining, lastRemaining, lastRemainingAtMs)
        }

        val instruction = NavInstruction(
            title = parsed.maneuver,
            street = parsed.street,
            distanceMeters = effectiveDistance,
            etaMinutes = effectiveEta,
            pictogram = parsed.pictogram,
            provider = n.packageName,
            remainingMeters = effectiveRemaining,
        )

        // Diagnostic snapshot for the Rides tab. Every extras key/value is
        // dumped so the missing distance, if any, can be located exactly.
        ClusterRepository.navDebug.value = buildString {
            appendLine("BUILD: test-final-1")
            appendLine("pkg=${n.packageName} ongoing=${n.isOngoing} key=${n.key} activeKey=$activeKey")
            appendLine("TITLE: $title")
            appendLine("TEXT: $text")
            appendLine("BIG: $bigText")
            appendLine("SUB: $subText")
            appendLine("parsed.dist=${parsed.distanceMeters} fallback=$distance")
            appendLine("lastKnown=$lastKnownDistance sent=$effectiveDistance unknown=${effectiveDistance < 0}")
            appendLine("eta=$effectiveEta pic=${parsed.pictogram} maneuver=${parsed.maneuver}")
            appendLine("remaining=$effectiveRemaining anchor=$remainingAnchorMeters odo=$remainingAnchorOdoKm")
            appendLine("android.shortCriticalText=${runCatching { extras.get("android.shortCriticalText")?.toString() }.getOrNull()}")
            appendLine("android.infoText=${runCatching { extras.get("android.infoText")?.toString() }.getOrNull()}")
            appendLine("android.progress=${extras.getInt("android.progress")}/${extras.getInt("android.progressMax")}")
            appendLine("android.progressSegments=${runCatching { extras.get("android.progressSegments")?.toString() }.getOrNull()}")
            appendLine("hasContentView=${n.notification.contentView != null}")
            appendLine("textSent=${sanitize(instruction.displayLine)}")
            appendLine("EXTRAS:")
            for (k in extras.keySet()) appendLine("  $k=${extras.get(k)}")
        }

        ClusterRepository.navInstruction.value = instruction
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
            // Maps removes then re-posts its notification on every update. If
            // any maps notification is still live, this was only an update -
            // clearing now is what blanked the cluster. Only a genuine end of
            // navigation (no maps notification left) clears the instruction.
            val stillActive = runCatching {
                activeNotifications?.any { it.packageName in MAP_PACKAGES && it.isOngoing }
            }.getOrDefault(false) == true
            if (stillActive) {
                pendingClear = null
                return@Runnable
            }
            ClusterRepository.navInstruction.value = null
            pendingClear = null
            lastKnownDistance = -1 // new route must not inherit the old distance
            lastDistanceAtMs = 0L
            lastEta = -1
            lastEtaAtMs = 0L
            lastRemaining = -1
            lastRemainingAtMs = 0L
            remainingAnchorMeters = -1
            remainingAnchorOdoKm = -1.0
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
    // Odometer anchor used to keep Google Maps' "km left" live.
    private var remainingAnchorMeters: Int = -1
    private var remainingAnchorOdoKm: Double = -1.0
    // Key of the Maps notification we accepted; only its removal clears nav.
    private var activeKey: String? = null
    // Last parsed maneuver. A change means a new step, so the cached distance
    // must not carry over (stale guard).
    private var lastManeuver: String = ""

    companion object {
        private const val CLEAR_GRACE_MS = 4000L
        private const val VALUE_HOLD_MS = 60_000L
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
