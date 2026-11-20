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

    // --- Verbatim tables from TVS Connect 8.14.3 -----------------------------
    // BleNavigationSendData.getPictogramForU725():71-213 maps a Maps MMI
    // pictogram ID to the byte written into 0x4E byte 9. These are the
    // authoritative cluster icons; the constants above are the legacy
    // ManeuverUtils values kept for A/B testing.

    fun fromU725(mmiId: Int): Int = when (mmiId) {
        -1 -> -1
        1 -> 1
        2, 15, 19 -> 2
        3, 14 -> 10
        4 -> 11
        5, 16, 20 -> 12
        6 -> 9
        7, 21, 44, 50 -> 57
        8, 9, 10 -> 22
        23 -> 62
        25 -> 63
        26, 27, 28 -> 8
        29, 30, 31 -> 18
        32, 33 -> 49
        35 -> 14
        36 -> 23
        41 -> 19
        42, 45, 48, 76, 77 -> 7
        43, 46, 47, 78, 79 -> 17
        65 -> 27
        66 -> 28
        67 -> 29
        68 -> 48
        69 -> 31
        70 -> 32
        71 -> 33
        72 -> 64
        else -> 0
    }

    /** BleNavigationSendData.getMMIv2PictogramIdsForCluster():505-549. */
    fun fromMmiV2(pictoId: Int): Int = when {
        pictoId == 11 || pictoId == 13 -> 0
        pictoId == 12 || pictoId == 14 -> 3
        pictoId == 9 || pictoId == 10 -> 8
        pictoId == 15 -> 2
        pictoId == 16 -> 5
        pictoId == 21 -> 7
        pictoId in 26..28 -> 2
        pictoId in 29..31 -> 5
        pictoId in 32..33 -> 7
        pictoId == 42 || pictoId == 45 || pictoId == 48 || pictoId == 76 || pictoId == 77 -> 74
        pictoId == 44 -> 73
        pictoId == 43 || pictoId == 46 || pictoId == 47 || pictoId == 78 || pictoId == 79 -> 75
        else -> pictoId
    }

    /** Full MMI-v2 -> U725 conversion for a numeric maneuver ID from Maps. */
    fun fromManeuverId(mmiId: Int): Int = fromU725(fromMmiV2(mmiId))
}
