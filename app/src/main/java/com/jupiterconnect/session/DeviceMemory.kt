package com.jupiterconnect.session

import android.content.Context

/**
 * Durable record of the last bike that actually connected.
 *
 * SharedPreferences remains the primary store (it is what the service reads on
 * restart), but a plain app-private file mirrors the same information so the
 * link survives a preference wipe and can be inspected without a debugger.
 * Both are app-private and are removed on uninstall, as expected on Android.
 */
object DeviceMemory {
    private const val FILE_NAME = "last_device.txt"

    data class LastDevice(
        val address: String,
        val name: String,
        val connectedAtMs: Long,
    )

    /** Called on every successful link and whenever the user picks a device. */
    fun save(context: Context, address: String, name: String? = null) {
        if (address.isBlank()) return
        val previous = read(context)
        val text = listOf(
            address,
            name?.takeIf { it.isNotBlank() } ?: previous?.name.orEmpty(),
            System.currentTimeMillis().toString(),
        ).joinToString("\n")
        runCatching {
            context.applicationContext.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun read(context: Context): LastDevice? {
        val text = runCatching {
            context.applicationContext.openFileInput(FILE_NAME).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        val lines = text.lines()
        val address = lines.getOrNull(0)?.trim().orEmpty()
        if (address.isBlank()) return null
        return LastDevice(
            address = address,
            name = lines.getOrNull(1)?.trim().orEmpty(),
            connectedAtMs = lines.getOrNull(2)?.toLongOrNull() ?: 0L,
        )
    }

    fun clear(context: Context) {
        runCatching { context.applicationContext.deleteFile(FILE_NAME) }
    }
}
