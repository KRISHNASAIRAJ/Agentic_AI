package com.jupiterconnect.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 */
object FrameLog {
    private const val MAX_ENTRIES = 400
    private val lock = Any()
    private val buffer = ArrayDeque<FrameEntry>()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<FrameEntry>>(emptyList())
    val entries: StateFlow<List<FrameEntry>> = _entries

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
