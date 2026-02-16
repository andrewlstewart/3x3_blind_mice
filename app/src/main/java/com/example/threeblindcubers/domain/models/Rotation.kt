package com.example.threeblindcubers.domain.models

/**
 * Represents the rotation direction and amount for a face
 */
enum class Rotation(val notation: String, val degrees: Int) {
    CLOCKWISE("", 90),          // Standard clockwise 90°
    COUNTER_CLOCKWISE("'", -90), // Counter-clockwise 90°
    DOUBLE("2", 180);            // 180° turn

    companion object {
        fun fromNotation(notation: String): Rotation {
            return when (notation) {
                "'" -> COUNTER_CLOCKWISE
                "2" -> DOUBLE
                else -> CLOCKWISE
            }
        }
    }
}
