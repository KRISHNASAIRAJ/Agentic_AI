package com.jupiterconnect.debug

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** One captured BLE frame (or a link event) for the Developer frame log. */
data class FrameEntry(val time: String, val dir: String, val payload: String) {
    val line: String get() = "$time $dir $payload"
}

/**
 * Bounded, thread-safe capture of BLE traffic. RX = cluster -> app, TX = app ->
 * cluster. Export the text and paste it into the Windows simulator's decode
 * tabs to see exactly what each side sent.
 *
 * In addition to the in-memory log, every connection writes its own plain-text
 * file to the phone's Downloads/JupiterConnect folder. The file is opened on
 * GATT connect and closed on disconnect, with each line flushed as it happens
 * so the capture survives an app kill. These are raw bytes, so a frame that is
 * unknown today can still be decoded later.
 */
object FrameLog {
    private const val MAX_ENTRIES = 400
    private val lock = Any()
    private val buffer = ArrayDeque<FrameEntry>()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private val _entries = MutableStateFlow<List<FrameEntry>>(emptyList())
    val entries: StateFlow<List<FrameEntry>> = _entries

    private var appContext: Context? = null
    private var writer: BufferedWriter? = null
    private var sessionName: String? = null

    /** Called once from the service so frames can be persisted. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Start a new per-connection capture in Downloads/JupiterConnect. */
    fun startSession(address: String = "") {
        synchronized(lock) {
            closeWriter()
            val ctx = appContext ?: return
            val name = "jupiter_frames_${fileStamp.format(Date())}.txt"
            val out = try {
                openDownload(ctx, name)
            } catch (e: IOException) {
                null
            } ?: return
            writer = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8))
            sessionName = name
            writeLine("Jupiter Connect frame capture")
            writeLine("started=${format.format(Date())} device=${address.ifEmpty { "unknown" }}")
            writeLine("time dir frame")
        }
    }

    /** Finish the current capture. Safe to call more than once. */
    fun endSession() {
        synchronized(lock) {
            writeLine("ended=${format.format(Date())}")
            closeWriter()
        }
    }

    /** Display name of the file being written, if any. */
    fun currentSession(): String? = synchronized(lock) { sessionName }

    private fun openDownload(ctx: Context, name: String) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/JupiterConnect")
        }
        ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?.let { ctx.contentResolver.openOutputStream(it) }
    } else {
        @Suppress("DEPRECATION")
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "JupiterConnect")
        dir.mkdirs()
        File(dir, name).outputStream()
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: IOException) {
        }
        writer = null
        sessionName = null
    }

    private fun writeLine(text: String) {
        val w = writer ?: return
        try {
            w.write(text)
            w.newLine()
            w.flush()
        } catch (_: IOException) {
            writer = null
        }
    }

    fun record(dir: String, bytes: ByteArray) {
        add(dir, bytes.joinToString(" ") { "%02X".format(it) })
    }

    /** Link-level note (connect, disconnect, errors) shown alongside frames. */
    fun note(text: String) = add("--", text)

    private fun add(dir: String, payload: String) {
        val entry = FrameEntry(format.format(Date()), dir, payload)
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            _entries.value = buffer.toList()
            writeLine(entry.line)
        }
    }

    fun exportText(): String = synchronized(lock) {
        buffer.joinToString("\n") { it.line }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }
}
