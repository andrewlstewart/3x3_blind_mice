package com.example.threeblindcubers.ui.test

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.threeblindcubers.data.bluetooth.MoyuCubeService
import com.example.threeblindcubers.data.bluetooth.MoyuCubeServiceImpl
import com.example.threeblindcubers.data.database.SolveDatabase
import com.example.threeblindcubers.data.repository.SolveRepository
import com.example.threeblindcubers.data.repository.SolveRepositoryImpl
import com.example.threeblindcubers.domain.cube.ScrambleGenerator
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.domain.models.CubeEvent
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.ScrambleMode
import com.example.threeblindcubers.domain.models.Solve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A single captured gyro sample with timestamp and full raw bytes.
 * Quaternion components are Q14 fixed-point (divide by 16384.0 for real value).
 */
data class GyroSample(
    val timestampMs: Long,       // millis since recording started
    val rawBytes: List<Int>,     // all 20 decrypted bytes as unsigned ints
    val quatW: Int,              // quaternion W (Q14 fixed-point, int16 LE from bytes 3-4)
    val quatX: Int,              // quaternion X (Q14, bytes 7-8)
    val quatY: Int,              // quaternion Y (Q14, bytes 11-12)
    val quatZ: Int               // quaternion Z (Q14, bytes 15-16)
)

/**
 * State for the gyro capture tool.
 */
data class GyroCaptureState(
    /** Which phase is currently recording, or null if idle */
    val recordingPhase: String? = null,
    /** Countdown seconds remaining, or null */
    val countdownSeconds: Int? = null,
    /** Captured data keyed by phase name */
    val captures: Map<String, List<GyroSample>> = emptyMap(),
    /** Pre-formatted clipboard text (all captures combined) */
    val clipboardText: String = ""
)

data class TestUiState(
    val currentScramble: String = "",
    val scrambleMode: ScrambleMode? = null,
    val scrambleMoveCount: Int = 0,
    val solves: List<Solve> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val discoveredDevices: List<BluetoothDevice> = emptyList(),
    val connectedDevice: BluetoothDevice? = null,
    val cubeEvents: List<String> = emptyList(),
    val rawDataLog: List<String> = emptyList(), // New field for raw cube data
    val quatW: Int = 0,      // quaternion W component (Q14 fixed-point, divide by 16384)
    val quatX: Int = 0,      // quaternion X component (Q14)
    val quatY: Int = 0,      // quaternion Y component (Q14)
    val quatZ: Int = 0,      // quaternion Z component (Q14)
    val gyroData: String = "", // Latest gyro/orientation data from cube
    val gyroCaptureState: GyroCaptureState = GyroCaptureState(),
    val statusMessage: String = "",
    val isError: Boolean = false
)

// Temporarily using manual instantiation instead of Hilt
// TODO: Re-enable Hilt once KSP compatibility is resolved
class TestViewModel(application: Application) : AndroidViewModel(application) {

    // Manual dependency instantiation
    private val scrambleGenerator: ScrambleGenerator = ScrambleGenerator()

    private val database: SolveDatabase = Room.databaseBuilder(
        application,
        SolveDatabase::class.java,
        SolveDatabase.DATABASE_NAME
    ).addMigrations(SolveDatabase.MIGRATION_1_2).build()

    private val solveRepository: SolveRepository = SolveRepositoryImpl(database.solveDao())

    private val moyuCubeService: MoyuCubeService = MoyuCubeServiceImpl(application)

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    private var currentScrambleMoves: List<Move> = emptyList()

    // ── Gyro capture machinery ──
    private var captureJob: Job? = null
    private var captureStartTime: Long = 0L
    private val captureSamples = mutableListOf<GyroSample>()
    private var isCapturing = false

    init {
        // Observe Bluetooth connection state
        viewModelScope.launch {
            moyuCubeService.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        connectionState = state,
                        connectedDevice = if (state == ConnectionState.CONNECTED)
                            moyuCubeService.getConnectedDevice()
                        else
                            null
                    )
                }
            }
        }

        // Observe cube events
        viewModelScope.launch {
            moyuCubeService.cubeEvents.collect { event ->
                // Gyro events update gyroData only, don't add to cubeEvents list
                if (event is CubeEvent.GyroUpdated) {
                    _uiState.update { state ->
                        state.copy(
                            quatW = event.quatW,
                            quatX = event.quatX,
                            quatY = event.quatY,
                            quatZ = event.quatZ,
                            gyroData = event.rawData
                        )
                    }
                    // If capture is active, buffer this sample
                    if (isCapturing) {
                        val elapsed = System.currentTimeMillis() - captureStartTime
                        captureSamples.add(
                            GyroSample(
                                timestampMs = elapsed,
                                rawBytes = event.rawBytes,
                                quatW = event.quatW,
                                quatX = event.quatX,
                                quatY = event.quatY,
                                quatZ = event.quatZ
                            )
                        )
                    }
                    return@collect
                }

                val eventMessage = when (event) {
                    is CubeEvent.Connected -> "🔵 Cube connected"
                    is CubeEvent.Disconnected -> "⚫ Cube disconnected"
                    is CubeEvent.MovePerformed -> "🎲 Move: ${event.move.toNotation()}"
                    is CubeEvent.GyroUpdated -> return@collect // handled above
                    is CubeEvent.Error -> {
                        // If it's a data received message, also log to raw data
                        if (event.message.contains("data received") || event.message.contains("Received")) {
                            _uiState.update { state ->
                                state.copy(
                                    rawDataLog = (listOf(event.message) + state.rawDataLog).take(20)
                                )
                            }
                        }
                        "❌ ${event.message}"
                    }
                }
                _uiState.update {
                    it.copy(
                        cubeEvents = (listOf(eventMessage) + it.cubeEvents).take(10)
                    )
                }
            }
        }
    }

    fun startScanning() {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        discoveredDevices = emptyList(),
                        statusMessage = "🔍 Scanning for Moyu cubes...",
                        isError = false
                    )
                }

                moyuCubeService.scan().collect { device ->
                    _uiState.update { state ->
                        val devices = state.discoveredDevices.toMutableList()
                        // Avoid duplicates
                        if (devices.none { it.address == device.address }) {
                            devices.add(device)
                        }
                        state.copy(
                            discoveredDevices = devices,
                            statusMessage = "Found ${devices.size} device(s)"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "❌ Scan error: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun stopScanning() {
        moyuCubeService.stopScan()
        _uiState.update {
            it.copy(
                statusMessage = "✅ Scan stopped",
                isError = false
            )
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            try {
                stopScanning()
                _uiState.update {
                    it.copy(
                        statusMessage = "🔄 Connecting to ${device.name}...",
                        isError = false
                    )
                }
                moyuCubeService.connect(device)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "❌ Connection error: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            moyuCubeService.disconnect()
            _uiState.update {
                it.copy(
                    statusMessage = "✅ Disconnected",
                    connectedDevice = null,
                    isError = false
                )
            }
        }
    }

    fun generateScramble(mode: ScrambleMode) {
        viewModelScope.launch {
            try {
                // Run solver on Default dispatcher since it does ~10-50ms of CPU work
                // and the solution() method is synchronized
                val moves = withContext(Dispatchers.Default) {
                    scrambleGenerator.generateScramble(mode)
                }
                currentScrambleMoves = moves
                val notation = moves.joinToString(" ") { it.toNotation() }

                _uiState.update {
                    it.copy(
                        currentScramble = notation,
                        scrambleMode = mode,
                        scrambleMoveCount = moves.size,
                        statusMessage = "✅ Generated ${mode.displayName} scramble",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "❌ Error generating scramble: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun saveTestSolve() {
        viewModelScope.launch {
            try {
                if (currentScrambleMoves.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            statusMessage = "⚠️ Generate a scramble first",
                            isError = true
                        )
                    }
                    return@launch
                }

                // Create a test solve with simulated solve moves (inverse of scramble)
                val testSolveMoves = currentScrambleMoves.reversed()
                val solve = Solve(
                    timestamp = System.currentTimeMillis(),
                    mode = _uiState.value.scrambleMode ?: ScrambleMode.FULL,
                    scrambleSequence = currentScrambleMoves,
                    solveMoves = testSolveMoves,
                    timeMillis = 45500L, // 45.5 seconds
                    isDNF = false
                )

                solveRepository.saveSolve(solve)

                _uiState.update {
                    it.copy(
                        statusMessage = "✅ Saved solve: 45.5s (${solve.mode.displayName})",
                        isError = false
                    )
                }

                // Auto-reload solves
                loadSolves()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "❌ Error saving solve: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun loadSolves() {
        viewModelScope.launch {
            try {
                solveRepository.getAllSolves().collect { solves ->
                    _uiState.update {
                        it.copy(
                            solves = solves,
                            statusMessage = if (solves.isEmpty())
                                "No solves found"
                            else
                                "✅ Loaded ${solves.size} solve(s)",
                            isError = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "❌ Error loading solves: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun clearAllSolves() {
        viewModelScope.launch {
            try {
                solveRepository.deleteAllSolves()
                _uiState.update {
                    it.copy(
                        solves = emptyList(),
                        statusMessage = "✅ All solves deleted",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "❌ Error deleting solves: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun onPermissionsResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        _uiState.update {
            it.copy(
                statusMessage = if (allGranted)
                    "✅ All Bluetooth permissions granted"
                else
                    "⚠️ Some permissions denied: ${permissions.filter { !it.value }.keys}",
                isError = !allGranted
            )
        }
    }

    // ── Gyro capture methods ──

    companion object {
        /** How long to record each phase, in seconds */
        const val CAPTURE_DURATION_SECONDS = 3
        val CAPTURE_PHASES = listOf("Baseline (still)", "X rotation", "Y rotation", "Z rotation")
    }

    /**
     * Start recording gyro samples for the given phase.
     * Records for [CAPTURE_DURATION_SECONDS] seconds, then stops and stores results.
     */
    fun startGyroCapture(phase: String) {
        // Cancel any existing capture
        captureJob?.cancel()
        captureSamples.clear()
        isCapturing = true
        captureStartTime = System.currentTimeMillis()

        _uiState.update { state ->
            state.copy(
                gyroCaptureState = state.gyroCaptureState.copy(
                    recordingPhase = phase,
                    countdownSeconds = CAPTURE_DURATION_SECONDS
                )
            )
        }

        captureJob = viewModelScope.launch {
            // Countdown timer
            for (remaining in CAPTURE_DURATION_SECONDS downTo 1) {
                _uiState.update { state ->
                    state.copy(
                        gyroCaptureState = state.gyroCaptureState.copy(
                            countdownSeconds = remaining
                        )
                    )
                }
                delay(1000L)
            }

            // Stop capturing
            isCapturing = false
            val captured = captureSamples.toList()

            // Store the captured samples and rebuild clipboard text
            _uiState.update { state ->
                val newCaptures = state.gyroCaptureState.captures.toMutableMap()
                newCaptures[phase] = captured
                val clipboardText = formatCapturesForClipboard(newCaptures)
                state.copy(
                    gyroCaptureState = state.gyroCaptureState.copy(
                        recordingPhase = null,
                        countdownSeconds = null,
                        captures = newCaptures,
                        clipboardText = clipboardText
                    ),
                    statusMessage = "✅ Captured ${captured.size} samples for \"$phase\"",
                    isError = false
                )
            }
        }
    }

    /** Clear all captured gyro data and reset the capture tool. */
    fun clearGyroCaptures() {
        captureJob?.cancel()
        isCapturing = false
        captureSamples.clear()
        _uiState.update { state ->
            state.copy(
                gyroCaptureState = GyroCaptureState()
            )
        }
    }

    /**
     * Build a paste-friendly text block from all captured phases.
     *
     * Format:
     * ```
     * === GYRO CAPTURE DATA ===
     * Phase: Baseline (still)
     * Samples: 150
     * t(ms) | raw hex (bytes 0-19)                           | x     | y     | z
     *     0 | ab 01 02 03 04 05 06 07 08 09 0a 0b 0c ...     |   258 |  1029 |  1542
     *    20 | ab ...                                          |       |       |
     * ...
     * ```
     */
    private fun formatCapturesForClipboard(captures: Map<String, List<GyroSample>>): String {
        return buildString {
            appendLine("=== GYRO CAPTURE DATA ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine()

            for (phase in CAPTURE_PHASES) {
                val samples = captures[phase] ?: continue
                appendLine("--- Phase: $phase ---")
                appendLine("Samples: ${samples.size}")
                appendLine("t(ms) | raw hex (all bytes)                                              | W      | X      | Y      | Z")
                for (s in samples) {
                    val hex = s.rawBytes.joinToString(" ") { "%02x".format(it) }
                    val line = "%5d | %-64s | %6d | %6d | %6d | %6d".format(
                        s.timestampMs, hex, s.quatW, s.quatX, s.quatY, s.quatZ
                    )
                    appendLine(line)
                }
                appendLine()
            }
        }
    }
}
