package com.example.threeblindcubers.domain.models

/**
 * Represents a single cube move (face + rotation)
 * Examples: "R", "U'", "F2"
 */
data class Move(
    val face: Face,
    val rotation: Rotation
) {
    /**
     * Returns the standard notation string for this move
     * Examples: "R", "U'", "F2"
     */
    fun toNotation(): String = "${face.name}${rotation.notation}"

    /**
     * Returns the inverse of this move.
     * CW becomes CCW, CCW becomes CW, DOUBLE stays DOUBLE.
     */
    fun inverse(): Move = Move(face, when (rotation) {
        Rotation.CLOCKWISE -> Rotation.COUNTER_CLOCKWISE
        Rotation.COUNTER_CLOCKWISE -> Rotation.CLOCKWISE
        Rotation.DOUBLE -> Rotation.DOUBLE
    })

    /**
     * Expands this move to quarter turns for tracking against BT cube.
     * Double moves become two CW turns; quarter turns return as-is.
     */
    fun expandToQuarterTurns(): List<Move> = when (rotation) {
        Rotation.DOUBLE -> listOf(Move(face, Rotation.CLOCKWISE), Move(face, Rotation.CLOCKWISE))
        else -> listOf(this)
    }

    companion object {
        /**
         * Parses a move from standard notation
         * Examples: "R" -> Move(Face.R, Rotation.CLOCKWISE)
         *          "U'" -> Move(Face.U, Rotation.COUNTER_CLOCKWISE)
         *          "F2" -> Move(Face.F, Rotation.DOUBLE)
         */
        fun parse(notation: String): Move? {
            if (notation.isEmpty()) return null

            val faceChar = notation[0].toString()
            val face = Face.fromString(faceChar) ?: return null

            val rotationNotation = if (notation.length > 1) notation.substring(1) else ""
            val rotation = Rotation.fromNotation(rotationNotation)

            return Move(face, rotation)
        }

        /**
         * Parses a scramble sequence from space-separated notation
         * Example: "R U R' F2 D" -> List of 5 moves
         */
        fun parseSequence(notation: String): List<Move> {
            return notation.trim()
                .split(Regex("\\s+"))
                .mapNotNull { parse(it) }
        }
    }
}
