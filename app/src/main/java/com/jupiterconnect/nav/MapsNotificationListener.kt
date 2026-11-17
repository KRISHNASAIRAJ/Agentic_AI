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
 * Notification access is a Settings toggle, not a runtime permission —
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

        val looksLikeNav = n.packageName in MAP_PACKAGES ||
            extras.containsKey("android.app.extra.MAPS_MODE") ||
            extras.containsKey("navigation.mode") ||
            (n.isOngoing && title.isNotEmpty() && (text.isNotEmpty() || bigText.isNotEmpty()))

        if (!looksLikeNav) return

        val parsed = NavParsing.parse(title, text, bigText, subText)
        ClusterRepository.navInstruction.value = NavInstruction(
            title = parsed.maneuver,
            street = parsed.street,
            distanceMeters = parsed.distanceMeters.coerceAtLeast(0),
            etaMinutes = parsed.etaMinutes,
            pictogram = parsed.pictogram,
            provider = n.packageName,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName in MAP_PACKAGES) {
            ClusterRepository.navInstruction.value = null
        }
    }

    companion object {
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
