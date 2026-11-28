package com.jupiterconnect.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash capture. The app is installed by tapping an APK, so there
 * is no ADB to pull a stack trace. Every uncaught exception is written to
 * Downloads/JupiterConnect (where the frame captures already live) and posted
 * as a notification, so the cause can be read even when the app dies before
 * its UI opens.
 */
object CrashLog {
    private const val CHANNEL = "jupiter_crash"
    private const val NOTIFICATION_ID = 9001
    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            val report = "time=${stamp.format(Date())}\nthread=${thread.name}\n" +
                "device=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT}\n\n" +
                sw.toString()
            runCatching {
                app.openFileOutput("last_crash.txt", Context.MODE_PRIVATE).use {
                    it.write(report.toByteArray(Charsets.UTF_8))
                }
            }
            runCatching {
                writeDownload(app, "jupiter_crash_${stamp.format(Date())}.txt", report)
            }
            val summary = "${error.javaClass.simpleName}: ${error.message ?: "no message"}"
            runCatching { notify(app, summary, report) }
            previous?.uncaughtException(thread, error)
                ?: kotlin.system.exitProcess(1)
        }
    }

    /** Same Downloads/JupiterConnect folder the frame captures use. */
    private fun writeDownload(context: Context, name: String, report: String) {
        val bytes = report.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/JupiterConnect")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "JupiterConnect")
            dir.mkdirs()
            File(dir, name).outputStream().use { it.write(bytes) }
        }
    }

    private fun notify(context: Context, summary: String, report: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL, "Jupiter Connect crash report", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Jupiter Connect crashed")
            .setContentText(summary.take(120))
            .setStyle(Notification.BigTextStyle().bigText(report.take(700)))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
