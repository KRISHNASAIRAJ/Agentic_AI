package com.jupiterconnect.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What each candidate protocol ID actually does on the rider's own cluster.
 *
 * This is deliberately a passive observer: it never sends anything. Every
 * inbound frame is counted and its raw bytes kept, so the Ride stats screen
 * can show which of the codes recovered from the TVS Connect DEX are seen on
 * this bike and which are not. TX frames are counted too, to tell "the app
 * sends it" apart from "the cluster emits it".
 */
data class FrameSeen(
    val id: Int,
    val label: String,
    val rx: Long,
    val tx: Long,
    val lastRxHex: String,
    val lastTxHex: String,
    val firstMs: Long,
    val lastMs: Long,
) {
    val hex: String get() = lastRxHex.ifEmpty { lastTxHex }
}

object FrameCatalog {

    data class Candidate(val id: Int, val label: String)

    /** IDs recovered from TVS Connect / Jupiter protocol research. */
    val CANDIDATES: List<Candidate> = listOf(
        Candidate(0x06, "unknown 06"),
        Candidate(0x10, "speedometer"),
        Candidate(0x11, "service / ISS"),
        Candidate(0x12, "speedo variant 12"),
        Candidate(0x13, "battery 13"),
        Candidate(0x14, "speedo variant 14"),
        Candidate(0x15, "speedo variant 15"),
        Candidate(0x16, "unknown 16"),
        Candidate(0x18, "engine"),
        Candidate(0x19, "economy / DTE"),
        Candidate(0x22, "user id (out)"),
        Candidate(0x23, "unknown 23"),
        Candidate(0x29, "wifi config"),
        Candidate(0x37, "calibration ack"),
        Candidate(0x42, "cluster address"),
        Candidate(0x43, "caller (out)"),
        Candidate(0x4A, "keepalive (out)"),
        Candidate(0x4C, "text row 1 (out)"),
        Candidate(0x4E, "nav control (out)"),
        Candidate(0x4F, "nav text (out)"),
        Candidate(0x50, "nav row 2 / pictogram"),
        Candidate(0x52, "rider name (out)"),
        Candidate(0x53, "SMS (out)"),
        Candidate(0x54, "ACK variant"),
        Candidate(0x5D, "climate (out)"),
        Candidate(0x60, "play / pause (out)"),
        Candidate(0x61, "album (out)"),
        Candidate(0x62, "title (out)"),
        Candidate(0x63, "text row 2 (out)"),
        Candidate(0x65, "artist new (out)"),
        Candidate(0x6B, "music key"),
        Candidate(0x6D, "unknown 6D"),
        Candidate(0x73, "calibration (out)"),
        Candidate(0x78, "screen weather (out)"),
        Candidate(0x79, "screen cricket (out)"),
        Candidate(0x7D, "unknown 7D"),
        Candidate(0x80, "screen football (out)"),
        Candidate(0x9D, "unknown 9D"),
        Candidate(0xF1, "handshake / brightness"),
        Candidate(0xF2, "handshake challenge"),
    )

    private val lock = Any()
    private val seen = HashMap<Int, FrameSeen>()
    private val _frames = MutableStateFlow<List<FrameSeen>>(emptyList())
    val frames: StateFlow<List<FrameSeen>> = _frames

    fun labelFor(id: Int): String =
        CANDIDATES.firstOrNull { it.id == id }?.label ?: "unlisted"

    /** Record one 20-byte frame. [rx] true = cluster to phone. */
    fun observe(raw: ByteArray, rx: Boolean) {
        if (raw.size < 2) return
        val start = raw[0].toInt() and 0xFF
        if (start != 0x5A && start != 0x5B && start != 0x9A && start != 0x9B) return
        val id = raw[1].toInt() and 0xFF
        val hex = raw.joinToString(" ") { "%02X".format(it) }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val p = seen[id]
            seen[id] = FrameSeen(
                id = id,
                label = labelFor(id),
                rx = (p?.rx ?: 0L) + if (rx) 1 else 0,
                tx = (p?.tx ?: 0L) + if (rx) 0 else 1,
                lastRxHex = if (rx) hex else p?.lastRxHex.orEmpty(),
                lastTxHex = if (rx) p?.lastTxHex.orEmpty() else hex,
                firstMs = p?.firstMs ?: now,
                lastMs = now,
            )
            _frames.value = seen.values.sortedWith(
                compareByDescending<FrameSeen> { it.rx }.thenByDescending { it.tx }.thenBy { it.id }
            )
        }
    }

    /** A new connection starts a fresh observation window. */
    fun reset() = synchronized(lock) {
        seen.clear()
        _frames.value = emptyList()
    }

    fun byId(id: Int): FrameSeen? = synchronized(lock) { seen[id] }
}
