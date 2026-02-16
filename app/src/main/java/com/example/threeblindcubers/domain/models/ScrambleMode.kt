package com.example.threeblindcubers.domain.models

/**
 * Scramble practice modes for blindfolded solving
 */
enum class ScrambleMode(val displayName: String) {
    CORNERS_ONLY("Corners Only"),
    EDGES_ONLY("Edges Only"),
    FULL("Full Cube");

    companion object {
        fun fromDisplayName(name: String): ScrambleMode? {
            return entries.find { it.displayName.equals(name, ignoreCase = true) }
        }
    }
}
