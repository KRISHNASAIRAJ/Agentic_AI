package com.jupiterconnect.transport

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Restores the cluster link after a reboot so the voice button works even when
 * the user has not opened Jupiter Connect yet. Auto-connect only happens when
 * Bluetooth permission is already granted and a device was remembered.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
        val bleReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!bleReady) return
        val address = context.getSharedPreferences("jupiter_device", Context.MODE_PRIVATE)
            .getString("address", null) ?: return
        val serviceIntent = Intent(context, ClusterService::class.java)
            .setAction(ClusterService.ACTION_CONNECT)
            .putExtra(ClusterService.EXTRA_ADDRESS, address)
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
    }
}
