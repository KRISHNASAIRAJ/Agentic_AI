package com.jupiterconnect.nav

/** One normalised turn-by-turn instruction, provider-agnostic. */
data class NavInstruction(
    val title: String,
    val street: String,
    val distanceMeters: Int,
    val etaMinutes: Int = 0,
    val pictogram: Int = STRAIGHT,
    val provider: String = "",
    /** Total distance left to the destination (cluster's "km left" field). */
    val remainingMeters: Int = -1,
) {
    val displayLine: String
        get() = listOf(title, street).firstOrNull { it.isNotBlank() } ?: "Navigating"

    companion object {
        const val LEFT = Pictograms.TURN_LEFT
        const val RIGHT = Pictograms.TURN_RIGHT
        const val MERGE = 14
        const val FORK = 15
        const val ROUNDABOUT = Pictograms.ROUNDABOUT
        const val STRAIGHT = Pictograms.CONTINUE_STRAIGHT
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
    private val DISTANCE = Regex("""(\d+(?:[.,]\d+)?)\s*(km|mi|ft|m)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val ETA = Regex("""(\d+)\s*(?:min|mins|minutes|hr|hrs|hour|hours)\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\b(\d{1,2}):(\d{2})\s*([AaPp])\.?[Mm]\.?\b""")
    private val ARRIVAL_TEXT = Regex(
        """\s*(?:arrive|arrival|eta|expected)\b[^,;]*""", RegexOption.IGNORE_CASE)

    /**
     * Total distance remaining to the destination (the cluster's "km left"
     * field beside the ETA). Maps usually does NOT publish this in the
     * notification — only the distance to the next turn. But some builds put
     * "12.4 km remaining" or "X km to destination" in bigText/subText, so look
     * for those phrases specifically. Returns -1 when genuinely absent.
     */
    private val REMAINING = Regex(
        """(\d+(?:[.,]\d+)?)\s*(km|m)\s*(?:remaining|left|to (?:go|destination|dest))""",
        RegexOption.IGNORE_CASE,
    )
    fun findRemaining(vararg texts: String): Int {
        for (t in texts) {
            if (t.isBlank()) continue
            val m = REMAINING.find(t) ?: continue
            val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            return if (m.groupValues[2].equals("km", true)) (value * 1000).toInt() else value.toInt()
        }
        return -1
    }

    /**
     * Mappls publishes the trip total as "15.5 km • 27 min • 11:11 pm ETA",
     * with no "remaining" keyword. Recognise a km figure in a string that also
     * carries minutes/ETA - that is the trip distance, not the turn distance.
     */
    private val TRIP_KM = Regex("""(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    fun findTripDistance(vararg texts: String): Int {
        for (t in texts) {
            if (t.isBlank()) continue
            if (!t.contains("min", true) && !t.contains("eta", true)) continue
            val m = TRIP_KM.find(t) ?: continue
            val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            return (value * 1000).toInt()
        }
        return -1
    }

    /** Extract a distance in metres from any string, or -1. */
    fun findDistance(vararg texts: String): Int {
        for (t in texts) {
            if (t.isBlank()) continue
            val m = DISTANCE.find(t) ?: continue
            val value = m.groupValues[1].replace('/', '.').replace(',', '.').toDoubleOrNull()
                ?: continue
            return when (m.groupValues[2].lowercase()) {
                "km" -> (value * 1000).toInt()
                "mi" -> (value * 1609).toInt()
                "ft" -> (value * 0.3048).toInt()
                else -> value.toInt()
            }
        }
        return -1
    }

    fun parse(title: String, text: String, bigText: String, subText: String): ParsedNav {
        val all = listOf(title, text, bigText, subText).joinToString(" ")

        val distance = DISTANCE.find(all)?.let { m ->
            val value = m.groupValues[1].replace('/', '.').replace(',', '.').toDoubleOrNull() ?: -1.0
            when (m.groupValues[2].lowercase()) {
                "km" -> (value * 1000).toInt()
                "mi" -> (value * 1609).toInt()
                "ft" -> (value * 0.3048).toInt()
                else -> value.toInt()
            }
        } ?: -1

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

        // Resolve the arrow from the full instruction text using the official
        // TVS pictogram table — not hand-picked IDs that render the wrong arrow.
        // Resolve arrow from the TITLE first (it holds the real maneuver like
        // "Head east on"). A bigText may mention "roundabout" in passing and
        // wrongly win if we search all fields at once.
        // Google Maps 2026 title format: "120 m · Turn right after Sri Devi ..."
        // Strip the leading "<distance> · " so the maneuver text is clean.
        val LEADING_DISTANCE = Regex(
            """^\s*\d+(?:[.,]\d+)?\s*(?:km|mi|ft|m)\s*[·•|\-\u2013\u2014:]\s*""",
            RegexOption.IGNORE_CASE)
        val maneuverSource = title.replace(LEADING_DISTANCE, "").trim()
        val pictogram = Pictograms.fromText(maneuverSource)
            ?: Pictograms.fromText(title)
            ?: Pictograms.fromText(all)
            ?: NavInstruction.STRAIGHT
        val maneuver = maneuverSource.ifBlank {
            listOf(text, bigText, subText).firstOrNull { it.isNotBlank() }.orEmpty()
        }.take(17)

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
