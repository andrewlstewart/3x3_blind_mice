package com.example.threeblindcubers.domain.models

/**
 * Represents a signed permutation mapping from IMU axes to display (OpenGL) axes.
 *
 * Each display axis (X, Y, Z) sources from one IMU axis (0=imuX, 1=imuY, 2=imuZ),
 * possibly negated. The remap is applied to the vector part (x, y, z) of a quaternion;
 * the scalar w passes through unchanged.
 *
 * Example: if the IMU's Z axis corresponds to the display's X axis (negated),
 * then xSourceAxis=2, xSign=-1f.
 */
data class AxisCalibration(
    val xSourceAxis: Int,  // 0=imuX, 1=imuY, 2=imuZ -> display X
    val xSign: Float,      // +1f or -1f
    val ySourceAxis: Int,  // -> display Y
    val ySign: Float,
    val zSourceAxis: Int,  // -> display Z (derived from X and Y)
    val zSign: Float
) {
    init {
        require(xSourceAxis in 0..2) { "xSourceAxis must be 0, 1, or 2" }
        require(ySourceAxis in 0..2) { "ySourceAxis must be 0, 1, or 2" }
        require(zSourceAxis in 0..2) { "zSourceAxis must be 0, 1, or 2" }
        require(xSign == 1f || xSign == -1f) { "xSign must be +1f or -1f" }
        require(ySign == 1f || ySign == -1f) { "ySign must be +1f or -1f" }
        require(zSign == 1f || zSign == -1f) { "zSign must be +1f or -1f" }
        require(xSourceAxis != ySourceAxis && ySourceAxis != zSourceAxis && xSourceAxis != zSourceAxis) {
            "All source axes must be distinct"
        }
    }

    /**
     * Applies the axis permutation to a quaternion's vector part.
     * The scalar w is passed through unchanged.
     *
     * @return FloatArray of [w, displayX, displayY, displayZ]
     */
    fun remapQuaternion(w: Float, x: Float, y: Float, z: Float): FloatArray {
        val imu = floatArrayOf(x, y, z)
        return floatArrayOf(
            w,
            xSign * imu[xSourceAxis],
            ySign * imu[ySourceAxis],
            zSign * imu[zSourceAxis]
        )
    }

    companion object {
        /** Identity calibration: no remapping (pass-through). */
        val IDENTITY = AxisCalibration(
            xSourceAxis = 0, xSign = 1f,
            ySourceAxis = 1, ySign = 1f,
            zSourceAxis = 2, zSign = 1f
        )

        /**
         * Derives the Z axis mapping from X and Y using the right-hand rule
         * (Levi-Civita symbol / cross product of the permutation).
         *
         * Given that display X sources from IMU axis [xSrc] and display Y from [ySrc],
         * the Z source axis is the remaining axis, and its sign is determined by the
         * parity of the permutation multiplied by xSign * ySign.
         *
         * @return Triple of (zSourceAxis, zSign)
         */
        fun deriveZAxis(
            xSourceAxis: Int, xSign: Float,
            ySourceAxis: Int, ySign: Float
        ): Pair<Int, Float> {
            require(xSourceAxis in 0..2 && ySourceAxis in 0..2 && xSourceAxis != ySourceAxis)

            // The remaining axis (0+1+2=3, so remaining = 3 - x - y)
            val zSourceAxis = 3 - xSourceAxis - ySourceAxis

            // Levi-Civita: epsilon(xSrc, ySrc, zSrc)
            // Even permutations (012, 120, 201) -> +1
            // Odd permutations (021, 210, 102) -> -1
            val parity = leviCivita(xSourceAxis, ySourceAxis, zSourceAxis)

            // zSign ensures right-handedness: parity * xSign * ySign
            val zSign = parity * xSign * ySign

            return Pair(zSourceAxis, zSign)
        }

        /**
         * Computes the Levi-Civita symbol for indices i, j, k in {0, 1, 2}.
         * Returns +1f for even permutations, -1f for odd, 0f if any repeat.
         */
        private fun leviCivita(i: Int, j: Int, k: Int): Float {
            if (i == j || j == k || i == k) return 0f
            // (j - i) * (k - i) * (k - j) gives:
            //  (0,1,2)->2, (1,2,0)->2, (2,0,1)->2  -> positive
            //  (0,2,1)->-2, (2,1,0)->-2, (1,0,2)->-2  -> negative
            val product = (j - i) * (k - i) * (k - j)
            return if (product > 0) 1f else -1f
        }
    }
}
