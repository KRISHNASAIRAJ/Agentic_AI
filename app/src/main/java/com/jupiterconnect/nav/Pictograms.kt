package com.jupiterconnect.nav

/**
 * Cluster maneuver pictogram IDs, transcribed from TVS Connect's ManeuverUtils
 * (see TVSRide's Pictogram.kt / docs/NAVIGATION.md). The earlier hand-picked
 * IDs (LEFT=1, RIGHT=2, ...) mapped to different arrows on the real cluster,
 * which is why "Turn left" rendered as the wrong icon.
 */
object Pictograms {
    const val TURN_LEFT = 0
    const val SHARP_LEFT = 1
    const val SLIGHT_LEFT = 2
    const val TURN_RIGHT = 3
    const val SHARP_RIGHT = 4
    const val SLIGHT_RIGHT = 5
    const val U_TURN = 41
    const val CONTINUE_STRAIGHT = 7
    const val DESTINATION = 8
    const val HEAD_NORTH = 50
    const val HEAD_NORTHEAST = 51
    const val HEAD_EAST = 52
    const val HEAD_SOUTHEAST = 53
    const val HEAD_SOUTH = 54
    const val HEAD_SOUTHWEST = 55
    const val HEAD_WEST = 56
    const val HEAD_NORTHWEST = 57
    const val ROUNDABOUT = 68

    /** Text -> pictogram. Order matters: specific rules before general. */
    private val rules: List<Pair<Regex, Int>> = listOf(
        Regex("u[- ]?turn|make a u", RegexOption.IGNORE_CASE) to U_TURN,
        Regex("\\broundabout\\b", RegexOption.IGNORE_CASE) to ROUNDABOUT,
        Regex("\\bfirst exit\\b|\\bexit.*roundabout\\b", RegexOption.IGNORE_CASE) to 65,
        Regex("\\bsharp left\\b", RegexOption.IGNORE_CASE) to SHARP_LEFT,
        Regex("\\bslight left\\b|\\bkeep left\\b", RegexOption.IGNORE_CASE) to SLIGHT_LEFT,
        Regex("\\bsharp right\\b", RegexOption.IGNORE_CASE) to SHARP_RIGHT,
        Regex("\\bslight right\\b|\\bkeep right\\b", RegexOption.IGNORE_CASE) to SLIGHT_RIGHT,
        // Google Maps shows a straight/up arrow for "Head <direction>" — not
        // a compass arrow. Resolve to straight so the cluster matches Maps.
        Regex("\\bhead\\b", RegexOption.IGNORE_CASE) to CONTINUE_STRAIGHT,
        Regex("\\bturn left\\b|\\bleft\\b", RegexOption.IGNORE_CASE) to TURN_LEFT,
        Regex("\\bturn right\\b|\\bright\\b", RegexOption.IGNORE_CASE) to TURN_RIGHT,
        Regex("\\bmerge\\b", RegexOption.IGNORE_CASE) to 14,
        Regex("\\bfork\\b", RegexOption.IGNORE_CASE) to 15,
        Regex("\\barrive|\\bdestination\\b|\\byou have arrived\\b", RegexOption.IGNORE_CASE) to DESTINATION,
        Regex("\\bstraight\\b|\\bcontinue\\b", RegexOption.IGNORE_CASE) to CONTINUE_STRAIGHT,
    )

    fun fromText(text: String?): Int? {
        val t = text?.takeIf { it.isNotBlank() } ?: return null
        for ((re, id) in rules) if (re.containsMatchIn(t)) return id
        return null
    }
}
