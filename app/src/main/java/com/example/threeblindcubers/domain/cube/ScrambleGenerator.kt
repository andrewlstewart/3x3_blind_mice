package com.example.threeblindcubers.domain.cube

import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.ScrambleMode
import cs.min2phase.Search
import cs.min2phase.Tools
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates random-state scrambles for blindfolded cube solving practice
 * using the min2phase Kociemba two-phase solver.
 *
 * Instead of picking random moves (which cannot isolate corners or edges),
 * this generates a random cube *state* with only the target pieces scrambled,
 * then uses the solver to find a move sequence that produces that state.
 */
@Singleton
class ScrambleGenerator @Inject constructor() {

    private val search = Search()

    /**
     * Generates a scramble for the specified mode using the min2phase solver.
     *
     * Mode mapping (counterintuitive but correct):
     * - CORNERS_ONLY -> Tools.randomEdgeSolved() (edges stay solved, only corners scrambled)
     * - EDGES_ONLY -> Tools.randomCornerSolved() (corners stay solved, only edges scrambled)
     * - FULL -> Tools.randomCube() (everything scrambled)
     *
     * @param mode which pieces to scramble
     * @param moveCount ignored - the solver determines optimal length (typically 19-21 moves)
     */
    fun generateScramble(mode: ScrambleMode, @Suppress("UNUSED_PARAMETER") moveCount: Int = 20): List<Move> {
        val facelets = when (mode) {
            ScrambleMode.CORNERS_ONLY -> Tools.randomEdgeSolved()
            ScrambleMode.EDGES_ONLY -> Tools.randomCornerSolved()
            ScrambleMode.FULL -> Tools.randomCube()
        }

        val solution = search.solution(
            facelets,
            21,               // maxDepth
            100_000_000L,     // timeOut in nanoseconds (100ms)
            0L,               // timeMin
            Search.INVERSE_SOLUTION
        )

        // The solver returns an error string starting with "Error" if it fails
        if (solution.startsWith("Error")) {
            throw IllegalStateException("Solver failed: $solution")
        }

        return Move.parseSequence(solution.trim())
    }

    /**
     * Computes the optimal move sequence to get from [currentFacelets] to [targetFacelets].
     *
     * Uses the Kociemba solver on the "difference" state: diff = current⁻¹ * target.
     * With INVERSE_SOLUTION, the solver returns the moves that *produce* the diff state,
     * which are exactly the moves to apply to the physical cube to reach the target.
     *
     * @param currentFacelets 54-char facelet string of the current cube state
     * @param targetFacelets 54-char facelet string of the desired target state
     * @return list of moves to apply to reach the target, or empty list if already at target
     */
    fun computeRemainingMoves(currentFacelets: String, targetFacelets: String): List<Move> {
        if (currentFacelets == targetFacelets) return emptyList()

        // Compute difference state using Tools helper (handles package-private CubieCube/Util)
        val diffFacelets = Tools.computeDifferenceFacelets(currentFacelets, targetFacelets)

        // Check if diff is identity (already at target)
        if (diffFacelets == SOLVED_FACELETS) return emptyList()

        // Solve with INVERSE_SOLUTION to get moves that produce the diff state
        val solution = search.solution(
            diffFacelets,
            21,               // maxDepth
            100_000_000L,     // timeOut in nanoseconds (100ms)
            0L,               // timeMin
            Search.INVERSE_SOLUTION
        )

        if (solution.startsWith("Error")) {
            throw IllegalStateException("Solver failed: $solution")
        }

        return Move.parseSequence(solution.trim())
    }

    /**
     * Pre-initializes the solver's pruning tables (~1MB, ~200ms).
     * This is synchronized and idempotent - safe to call multiple times.
     * Call from a background thread to avoid blocking the UI.
     */
    fun initialize() {
        Search.init()
    }

    companion object {
        const val DEFAULT_SCRAMBLE_LENGTH = 20
        private const val SOLVED_FACELETS = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB"
    }
}
