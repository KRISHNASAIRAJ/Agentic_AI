package com.jupiterconnect.nav

/** One normalised turn-by-turn instruction, provider-agnostic. */
data class NavInstruction(
    val title: String,
    val street: String,
    val distanceMeters: Int,
    val etaMinutes: Int = 0,
    val pictogram: Int = STRAIGHT,
    val provider: String = "",
) {
    /** Best single line for the cluster's 17-byte text row. */
    val displayLine: String
        get() = listOf(title, street).firstOrNull { it.isNotBlank() } ?: "Navigating"

    companion object {
        // Pictogram IDs are inherited from the Jupiter reference and are
        // UNVERIFIED on the real 2026 cluster. Keep them named.
        const val LEFT = 1
        const val RIGHT = 2
        const val MERGE = 3
        const val FORK = 4
        const val ROUNDABOUT = 5
        const val STRAIGHT = 7
    }
}

data class ParsedNav(
    val maneuver: String = "",
    val street: String = "",
    val distanceMeters: Int = -1,
    val etaMinutes: Int = 0,
    val pictogram: Int = NavInstruction.STRAIGHT,
)

/** Parse Google Maps / MapMyIndia notification extras without a maps SDK. */
object NavParsing {
    private val DISTANCE = Regex("""(\d+(?:\.\d+)?)\s*(km|m|mi|ft)\b""", RegexOption.IGNORE_CASE)
    private val ETA = Regex("""(\d+)\s*(?:min|mins|minutes)\b""", RegexOption.IGNORE_CASE)

    fun parse(title: String, text: String, bigText: String, subText: String): ParsedNav {
        val all = listOf(title, text, bigText, subText).joinToString(" ")

        val distance = DISTANCE.find(all)?.let { m ->
            val value = m.groupValues[1].toDouble()
            when (m.groupValues[2].lowercase()) {
                "km" -> (value * 1000).toInt()
                "mi" -> (value * 1609).toInt()
                "ft" -> (value * 0.3048).toInt()
                else -> value.toInt()
            }
        } ?: -1

        val eta = ETA.find(all)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val lower = all.lowercase()
        val (maneuver, pictogram) = when {
            "roundabout" in lower -> "Roundabout" to NavInstruction.ROUNDABOUT
            "fork" in lower -> "Fork" to NavInstruction.FORK
            "merge" in lower -> "Merge" to NavInstruction.MERGE
            "turn left" in lower || "left turn" in lower -> "Turn left" to NavInstruction.LEFT
            "turn right" in lower || "right turn" in lower -> "Turn right" to NavInstruction.RIGHT
            "continue" in lower || "straight" in lower -> "Continue" to NavInstruction.STRAIGHT
            else -> "" to NavInstruction.STRAIGHT
        }

        // Google Maps posts distance as the title and the road as text;
        // TVS Connect posts the maneuver as title and metrics as text.
        val street = listOf(text, bigText, title, subText)
            .map { it.trim().replace(DISTANCE, "").trim(' ', '·', '-', ',') }
            .firstOrNull { it.length > 2 && !it.matches(ETA) } ?: ""

        return ParsedNav(maneuver, street, distance, eta, pictogram)
    }

    private fun String.matches(regex: Regex) = regex.matches(this)
}
