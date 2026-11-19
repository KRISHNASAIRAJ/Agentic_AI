package com.jupiterconnect.nav

import android.app.Notification
import android.app.Person
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.PhoneLookup
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.jupiterconnect.session.RideStore
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
        // Calls and messages mirror to the cluster independently of navigation;
        // the maps-only whitelist below still governs the navigation text row.
        handlePhoneEvent(n)
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
        // A metrics-only update (Mappls "5 m" + "15.5 km • 27 min • ETA") has
        // no maneuver word. Parsing it fresh turns the arrow into "straight"
        // and the text row into "5 m" - that is the visible blinking. Reuse the
        // last real turn until a notification actually carries a new one.
        val maneuver = if (hasManeuver) parsed.maneuver else lastManeuver.ifBlank { parsed.maneuver }
        val pictogram = if (hasManeuver) parsed.pictogram else lastPictogram
        val street = if (hasManeuver) parsed.street else lastStreet
        lastManeuver = maneuver
        lastPictogram = pictogram
        lastStreet = street

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

        // Personal ETA needs the ETA published at the START of this route and
        // the real time until navigation ended. Capture it here rather than at
        // ride start, because riders usually begin navigation after mounting.
        if (effectiveEta > 0) {
            if (!navSessionActive) {
                navSessionActive = true
                navSessionStartMs = now
                // Keep this first ETA: it is Maps' prediction for this route,
                // which is what the personal factor corrects. Later updates
                // merely count down toward arrival.
                navSessionEta = effectiveEta
            }
        }

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
            appendLine("remaining=$effectiveRemaining anchor=$remainingAnchorMeters odo=$remainingAnchorOdoKm")
            appendLine("shortCritical=${runCatching { extras.get("android.shortCriticalText")?.toString() }.getOrNull()}")
            appendLine("info=${runCatching { extras.get("android.infoText")?.toString() }.getOrNull()}")
            appendLine("progress=${extras.getInt("android.progress")}/${extras.getInt("android.progressMax")}")
            appendLine("segments=${runCatching { extras.get("android.progressSegments")?.toString() }.getOrNull()}")
            appendLine("EXTRAS: ${extras.keySet().joinToString { k -> "$k=${extras.get(k)}" }}")
            appendLine("hasContentView=${n.notification.contentView != null}")
        }

        ClusterRepository.navInstruction.value = NavInstruction(
            title = maneuver,
            street = street,
            distanceMeters = effectiveDistance,
            etaMinutes = effectiveEta,
            pictogram = pictogram,
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
            // Route really ended: save one ETA sample (Maps' prediction vs the
            // time it actually took) for the personal correction.
            if (navSessionActive) {
                val actualMin = ((System.currentTimeMillis() - navSessionStartMs) / 60_000L)
                if (navSessionEta > 0 && actualMin >= 2) {
                    RideStore.addNavEtaSample(navSessionEta, actualMin.toInt())
                }
                navSessionActive = false
                navSessionStartMs = 0L
                navSessionEta = 0
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
            lastPictogram = NavInstruction.STRAIGHT
            lastStreet = ""
            activeKey = null
        }.also { handler.postDelayed(it, CLEAR_GRACE_MS) }
    }

    /**
     * Mirror incoming calls and chat messages to the cluster, the same way
     * TVS Connect does. Nothing is sent unless the scooter is linked and the
     * owner enabled this in Settings. Calls (0x43) and messages (0x53) use
     * their own full-screen cluster cards, leaving the navigation row alone.
     */
    private fun handlePhoneEvent(n: StatusBarNotification) {
        if (!ClusterRepository.phoneMirroring.value) return
        if (n.packageName == packageName) return // never echo our own notification
        if (!ClusterRepository.telemetry.value.connected) return
        val notification = n.notification ?: return
        val extras = notification.extras ?: return
        val category = notification.category
        val isVoipApp = n.packageName in VOIP_CALL_PACKAGES
        val isCall = category == Notification.CATEGORY_CALL ||
            n.packageName in DIALER_PACKAGES ||
            (isVoipApp && isVoipCall(notification, extras))
        @Suppress("DEPRECATION")
        val messages = runCatching { extras.getParcelableArray(Notification.EXTRA_MESSAGES) }.getOrNull()
        val isMessage = messages != null || category == Notification.CATEGORY_MESSAGE
        if (!isCall && !isMessage) return

        @Suppress("DEPRECATION")
        val people = runCatching { extras.getParcelableArray(Notification.EXTRA_PEOPLE) }.getOrNull()
        val person = people?.filterIsInstance<Person>()?.firstOrNull()
        // Person.getUri()/getName() landed in API 28; minSdk is 26.
        val personInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            person?.uri?.toString() ?: person?.name?.toString()
        } else null
        val text = if (isCall) {
            val raw = if (isVoipApp) voipCaller(extras, personInfo) else
                personInfo
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            // A WhatsApp display name is already a name; only look up contacts
            // for phone numbers, where the saved contact is more useful.
            val who = if (raw.any { it.isLetter() }) raw else (resolveContactName(raw) ?: raw)
            PhoneEventFormat.call(who)
        } else {
            val latest = messages?.filterIsInstance<Notification.MessagingStyle.Message>()?.lastOrNull()
            val rawSender = personInfo
                ?: latest?.sender?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val sender = resolveContactName(rawSender) ?: rawSender
            val body = latest?.text?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            PhoneEventFormat.message(sender, body)
        }
        if (text.isBlank()) return
        // Dialers repost the same call notification repeatedly. Only show a
        // given key+text once so the 5 s override ends and navigation resumes
        // on schedule instead of being extended by every repost.
        if (n.key == lastPhoneKey && text == lastPhoneText) return
        lastPhoneKey = n.key
        lastPhoneText = text
        // Use the cluster's dedicated call/message cards so the turn-by-turn
        // direction row is never overwritten. Both clear after 5 s and Maps
        // keeps rendering underneath without any extra work.
        ClusterRepository.showPhoneEvent(isCall, text, PHONE_HOLD_MS)
    }

    /**
     * WhatsApp (and other VOIP apps) do not always tag a call with CATEGORY_CALL.
     * Their incoming-call notification carries Accept/Decline/Reject actions,
     * which is a reliable signal that this is a call and not a chat message.
     */
    private fun isVoipCall(notification: Notification, extras: Bundle): Boolean {
        if (notification.category == Notification.CATEGORY_CALL) return true
        val actions = notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
        if (actions.any { label ->
            listOf("answer", "accept", "reject", "decline", "hang up", "end call", "join")
                .any { label.contains(it, ignoreCase = true) }
        }) return true
        val text = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty() + " " +
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return text.contains("call", true) || text.contains("ringing", true) ||
            text.contains("incoming", true)
    }

    /**
     * Prefer the caller's name over generic labels like "Incoming voice call"
     * or the app name, which several VOIP apps put in the notification title.
     */
    private fun voipCaller(extras: Bundle, personInfo: String?): String {
        val generic = listOf("incoming", "voice call", "video call", "whatsapp",
            "ringing", "call from", "telegram", "signal", "skype", "zoom")
        val candidates = buildList {
            add(personInfo)
            add(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            add(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            addAll(collectExtraTexts(extras).asIterable())
        }.mapNotNull { it?.trim() }.filter { it.isNotBlank() }
        return candidates.firstOrNull { c -> generic.none { c.contains(it, true) } }
            ?: candidates.firstOrNull().orEmpty()
    }

    /** Saved contact name for a number/contact URI, or null (show the raw value). */
    private fun resolveContactName(value: String): String? {
        if (value.isBlank()) return null
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) return null
        val uri = if (value.startsWith("content:")) Uri.parse(value)
        else PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendEncodedPath(value).build()
        return runCatching {
            contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(PhoneLookup.DISPLAY_NAME))
                else null
            }
        }.getOrNull()
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
    // One display per notification/text so a repost cannot extend the 5 s hold.
    private var lastPhoneKey: String? = null
    private var lastPhoneText = ""
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
    // One navigation session = one Maps ETA sample for personalisation. Rides
    // start/stop with Bluetooth; routes do not, so measuring ride duration
    // against a Maps ETA was comparing unrelated time spans.
    private var navSessionStartMs = 0L
    private var navSessionEta = 0
    private var navSessionActive = false
    // Last real turn, reused for metrics-only updates so the arrow and text
    // row do not blink between the real instruction and a bare distance.
    private var lastManeuver: String = ""
    private var lastPictogram: Int = NavInstruction.STRAIGHT
    private var lastStreet: String = ""

    companion object {
        private const val CLEAR_GRACE_MS = 4000L
        private const val VALUE_HOLD_MS = 60_000L
        private const val PHONE_HOLD_MS = 5_000L
        private val DIALER_PACKAGES = setOf(
            "com.google.android.dialer", "com.android.dialer", "com.android.phone",
            "com.samsung.android.dialer", "com.samsung.android.incallui",
            "com.miui.dialer", "com.oneplus.dialer", "com.oppo.dialer",
            "com.vivo.dialer", "com.qualcomm.timeservice",
        )
        /** Apps whose incoming calls must show the same 0x43 card as dialer calls. */
        private val VOIP_CALL_PACKAGES = setOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "org.telegram.messenger", "org.telegram.messenger.web",
            "org.thoughtcrime.securesms", "com.skype.raider",
            "com.microsoft.teams", "us.zoom.videomeetings",
            "com.google.android.apps.meetings", "com.discord",
        )
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
