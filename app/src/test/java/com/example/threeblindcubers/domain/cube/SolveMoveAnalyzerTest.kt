package com.example.threeblindcubers.domain.cube

import com.example.threeblindcubers.domain.models.Move
import org.junit.Assert.*
import org.junit.Test

class SolveMoveAnalyzerTest {

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun parseMoves(notation: String): List<Move> = Move.parseSequence(notation)

    /** Asserts a segment has the expected type and move count. */
    private fun assertSegment(
        segment: SolveSegment,
        expectedType: SegmentType,
        expectedMoveCount: Int,
        labelContains: String? = null
    ) {
        assertEquals("Segment type", expectedType, segment.type)
        assertEquals("Segment move count", expectedMoveCount, segment.moves.size)
        if (labelContains != null) {
            assertNotNull("Segment label should not be null", segment.label)
            assertTrue(
                "Label '${segment.label}' should contain '$labelContains'",
                segment.label!!.contains(labelContains)
            )
        }
    }

    // ========================================================================
    // 1. Empty move list
    // ========================================================================

    @Test
    fun `empty move list returns empty segments`() {
        val result = SolveMoveAnalyzer.analyze(emptyList())
        assertTrue("Should return empty segments", result.isEmpty())
    }

    // ========================================================================
    // 2. No recognizable perms — all UNKNOWN
    // ========================================================================

    @Test
    fun `moves with no recognizable perms returns single UNKNOWN segment`() {
        val moves = parseMoves("R U R' D F B")
        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals("Should have exactly 1 segment", 1, result.size)
        assertSegment(result[0], SegmentType.UNKNOWN, 6)
    }

    // ========================================================================
    // 3. Detect Y-perm
    // ========================================================================

    @Test
    fun `detect Y-perm in a sequence`() {
        val yPermNotation = "R U' R' U' R U R' F' R U R' U' R' F R"
        val moves = parseMoves(yPermNotation)
        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals("Should have 1 segment", 1, result.size)
        assertSegment(result[0], SegmentType.Y_PERM, 15)
    }

    // ========================================================================
    // 4. Detect T-perm
    // ========================================================================

    @Test
    fun `detect T-perm in a sequence`() {
        val tPermNotation = "R U R' U' R' F R2 U' R' U' R U R' F'"
        val moves = parseMoves(tPermNotation)
        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals("Should have 1 segment", 1, result.size)
        assertSegment(result[0], SegmentType.T_PERM, 14)
    }

    // ========================================================================
    // 5. Detect Ra-perm
    // ========================================================================

    @Test
    fun `detect Ra-perm in a sequence`() {
        val raPermNotation = "R U R' F' R U2 R' U2 R' F R U R U2 R'"
        val moves = parseMoves(raPermNotation)
        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals("Should have 1 segment", 1, result.size)
        assertSegment(result[0], SegmentType.RA_PERM, 15)
    }

    // ========================================================================
    // 6. Moves before and after a perm
    // ========================================================================

    @Test
    fun `moves before perm and moves after perm`() {
        // Moves: F D  |  Y-perm  |  D' F'
        val beforeMoves = "F D"
        val yPermNotation = "R U' R' U' R U R' F' R U R' U' R' F R"
        val afterMoves = "D' F'"
        val moves = parseMoves("$beforeMoves $yPermNotation $afterMoves")

        val result = SolveMoveAnalyzer.analyze(moves)

        // Expect: MOVES(2) + Y_PERM(15) + MOVES(2)
        assertEquals("Should have 3 segments", 3, result.size)
        assertSegment(result[0], SegmentType.MOVES, 2)
        assertSegment(result[1], SegmentType.Y_PERM, 15)
        assertSegment(result[2], SegmentType.MOVES, 2)
    }

    // ========================================================================
    // 7. Multiple perms in sequence
    // ========================================================================

    @Test
    fun `two Y-perms with moves in between`() {
        val yPerm = "R U' R' U' R U R' F' R U R' U' R' F R"
        // Gap between perms: D' F' F D (4 moves grouped as one MOVES segment)
        val gap = "D' F' F D"
        val moves = parseMoves("$yPerm $gap $yPerm")

        val result = SolveMoveAnalyzer.analyze(moves)

        // Expect: Y_PERM(15) + MOVES(4) + Y_PERM(15)
        assertEquals("Should have 3 segments", 3, result.size)
        assertSegment(result[0], SegmentType.Y_PERM, 15)
        assertSegment(result[1], SegmentType.MOVES, 4)
        assertSegment(result[2], SegmentType.Y_PERM, 15)
    }

    @Test
    fun `two perms back to back with no gap`() {
        val yPerm = "R U' R' U' R U R' F' R U R' U' R' F R"
        val tPerm = "R U R' U' R' F R2 U' R' U' R U R' F'"
        val moves = parseMoves("$yPerm $tPerm")

        val result = SolveMoveAnalyzer.analyze(moves)

        // No gap means no MOVES segment between them
        assertEquals("Should have 2 segments", 2, result.size)
        assertSegment(result[0], SegmentType.Y_PERM, 15)
        assertSegment(result[1], SegmentType.T_PERM, 14)
    }

    // ========================================================================
    // 8. Perm labels are just the perm name (no Speffz letters)
    // ========================================================================

    @Test
    fun `Y-perm gets Y-perm label`() {
        val yPermNotation = "R U' R' U' R U R' F' R U R' U' R' F R"
        val moves = parseMoves(yPermNotation)

        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals(1, result.size)
        assertSegment(result[0], SegmentType.Y_PERM, 15, "Y-perm")
    }

    @Test
    fun `T-perm gets T-perm label`() {
        val tPermNotation = "R U R' U' R' F R2 U' R' U' R U R' F'"
        val moves = parseMoves(tPermNotation)

        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals(1, result.size)
        assertSegment(result[0], SegmentType.T_PERM, 14, "T-perm")
    }

    @Test
    fun `Ra-perm gets Ra-perm label`() {
        val raPermNotation = "R U R' F' R U2 R' U2 R' F R U R U2 R'"
        val moves = parseMoves(raPermNotation)

        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals(1, result.size)
        assertSegment(result[0], SegmentType.RA_PERM, 15, "Ra-perm")
    }

    // ========================================================================
    // 9. Mixed solve: corners (Y-perms) + parity (Ra-perm) + edges (T-perms)
    // ========================================================================

    @Test
    fun `mixed solve with corners parity and edges`() {
        val yPerm = "R U' R' U' R U R' F' R U R' U' R' F R"
        val raPerm = "R U R' F' R U2 R' U2 R' F R U R U2 R'"
        val tPerm = "R U R' U' R' F R2 U' R' U' R U R' F'"

        val moves = parseMoves("$yPerm $raPerm $tPerm")

        val result = SolveMoveAnalyzer.analyze(moves)

        // Y_PERM + RA_PERM + T_PERM (no gaps between them)
        assertEquals("Should have 3 segments", 3, result.size)
        assertSegment(result[0], SegmentType.Y_PERM, 15, "Y-perm")
        assertSegment(result[1], SegmentType.RA_PERM, 15, "Ra-perm")
        assertSegment(result[2], SegmentType.T_PERM, 14, "T-perm")
    }

    // ========================================================================
    // 10. Multiple Y-perms detected independently
    // ========================================================================

    @Test
    fun `multiple Y-perms each get Y-perm label`() {
        val yPerm = "R U' R' U' R U R' F' R U R' U' R' F R"
        val moves = parseMoves("$yPerm $yPerm")

        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals(2, result.size)
        assertSegment(result[0], SegmentType.Y_PERM, 15, "Y-perm")
        assertSegment(result[1], SegmentType.Y_PERM, 15, "Y-perm")
    }

    // ========================================================================
    // 11. Multiple T-perms detected independently
    // ========================================================================

    @Test
    fun `multiple T-perms each get T-perm label`() {
        val tPerm = "R U R' U' R' F R2 U' R' U' R U R' F'"
        val moves = parseMoves("$tPerm $tPerm")

        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals(2, result.size)
        assertSegment(result[0], SegmentType.T_PERM, 14, "T-perm")
        assertSegment(result[1], SegmentType.T_PERM, 14, "T-perm")
    }

    // ========================================================================
    // 12. findPermMatches — internal verification
    // ========================================================================

    @Test
    fun `findPermMatches returns correct start and end indices`() {
        val yPerm = "R U' R' U' R U R' F' R U R' U' R' F R"
        val prefix = "F D"
        val moves = parseMoves("$prefix $yPerm")

        val matches = SolveMoveAnalyzer.findPermMatches(moves)

        assertEquals("Should find exactly 1 match", 1, matches.size)
        assertEquals("Start index", 2, matches[0].startIndex)
        assertEquals("End index", 17, matches[0].endIndex)
        assertEquals("Type", SegmentType.Y_PERM, matches[0].type)
    }

    @Test
    fun `findPermMatches finds non-overlapping matches left-to-right`() {
        val yPerm = "R U' R' U' R U R' F' R U R' U' R' F R"
        val tPerm = "R U R' U' R' F R2 U' R' U' R U R' F'"
        val moves = parseMoves("$yPerm $tPerm")

        val matches = SolveMoveAnalyzer.findPermMatches(moves)

        assertEquals("Should find 2 matches", 2, matches.size)
        assertEquals(SegmentType.Y_PERM, matches[0].type)
        assertEquals(SegmentType.T_PERM, matches[1].type)
        // Verify no overlap
        assertTrue(
            "End of first match should be <= start of second",
            matches[0].endIndex <= matches[1].startIndex
        )
    }

    // ========================================================================
    // 13. Moves segments get "Moves" label
    // ========================================================================

    @Test
    fun `moves between perms get Moves label`() {
        val beforeMoves = "F"
        val yPermNotation = "R U' R' U' R U R' F' R U R' U' R' F R"
        val afterMoves = "F'"
        val moves = parseMoves("$beforeMoves $yPermNotation $afterMoves")

        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals(3, result.size)
        assertSegment(result[0], SegmentType.MOVES, 1, "Moves")
        assertSegment(result[1], SegmentType.Y_PERM, 15, "Y-perm")
        assertSegment(result[2], SegmentType.MOVES, 1, "Moves")
    }

    // ========================================================================
    // 14. OpSequence parameter is accepted but ignored
    // ========================================================================

    @Test
    fun `analyze with OpSequence still uses plain perm labels`() {
        val yPermNotation = "R U' R' U' R U R' F' R U R' U' R' F R"
        val moves = parseMoves(yPermNotation)
        val opSequence = OpSequence(
            cornerMemo = listOf('C', 'Q'),
            edgeMemo = emptyList(),
            hasParity = false
        )

        val result = SolveMoveAnalyzer.analyze(moves, opSequence)

        assertEquals(1, result.size)
        assertSegment(result[0], SegmentType.Y_PERM, 15, "Y-perm")
    }

    // ========================================================================
    // 15. Ra-perm is detected before T-perm (longer match wins)
    // ========================================================================

    @Test
    fun `Ra-perm detected in preference to partial T-perm overlap`() {
        // Ra-perm starts with "R U R'" just like T-perm.
        // The analyzer should match Ra-perm since it checks longer patterns first.
        val raPermNotation = "R U R' F' R U2 R' U2 R' F R U R U2 R'"
        val moves = parseMoves(raPermNotation)

        val matches = SolveMoveAnalyzer.findPermMatches(moves)

        assertEquals("Should find exactly 1 match", 1, matches.size)
        assertEquals("Should be Ra-perm", SegmentType.RA_PERM, matches[0].type)
    }

    // ========================================================================
    // 16. All segments cover all moves (no moves lost)
    // ========================================================================

    @Test
    fun `all segments together cover all original moves`() {
        val beforeMoves = "F D"
        val yPermNotation = "R U' R' U' R U R' F' R U R' U' R' F R"
        val betweenMoves = "D' F'"
        val extraMoves = "L B"
        val tPermNotation = "R U R' U' R' F R2 U' R' U' R U R' F'"
        val allNotation = "$beforeMoves $yPermNotation $betweenMoves $extraMoves $tPermNotation"
        val moves = parseMoves(allNotation)

        val result = SolveMoveAnalyzer.analyze(moves)

        val totalSegmentMoves = result.sumOf { it.moves.size }
        assertEquals("Total segment moves should equal original move count", moves.size, totalSegmentMoves)
    }

    // ========================================================================
    // 17. Single move — treated as UNKNOWN
    // ========================================================================

    @Test
    fun `single move is treated as UNKNOWN`() {
        val moves = parseMoves("R")
        val result = SolveMoveAnalyzer.analyze(moves)

        assertEquals(1, result.size)
        assertSegment(result[0], SegmentType.UNKNOWN, 1)
    }
}
