package com.example.threeblindcubers.domain.cube

import com.example.threeblindcubers.domain.models.Face
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.Rotation
import com.example.threeblindcubers.domain.models.ScrambleMode

/**
 * Tracks the state of a Rubik's cube using 54 facelets.
 *
 * Uses Kociemba standard facelet numbering:
 * ```
 *              U face
 *           0  1  2
 *           3  4  5
 *           6  7  8
 *  L face      F face      R face      B face
 * 36 37 38 | 18 19 20 |  9 10 11 | 45 46 47
 * 39 40 41 | 21 22 23 | 12 13 14 | 48 49 50
 * 42 43 44 | 24 25 26 | 15 16 17 | 51 52 53
 *              D face
 *          27 28 29
 *          30 31 32
 *          33 34 35
 * ```
 *
 * Color mapping: 0=White(U), 1=Red(R), 2=Green(F), 3=Yellow(D), 4=Orange(L), 5=Blue(B)
 */
class CubeStateTracker {

    private val state = IntArray(54) { it / 9 }

    // Corner facelet indices (positions 0,2,6,8 within each face's 3x3 grid)
    private val cornerIndices = setOf(
        0, 2, 6, 8,       // U
        9, 11, 15, 17,    // R
        18, 20, 24, 26,   // F
        27, 29, 33, 35,   // D
        36, 38, 42, 44,   // L
        45, 47, 51, 53    // B
    )

    // Edge facelet indices (positions 1,3,5,7 within each face's 3x3 grid)
    private val edgeIndices = setOf(
        1, 3, 5, 7,       // U
        10, 12, 14, 16,   // R
        19, 21, 23, 25,   // F
        28, 30, 32, 34,   // D
        37, 39, 41, 43,   // L
        46, 48, 50, 52    // B
    )

    /**
     * Applies a move to the cube state. Handles CW, CCW, and double moves.
     */
    fun applyMove(move: Move) {
        val cycles = MOVE_CYCLES[move.face] ?: return

        when (move.rotation) {
            Rotation.CLOCKWISE -> {
                for (cycle in cycles) {
                    applyCycleCW(cycle)
                }
            }
            Rotation.COUNTER_CLOCKWISE -> {
                for (cycle in cycles) {
                    applyCycleCCW(cycle)
                }
            }
            Rotation.DOUBLE -> {
                // Double move = two clockwise quarter turns
                for (cycle in cycles) {
                    applyCycleCW(cycle)
                }
                for (cycle in cycles) {
                    applyCycleCW(cycle)
                }
            }
        }
    }

    /**
     * Applies a four-cycle in the clockwise direction: a→b→c→d→a
     * Result: [a]=old[d], [b]=old[a], [c]=old[b], [d]=old[c]
     */
    private fun applyCycleCW(cycle: IntArray) {
        val temp = state[cycle[3]]
        state[cycle[3]] = state[cycle[2]]
        state[cycle[2]] = state[cycle[1]]
        state[cycle[1]] = state[cycle[0]]
        state[cycle[0]] = temp
    }

    /**
     * Applies a four-cycle in the counter-clockwise direction (reverse of CW).
     * Result: [a]=old[b], [b]=old[c], [c]=old[d], [d]=old[a]
     */
    private fun applyCycleCCW(cycle: IntArray) {
        val temp = state[cycle[0]]
        state[cycle[0]] = state[cycle[1]]
        state[cycle[1]] = state[cycle[2]]
        state[cycle[2]] = state[cycle[3]]
        state[cycle[3]] = temp
    }

    /**
     * Resets the cube to the solved state.
     */
    fun reset() {
        for (i in state.indices) {
            state[i] = i / 9
        }
    }

    /**
     * Returns a copy of the cube state for UI rendering.
     * Uses List<Int> for structural equality in Compose recomposition.
     */
    fun getState(): List<Int> = state.toList()

    /**
     * Returns the cube state as a 54-character facelet string in the format
     * expected by the min2phase Kociemba solver.
     *
     * Both CubeStateTracker and min2phase use the same Kociemba facelet numbering
     * (U0-U8, R9-R17, F18-F26, D27-D35, L36-L44, B45-B53) with the same
     * color mapping (0=U, 1=R, 2=F, 3=D, 4=L, 5=B), so no re-indexing is needed.
     */
    fun toFaceletString(): String {
        val chars = CharArray(54) { FACELET_CHARS[state[it]] }
        return String(chars)
    }

    /**
     * Checks if the cube is fully solved.
     */
    fun isSolved(): Boolean {
        return state.indices.all { state[it] == it / 9 }
    }

    /**
     * Checks if the cube is solved for the given mode:
     * - FULL: all facelets match solved state
     * - CORNERS_ONLY: only corner facelets need to match
     * - EDGES_ONLY: only edge facelets need to match
     */
    fun isSolved(mode: ScrambleMode): Boolean {
        return when (mode) {
            ScrambleMode.FULL -> isSolved()
            ScrambleMode.CORNERS_ONLY -> cornerIndices.all { state[it] == it / 9 }
            ScrambleMode.EDGES_ONLY -> edgeIndices.all { state[it] == it / 9 }
        }
    }

    companion object {
        /** Maps color index to the facelet character used by min2phase solver */
        private val FACELET_CHARS = charArrayOf('U', 'R', 'F', 'D', 'L', 'B')

        /**
         * Four-cycle definitions for each face's clockwise rotation.
         * Each face has 5 four-cycles: 2 for the face itself (corners + edges),
         * 3 for adjacent strips.
         *
         * For CW rotation, each cycle [a,b,c,d] means: d→a, a→b, b→c, c→d
         *
         * Adjacent strip cycles derived from Kociemba facelet numbering:
         * - U CW: F top row → R top row → B top row → L top row
         * - R CW: F right col → U right col → B left col → D right col
         * - F CW: U bottom row → R left col → D top row (rev) → L right col (rev)
         * - D CW: F bottom row → R bottom row → B bottom row (rev) → L bottom row (rev)
         * - L CW: U left col → F left col → D left col → B right col (rev)
         * - B CW: U top row (rev) → L left col → D bottom row (rev) → R right col
         */
        val MOVE_CYCLES: Map<Face, List<IntArray>> = mapOf(
            Face.U to listOf(
                intArrayOf(0, 2, 8, 6),     // U face corners
                intArrayOf(1, 5, 7, 3),     // U face edges
                intArrayOf(18, 36, 45, 9),  // adjacent strip: F→L→B→R (top rows, CW from above)
                intArrayOf(19, 37, 46, 10), // adjacent strip
                intArrayOf(20, 38, 47, 11)  // adjacent strip
            ),
            Face.R to listOf(
                intArrayOf(9, 11, 17, 15),  // R face corners
                intArrayOf(10, 14, 16, 12), // R face edges
                intArrayOf(2, 51, 29, 20),  // adjacent strip: F→U→B→D (right col, via B left col)
                intArrayOf(5, 48, 32, 23),  // adjacent strip
                intArrayOf(8, 45, 35, 26)   // adjacent strip
            ),
            Face.F to listOf(
                intArrayOf(18, 20, 26, 24), // F face corners
                intArrayOf(19, 23, 25, 21), // F face edges
                intArrayOf(6, 9, 29, 44),   // adjacent strip
                intArrayOf(7, 12, 28, 41),  // adjacent strip
                intArrayOf(8, 15, 27, 38)   // adjacent strip
            ),
            Face.D to listOf(
                intArrayOf(27, 29, 35, 33), // D face corners
                intArrayOf(28, 32, 34, 30), // D face edges
                intArrayOf(15, 51, 42, 24), // adjacent strip: F→R→B→L (bottom rows, CW from below)
                intArrayOf(16, 52, 43, 25), // adjacent strip
                intArrayOf(17, 53, 44, 26)  // adjacent strip
            ),
            Face.L to listOf(
                intArrayOf(36, 38, 44, 42), // L face corners
                intArrayOf(37, 41, 43, 39), // L face edges
                intArrayOf(0, 18, 27, 53),  // adjacent strip
                intArrayOf(3, 21, 30, 50),  // adjacent strip
                intArrayOf(6, 24, 33, 47)   // adjacent strip
            ),
            Face.B to listOf(
                intArrayOf(45, 47, 53, 51), // B face corners
                intArrayOf(46, 50, 52, 48), // B face edges
                intArrayOf(2, 36, 33, 17),  // adjacent strip
                intArrayOf(1, 39, 34, 14),  // adjacent strip
                intArrayOf(0, 42, 35, 11)   // adjacent strip
            )
        )
    }
}
