package com.example.threeblindcubers.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisCalibrationDerivationTest {

    // ========================================================================
    // deriveZAxis: all valid (xSrc, ySrc) combinations with positive signs
    // ========================================================================

    @Test
    fun `derive Z from X=0 Y=1 gives Z=2 sign=+1 (even permutation 012)`() {
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(0, 1f, 1, 1f)
        assertEquals(2, zSrc)
        assertEquals(1f, zSign)
    }

    @Test
    fun `derive Z from X=1 Y=2 gives Z=0 sign=+1 (even permutation 120)`() {
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(1, 1f, 2, 1f)
        assertEquals(0, zSrc)
        assertEquals(1f, zSign)
    }

    @Test
    fun `derive Z from X=2 Y=0 gives Z=1 sign=+1 (even permutation 201)`() {
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(2, 1f, 0, 1f)
        assertEquals(1, zSrc)
        assertEquals(1f, zSign)
    }

    @Test
    fun `derive Z from X=0 Y=2 gives Z=1 sign=-1 (odd permutation 021)`() {
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(0, 1f, 2, 1f)
        assertEquals(1, zSrc)
        assertEquals(-1f, zSign)
    }

    @Test
    fun `derive Z from X=2 Y=1 gives Z=0 sign=-1 (odd permutation 210)`() {
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(2, 1f, 1, 1f)
        assertEquals(0, zSrc)
        assertEquals(-1f, zSign)
    }

    @Test
    fun `derive Z from X=1 Y=0 gives Z=2 sign=-1 (odd permutation 102)`() {
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(1, 1f, 0, 1f)
        assertEquals(2, zSrc)
        assertEquals(-1f, zSign)
    }

    // ========================================================================
    // deriveZAxis with negated signs
    // ========================================================================

    @Test
    fun `derive Z with xSign=-1 flips Z sign`() {
        // Base: (0,1) -> zSign = +1
        // With xSign = -1: zSign = +1 * (-1) * 1 = -1
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(0, -1f, 1, 1f)
        assertEquals(2, zSrc)
        assertEquals(-1f, zSign)
    }

    @Test
    fun `derive Z with ySign=-1 flips Z sign`() {
        // Base: (0,1) -> zSign = +1
        // With ySign = -1: zSign = +1 * 1 * (-1) = -1
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(0, 1f, 1, -1f)
        assertEquals(2, zSrc)
        assertEquals(-1f, zSign)
    }

    @Test
    fun `derive Z with both signs negative keeps Z sign`() {
        // Base: (0,1) -> zSign = +1
        // With xSign=-1, ySign=-1: zSign = +1 * (-1) * (-1) = +1
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(0, -1f, 1, -1f)
        assertEquals(2, zSrc)
        assertEquals(1f, zSign)
    }

    @Test
    fun `derive Z from odd permutation with xSign=-1`() {
        // Base: (0,2) -> parity = -1
        // With xSign = -1: zSign = (-1) * (-1) * 1 = +1
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(0, -1f, 2, 1f)
        assertEquals(1, zSrc)
        assertEquals(1f, zSign)
    }

    @Test
    fun `derive Z from odd permutation with both signs negative`() {
        // Base: (2,1) -> parity = -1
        // With xSign=-1, ySign=-1: zSign = (-1) * (-1) * (-1) = -1
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(2, -1f, 1, -1f)
        assertEquals(0, zSrc)
        assertEquals(-1f, zSign)
    }

    // ========================================================================
    // Right-hand rule verification: for identity mapping,
    // cross(displayX, displayY) should align with displayZ
    // ========================================================================

    @Test
    fun `right-hand rule holds for all even permutations with positive signs`() {
        // For even permutations, Z sign should be +1 with positive x,y signs
        val evenPerms = listOf(Pair(0, 1), Pair(1, 2), Pair(2, 0))
        for ((xSrc, ySrc) in evenPerms) {
            val (_, zSign) = AxisCalibration.deriveZAxis(xSrc, 1f, ySrc, 1f)
            assertEquals(
                "Even perm ($xSrc,$ySrc) should give zSign=+1",
                1f, zSign
            )
        }
    }

    @Test
    fun `right-hand rule holds for all odd permutations with positive signs`() {
        // For odd permutations, Z sign should be -1 with positive x,y signs
        val oddPerms = listOf(Pair(0, 2), Pair(2, 1), Pair(1, 0))
        for ((xSrc, ySrc) in oddPerms) {
            val (_, zSign) = AxisCalibration.deriveZAxis(xSrc, 1f, ySrc, 1f)
            assertEquals(
                "Odd perm ($xSrc,$ySrc) should give zSign=-1",
                -1f, zSign
            )
        }
    }
}
