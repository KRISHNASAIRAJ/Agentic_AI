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
    private val ETA = Regex("""(\d+)\s*(?:min|mins|minutes|hr|hrs|hour|hours)\b""", RegexOption.IGNORE_CASE)

    /** Arrival clock anywhere in a string: `1:39 pm`, `Arrive 1:39 pm`, `ETA 4:16 AM`. */
    private val CLOCK = Regex("""\b(\d{1,2}):(\d{2})\s*([AaPp])\.?[Mm]\.?\b""")

    /** Arrival sentences Maps puts in the text field, not a street name. */
    private val ARRIVAL_TEXT = Regex(
        """\s*(?:arrive|arrival|eta|expected)\b[^,;]*""", RegexOption.IGNORE_CASE)

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

        // Duration first; if none, fall back to an arrival clock ("1:39 pm").
        var eta = ETA.findAll(all).mapNotNull { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            when (m.groupValues[0].substringAfter(m.groupValues[1]).trim().lowercase()) {
                "hr", "hrs", "hour", "hours" -> n * 60
                else -> n
            }
        }.sum()
        if (eta == 0) {
            val c = CLOCK.find(all)
            if (c != null) {
                var hour = c.groupValues[1].toIntOrNull() ?: 0
                val minute = c.groupValues[2].toIntOrNull() ?: 0
                val mer = c.groupValues[3]
                if (mer.equals("p", true) && hour < 12) hour += 12
                if (mer.equals("a", true) && hour == 12) hour = 0
                val now = java.util.Calendar.getInstance()
                val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                var delta = hour * 60 + minute - nowMin
                if (delta < 0) delta += 24 * 60
                eta = delta
            }
        }

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
        // Strip arrival clocks and sentences ("Arrive 1:39 pm") before using
        // text as a street name; otherwise the card shows a time as the road.
        val street = listOf(text, bigText, subText, title)
            .map {
                it.replace(ARRIVAL_TEXT, "")
                    .replace(CLOCK, "")
                    .replace(DISTANCE, "")
                    .trim(' ', '-', ',', '.')
            }
            .firstOrNull { it.length > 2 && !it.matches(ETA) && CLOCK.find(it) == null } ?: ""

        return ParsedNav(maneuver, street, distance, eta, pictogram)
    }

    private fun String.matches(regex: Regex) = regex.matches(this)
}
