/*
 * Copyright (C) 2025-2026 Andrew Stewart
 *
 * BLE protocol handling derived from CSTimer's moyu32cube.js
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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.domain.models.CubeEvent
import com.example.threeblindcubers.domain.models.Face
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.Rotation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MoyuCubeService for Bluetooth LE communication with Moyu V10 cube
 *
 * TODO: This implementation needs the Moyu V10 Bluetooth protocol details:
 * - Service UUID
 * - Characteristic UUIDs for move data
 * - Data packet format for moves
 *
 * Research needed:
 * 1. Use nRF Connect app to scan the Moyu V10 and identify GATT services/characteristics
 * 2. Search GitHub for "moyu bluetooth" or "moyu v10 protocol"
 * 3. Check SpeedSolving.com forums for protocol documentation
 */
@Singleton
class MoyuCubeServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MoyuCubeService {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    private var cubeModel: CubeModel? = null
    private var moyuCrypto: MoyuCrypto? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    /** Previous move counter for detecting new moves (8-bit wrapping) */
    @Volatile
    private var prevMoveCnt: Int = -1

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    // SharedFlow with buffer so rapid successive moves aren't dropped by conflation
    private val _cubeEvents = MutableSharedFlow<CubeEvent>(
        replay = 1,
        extraBufferCapacity = 16
    )
    override val cubeEvents: SharedFlow<CubeEvent> = _cubeEvents

    private var isCurrentlyScanning = false
    private var currentScanCallback: ScanCallback? = null

    companion object {
        private const val TAG = "MoyuCubeService"

        // Moyu V10 (MHC prefix) UUIDs
        private const val MOYU_V10_SERVICE_UUID = "00001000-0000-1000-8000-00805f9b34fb"
        private const val MOYU_V10_CHRCT_UUID_WRITE = "00001001-0000-1000-8000-00805f9b34fb"
        private const val MOYU_V10_CHRCT_UUID_READ = "00001002-0000-1000-8000-00805f9b34fb"
        private const val MOYU_V10_CHRCT_UUID_TURN = "00001003-0000-1000-8000-00805f9b34fb"
        private const val MOYU_V10_CHRCT_UUID_GYRO = "00001004-0000-1000-8000-00805f9b34fb"

        // Moyu MY32 (WCU_MY3 prefix) UUIDs
        private const val MOYU_MY32_SERVICE_UUID = "0783b03e-7735-b5a0-1760-a305d2795cb0"
        private const val MOYU_MY32_CHRCT_UUID_READ = "0783b03e-7735-b5a0-1760-a305d2795cb1"
        private const val MOYU_MY32_CHRCT_UUID_WRITE = "0783b03e-7735-b5a0-1760-a305d2795cb2"

        // Face mapping: [3, 4, 5, 1, 2, 0] maps to URFDLB
        private val FACE_MAP = arrayOf(3, 4, 5, 1, 2, 0)
        private const val FACES = "URFDLB"

        // MY32 move parsing: face order from CSTimer "FBUDLR"
        private const val MY32_FACES = "FBUDLR"
    }

    private enum class CubeModel {
        V10,    // MHC prefix - older model
        MY32    // WCU_MY3 prefix - newer model with encryption
    }

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<BluetoothDevice> = callbackFlow {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter not available")
            close()
            return@callbackFlow
        }

        if (!BluetoothPermissionHelper.hasScanPermission(context)) {
            Log.e(TAG, "Bluetooth scan permission not granted")
            close()
            return@callbackFlow
        }

        _connectionState.value = ConnectionState.SCANNING
        isCurrentlyScanning = true

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name

                // Filter for Moyu cubes - MHC prefix for V10, WCU_MY3 for newer models
                if (deviceName != null &&
                    (deviceName.startsWith("MHC") ||
                     deviceName.startsWith("WCU_MY3") ||
                     deviceName.contains("Moyu", ignoreCase = true))) {
                    Log.d(TAG, "Found Moyu cube: $deviceName (${device.address})")
                    trySend(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _connectionState.value = ConnectionState.ERROR
                close()
            }
        }

        currentScanCallback = scanCallback

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothAdapter.bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)

        awaitClose {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            isCurrentlyScanning = false
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice) {
        if (!BluetoothPermissionHelper.hasConnectPermission(context)) {
            Log.e(TAG, "Bluetooth connect permission not granted")
            _connectionState.value = ConnectionState.ERROR
            _cubeEvents.emit(CubeEvent.Error("Bluetooth permission not granted"))
            return
        }

        disconnect() // Disconnect any existing connection

        _connectionState.value = ConnectionState.CONNECTING
        connectedDevice = device
        prevMoveCnt = -1  // Reset move counter on new connection

        // Detect cube model from device name
        cubeModel = when {
            device.name?.startsWith("MHC") == true -> CubeModel.V10
            device.name?.startsWith("WCU_MY3") == true -> CubeModel.MY32
            else -> {
                Log.w(TAG, "Unknown cube model: ${device.name}")
                CubeModel.MY32 // Default to MY32 for newer cubes
            }
        }
        Log.i(TAG, "Detected cube model: $cubeModel")

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "Connected to GATT server")
                        _connectionState.value = ConnectionState.CONNECTED
                        _cubeEvents.tryEmit(CubeEvent.Connected)
                        // Discover services to find the Moyu characteristics
                        Log.d(TAG, "Calling discoverServices()...")
                        _cubeEvents.tryEmit(CubeEvent.Error("🔍 Discovering services..."))
                        val discoverResult = gatt.discoverServices()
                        Log.d(TAG, "discoverServices() returned: $discoverResult")
                        _cubeEvents.tryEmit(CubeEvent.Error("🔍 discoverServices: $discoverResult"))
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "Disconnected from GATT server")
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _cubeEvents.tryEmit(CubeEvent.Disconnected)
                        bluetoothGatt?.close()
                        bluetoothGatt = null
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.i(TAG, "onServicesDiscovered called with status=$status")
                _cubeEvents.tryEmit(CubeEvent.Error("📡 onServicesDiscovered: status=$status"))

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Services discovered successfully")
                    _cubeEvents.tryEmit(CubeEvent.Error("✅ Services discovered, setting up..."))
                    setupNotifications(gatt)
                } else {
                    Log.w(TAG, "onServicesDiscovered received error: $status")
                    _cubeEvents.tryEmit(CubeEvent.Error("❌ Service discovery failed: $status"))
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.d(TAG, "onDescriptorWrite: status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "✅ Notifications enabled!")
                    _cubeEvents.tryEmit(CubeEvent.Error("✅ Notifications ON! Sending requests..."))

                    // Send initialization requests like CSTimer (lines 228-232)
                    // Opcode 161 (0xA1) = request cube info
                    // Opcode 163 (0xA3) = request cube status
                    // Opcode 164 (0xA4) = request battery level
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        sendMY32Request(161) // 0xA1 - cube info
                        _cubeEvents.tryEmit(CubeEvent.Error("📤 Sent 0xA1 (info)"))
                    }, 100)

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        sendMY32Request(163) // 0xA3 - cube status
                        _cubeEvents.tryEmit(CubeEvent.Error("📤 Sent 0xA3 (status)"))
                    }, 300)

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        sendMY32Request(164) // 0xA4 - battery level
                        _cubeEvents.tryEmit(CubeEvent.Error("📤 Sent 0xA4 (battery). Turn cube!"))
                    }, 500)
                } else {
                    Log.e(TAG, "❌ Failed to write descriptor: status=$status")
                    _cubeEvents.tryEmit(CubeEvent.Error("❌ Notification setup failed"))
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                // Log all data received from the cube
                val hexData = value.joinToString(" ") { "%02x".format(it) }
                Log.i(TAG, "🎲 DATA RECEIVED: ${value.size} bytes")
                Log.d(TAG, "Encrypted: $hexData")

                // Decrypt and parse
                parseMoveData(value)
            }
        }

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private fun setupNotifications(gatt: BluetoothGatt) {
        when (cubeModel) {
            CubeModel.V10 -> setupV10Notifications(gatt)
            CubeModel.MY32 -> setupMY32Notifications(gatt)
            null -> {
                Log.e(TAG, "Cube model not detected")
                _connectionState.value = ConnectionState.ERROR
                _cubeEvents.tryEmit(CubeEvent.Error("Cube model not detected"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupV10Notifications(gatt: BluetoothGatt) {
        val service = gatt.getService(java.util.UUID.fromString(MOYU_V10_SERVICE_UUID))
        if (service == null) {
            Log.e(TAG, "Moyu V10 service not found")
            _connectionState.value = ConnectionState.ERROR
            _cubeEvents.tryEmit(CubeEvent.Error("Moyu V10 service not found"))
            return
        }

        // Get the turn characteristic for move notifications
        val turnCharacteristic = service.getCharacteristic(
            java.util.UUID.fromString(MOYU_V10_CHRCT_UUID_TURN)
        )

        if (turnCharacteristic == null) {
            Log.e(TAG, "Turn characteristic not found")
            _connectionState.value = ConnectionState.ERROR
            _cubeEvents.tryEmit(CubeEvent.Error("Turn characteristic not found"))
            return
        }

        // Enable notifications
        gatt.setCharacteristicNotification(turnCharacteristic, true)

        // Enable notifications on the device side
        val descriptor = turnCharacteristic.getDescriptor(
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Client Characteristic Configuration
        )

        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Log.d(TAG, "V10 notifications enabled on turn characteristic")
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupMY32Notifications(gatt: BluetoothGatt) {
        Log.d(TAG, "Setting up MY32 notifications...")
        _cubeEvents.tryEmit(CubeEvent.Error("🔧 [v1.1.0 Fixed Protocol] Setting up..."))

        // Initialize encryption using the real BLE MAC address from Android
        val macAddress = connectedDevice?.address
        if (macAddress == null) {
            Log.e(TAG, "Cannot get device MAC address")
            _cubeEvents.tryEmit(CubeEvent.Error("❌ Cannot get device MAC address"))
            return
        }

        try {
            moyuCrypto = MoyuCrypto(macAddress)
            Log.d(TAG, "✅ Crypto initialized with MAC: $macAddress")
            _cubeEvents.tryEmit(CubeEvent.Error("🔐 Crypto init: $macAddress"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize crypto", e)
            _cubeEvents.tryEmit(CubeEvent.Error("❌ Crypto init failed: ${e.message}"))
            return
        }

        val service = gatt.getService(java.util.UUID.fromString(MOYU_MY32_SERVICE_UUID))
        if (service == null) {
            Log.e(TAG, "Moyu MY32 service not found")
            _connectionState.value = ConnectionState.ERROR
            _cubeEvents.tryEmit(CubeEvent.Error("❌ MY32 service not found"))
            return
        }
        Log.d(TAG, "✅ Found MY32 service")
        _cubeEvents.tryEmit(CubeEvent.Error("✅ Service found"))

        // Get read and write characteristics
        val readCharacteristic = service.getCharacteristic(
            java.util.UUID.fromString(MOYU_MY32_CHRCT_UUID_READ)
        )
        val writeChar = service.getCharacteristic(
            java.util.UUID.fromString(MOYU_MY32_CHRCT_UUID_WRITE)
        )

        if (readCharacteristic == null || writeChar == null) {
            Log.e(TAG, "Required characteristics not found")
            _cubeEvents.tryEmit(CubeEvent.Error("❌ Characteristics not found"))
            return
        }

        writeCharacteristic = writeChar
        Log.d(TAG, "✅ Found characteristics")
        _cubeEvents.tryEmit(CubeEvent.Error("✅ Found characteristics"))

        // Enable notifications
        gatt.setCharacteristicNotification(readCharacteristic, true)

        // Enable CCCD
        val descriptor = readCharacteristic.getDescriptor(
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )

        if (descriptor == null) {
            Log.e(TAG, "❌ CCCD descriptor not found!")
            _cubeEvents.tryEmit(CubeEvent.Error("❌ CCCD not found"))
            return
        }

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
        Log.d(TAG, "✅ CCCD write initiated")
        _cubeEvents.tryEmit(CubeEvent.Error("✅ Enabling notifications..."))
    }

    /**
     * Send encrypted request to MY32 cube
     * Based on CSTimer sendRequest() and sendSimpleRequest()
     */
    @SuppressLint("MissingPermission")
    private fun sendMY32Request(opcode: Int) {
        val crypto = moyuCrypto
        val writeChar = writeCharacteristic

        if (crypto == null || writeChar == null) {
            Log.w(TAG, "Cannot send request: crypto=$crypto, writeChar=$writeChar")
            return
        }

        // Create 20-byte request (CSTimer line 110-112)
        val request = ByteArray(20) { 0 }
        request[0] = opcode.toByte()

        // Encrypt the request (CSTimer line 104)
        val encrypted = crypto.encrypt(request)

        Log.d(TAG, "Sending request opcode=$opcode (0x${opcode.toString(16)})")
        Log.d(TAG, "  Plain: ${request.joinToString(" ") { "%02x".format(it) }}")
        Log.d(TAG, "  Encrypted: ${encrypted.joinToString(" ") { "%02x".format(it) }}")

        writeChar.value = encrypted
        bluetoothGatt?.writeCharacteristic(writeChar)
    }

    /**
     * Parses move data from the Bluetooth characteristic value
     * Supports both V10 and MY32 protocols
     */
    private fun parseMoveData(data: ByteArray) {
        when (cubeModel) {
            CubeModel.V10 -> {
                val move = parseV10MoveData(data)
                if (move != null) {
                    _cubeEvents.tryEmit(CubeEvent.MovePerformed(move))
                }
            }
            CubeModel.MY32 -> parseMY32MoveData(data)
            null -> {}
        }
    }

    /**
     * Parses V10 move data
     * Protocol format:
     * - Byte 0: Number of moves in this packet
     * - Each move is 6 bytes:
     *   - Bytes 0-3: Timestamp (reordered as [1][0][3][2])
     *   - Byte 4: Face index (0-5)
     *   - Byte 5: Direction (0-255, divided by 36 to get rotation)
     */
    private fun parseV10MoveData(data: ByteArray): Move? {
        if (data.isEmpty()) {
            return null
        }

        val nMoves = data[0].toInt() and 0xFF
        Log.d(TAG, "Received V10 move packet with $nMoves moves")

        if (data.size < 1 + nMoves * 6) {
            Log.w(TAG, "Incomplete move data: expected ${1 + nMoves * 6} bytes, got ${data.size}")
            return null
        }

        // Parse the first move
        if (nMoves > 0) {
            val offset = 1

            val timestamp = ((data[offset + 1].toInt() and 0xFF) shl 24) or
                          ((data[offset + 0].toInt() and 0xFF) shl 16) or
                          ((data[offset + 3].toInt() and 0xFF) shl 8) or
                          (data[offset + 2].toInt() and 0xFF)

            val faceIndex = data[offset + 4].toInt() and 0xFF
            val dirValue = data[offset + 5].toInt() and 0xFF
            val dir = Math.round(dirValue.toFloat() / 36f)

            Log.d(TAG, "V10 Move: face=$faceIndex dir=$dir dirValue=$dirValue timestamp=$timestamp")

            if (faceIndex in 0..5) {
                val axis = FACE_MAP[faceIndex]
                val face = parseFace(axis)

                val rotation = when {
                    dir <= 1 -> Rotation.CLOCKWISE
                    dir >= 5 -> Rotation.COUNTER_CLOCKWISE
                    else -> Rotation.DOUBLE
                }

                return face?.let { Move(it, rotation) }
            }
        }

        return null
    }

    /**
     * Parses MY32 move data with AES decryption
     * Based on CSTimer moyu32cube.js parseData() (line 258-312)
     *
     * Opcode 165 (0xA5) move packet format:
     *   20 decrypted bytes → 160 bits
     *   Bits 88-95: move counter (8-bit unsigned, wrapping)
     *   Moves packed at 5 bits each starting at bit 96
     *   Each 5-bit value m: face = m >> 1 (index into "FBUDLR"), dir = m & 1 (0=CW, 1=CCW)
     */
    private fun parseMY32MoveData(data: ByteArray) {
        val crypto = moyuCrypto
        if (crypto == null) {
            Log.w(TAG, "No crypto available for decryption")
            return
        }

        // Decrypt the data
        val decrypted = try {
            crypto.decrypt(data)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            _cubeEvents.tryEmit(CubeEvent.Error("❌ Decrypt failed: ${e.message}"))
            return
        }

        // Log decrypted data
        val hexDecrypted = decrypted.joinToString(" ") { "%02x".format(it) }
        Log.d(TAG, "Decrypted: $hexDecrypted")

        // Parse message type (first byte)
        val msgType = decrypted[0].toInt() and 0xFF

        when (msgType) {
            161 -> { // 0xA1 - cube info
                Log.i(TAG, "📋 Received cube info")
                _cubeEvents.tryEmit(CubeEvent.Error("📋 Got cube info (0xA1)"))
            }
            163 -> { // 0xA3 - cube status
                Log.i(TAG, "📊 Received cube status")
                _cubeEvents.tryEmit(CubeEvent.Error("📊 Got status (0xA3)"))
            }
            164 -> { // 0xA4 - battery level
                val battery = decrypted[1].toInt() and 0xFF
                Log.i(TAG, "🔋 Battery level: $battery%")
                _cubeEvents.tryEmit(CubeEvent.Error("🔋 Battery: $battery%"))
            }
            165 -> { // 0xA5 - move data!
                Log.i(TAG, "🎲 MOVE DATA RECEIVED!")
                parseMY32Moves(decrypted)
            }
            171 -> { // 0xAB - gyro/orientation data
                parseMY32Gyro(decrypted)
            }
            else -> {
                Log.d(TAG, "Unknown message type: $msgType")
                _cubeEvents.tryEmit(CubeEvent.Error("❓ Unknown type: $msgType"))
            }
        }
    }

    /**
     * Parse individual moves from an opcode 165 (0xA5) decrypted packet.
     *
     * CSTimer moyu32cube.js parseData() lines 286-312:
     *   - Convert 20 bytes to 160-bit binary string
     *   - Bits 88-95 = move counter (8-bit)
     *   - Moves are packed at 5 bits each starting at bit 96
     *   - Each 5-bit move value: m >> 1 = face index (0-5), m & 1 = direction
     *   - Face order: "FBUDLR" (index 0-5)
     *   - Direction: 0 = clockwise (" "), 1 = counter-clockwise ("'")
     */
    private fun parseMY32Moves(decrypted: ByteArray) {
        // Convert 20 bytes to 160-bit binary string
        val bits = buildString {
            for (b in decrypted) {
                val v = b.toInt() and 0xFF
                for (bit in 7 downTo 0) {
                    append((v shr bit) and 1)
                }
            }
        }

        // Extract move counter from bits 88-95
        val moveCntStr = bits.substring(88, 96)
        val moveCnt = moveCntStr.toInt(2)

        Log.d(TAG, "Move counter: $moveCnt, prev: $prevMoveCnt")

        val moveDiff: Int

        if (prevMoveCnt == -1) {
            // First packet after connect — the user's first move triggered this notification
            Log.d(TAG, "First move packet: counter=$moveCnt")
            _cubeEvents.tryEmit(CubeEvent.Error("🎲 Ready! (counter=$moveCnt)"))
            moveDiff = 1
        } else {
            // Calculate how many new moves arrived (8-bit wrapping)
            val rawDiff = (moveCnt - prevMoveCnt + 256) % 256
            if (rawDiff == 0) {
                Log.d(TAG, "No new moves (counter unchanged)")
                return
            }
            moveDiff = if (rawDiff > 5) {
                Log.w(TAG, "Move diff $rawDiff > 5, clamping (missed packets?)")
                5
            } else rawDiff
        }

        prevMoveCnt = moveCnt

        Log.d(TAG, "Parsing $moveDiff new move(s)")

        // Parse each new move — most recent moves are at the beginning of the data
        // CSTimer line 309: var m = parseInt(value.slice(96 + i * 5, 101 + i * 5), 2)
        // CSTimer line 310: prevMoves[i] = "FBUDLR".charAt(m >> 1) + " '".charAt(m & 1)
        for (i in (moveDiff - 1) downTo 0) {
            val bitOffset = 96 + i * 5
            if (bitOffset + 5 > bits.length) {
                Log.w(TAG, "Move $i: bit offset $bitOffset out of range")
                continue
            }

            val moveBits = bits.substring(bitOffset, bitOffset + 5)
            val moveValue = moveBits.toInt(2)

            // CSTimer: m >> 1 = face index (0-5), m & 1 = direction
            val faceIdx = moveValue shr 1
            val direction = moveValue and 1

            // Validate: only face indices 0-5 are valid (6 faces)
            if (faceIdx > 5) {
                Log.w(TAG, "Move $i: invalid face index $faceIdx (moveValue=$moveValue)")
                continue
            }

            // Map face index to Face enum using "FBUDLR" order
            val faceChar = MY32_FACES[faceIdx]
            val face = when (faceChar) {
                'F' -> Face.F
                'B' -> Face.B
                'U' -> Face.U
                'D' -> Face.D
                'L' -> Face.L
                'R' -> Face.R
                else -> {
                    Log.w(TAG, "Move $i: unexpected face char '$faceChar'")
                    continue
                }
            }

            // Direction: 0 = clockwise, 1 = counter-clockwise
            val rotation = if (direction == 0) Rotation.CLOCKWISE else Rotation.COUNTER_CLOCKWISE

            val move = Move(face, rotation)
            Log.i(TAG, "🎲 Move: ${move.toNotation()} (face=$faceChar, dir=$direction)")

            _cubeEvents.tryEmit(CubeEvent.MovePerformed(move))
        }
    }

    /**
     * Parse gyro/orientation data from opcode 171 (0xAB) packet.
     *
     * Contains an absolute orientation quaternion in Q14 fixed-point format,
     * interleaved with unknown noisy sensor data at alternate 2-byte positions.
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
     * Divide each component by 16384.0 to get the real quaternion value (-1 to +1).
     * The norm w²+x²+y²+z² ≈ 16384² (verified < 0.01% error).
     */
    private fun parseMY32Gyro(decrypted: ByteArray) {
        if (decrypted.size < 17) return

        // Extract quaternion components as int16 LE from interleaved positions
        fun int16LE(lo: Int, hi: Int): Int {
            val value = (decrypted[lo].toInt() and 0xFF) or
                    ((decrypted[hi].toInt() and 0xFF) shl 8)
            // Sign-extend from 16 bits
            return if (value >= 0x8000) value - 0x10000 else value
        }

        val quatW = int16LE(3, 4)
        val quatX = int16LE(7, 8)
        val quatY = int16LE(11, 12)
        val quatZ = int16LE(15, 16)

        // Format for debug display
        val formatted = buildString {
            append("Gyro 0xAB [${decrypted.size}B]\n")
            append("Hex: ")
            append(decrypted.drop(1).joinToString(" ") { "%02x".format(it) })
            append("\n")
            val w = quatW / 16384.0
            val x = quatX / 16384.0
            val y = quatY / 16384.0
            val z = quatZ / 16384.0
            append("Quat: W=%.4f X=%.4f Y=%.4f Z=%.4f".format(w, x, y, z))
        }

        Log.d(TAG, "Gyro data: $formatted")
        val rawBytes = decrypted.map { it.toInt() and 0xFF }
        _cubeEvents.tryEmit(
            CubeEvent.GyroUpdated(
                quatW = quatW,
                quatX = quatX,
                quatY = quatY,
                quatZ = quatZ,
                rawBytes = rawBytes,
                rawData = formatted
            )
        )
    }

    private fun parseFace(axis: Int): Face? {
        return when (axis) {
            0 -> Face.U
            1 -> Face.R
            2 -> Face.F
            3 -> Face.D
            4 -> Face.L
            5 -> Face.B
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        connectedDevice = null
        prevMoveCnt = -1  // Reset move counter on disconnect
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        if (isCurrentlyScanning) {
            currentScanCallback?.let { callback ->
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            }
            isCurrentlyScanning = false
            currentScanCallback = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    override fun isScanning(): Boolean = isCurrentlyScanning

    override fun getConnectedDevice(): BluetoothDevice? = connectedDevice
}
