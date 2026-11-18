package com.jupiterconnect.ui

/** Snapshot of the runtime permissions BLE scanning depends on. */
data class PermissionStatus(
    val bluetooth: Boolean = false,
    val location: Boolean = false,
    val notifications: Boolean = false,
    val contacts: Boolean = false,
) {
    val bleReady: Boolean get() = bluetooth && location
    val allGranted: Boolean get() = bluetooth && location && notifications
}
