package com.jupiterconnect.nav

/**
 * The cluster's text row holds only 17 ASCII bytes, so caller/message text has
 * to be compressed before it goes over BLE. These helpers are deliberately
 * pure so the exact on-screen strings can be unit tested.
 */
internal object PhoneEventFormat {
    private const val MAX = 17

    /**
     * Caller for the cluster's own 0x43 incoming-call card. The dash renders
     * the "Incoming call" label itself, so only the saved name (or the number)
     * goes on the wire — no "CALL" prefix stealing row width.
     */
    fun call(name: String): String {
        val cleaned = clean(name).ifBlank { return "" }
        val who = if (cleaned.count { it.isDigit() } >= 7)
            cleaned.filter { it != ' ' } else cleaned
        return who.take(MAX)
    }

    /** "Sender: message" in full — the transport pages it across the hold window. */
    fun message(sender: String, body: String): String {
        val who = clean(sender).ifBlank { "MSG" }
        val text = clean(body).ifBlank { return "" }
        return "$who: $text"
    }

    /**
     * Split a long message into frames the 17-byte cluster text card can show.
     * Capped so every page still gets roughly a second on screen during the
     * 5 s hold instead of flashing past.
     */
    fun pages(text: String): List<String> {
        if (text.length <= MAX) return listOf(text)
        return text.chunked(MAX).take(8)
    }

    /** Drop punctuation (the protocol sanitizer maps it to X) and collapse spaces. */
    private fun clean(value: String): String =
        value.map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }
            .joinToString("")
            .replace(Regex(" +"), " ")
            .trim()
}
