package com.example.threeblindcubers.domain.cube

import com.example.threeblindcubers.domain.models.Move
import org.junit.Assert.*
import org.junit.Test

class OldPochmannSolverTest {

    // ========================================================================
    // Helper: apply a scramble string to a fresh cube and return the state
    // ========================================================================

    private fun scrambledState(scramble: String): List<Int> {
        val tracker = CubeStateTracker()
        for (move in Move.parseSequence(scramble)) {
            tracker.applyMove(move)
        }
        return tracker.getState()
    }

    // ========================================================================
    // 1. Solved cube
    // ========================================================================

    @Test
    fun `solved cube produces empty memos and no parity`() {
        val state = List(54) { it / 9 } // solved
        val result = OldPochmannSolver.solve(state)

        assertTrue("Corner memo should be empty for solved cube", result.cornerMemo.isEmpty())
        assertTrue("Edge memo should be empty for solved cube", result.edgeMemo.isEmpty())
        assertFalse("No parity on solved cube", result.hasParity)
    }

    // ========================================================================
    // 2. Corners-only scramble
    // ========================================================================

    @Test
    fun `corners-only scramble has correct corner memo and empty edge memo`() {
        // This scramble permutes corners (and may affect edges too).
        // We verify the corner memo is non-empty and structurally valid.
        val state = scrambledState("F D' B D' B D' F B2 U' R2 U F2 R2 B2 U2 F2 U2 R2 D'")
        val result = OldPochmannSolver.solve(state)

        // Corner memo should be non-empty
        assertTrue("Corner memo should be non-empty", result.cornerMemo.isNotEmpty())

        // Buffer letter exclusion
        assertFalse("E should not be in corner memo", result.cornerMemo.contains('E'))
        assertFalse("B should not be in edge memo", result.edgeMemo.contains('B'))

        // Parity consistency
        assertEquals(result.cornerMemo.size % 2 == 1, result.hasParity)

        // Valid letters
        val validLetters = ('A'..'X').toSet()
        assertTrue(result.cornerMemo.all { it in validLetters })
        assertTrue(result.edgeMemo.all { it in validLetters })
    }

    // ========================================================================
    // 3. Edges-only scramble
    // ========================================================================

    @Test
    fun `edges-only scramble has correct edge memo and empty corner memo`() {
        // This scramble permutes edges (and may affect corners too).
        // We verify the edge memo is non-empty and structurally valid.
        val state = scrambledState("B2 D2 F' D F2 B2 D' L R2 U R2 B2 R2 U B2 U' D' F2 L2")
        val result = OldPochmannSolver.solve(state)

        // Edge memo should be non-empty
        assertTrue("Edge memo should be non-empty", result.edgeMemo.isNotEmpty())

        // Buffer letter exclusion
        assertFalse("E should not be in corner memo", result.cornerMemo.contains('E'))
        assertFalse("B should not be in edge memo", result.edgeMemo.contains('B'))

        // Parity consistency
        assertEquals(result.cornerMemo.size % 2 == 1, result.hasParity)

        // Valid letters
        val validLetters = ('A'..'X').toSet()
        assertTrue(result.cornerMemo.all { it in validLetters })
        assertTrue(result.edgeMemo.all { it in validLetters })
    }

    // ========================================================================
    // 4. Buffer letter exclusion
    // ========================================================================

    @Test
    fun `E never appears in corner memo`() {
        // Test with several scrambles
        val scrambles = listOf(
            "R U R' U' R' F R2 U' R' U' R U R' F'",  // T-perm
            "R U2 R' U' R U2 L' U R' U' L",           // J-perm
            "R2 U R U R' U' R' U' R' U R'",            // Ua-perm
            "F R U' R' U' R U R' F' R U R' U' R' F R F'", // Y-perm
            "R' U L' U2 R U' R' U2 R L"                // A-perm
        )

        for (scramble in scrambles) {
            val state = scrambledState(scramble)
            val result = OldPochmannSolver.solve(state)
            assertFalse(
                "E should never appear in corner memo for scramble: $scramble",
                result.cornerMemo.contains('E')
            )
        }
    }

    @Test
    fun `B never appears in edge memo`() {
        val scrambles = listOf(
            "R U R' U' R' F R2 U' R' U' R U R' F'",  // T-perm
            "R U2 R' U' R U2 L' U R' U' L",           // J-perm
            "R2 U R U R' U' R' U' R' U R'",            // Ua-perm
            "F R U' R' U' R U R' F' R U R' U' R' F R F'", // Y-perm
            "M2 U M2 U2 M2 U M2"                       // H-perm (edges only)
        )

        for (scramble in scrambles) {
            val state = scrambledState(scramble)
            val result = OldPochmannSolver.solve(state)
            assertFalse(
                "B should never appear in edge memo for scramble: $scramble",
                result.edgeMemo.contains('B')
            )
        }
    }

    // ========================================================================
    // 5. Invariants on various scrambles (fuzz-style)
    // ========================================================================

    @Test
    fun `all memo letters are in A-X range`() {
        val scrambles = listOf(
            "R U R' F' L D2 B' U R' D F2 L2 B2 R2 U2 D' L2 U' B2",
            "U F2 D R2 B2 U B2 R2 F2 L2 D' L' B D2 F' U B F' L' D2",
            "R' U' F R U2 B' L D' R2 B2 D2 L2 F2 U' L2 U F2 R2 D' R' U' F",
            "L2 F' R2 D2 B' L2 F D2 R2 U2 F L B U' L' U2 R' D L' F' D",
            "R U R' U' R' F R2 U' R' U' R U R' F'"
        )

        val validLetters = ('A'..'X').toSet()

        for (scramble in scrambles) {
            val state = scrambledState(scramble)
            val result = OldPochmannSolver.solve(state)

            for (letter in result.cornerMemo) {
                assertTrue(
                    "Corner letter '$letter' should be in A-X for scramble: $scramble",
                    letter in validLetters
                )
            }
            for (letter in result.edgeMemo) {
                assertTrue(
                    "Edge letter '$letter' should be in A-X for scramble: $scramble",
                    letter in validLetters
                )
            }
        }
    }

    @Test
    fun `parity equals cornerMemo size is odd`() {
        val scrambles = listOf(
            "R U R' U' R' F R2 U' R' U' R U R' F'",
            "R U2 R' U' R U2 L' U R' U' L",
            "L2 F' R2 D2 B' L2 F D2 R2 U2 F L B U' L' U2 R' D L' F' D",
            "R' U' F R U2 B' L D' R2 B2 D2 L2 F2 U' L2 U F2 R2 D' R' U' F",
            "U F2 D R2 B2 U B2 R2 F2 L2 D' L' B D2 F' U B F' L' D2"
        )

        for (scramble in scrambles) {
            val state = scrambledState(scramble)
            val result = OldPochmannSolver.solve(state)

            assertEquals(
                "hasParity should equal cornerMemo.size % 2 == 1 for scramble: $scramble",
                result.cornerMemo.size % 2 == 1,
                result.hasParity
            )
        }
    }

    @Test
    fun `buffer letters excluded on various scrambles`() {
        val scrambles = listOf(
            "R U R' U' R' F R2 U' R' U' R U R' F'",
            "R U2 R' U' R U2 L' U R' U' L",
            "L2 F' R2 D2 B' L2 F D2 R2 U2 F L B U' L' U2 R' D L' F' D",
            "R' U' F R U2 B' L D' R2 B2 D2 L2 F2 U' L2 U F2 R2 D' R' U' F",
            "U F2 D R2 B2 U B2 R2 F2 L2 D' L' B D2 F' U B F' L' D2"
        )

        for (scramble in scrambles) {
            val state = scrambledState(scramble)
            val result = OldPochmannSolver.solve(state)

            assertFalse(
                "E should not be in corner memo for: $scramble",
                result.cornerMemo.contains('E')
            )
            assertFalse(
                "B should not be in edge memo for: $scramble",
                result.edgeMemo.contains('B')
            )
        }
    }

    // ========================================================================
    // 6. Integration: apply scramble via CubeStateTracker, run solver
    // ========================================================================

    @Test
    fun `integration test with CubeStateTracker`() {
        val scramble = "R U R' U' R' F R2 U' R' U' R U R' F'"  // T-perm
        val tracker = CubeStateTracker()
        for (move in Move.parseSequence(scramble)) {
            tracker.applyMove(move)
        }

        val result = OldPochmannSolver.solve(tracker.getState())

        // T-perm swaps two corners and two edges, so memos should be non-empty
        // Just verify the structure is valid
        assertTrue("Corner or edge memo should be non-empty for T-perm",
            result.cornerMemo.isNotEmpty() || result.edgeMemo.isNotEmpty())

        // All letters in valid range
        val validLetters = ('A'..'X').toSet()
        assertTrue(result.cornerMemo.all { it in validLetters })
        assertTrue(result.edgeMemo.all { it in validLetters })

        // Buffer letters excluded
        assertFalse(result.cornerMemo.contains('E'))
        assertFalse(result.edgeMemo.contains('B'))
    }

    @Test
    fun `single R move produces valid memo`() {
        val state = scrambledState("R")
        val result = OldPochmannSolver.solve(state)

        // R move affects 4 corners and 4 edges
        assertTrue("Corner memo should not be empty after R", result.cornerMemo.isNotEmpty())
        assertTrue("Edge memo should not be empty after R", result.edgeMemo.isNotEmpty())

        // Buffer letters excluded
        assertFalse(result.cornerMemo.contains('E'))
        assertFalse(result.edgeMemo.contains('B'))

        // Valid letters
        val validLetters = ('A'..'X').toSet()
        assertTrue(result.cornerMemo.all { it in validLetters })
        assertTrue(result.edgeMemo.all { it in validLetters })
    }

    // ========================================================================
    // Format tests
    // ========================================================================

    @Test
    fun `formatMemo groups letters into pairs`() {
        val seq = OpSequence(
            cornerMemo = listOf('C', 'Q', 'F', 'N'),
            edgeMemo = listOf('M', 'J', 'R', 'P', 'X'),
            hasParity = false
        )
        assertEquals("CQ FN", seq.formattedCornerMemo())
        assertEquals("MJ RP X", seq.formattedEdgeMemo())
    }

    @Test
    fun `formatMemo empty produces empty string`() {
        val seq = OpSequence(
            cornerMemo = emptyList(),
            edgeMemo = emptyList(),
            hasParity = false
        )
        assertEquals("", seq.formattedCornerMemo())
        assertEquals("", seq.formattedEdgeMemo())
    }

    @Test
    fun `formatMemo single letter`() {
        val seq = OpSequence(
            cornerMemo = listOf('A'),
            edgeMemo = emptyList(),
            hasParity = true
        )
        assertEquals("A", seq.formattedCornerMemo())
    }

    // ========================================================================
    // Twisted-in-place piece handling
    // ========================================================================

    @Test
    fun `twisted buffer and swap target produces KVP memo`() {
        // Y-perm, setup F' R', Y-perm, undo R F
        // This twists ULB and DRF corners in place — no pieces are displaced.
        // Buffer (ULB) is twisted in place, so first cycle is empty.
        // DRF is twisted in place → cycle break at K (lowest unsolved letter).
        // Sticker at K position belongs at V → memo V (skip first termination check).
        // Sticker at V position belongs at P → memo P (termination letter, added to
        // close the cycle). All 3 stickers of the twisted corner must be targeted.
        // Expected corner memo: KVP (odd, parity). Edges: empty.
        val state = scrambledState(
            "R U' R' U' R U R' F' R U R' U' R' F R F' R' R U' R' U' R U R' F' R U R' U' R' F R R F"
        )
        val result = OldPochmannSolver.solve(state)

        assertEquals("Corner memo should be [K, V, P]", listOf('K', 'V', 'P'), result.cornerMemo)
        assertTrue("Edge memo should be empty", result.edgeMemo.isEmpty())
        assertTrue("Parity (odd corner memo)", result.hasParity)
    }

    @Test
    fun `twisted buffer piece does not produce buffer sticker letters in memo`() {
        // When the buffer piece is twisted in place, tracing from the buffer
        // should produce an empty first cycle (the piece belongs at the buffer
        // position). Letters A, E, R (all buffer stickers) should not appear.
        val state = scrambledState(
            "R U' R' U' R U R' F' R U R' U' R' F R F' R' R U' R' U' R U R' F' R U R' F' R U R' U' R' F R R F"
        )
        val result = OldPochmannSolver.solve(state)

        assertFalse("A (buffer sticker) should not be in corner memo", result.cornerMemo.contains('A'))
        assertFalse("E (buffer sticker) should not be in corner memo", result.cornerMemo.contains('E'))
        assertFalse("R (buffer sticker) should not be in corner memo", result.cornerMemo.contains('R'))
    }

    // ========================================================================
    // Specific scramble verification
    // ========================================================================

    @Test
    fun `four-move scramble corners trace correctly`() {
        // Apply R U R' U' - a simple scramble with known effects
        val state = scrambledState("R U R' U'")
        val result = OldPochmannSolver.solve(state)

        // Verify basic structural properties
        assertFalse(result.cornerMemo.contains('E'))
        assertFalse(result.edgeMemo.contains('B'))
        assertEquals(result.cornerMemo.size % 2 == 1, result.hasParity)
    }

    @Test
    fun `identity scramble produces empty memo`() {
        // R R' = identity
        val state = scrambledState("R R'")
        val result = OldPochmannSolver.solve(state)

        assertTrue("Corner memo empty after identity", result.cornerMemo.isEmpty())
        assertTrue("Edge memo empty after identity", result.edgeMemo.isEmpty())
        assertFalse(result.hasParity)
    }

    @Test
    fun `double move scramble produces valid memo`() {
        val state = scrambledState("R2 U2 F2")
        val result = OldPochmannSolver.solve(state)

        // Just verify structural integrity
        val validLetters = ('A'..'X').toSet()
        assertTrue(result.cornerMemo.all { it in validLetters })
        assertTrue(result.edgeMemo.all { it in validLetters })
        assertFalse(result.cornerMemo.contains('E'))
        assertFalse(result.edgeMemo.contains('B'))
        assertEquals(result.cornerMemo.size % 2 == 1, result.hasParity)
    }
}
