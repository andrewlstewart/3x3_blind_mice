package com.example.threeblindcubers.domain.models

/**
 * Events emitted by the smart cube via Bluetooth
 */
sealed class CubeEvent {
    /**
     * A move was performed on the physical cube
     */
    data class MovePerformed(val move: Move) : CubeEvent()

    /**
     * Cube connected successfully
     */
    data object Connected : CubeEvent()

    /**
     * Cube disconnected
     */
    data object Disconnected : CubeEvent()

    /**
     * Connection error occurred
     */
    data class Error(val message: String) : CubeEvent()

    /**
     * Gyro/orientation data received from the cube (opcode 0xAB)
     *
     * Contains an absolute orientation quaternion in Q14 fixed-point format,
     * extracted from interleaved byte positions in the decrypted packet.
     * Divide each component by 16384.0 to get the real quaternion value (-1 to +1).
     * The norm w²+x²+y²+z² ≈ 16384² (verified < 0.01% error).
     *
     * Packet layout (20 bytes):
     *   Byte  0:      0xAB opcode
     *   Bytes 1-2:    counter/timestamp
     *   Bytes 3-4:    quaternion W (int16 LE)
     *   Bytes 5-6:    unknown (noisy sensor data)
     *   Bytes 7-8:    quaternion X (int16 LE)
     *   Bytes 9-10:   unknown (noisy sensor data)
     *   Bytes 11-12:  quaternion Y (int16 LE)
     *   Bytes 13-14:  unknown (noisy sensor data)
     *   Bytes 15-16:  quaternion Z (int16 LE)
     *   Bytes 17-19:  padding (0x00)
     *
     * @param quatW quaternion W component (Q14 fixed-point, divide by 16384)
     * @param quatX quaternion X component (Q14 fixed-point)
     * @param quatY quaternion Y component (Q14 fixed-point)
     * @param quatZ quaternion Z component (Q14 fixed-point)
     * @param rawBytes all decrypted bytes (including opcode) as unsigned ints for capture/analysis
     * @param rawData formatted text for debug display
     */
    data class GyroUpdated(
        val quatW: Int,
        val quatX: Int,
        val quatY: Int,
        val quatZ: Int,
        val rawBytes: List<Int>,
        val rawData: String
    ) : CubeEvent()
}
