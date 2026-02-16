package com.example.threeblindcubers.domain.models

/**
 * Represents a completed blindfolded solve
 */
data class Solve(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: ScrambleMode,
    val scrambleSequence: List<Move>,
    val solveMoves: List<Move>,
    val timeMillis: Long,
    val memoTimeMillis: Long = 0,
    val isDNF: Boolean = false // Did Not Finish
) {
    /**
     * Formats the solve time as MM:SS.mmm
     */
    fun formattedTime(): String {
        val totalSeconds = timeMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = timeMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }

    /**
     * Returns the scramble as a space-separated notation string
     */
    fun scrambleNotation(): String {
        return scrambleSequence.joinToString(" ") { it.toNotation() }
    }

    /**
     * Returns the solve moves as a space-separated notation string
     */
    fun solveMoveNotation(): String {
        return solveMoves.joinToString(" ") { it.toNotation() }
    }
}
