package com.example.threeblindcubers.domain.models

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class AxisCalibrationTest {

    private val EPSILON = 0.0001f

    // ========================================================================
    // Identity remap
    // ========================================================================

    @Test
    fun `identity remap passes through unchanged`() {
        val cal = AxisCalibration.IDENTITY
        val result = cal.remapQuaternion(0.5f, 0.1f, 0.2f, 0.3f)
        assertArrayEquals(floatArrayOf(0.5f, 0.1f, 0.2f, 0.3f), result, EPSILON)
    }

    // ========================================================================
    // W is always preserved
    // ========================================================================

    @Test
    fun `w component passes through for all calibrations`() {
        val cal = AxisCalibration(
            xSourceAxis = 2, xSign = -1f,
            ySourceAxis = 0, ySign = 1f,
            zSourceAxis = 1, zSign = -1f
        )
        val result = cal.remapQuaternion(0.9f, 0.1f, 0.2f, 0.3f)
        assertEquals(0.9f, result[0], EPSILON)
    }

    // ========================================================================
    // X-Z swap
    // ========================================================================

    @Test
    fun `swap X and Z axes`() {
        // display X <- imu Z, display Z <- imu X
        val (zSrc, zSign) = AxisCalibration.deriveZAxis(
            xSourceAxis = 2, xSign = 1f,
            ySourceAxis = 1, ySign = 1f
        )
        val cal = AxisCalibration(
            xSourceAxis = 2, xSign = 1f,
            ySourceAxis = 1, ySign = 1f,
            zSourceAxis = zSrc, zSign = zSign
        )
        val result = cal.remapQuaternion(1f, 0.1f, 0.2f, 0.3f)
        // displayX = imuZ = 0.3, displayY = imuY = 0.2, displayZ = imuX (with sign)
        assertEquals(1f, result[0], EPSILON)
        assertEquals(0.3f, result[1], EPSILON)  // imuZ -> displayX
        assertEquals(0.2f, result[2], EPSILON)  // imuY -> displayY
        // Z derived: remaining axis is 0 (imuX), parity of (2,1,0) = -1, sign = -1*1*1 = -1
        assertEquals(-0.1f, result[3], EPSILON)  // -imuX -> displayZ
    }

    // ========================================================================
    // Sign negation
    // ========================================================================

    @Test
    fun `sign negation on X axis`() {
        val cal = AxisCalibration(
            xSourceAxis = 0, xSign = -1f,
            ySourceAxis = 1, ySign = 1f,
            zSourceAxis = 2, zSign = -1f  // derived: parity(0,1,2)=+1, sign = 1*(-1)*1 = -1
        )
        val result = cal.remapQuaternion(1f, 0.5f, 0.6f, 0.7f)
        assertEquals(-0.5f, result[1], EPSILON)
        assertEquals(0.6f, result[2], EPSILON)
        assertEquals(-0.7f, result[3], EPSILON)
    }

    @Test
    fun `sign negation on Y axis`() {
        val cal = AxisCalibration(
            xSourceAxis = 0, xSign = 1f,
            ySourceAxis = 1, ySign = -1f,
            zSourceAxis = 2, zSign = -1f  // derived: parity(0,1,2)=+1, sign = 1*1*(-1) = -1
        )
        val result = cal.remapQuaternion(1f, 0.5f, 0.6f, 0.7f)
        assertEquals(0.5f, result[1], EPSILON)
        assertEquals(-0.6f, result[2], EPSILON)
        assertEquals(-0.7f, result[3], EPSILON)
    }

    // ========================================================================
    // Norm preservation
    // ========================================================================

    @Test
    fun `remap preserves quaternion norm`() {
        val cal = AxisCalibration(
            xSourceAxis = 1, xSign = -1f,
            ySourceAxis = 2, ySign = 1f,
            zSourceAxis = 0, zSign = -1f
        )
        // Create a unit quaternion
        val norm = sqrt(0.5f * 0.5f + 0.3f * 0.3f + 0.4f * 0.4f + 0.6f * 0.6f)
        val w = 0.5f / norm
        val x = 0.3f / norm
        val y = 0.4f / norm
        val z = 0.6f / norm

        val result = cal.remapQuaternion(w, x, y, z)
        val resultNorm = sqrt(
            result[0] * result[0] + result[1] * result[1] +
                    result[2] * result[2] + result[3] * result[3]
        )
        assertEquals(1f, resultNorm, EPSILON)
    }

    // ========================================================================
    // All 6 axis permutations
    // ========================================================================

    @Test
    fun `permutation 012 (identity order)`() {
        val cal = AxisCalibration(0, 1f, 1, 1f, 2, 1f)
        val r = cal.remapQuaternion(1f, 0.1f, 0.2f, 0.3f)
        assertArrayEquals(floatArrayOf(1f, 0.1f, 0.2f, 0.3f), r, EPSILON)
    }

    @Test
    fun `permutation 021`() {
        val (zS, zSn) = AxisCalibration.deriveZAxis(0, 1f, 2, 1f)
        val cal = AxisCalibration(0, 1f, 2, 1f, zS, zSn)
        val r = cal.remapQuaternion(1f, 0.1f, 0.2f, 0.3f)
        assertEquals(1f, r[0], EPSILON)
        assertEquals(0.1f, r[1], EPSILON)   // imu0
        assertEquals(0.3f, r[2], EPSILON)   // imu2
        // z comes from imu1, parity of (0,2,1) = -1, sign = -1*1*1 = -1
        assertEquals(-0.2f, r[3], EPSILON)
    }

    @Test
    fun `permutation 102`() {
        val (zS, zSn) = AxisCalibration.deriveZAxis(1, 1f, 0, 1f)
        val cal = AxisCalibration(1, 1f, 0, 1f, zS, zSn)
        val r = cal.remapQuaternion(1f, 0.1f, 0.2f, 0.3f)
        assertEquals(1f, r[0], EPSILON)
        assertEquals(0.2f, r[1], EPSILON)   // imu1
        assertEquals(0.1f, r[2], EPSILON)   // imu0
        // z comes from imu2, parity of (1,0,2) = -1, sign = -1*1*1 = -1
        assertEquals(-0.3f, r[3], EPSILON)
    }

    @Test
    fun `permutation 120`() {
        val (zS, zSn) = AxisCalibration.deriveZAxis(1, 1f, 2, 1f)
        val cal = AxisCalibration(1, 1f, 2, 1f, zS, zSn)
        val r = cal.remapQuaternion(1f, 0.1f, 0.2f, 0.3f)
        assertEquals(1f, r[0], EPSILON)
        assertEquals(0.2f, r[1], EPSILON)   // imu1
        assertEquals(0.3f, r[2], EPSILON)   // imu2
        // z comes from imu0, parity of (1,2,0) = +1, sign = 1*1*1 = 1
        assertEquals(0.1f, r[3], EPSILON)
    }

    @Test
    fun `permutation 201`() {
        val (zS, zSn) = AxisCalibration.deriveZAxis(2, 1f, 0, 1f)
        val cal = AxisCalibration(2, 1f, 0, 1f, zS, zSn)
        val r = cal.remapQuaternion(1f, 0.1f, 0.2f, 0.3f)
        assertEquals(1f, r[0], EPSILON)
        assertEquals(0.3f, r[1], EPSILON)   // imu2
        assertEquals(0.1f, r[2], EPSILON)   // imu0
        // z comes from imu1, parity of (2,0,1) = +1, sign = 1*1*1 = 1
        assertEquals(0.2f, r[3], EPSILON)
    }

    @Test
    fun `permutation 210`() {
        val (zS, zSn) = AxisCalibration.deriveZAxis(2, 1f, 1, 1f)
        val cal = AxisCalibration(2, 1f, 1, 1f, zS, zSn)
        val r = cal.remapQuaternion(1f, 0.1f, 0.2f, 0.3f)
        assertEquals(1f, r[0], EPSILON)
        assertEquals(0.3f, r[1], EPSILON)   // imu2
        assertEquals(0.2f, r[2], EPSILON)   // imu1
        // z comes from imu0, parity of (2,1,0) = -1, sign = -1*1*1 = -1
        assertEquals(-0.1f, r[3], EPSILON)
    }
}
