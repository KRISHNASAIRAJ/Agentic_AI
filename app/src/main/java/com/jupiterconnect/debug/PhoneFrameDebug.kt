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
        // Caller-ID frames carry a sub-id at byte 2 (1 number, 3 country code,
        // 2 contact name) and the text starts at byte 3. The old single-frame
        // form had no sub-id, so fall back to byte 2 for those.
        val subId = bytes[2]
        val offset = if (id == 0x43 && subId in setOf(1, 3, 2) && bytes.size >= 21) 3 else 2
        return String(bytes.map { it.toByte() }.toByteArray(), offset, 17, Charsets.US_ASCII)
            .trimEnd('\u0000', ' ')
    }
}
