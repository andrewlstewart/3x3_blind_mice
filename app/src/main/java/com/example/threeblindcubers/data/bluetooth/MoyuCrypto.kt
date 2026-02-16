/*
 * Copyright (C) 2025-2026 Andrew Stewart
 *
 * Derived from CSTimer's moyu32cube.js
 * Copyright (C) cs0x7f — https://github.com/cs0x7f/cstimer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.example.threeblindcubers.data.bluetooth

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Moyu MY32 Cube Encryption/Decryption
 * Ported from CSTimer's moyu32cube.js
 * Uses AES-128-ECB with MAC-derived key and IV
 *
 * The encrypt/decrypt scheme uses overlapping 16-byte blocks for a 20-byte payload:
 *   Block 1: bytes [0..15]
 *   Block 2: bytes [4..19]  (overlaps with block 1)
 */
class MoyuCrypto(macAddress: String) {
    private val TAG = "MoyuCrypto"

    // Decompressed key and IV from CSTimer (LZ-String decompressed)
    private val BASE_KEY = intArrayOf(
        21, 119, 58, 92, 103, 14, 45, 31, 23, 103, 42, 19, 155, 103, 82, 87
    )
    private val BASE_IV = intArrayOf(
        17, 35, 38, 37, 134, 42, 44, 59, 85, 6, 127, 49, 126, 103, 33, 87
    )

    private val key: ByteArray
    private val iv: ByteArray

    init {
        // Parse MAC address and derive key/IV
        val macBytes = parseMacAddress(macAddress)
        key = deriveKey(macBytes)
        iv = deriveIv(macBytes)

        Log.d(TAG, "Initialized with MAC: $macAddress")
        Log.d(TAG, "Key: ${key.joinToString(" ") { "%02x".format(it) }}")
        Log.d(TAG, "IV: ${iv.joinToString(" ") { "%02x".format(it) }}")
    }

    private fun parseMacAddress(mac: String): IntArray {
        // MAC format: "CF:30:16:02:26:9C"
        val parts = mac.split(":")
        require(parts.size == 6) { "Invalid MAC address format" }
        return parts.map { it.toInt(16) }.toIntArray()
    }

    private fun deriveKey(macBytes: IntArray): ByteArray {
        val derivedKey = BASE_KEY.copyOf()
        for (i in 0..5) {
            derivedKey[i] = (derivedKey[i] + macBytes[5 - i]) % 255
        }
        return derivedKey.map { it.toByte() }.toByteArray()
    }

    private fun deriveIv(macBytes: IntArray): ByteArray {
        val derivedIv = BASE_IV.copyOf()
        for (i in 0..5) {
            derivedIv[i] = (derivedIv[i] + macBytes[5 - i]) % 255
        }
        return derivedIv.map { it.toByte() }.toByteArray()
    }

    /**
     * Encrypt a 20-byte request packet.
     *
     * CSTimer scheme (lines 76-96):
     *   1. XOR bytes [0..15] with IV, AES-ECB encrypt, write back to [0..15]
     *   2. Copy bytes [4..19] (the overlapping tail), XOR with IV, AES-ECB encrypt, write back to [4..19]
     */
    fun encrypt(data: ByteArray): ByteArray {
        require(data.size == 20) { "Data must be 20 bytes" }

        val result = data.copyOf()
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")

        // Step 1: XOR bytes [0..15] with IV, then AES-ECB encrypt
        for (i in 0..15) {
            result[i] = (result[i].toInt() xor iv[i].toInt()).toByte()
        }
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val block1 = cipher.doFinal(result, 0, 16)
        System.arraycopy(block1, 0, result, 0, 16)

        // Step 2: Copy bytes [4..19], XOR with IV, AES-ECB encrypt, write back to [4..19]
        val block2 = result.copyOfRange(4, 20)
        for (i in 0..15) {
            block2[i] = (block2[i].toInt() xor iv[i].toInt()).toByte()
        }
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted2 = cipher.doFinal(block2)
        System.arraycopy(encrypted2, 0, result, 4, 16)

        return result
    }

    /**
     * Decrypt a 20-byte notification packet.
     *
     * CSTimer scheme (lines 53-74) — reverse of encrypt:
     *   1. Copy bytes [4..19], AES-ECB decrypt, XOR with IV, write back to [4..19]
     *   2. AES-ECB decrypt bytes [0..15], XOR with IV, write back to [0..15]
     */
    fun decrypt(data: ByteArray): ByteArray {
        require(data.size == 20) { "Data must be 20 bytes" }

        val result = data.copyOf()
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")

        // Step 1: Decrypt the overlapping tail block [4..19]
        val block2 = result.copyOfRange(4, 20)
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decrypted2 = cipher.doFinal(block2)
        for (i in 0..15) {
            result[i + 4] = (decrypted2[i].toInt() xor iv[i].toInt()).toByte()
        }

        // Step 2: Decrypt the first block [0..15]
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decrypted1 = cipher.doFinal(result, 0, 16)
        for (i in 0..15) {
            result[i] = (decrypted1[i].toInt() xor iv[i].toInt()).toByte()
        }

        return result
    }
}
