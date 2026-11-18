package com.jupiterconnect.nav

/**
 * The cluster's text row holds only 17 ASCII bytes, so caller/message text has
 * to be compressed before it goes over BLE. These helpers are deliberately
 * pure so the exact on-screen strings can be unit tested.
 */
internal object PhoneEventFormat {
    private const val MAX = 17

    /** "CALL Mom" for a saved contact, or "C <number>" so a long number fits. */
    fun call(name: String): String {
        val cleaned = clean(name).ifBlank { return "" }
        // A number wastes precious row width on spaces; a contact name keeps them.
        val who = if (cleaned.count { it.isDigit() } >= 7)
            cleaned.filter { it != ' ' } else cleaned
        val prefix = if (who.any { it.isDigit() }) "C " else "CALL "
        return (prefix + who).take(MAX)
    }

    /** "Sender: message", truncated to the row width. */
    fun message(sender: String, body: String): String {
        val who = clean(sender).ifBlank { "MSG" }
        val text = clean(body).ifBlank { return "" }
        return ("$who: $text").take(MAX)
    }

    /** Drop punctuation (the protocol sanitizer maps it to X) and collapse spaces. */
    private fun clean(value: String): String =
        value.map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }
            .joinToString("")
            .replace(Regex(" +"), " ")
            .trim()
}
