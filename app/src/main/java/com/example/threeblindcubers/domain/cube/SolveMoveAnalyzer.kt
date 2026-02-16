package com.example.threeblindcubers.domain.cube

import com.example.threeblindcubers.domain.models.Move

/**
 * Represents a segment of solve moves, categorized by type.
 *
 * @property type The category of this segment (perm or other moves)
 * @property moves The actual moves in this segment
 * @property label Human-readable label, e.g., "Y-perm (CQ)", "Moves"
 */
data class SolveSegment(
    val type: SegmentType,
    val moves: List<Move>,
    val label: String? = null
)

/**
 * Categorizes a segment of solve moves.
 */
enum class SegmentType {
    /** Corner swap algorithm (Old Pochmann) */
    Y_PERM,
    /** Edge swap algorithm (Old Pochmann) */
    T_PERM,
    /** Parity algorithm */
    RA_PERM,
    /** Any moves between perms (setup, undo, rotations, etc.) */
    MOVES,
    /** Unrecognized moves (no perms found at all) */
    UNKNOWN
}

/**
 * Analyzes a list of solve moves to detect Old Pochmann algorithms (Y-perm,
 * T-perm, Ra-perm) and group remaining moves into simple segments.
 *
 * The analyzer scans for exact perm subsequences, then groups everything
 * between perms as MOVES segments. No attempt is made to distinguish
 * setup from undo — just "moves then perm then moves then perm".
 */
object SolveMoveAnalyzer {

    // ========================================================================
    // Algorithm Definitions
    // ========================================================================

    /** Y-perm (corners): R U' R' U' R U R' F' R U R' U' R' F R */
    val Y_PERM_MOVES: List<Move> = Move.parseSequence("R U' R' U' R U R' F' R U R' U' R' F R")

    /** T-perm (edges): R U R' U' R' F R2 U' R' U' R U R' F' */
    val T_PERM_MOVES: List<Move> = Move.parseSequence("R U R' U' R' F R2 U' R' U' R U R' F'")

    /** Ra-perm (parity): R U R' F' R U2 R' U2 R' F R U R U2 R' (15 Move objects, 3 are double turns) */
    val RA_PERM_MOVES: List<Move> = Move.parseSequence("R U R' F' R U2 R' U2 R' F R U R U2 R'")

    /**
     * Perm definitions paired with their segment types for matching.
     * Order matters: Ra-perm is checked first to avoid partial
     * matches with the shorter T-perm which shares a similar prefix.
     */
    private val PERM_DEFINITIONS: List<Pair<List<Move>, SegmentType>> = listOf(
        RA_PERM_MOVES to SegmentType.RA_PERM,   // 15 moves — check first (longest)
        Y_PERM_MOVES to SegmentType.Y_PERM,      // 15 moves
        T_PERM_MOVES to SegmentType.T_PERM       // 14 moves
    )

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Analyzes a solve's move list and returns a list of [SolveSegment]s.
     *
     * The result alternates between MOVES segments (setup/undo/rotations)
     * and perm segments (Y_PERM, T_PERM, RA_PERM). Empty gaps between
     * adjacent perms are omitted.
     *
     * @param moves The raw list of solve moves (quarter turns)
     * @param opSequence Unused, kept for API compatibility
     * @return Ordered list of segments covering all moves
     */
    fun analyze(moves: List<Move>, opSequence: OpSequence? = null): List<SolveSegment> {
        if (moves.isEmpty()) return emptyList()

        // Step 1: Find all perm matches
        val permMatches = findPermMatches(moves)

        // Step 2: If no perms found, everything is UNKNOWN
        if (permMatches.isEmpty()) {
            return listOf(SolveSegment(SegmentType.UNKNOWN, moves))
        }

        // Step 3: Build segments — just perms and the gaps between them
        return buildSegments(moves, permMatches)
    }

    // ========================================================================
    // Perm Matching
    // ========================================================================

    /**
     * Represents a matched perm at a specific position in the move list.
     */
    data class PermMatch(
        val startIndex: Int,
        val endIndex: Int,     // exclusive
        val type: SegmentType
    )

    /**
     * Scans the move list left-to-right for exact perm subsequences.
     * Returns non-overlapping matches in order of their start position.
     */
    internal fun findPermMatches(moves: List<Move>): List<PermMatch> {
        val matches = mutableListOf<PermMatch>()
        var searchFrom = 0

        while (searchFrom < moves.size) {
            var matched = false

            for ((permMoves, permType) in PERM_DEFINITIONS) {
                if (searchFrom + permMoves.size <= moves.size) {
                    val candidate = moves.subList(searchFrom, searchFrom + permMoves.size)
                    if (movesMatch(candidate, permMoves)) {
                        matches.add(PermMatch(searchFrom, searchFrom + permMoves.size, permType))
                        searchFrom += permMoves.size
                        matched = true
                        break
                    }
                }
            }

            if (!matched) {
                searchFrom++
            }
        }

        return matches
    }

    /**
     * Checks if two move lists are identical (same face and rotation for each).
     */
    private fun movesMatch(a: List<Move>, b: List<Move>): Boolean {
        if (a.size != b.size) return false
        return a.zip(b).all { (m1, m2) -> m1.face == m2.face && m1.rotation == m2.rotation }
    }

    // ========================================================================
    // Segment Building
    // ========================================================================

    /**
     * Builds the full segment list from perm matches and the gaps between them.
     *
     * Simple approach: any moves between perms are grouped as a single MOVES
     * segment. No attempt to split into setup/undo — just shows the actual
     * moves the solver performed between each algorithm.
     */
    private fun buildSegments(
        moves: List<Move>,
        permMatches: List<PermMatch>
    ): List<SolveSegment> {
        val segments = mutableListOf<SolveSegment>()

        for ((matchIdx, match) in permMatches.withIndex()) {
            // --- Gap before this perm ---
            val gapStart = if (matchIdx == 0) 0 else permMatches[matchIdx - 1].endIndex
            val gapEnd = match.startIndex

            if (gapEnd > gapStart) {
                val gapMoves = moves.subList(gapStart, gapEnd)
                segments.add(SolveSegment(SegmentType.MOVES, gapMoves, "Moves"))
            }

            // --- The perm itself ---
            val permLabel = getPermLabel(match.type)
            segments.add(SolveSegment(match.type, moves.subList(match.startIndex, match.endIndex), permLabel))
        }

        // --- Gap after the last perm ---
        val lastMatch = permMatches.last()
        if (lastMatch.endIndex < moves.size) {
            val tailMoves = moves.subList(lastMatch.endIndex, moves.size)
            segments.add(SolveSegment(SegmentType.MOVES, tailMoves, "Moves"))
        }

        return segments
    }

    // ========================================================================
    // Label Generation
    // ========================================================================

    /**
     * Generates a simple label for a perm segment.
     */
    private fun getPermLabel(type: SegmentType): String = when (type) {
        SegmentType.Y_PERM -> "Y-perm"
        SegmentType.T_PERM -> "T-perm"
        SegmentType.RA_PERM -> "Ra-perm"
        else -> "Unknown"
    }
}
