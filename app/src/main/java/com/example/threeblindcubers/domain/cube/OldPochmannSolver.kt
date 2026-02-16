package com.example.threeblindcubers.domain.cube

/**
 * Result of Old Pochmann BLD memo tracing.
 *
 * @property cornerMemo Letters representing the corner tracing sequence
 * @property edgeMemo Letters representing the edge tracing sequence
 * @property hasParity True when cornerMemo.size is odd (requires parity alg)
 */
data class OpSequence(
    val cornerMemo: List<Char>,
    val edgeMemo: List<Char>,
    val hasParity: Boolean
) {
    /**
     * Formats a memo list as letter pairs separated by spaces.
     * E.g., ['C','Q','F','N'] -> "CQ FN"
     */
    fun formatMemo(memo: List<Char>): String {
        return memo.chunked(2).joinToString(" ") { it.joinToString("") }
    }

    fun formattedCornerMemo(): String = formatMemo(cornerMemo)
    fun formattedEdgeMemo(): String = formatMemo(edgeMemo)
}

/**
 * Computes the Old Pochmann (OP) BLD memo sequence from a scrambled cube state.
 *
 * Uses the Speffz letter scheme (A-X) mapped to Kociemba facelet indices.
 *
 * **Corner buffer**: ULB (stickers A/E/R), tracing sticker E (index 36).
 * Swap target: DRF-V (index 29). Letter E signals cycle completion.
 *
 * **Edge buffer**: UR (stickers B/M), tracing sticker B (index 5).
 * Swap target: UL-D (index 3). Letter B signals cycle completion.
 */
object OldPochmannSolver {

    // ========================================================================
    // Speffz Letter Scheme
    // ========================================================================

    // Corner: Kociemba index -> letter
    private val CORNER_INDEX_TO_LETTER: Map<Int, Char> = mapOf(
        0 to 'A', 2 to 'B', 8 to 'C', 6 to 'D',
        36 to 'E', 38 to 'F', 44 to 'G', 42 to 'H',
        18 to 'I', 20 to 'J', 26 to 'K', 24 to 'L',
        9 to 'M', 11 to 'N', 17 to 'O', 15 to 'P',
        45 to 'Q', 47 to 'R', 53 to 'S', 51 to 'T',
        27 to 'U', 29 to 'V', 35 to 'W', 33 to 'X'
    )

    // Corner: letter -> Kociemba index
    private val CORNER_LETTER_TO_INDEX: Map<Char, Int> =
        CORNER_INDEX_TO_LETTER.entries.associate { (k, v) -> v to k }

    // Edge: Kociemba index -> letter
    private val EDGE_INDEX_TO_LETTER: Map<Int, Char> = mapOf(
        1 to 'A', 5 to 'B', 7 to 'C', 3 to 'D',
        37 to 'E', 41 to 'F', 43 to 'G', 39 to 'H',
        19 to 'I', 23 to 'J', 25 to 'K', 21 to 'L',
        10 to 'M', 14 to 'N', 16 to 'O', 12 to 'P',
        46 to 'Q', 50 to 'R', 52 to 'S', 48 to 'T',
        28 to 'U', 32 to 'V', 34 to 'W', 30 to 'X'
    )

    // Edge: letter -> Kociemba index
    private val EDGE_LETTER_TO_INDEX: Map<Char, Int> =
        EDGE_INDEX_TO_LETTER.entries.associate { (k, v) -> v to k }

    // ========================================================================
    // Piece Definitions (sticker groups that form each physical piece)
    // ========================================================================

    /**
     * 8 corner pieces. Each is a list of 3 Kociemba indices (stickers).
     */
    private val CORNER_PIECES: List<List<Int>> = listOf(
        listOf(0, 36, 47),   // ULB -> A, E, R
        listOf(2, 45, 11),   // UBR -> B, Q, N
        listOf(8, 9, 20),    // URF -> C, M, J
        listOf(6, 18, 38),   // UFL -> D, I, F
        listOf(27, 24, 44),  // DFL -> U, L, G
        listOf(29, 15, 26),  // DRF -> V, P, K
        listOf(35, 51, 17),  // DBR -> W, T, O
        listOf(33, 42, 53)   // DLB -> X, H, S
    )

    /**
     * 12 edge pieces. Each is a list of 2 Kociemba indices (stickers).
     */
    private val EDGE_PIECES: List<List<Int>> = listOf(
        listOf(1, 46),   // UB -> A, Q
        listOf(5, 10),   // UR -> B, M
        listOf(7, 19),   // UF -> C, I
        listOf(3, 37),   // UL -> D, E
        listOf(21, 41),  // FL -> L, F
        listOf(23, 12),  // FR -> J, P
        listOf(39, 50),  // BL -> H, R
        listOf(48, 14),  // BR -> T, N
        listOf(28, 25),  // DF -> U, K
        listOf(32, 16),  // DR -> V, O
        listOf(34, 52),  // DB -> W, S
        listOf(30, 43)   // DL -> X, G
    )

    // Solved state for color lookup
    private val SOLVED_STATE = IntArray(54) { it / 9 }

    // ========================================================================
    // Buffer definitions
    // ========================================================================

    // Corner buffer: ULB, tracing sticker = E (index 36)
    private const val CORNER_BUFFER_STICKER_INDEX = 36
    private const val CORNER_BUFFER_LETTER = 'E'

    // Edge buffer: UR, tracing sticker = B (index 5)
    private const val EDGE_BUFFER_STICKER_INDEX = 5
    private const val EDGE_BUFFER_LETTER = 'B'

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Computes the Old Pochmann memo sequence for the given cube state.
     *
     * @param state 54-element list of colors (Kociemba ordering), where
     *              solved state is `state[i] == i / 9`.
     * @return [OpSequence] with corner and edge memo letters.
     */
    fun solve(state: List<Int>): OpSequence {
        val stateArray = state.toIntArray()

        val cornerMemo = trace(
            state = stateArray,
            pieces = CORNER_PIECES,
            indexToLetter = CORNER_INDEX_TO_LETTER,
            letterToIndex = CORNER_LETTER_TO_INDEX,
            bufferStickerIndex = CORNER_BUFFER_STICKER_INDEX,
            bufferLetter = CORNER_BUFFER_LETTER
        )

        val edgeMemo = trace(
            state = stateArray,
            pieces = EDGE_PIECES,
            indexToLetter = EDGE_INDEX_TO_LETTER,
            letterToIndex = EDGE_LETTER_TO_INDEX,
            bufferStickerIndex = EDGE_BUFFER_STICKER_INDEX,
            bufferLetter = EDGE_BUFFER_LETTER
        )

        return OpSequence(
            cornerMemo = cornerMemo,
            edgeMemo = edgeMemo,
            hasParity = cornerMemo.size % 2 == 1
        )
    }

    // ========================================================================
    // Tracing Algorithm
    // ========================================================================

    /**
     * Generic Old Pochmann tracing for either corners or edges.
     *
     * The algorithm traces the permutation cycles of pieces relative to the
     * buffer position.
     *
     * **First cycle** (buffer cycle):
     * - Start at the buffer sticker position
     * - Read what's there → find where it belongs (getTargetLetter) → memo it
     * - Jump to that letter's home position → repeat
     * - Cycle ends when getTargetLetter returns the buffer letter (piece at
     *   current position belongs in the buffer)
     *
     * **Cycle breaks** (disjoint cycles not touching the buffer):
     * - Find the lowest unsolved letter → memo it (break-in)
     * - Jump to that letter's home position → trace until getTargetLetter
     *   returns that same break-in letter (completing the disjoint cycle)
     *
     * The buffer letter (E/B) never appears in the memo.
     */
    private fun trace(
        state: IntArray,
        pieces: List<List<Int>>,
        indexToLetter: Map<Int, Char>,
        letterToIndex: Map<Char, Int>,
        bufferStickerIndex: Int,
        bufferLetter: Char
    ): List<Char> {
        val memo = mutableListOf<Char>()

        // Track which piece positions have been solved/visited.
        val solvedPositions = BooleanArray(pieces.size)

        // Pre-mark solved pieces (correctly placed and oriented)
        for ((pieceIdx, stickerIndices) in pieces.withIndex()) {
            if (isPieceSolvedInPlace(state, stickerIndices)) {
                solvedPositions[pieceIdx] = true
            }
        }

        // The buffer piece is always marked as "handled" — we never shoot to
        // the buffer, even if it's twisted in place.
        val bufferPieceIdx = findPieceContainingIndex(pieces, bufferStickerIndex)
        solvedPositions[bufferPieceIdx] = true

        // Collect ALL letters that belong to the buffer piece, so we can
        // terminate the first cycle when any of them is encountered and
        // exclude all of them from cycle-break candidates.
        val bufferPiece = pieces[bufferPieceIdx]
        val bufferPieceLetters = bufferPiece.mapNotNull { indexToLetter[it] }.toSet()

        // --- First cycle: trace from buffer ---
        // Terminates when the target letter belongs to the buffer piece
        // (any of its stickers, not just the tracing sticker).
        traceCycle(
            state = state,
            pieces = pieces,
            indexToLetter = indexToLetter,
            letterToIndex = letterToIndex,
            terminationLetters = bufferPieceLetters,
            startStickerIndex = bufferStickerIndex,
            solvedPositions = solvedPositions,
            memo = memo
        )

        // --- Cycle breaks: pick lowest unsolved letter ---
        while (true) {
            val breakLetter = findLowestUnsolvedLetter(
                pieces = pieces,
                indexToLetter = indexToLetter,
                solvedPositions = solvedPositions,
                excludeLetters = bufferPieceLetters
            ) ?: break

            // Memo the break-in letter
            memo.add(breakLetter)

            // Mark the piece at the break-in letter's home position as solved
            val breakIndex = letterToIndex[breakLetter]!!
            val breakPieceIdx = findPieceContainingIndex(pieces, breakIndex)
            solvedPositions[breakPieceIdx] = true

            // Collect letters for the break-in piece (for termination)
            val breakPiece = pieces[breakPieceIdx]
            val breakPieceLetters = breakPiece.mapNotNull { indexToLetter[it] }.toSet()

            // Trace from the break-in position. The cycle ends when we return
            // to any sticker on the break-in piece (completing the disjoint cycle).
            // Use skipFirstTerminationCheck=true so that for twisted-in-place
            // pieces, the first target (on the same piece) is added to memo
            // before subsequent iterations check for termination.
            traceCycle(
                state = state,
                pieces = pieces,
                indexToLetter = indexToLetter,
                letterToIndex = letterToIndex,
                terminationLetters = breakPieceLetters,
                startStickerIndex = breakIndex,
                solvedPositions = solvedPositions,
                memo = memo,
                skipFirstTerminationCheck = true,
                addTerminationLetter = true
            )
        }

        return memo
    }

    /**
     * Traces a single cycle starting from [startStickerIndex].
     *
     * Reads the color at the current position, determines where that sticker
     * belongs (getTargetLetter), adds the target letter to memo, marks the
     * target piece as solved, and jumps to the target letter's position.
     *
     * The cycle ends when getTargetLetter returns any letter in
     * [terminationLetters].
     *
     * [skipFirstTerminationCheck] controls whether the termination check is
     * skipped on the first iteration:
     * - **false** (buffer cycle): check every iteration. Buffer piece stickers
     *   never appear in memo. When the buffer is twisted in place, the first
     *   target is another buffer sticker → terminate immediately → empty cycle.
     * - **true** (cycle break): skip the check on the first iteration. This
     *   handles twisted-in-place pieces correctly. For a twisted DRF corner
     *   (stickers K/V/P), breaking into K and tracing: first iteration finds
     *   V (on break-in piece, but skip check) → add V. Second iteration finds
     *   P (on break-in piece, check active) → add P, then terminate.
     *   For a normal multi-piece cycle, the first target is on a different
     *   piece anyway, so skipping the check is harmless.
     *
     * [addTerminationLetter] controls whether the termination letter is added
     * to memo before breaking:
     * - **false** (buffer cycle): the buffer letter (E/B) is never memoed.
     * - **true** (cycle break): the final letter that returns to the break-in
     *   piece IS part of the memo — it's the last swap that closes the cycle.
     */
    private fun traceCycle(
        state: IntArray,
        pieces: List<List<Int>>,
        indexToLetter: Map<Int, Char>,
        letterToIndex: Map<Char, Int>,
        terminationLetters: Set<Char>,
        startStickerIndex: Int,
        solvedPositions: BooleanArray,
        memo: MutableList<Char>,
        skipFirstTerminationCheck: Boolean = false,
        addTerminationLetter: Boolean = false
    ) {
        var currentIndex = startStickerIndex

        // Safety limit to prevent infinite loops
        val maxIterations = pieces.size * 4
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++

            // Read what sticker is currently at this position and find where it belongs
            val targetLetter = getTargetLetter(
                state = state,
                stickerIndex = currentIndex,
                pieces = pieces,
                indexToLetter = indexToLetter
            )

            // Check termination (skip on first iteration for cycle breaks)
            val shouldCheckTermination = !(skipFirstTerminationCheck && iterations == 1)
            if (shouldCheckTermination && targetLetter in terminationLetters) {
                if (addTerminationLetter) {
                    memo.add(targetLetter)
                }
                break
            }

            // Add to memo
            memo.add(targetLetter)

            // Mark the piece at the target letter's home position as solved
            val targetIndex = letterToIndex[targetLetter]!!
            val targetPieceIdx = findPieceContainingIndex(pieces, targetIndex)
            solvedPositions[targetPieceIdx] = true

            // Jump to the target letter's home position for the next iteration
            currentIndex = targetIndex
        }
    }

    /**
     * Determines where the sticker currently at [stickerIndex] belongs.
     *
     * 1. Read the color at stickerIndex.
     * 2. Find which piece position contains stickerIndex → read all sticker
     *    colors at that position.
     * 3. Find the home piece (by matching the color set against solved colors).
     * 4. Within the home piece, find the sticker whose solved color matches
     *    our color → return that sticker's letter.
     */
    private fun getTargetLetter(
        state: IntArray,
        stickerIndex: Int,
        pieces: List<List<Int>>,
        indexToLetter: Map<Int, Char>
    ): Char {
        // Find which piece occupies the position containing stickerIndex
        val currentPiece = pieces.first { stickerIndex in it }

        // Read the colors at the current position
        val colorsAtPosition = currentPiece.map { state[it] }

        // Find the home piece: the piece whose solved colors match this color set
        val colorSet = colorsAtPosition.sorted()
        val homePiece = pieces.first { piece ->
            piece.map { SOLVED_STATE[it] }.sorted() == colorSet
        }

        // The color at our stickerIndex
        val myColor = state[stickerIndex]

        // Find which sticker in the home piece has this color when solved
        val homeSticker = homePiece.first { SOLVED_STATE[it] == myColor }

        return indexToLetter[homeSticker]!!
    }

    /**
     * Checks if a piece is correctly placed AND correctly oriented.
     */
    private fun isPieceSolvedInPlace(state: IntArray, stickerIndices: List<Int>): Boolean {
        return stickerIndices.all { state[it] == SOLVED_STATE[it] }
    }

    /**
     * Finds the piece index (in the pieces list) that contains the given
     * facelet index.
     */
    private fun findPieceContainingIndex(pieces: List<List<Int>>, index: Int): Int {
        return pieces.indexOfFirst { index in it }
    }

    /**
     * Finds the alphabetically lowest letter whose piece position is still
     * unsolved. Skips letters in [excludeLetters] (all buffer piece stickers)
     * since the buffer piece is never a target.
     */
    private fun findLowestUnsolvedLetter(
        pieces: List<List<Int>>,
        indexToLetter: Map<Int, Char>,
        solvedPositions: BooleanArray,
        excludeLetters: Set<Char>
    ): Char? {
        // Get all letters sorted alphabetically
        val sortedLetters = indexToLetter.values.sorted()

        for (letter in sortedLetters) {
            if (letter in excludeLetters) continue

            val index = indexToLetter.entries.first { it.value == letter }.key
            val pieceIdx = findPieceContainingIndex(pieces, index)

            // Check if this piece position is unsolved
            if (!solvedPositions[pieceIdx]) {
                return letter
            }
        }

        return null
    }
}
