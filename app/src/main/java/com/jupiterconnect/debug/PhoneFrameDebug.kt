package com.jupiterconnect.debug

/**
 * Decode the ASCII payload carried in a START_PHONE frame.
 *
 * Community research decodes an inbound `0x5B 0x53` frame as
 * `b[2:19].decode("ascii").rstrip("\x00 ")` - i.e. bytes 2..18 are the text.
 * This helper applies exactly that rule to the raw hex captured by
 * [FrameCatalog], so the More screen can show whether the cluster ever sends
 * anything on the call (0x43) / message (0x53) channels.
 */
object PhoneFrameDebug {

    /** IDs whose payload is caller/sender text. */
    val PHONE_IDS = listOf(0x43, 0x53)

    /**
     * [hex] is a space-separated byte string such as "5B 53 41 6C ...".
     * Returns the decoded text, or an empty string when the frame is not a
     * START_PHONE frame of [id].
     */
    fun decode(hex: String, id: Int): String {
        val bytes = hex.split(' ').mapNotNull { it.trim().toIntOrNull(16) }
        if (bytes.size < 19) return ""
        val start = bytes[0]
        if (start != 0x5A && start != 0x5B && start != 0x9B) return ""
        if (bytes[1] != id) return ""
        val payload = ByteArray(19) { bytes[it].toByte() }
        return String(payload, 2, 17, Charsets.US_ASCII)
            .trimEnd('\u0000', ' ')
    }
}
