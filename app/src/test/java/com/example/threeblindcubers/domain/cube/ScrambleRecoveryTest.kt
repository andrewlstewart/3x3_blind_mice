package com.example.threeblindcubers.domain.cube

import com.example.threeblindcubers.domain.models.Face
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.Rotation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the smart scramble recovery system using the Kociemba solver.
 *
 * Tests both CubeStateTracker.toFaceletString() and
 * ScrambleGenerator.computeRemainingMoves().
 */
class ScrambleRecoveryTest {

    private lateinit var generator: ScrambleGenerator
    private lateinit var tracker: CubeStateTracker

    @Before
    fun setUp() {
        generator = ScrambleGenerator()
        generator.initialize()
        tracker = CubeStateTracker()
    }

    // ========================================================================
    // CubeStateTracker.toFaceletString() Tests
    // ========================================================================

    @Test
    fun `toFaceletString returns solved string for solved cube`() {
        val result = tracker.toFaceletString()
        assertEquals(54, result.length)
        assertEquals("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB", result)
    }

    @Test
    fun `toFaceletString changes after a move`() {
        val solved = tracker.toFaceletString()
        tracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        val after = tracker.toFaceletString()
        assertNotEquals(solved, after)
        assertEquals(54, after.length)
    }

    @Test
    fun `toFaceletString returns to solved after identity sequence`() {
        tracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        tracker.applyMove(Move(Face.R, Rotation.COUNTER_CLOCKWISE))
        assertEquals("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB", tracker.toFaceletString())
    }

    @Test
    fun `toFaceletString only contains valid characters`() {
        tracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        tracker.applyMove(Move(Face.U, Rotation.DOUBLE))
        tracker.applyMove(Move(Face.F, Rotation.COUNTER_CLOCKWISE))
        val result = tracker.toFaceletString()
        assertTrue(result.all { it in "URFDLB" })
    }

    @Test
    fun `toFaceletString has exactly 9 of each color`() {
        tracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        tracker.applyMove(Move(Face.U, Rotation.DOUBLE))
        tracker.applyMove(Move(Face.F, Rotation.COUNTER_CLOCKWISE))
        val result = tracker.toFaceletString()
        for (c in "URFDLB") {
            assertEquals("Expected 9 of '$c'", 9, result.count { it == c })
        }
    }

    // ========================================================================
    // computeRemainingMoves() Tests
    // ========================================================================

    @Test
    fun `identity - current equals target returns empty list`() {
        val state = tracker.toFaceletString()
        val result = generator.computeRemainingMoves(state, state)
        assertTrue("Should return empty list when already at target", result.isEmpty())
    }

    @Test
    fun `commutative - U2 D2 vs D2 U2 returns empty list`() {
        // Apply U2 D2 to get target state
        val targetTracker = CubeStateTracker()
        targetTracker.applyMove(Move(Face.U, Rotation.DOUBLE))
        targetTracker.applyMove(Move(Face.D, Rotation.DOUBLE))
        val target = targetTracker.toFaceletString()

        // Apply D2 U2 to get current state (commutative moves, same result)
        val currentTracker = CubeStateTracker()
        currentTracker.applyMove(Move(Face.D, Rotation.DOUBLE))
        currentTracker.applyMove(Move(Face.U, Rotation.DOUBLE))
        val current = currentTracker.toFaceletString()

        // Both should be identical
        assertEquals("Commutative moves should produce same state", target, current)

        val result = generator.computeRemainingMoves(current, target)
        assertTrue("Should return empty list for commutative equivalent states", result.isEmpty())
    }

    @Test
    fun `one move short - recovery returns that move`() {
        // Target state: R U
        val targetTracker = CubeStateTracker()
        targetTracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        targetTracker.applyMove(Move(Face.U, Rotation.CLOCKWISE))
        val target = targetTracker.toFaceletString()

        // Current state: R (one move short)
        val currentTracker = CubeStateTracker()
        currentTracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        val current = currentTracker.toFaceletString()

        val result = generator.computeRemainingMoves(current, target)
        assertFalse("Should return moves when not at target", result.isEmpty())

        // Apply recovery moves and verify we reach target
        for (move in result) {
            currentTracker.applyMove(move)
        }
        assertEquals("After recovery, should match target", target, currentTracker.toFaceletString())
    }

    @Test
    fun `wrong face entirely - recovery produces valid path`() {
        // Target state: R U F
        val targetTracker = CubeStateTracker()
        for (move in Move.parseSequence("R U F")) {
            targetTracker.applyMove(move)
        }
        val target = targetTracker.toFaceletString()

        // Current state: user did L instead of R (wrong face from the start)
        val currentTracker = CubeStateTracker()
        currentTracker.applyMove(Move(Face.L, Rotation.CLOCKWISE))
        val current = currentTracker.toFaceletString()

        val result = generator.computeRemainingMoves(current, target)
        assertFalse("Should return recovery moves", result.isEmpty())

        // Apply recovery and verify
        for (move in result) {
            currentTracker.applyMove(move)
        }
        assertEquals("After recovery, should match target", target, currentTracker.toFaceletString())
    }

    @Test
    fun `round trip - partial scramble plus recovery matches target`() {
        // Generate a scramble
        val scramble = Move.parseSequence("R U R' F2 D L' B2 U2 F D'")

        // Compute target state
        val targetTracker = CubeStateTracker()
        for (move in scramble) {
            targetTracker.applyMove(move)
        }
        val target = targetTracker.toFaceletString()

        // Do partial scramble (first 5 moves)
        val currentTracker = CubeStateTracker()
        for (move in scramble.take(5)) {
            currentTracker.applyMove(move)
        }
        val current = currentTracker.toFaceletString()

        val recovery = generator.computeRemainingMoves(current, target)

        // Apply recovery moves
        for (move in recovery) {
            currentTracker.applyMove(move)
        }
        assertEquals("After partial scramble + recovery, should match target",
            target, currentTracker.toFaceletString())
    }

    @Test
    fun `recovery after divergence produces valid path`() {
        // Target: R U F D
        val scramble = Move.parseSequence("R U F D")
        val targetTracker = CubeStateTracker()
        for (move in scramble) {
            targetTracker.applyMove(move)
        }
        val target = targetTracker.toFaceletString()

        // User did: R U' (wrong second move)
        val currentTracker = CubeStateTracker()
        currentTracker.applyMove(Move(Face.R, Rotation.CLOCKWISE))
        currentTracker.applyMove(Move(Face.U, Rotation.COUNTER_CLOCKWISE)) // wrong!
        val current = currentTracker.toFaceletString()

        val recovery = generator.computeRemainingMoves(current, target)
        assertFalse("Should have recovery moves", recovery.isEmpty())

        // Apply recovery and verify
        for (move in recovery) {
            currentTracker.applyMove(move)
        }
        assertEquals("After recovery, should match target", target, currentTracker.toFaceletString())
    }

    @Test
    fun `recovery from completely scrambled state`() {
        // Target: a specific scramble
        val targetTracker = CubeStateTracker()
        for (move in Move.parseSequence("R2 U' F D2 L B'")) {
            targetTracker.applyMove(move)
        }
        val target = targetTracker.toFaceletString()

        // Current: a totally different scramble
        val currentTracker = CubeStateTracker()
        for (move in Move.parseSequence("F' D R' U2 B L")) {
            currentTracker.applyMove(move)
        }
        val current = currentTracker.toFaceletString()

        val recovery = generator.computeRemainingMoves(current, target)
        assertFalse("Should have recovery moves", recovery.isEmpty())

        // Apply recovery and verify
        for (move in recovery) {
            currentTracker.applyMove(move)
        }
        assertEquals("After recovery, should match target", target, currentTracker.toFaceletString())
    }

    @Test
    fun `recovery from solved to target`() {
        // Current: solved
        val current = tracker.toFaceletString()

        // Target: some scramble
        val targetTracker = CubeStateTracker()
        for (move in Move.parseSequence("R U R' U'")) {
            targetTracker.applyMove(move)
        }
        val target = targetTracker.toFaceletString()

        val recovery = generator.computeRemainingMoves(current, target)
        assertFalse("Should have recovery moves from solved to scrambled", recovery.isEmpty())

        // Apply and verify
        for (move in recovery) {
            tracker.applyMove(move)
        }
        assertEquals("After recovery, should match target", target, tracker.toFaceletString())
    }

    @Test
    fun `recovery moves are reasonable length`() {
        // Target: a long scramble
        val targetTracker = CubeStateTracker()
        for (move in Move.parseSequence("R U R' F2 D L' B2 U2 F D' R2 B U'")) {
            targetTracker.applyMove(move)
        }
        val target = targetTracker.toFaceletString()

        // Current: user made 3 wrong moves
        val currentTracker = CubeStateTracker()
        for (move in Move.parseSequence("L' D F")) {
            currentTracker.applyMove(move)
        }
        val current = currentTracker.toFaceletString()

        val recovery = generator.computeRemainingMoves(current, target)
        // Kociemba solver should find solutions ≤ 21 moves
        assertTrue("Recovery should be at most 21 moves, was ${recovery.size}",
            recovery.size <= 21)
    }
}
