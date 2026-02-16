package com.example.threeblindcubers.ui.timer

import com.example.threeblindcubers.domain.cube.OpSequence
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.ScrambleMode

/**
 * UI state for the main timer screen.
 */
data class TimerUiState(
    val phase: SolvePhase = SolvePhase.IDLE,
    val scrambleMode: ScrambleMode = ScrambleMode.FULL,
    val scrambleMoves: List<ScrambleMoveDisplay> = emptyList(),
    val memoTimeMillis: Long = 0,
    val solveTimeMillis: Long = 0,
    val cubeState: List<Int> = List(54) { it / 9 },
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val showSettingsSheet: Boolean = false,
    val showDnfDialog: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val solveResult: SolveResult? = null,
    val snackbarMessage: String? = null,
    val needsPermissions: Boolean = false,
    val opSequence: OpSequence? = null,
    // IMU orientation quaternion (identity = no rotation)
    val orientationW: Float = 1f,
    val orientationX: Float = 0f,
    val orientationY: Float = 0f,
    val orientationZ: Float = 0f,
    // IMU axis calibration wizard
    val showCalibrationWizard: Boolean = false,
    val hasAxisCalibration: Boolean = false,
    // Last move applied (for turn animation, incremented to trigger recomposition)
    val lastMove: Move? = null,
    val lastMoveId: Int = 0
)

/**
 * Display model for a single scramble move with completion status.
 */
data class ScrambleMoveDisplay(
    val originalNotation: String,
    val status: ScrambleMoveStatus
)

/**
 * Status of a scramble move for rendering.
 */
enum class ScrambleMoveStatus {
    /** Not yet reached */
    PENDING,
    /** Currently expected move */
    CURRENT,
    /** Successfully completed */
    COMPLETED,
    /** Correction move (user made wrong move, must undo) */
    CORRECTION,
    /** Recovery move computed by solver to reach target state */
    RECOVERY
}

/**
 * Results of a completed solve.
 */
data class SolveResult(
    val memoTimeMillis: Long,
    val solveTimeMillis: Long,
    val totalTimeMillis: Long,
    val moveCount: Int,
    val isDNF: Boolean,
    val mode: ScrambleMode,
    val solveMoves: List<Move> = emptyList()
) {
    fun formattedMemoTime(): String = formatTime(memoTimeMillis)
    fun formattedSolveTime(): String = formatTime(solveTimeMillis)
    fun formattedTotalTime(): String = formatTime(totalTimeMillis)
}

/**
 * Safe wrapper around BluetoothDevice for UI display.
 */
data class DiscoveredDevice(
    val name: String,
    val address: String
)

/**
 * Formats milliseconds as MM:SS.mmm
 */
fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val ms = millis % 1000
    return String.format("%02d:%02d.%03d", minutes, seconds, ms)
}
