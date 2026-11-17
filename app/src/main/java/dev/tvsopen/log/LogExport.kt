package dev.tvsopen.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Export the captured BLE log straight into the user's Downloads folder.
 *
 * On Android 10+ this uses [MediaStore.Downloads] via a `ContentResolver`, so no
 * storage permission is needed and the file is visible in the Files app and on
 * a USB-connected computer. On older versions it falls back to the public
 * Downloads directory, which only needs WRITE_EXTERNAL_STORAGE (declared with
 * maxSdkVersion 28 in the manifest).
 */
object LogExport {

    /** Write a fresh log bundle to Downloads and return the display name, or null. */
    fun toDownloads(context: Context): String? {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis()))
        val name = "jupiter-drive-$stamp.log"

        // Flush so the bundle reflects what actually happened, then render it.
        Log.fileSizeBytes()
        val bytes = Log.bundleText()?.toByteArray(Charsets.UTF_8) ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }.isSuccess
            if (ok) name else null
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            runCatching { file.writeBytes(bytes) }.getOrNull()?.let { name }
        }
    }
}
