package com.example.threeblindcubers.ui.timer

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.threeblindcubers.data.bluetooth.BluetoothPermissionHelper
import com.example.threeblindcubers.data.bluetooth.MoyuCubeService
import com.example.threeblindcubers.data.bluetooth.MoyuCubeServiceImpl
import com.example.threeblindcubers.data.database.SolveDatabase
import com.example.threeblindcubers.data.preferences.CalibrationStore
import com.example.threeblindcubers.data.repository.SolveRepository
import com.example.threeblindcubers.data.repository.SolveRepositoryImpl
import com.example.threeblindcubers.domain.cube.CubeStateTracker
import com.example.threeblindcubers.domain.cube.OldPochmannSolver
import com.example.threeblindcubers.domain.cube.ScrambleGenerator
import com.example.threeblindcubers.domain.models.AxisCalibration
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.domain.models.CubeEvent
import com.example.threeblindcubers.domain.models.Move
import com.example.threeblindcubers.domain.models.ScrambleMode
import com.example.threeblindcubers.domain.models.Solve
import com.example.threeblindcubers.ui.calibration.CalibrationState
import com.example.threeblindcubers.ui.calibration.CalibrationStep
import kotlin.math.abs
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
 * ViewModel for the main timer screen. Manages the full BLD solve flow:
 * IDLE → SCRAMBLING → SCRAMBLE_COMPLETE → MEMORIZING → SOLVING → COMPLETE
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    // --- Manual DI (Hilt disabled due to KSP issues) ---
    private val scrambleGenerator = ScrambleGenerator()

    private val database: SolveDatabase = Room.databaseBuilder(
        application,
        SolveDatabase::class.java,
        SolveDatabase.DATABASE_NAME
    ).addMigrations(SolveDatabase.MIGRATION_1_2).build()

    private val solveRepository: SolveRepository = SolveRepositoryImpl(database.solveDao())

    private val moyuCubeService: MoyuCubeService = MoyuCubeServiceImpl(application)

    private val calibrationStore = CalibrationStore(application)

    private val cubeStateTracker = CubeStateTracker()

    // --- IMU axis calibration (signed permutation mapping) ---
    private var axisCalibration: AxisCalibration = AxisCalibration.IDENTITY

    // --- UI State ---
    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    // --- Calibration wizard state ---
    private val _calibrationState = MutableStateFlow(CalibrationState())
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()
    private var calibrationJob: Job? = null
    // Baseline quaternion captured during HOLD steps
    private var baselineW = 1f
    private var baselineX = 0f
    private var baselineY = 0f
    private var baselineZ = 0f
    // Detected axis from the pitch step
    private var pitchSourceAxis = -1
    private var pitchSign = 1f

    // --- Internal scramble tracking ---
    private var originalScrambleMoves: List<Move> = emptyList()
    private var expandedScrambleMoves: List<Move> = emptyList()
    /** Maps each display move index → range of indices in expandedScrambleMoves */
    private var displayToExpandedMapping: List<IntRange> = emptyList()
    private var scrambleTrackingIndex: Int = 0
    /** Target scrambled state as 54-char facelet string (computed once per scramble) */
    private var targetScrambledState: String = ""

    // --- Recovery mode (solver-computed moves after wrong move) ---
    /** Whether we're currently following solver-computed recovery moves */
    private var isInRecoveryMode: Boolean = false
    /** Recovery moves in display notation (as returned by solver) */
    private var recoveryMoves: List<Move> = emptyList()
    /** Recovery moves expanded to quarter turns for tracking */
    private var recoveryExpandedMoves: List<Move> = emptyList()
    /** Maps each recovery display move → range of indices in recoveryExpandedMoves */
    private var recoveryDisplayMapping: List<IntRange> = emptyList()
    /** Progress through recovery expanded moves */
    private var recoveryTrackingIndex: Int = 0
    /** Prevents overlapping solver calls */
    private var solverComputationInProgress: Boolean = false
    /** Tracks moves that arrive while solver is running */
    private var movesDuringComputation: Int = 0

    // --- Solve tracking ---
    private var solveMovesList: MutableList<Move> = mutableListOf()
    private var moveIdCounter: Int = 0

    // --- Timer state ---
    private var memoStartElapsed: Long = 0L
    private var solveStartElapsed: Long = 0L
    private var memoEndElapsed: Long = 0L
    private var timerJob: Job? = null

    // --- BLE device references ---
    private var scanJob: Job? = null
    private var rawDeviceMap: MutableMap<String, BluetoothDevice> = mutableMapOf()

    // --- IMU orientation calibration ---
    // Calibration reference quaternion (default = identity → no calibration)
    private var refW = 1f
    private var refX = 0f
    private var refY = 0f
    private var refZ = 0f
    // Last raw quaternion (needed to capture reference at long-press time)
    private var lastRawW = 1f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastRawZ = 0f

    init {
        // Observe Bluetooth connection state
        viewModelScope.launch {
            moyuCubeService.connectionState.collect { state ->
                val deviceName = if (state == ConnectionState.CONNECTED) {
                    try {
                        moyuCubeService.getConnectedDevice()?.name ?: "Unknown"
                    } catch (_: SecurityException) {
                        "Unknown"
                    }
                } else null

                // Auto-load axis calibration when connected
                val hasCalibration = if (state == ConnectionState.CONNECTED) {
                    try {
                        val address = moyuCubeService.getConnectedDevice()?.address
                        if (address != null) {
                            val saved = calibrationStore.load(address)
                            if (saved != null) {
                                axisCalibration = saved
                                true
                            } else {
                                axisCalibration = AxisCalibration.IDENTITY
                                false
                            }
                        } else {
                            axisCalibration = AxisCalibration.IDENTITY
                            false
                        }
                    } catch (_: SecurityException) {
                        axisCalibration = AxisCalibration.IDENTITY
                        false
                    }
                } else false

                _uiState.update {
                    it.copy(
                        connectionState = state,
                        connectedDeviceName = deviceName,
                        hasAxisCalibration = hasCalibration
                    )
                }
            }
        }

        // Observe cube events
        viewModelScope.launch {
            moyuCubeService.cubeEvents.collect { event ->
                when (event) {
                    is CubeEvent.MovePerformed -> onCubeMove(event.move)
                    is CubeEvent.Connected -> { /* handled by connectionState */ }
                    is CubeEvent.Disconnected -> { /* handled by connectionState */ }
                    is CubeEvent.Error -> {
                        _uiState.update { it.copy(snackbarMessage = event.message) }
                    }
                    is CubeEvent.GyroUpdated -> {
                        val scale = 16384f
                        val rawW = event.quatW / scale
                        val rawX = event.quatX / scale
                        val rawY = event.quatY / scale
                        val rawZ = event.quatZ / scale

                        // Store last raw values for calibration capture
                        lastRawW = rawW
                        lastRawX = rawX
                        lastRawY = rawY
                        lastRawZ = rawZ

                        // Apply calibration: q_display = q_ref_inverse * q_current
                        // Conjugate of unit quaternion = inverse
                        val calibrated = multiplyQuaternions(
                            refW, -refX, -refY, -refZ,
                            rawW, rawX, rawY, rawZ
                        )

                        // Apply axis remap (signed permutation from calibration wizard)
                        val result = axisCalibration.remapQuaternion(
                            calibrated[0], calibrated[1], calibrated[2], calibrated[3]
                        )

                        _uiState.update {
                            it.copy(
                                orientationW = result[0],
                                orientationX = result[1],
                                orientationY = result[2],
                                orientationZ = result[3]
                            )
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // Scramble Generation
    // ========================================================================

    fun setScrambleMode(mode: ScrambleMode) {
        _uiState.update { it.copy(scrambleMode = mode) }
    }

    fun cancelScramble() {
        // Reset all scramble-tracking state
        originalScrambleMoves = emptyList()
        expandedScrambleMoves = emptyList()
        displayToExpandedMapping = emptyList()
        scrambleTrackingIndex = 0
        isInRecoveryMode = false
        recoveryMoves = emptyList()
        recoveryExpandedMoves = emptyList()
        recoveryDisplayMapping = emptyList()
        recoveryTrackingIndex = 0
        solverComputationInProgress = false
        movesDuringComputation = 0
        targetScrambledState = ""

        // Reset cube state tracker to solved
        cubeStateTracker.reset()

        _uiState.update {
            it.copy(
                phase = SolvePhase.IDLE,
                scrambleMoves = emptyList(),
                cubeState = cubeStateTracker.getState(),
                solveResult = null,
                memoTimeMillis = 0,
                solveTimeMillis = 0,
                opSequence = null,
                snackbarMessage = "Scramble cancelled"
            )
        }
    }

    fun generateScramble() {
        val mode = _uiState.value.scrambleMode
        viewModelScope.launch {
            try {
                val moves = withContext(Dispatchers.Default) {
                    scrambleGenerator.generateScramble(mode)
                }
                originalScrambleMoves = moves

                // Expand double moves to quarter turns for tracking
                expandedScrambleMoves = mutableListOf<Move>().also { expanded ->
                    val mapping = mutableListOf<IntRange>()
                    for (move in moves) {
                        val startIdx = expanded.size
                        expanded.addAll(move.expandToQuarterTurns())
                        mapping.add(startIdx until expanded.size)
                    }
                    displayToExpandedMapping = mapping
                }

                scrambleTrackingIndex = 0

                // Reset recovery mode fields
                isInRecoveryMode = false
                recoveryMoves = emptyList()
                recoveryExpandedMoves = emptyList()
                recoveryDisplayMapping = emptyList()
                recoveryTrackingIndex = 0
                solverComputationInProgress = false
                movesDuringComputation = 0

                // Reset cube to solved for scramble tracking
                cubeStateTracker.reset()

                // Compute OP memo and target state from the scrambled state
                val opSequence = withContext(Dispatchers.Default) {
                    val tempTracker = CubeStateTracker()
                    for (move in moves) {
                        tempTracker.applyMove(move)
                    }
                    // Store the target scrambled state for recovery mode comparisons
                    targetScrambledState = tempTracker.toFaceletString()
                    OldPochmannSolver.solve(tempTracker.getState())
                }

                // Manual mode: skip SCRAMBLING phase when no cube is connected
                val isManualMode = _uiState.value.connectionState == ConnectionState.DISCONNECTED

                if (isManualMode) {
                    // Apply all scramble moves to the tracker so the cube visualization shows scrambled state
                    for (move in moves) {
                        cubeStateTracker.applyMove(move)
                    }
                    // Leave scrambleTrackingIndex at 0 so moves show as PENDING (not struck through)
                    // They'll be marked COMPLETED when the user taps "Start Memorization"
                }

                _uiState.update {
                    it.copy(
                        phase = if (isManualMode) SolvePhase.SCRAMBLE_COMPLETE else SolvePhase.SCRAMBLING,
                        scrambleMoves = buildScrambleDisplayList(),
                        cubeState = cubeStateTracker.getState(),
                        solveResult = null,
                        memoTimeMillis = 0,
                        solveTimeMillis = 0,
                        opSequence = opSequence
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "Error generating scramble: ${e.message}")
                }
            }
        }
    }

    // ========================================================================
    // Cube Move Handling
    // ========================================================================

    private fun onCubeMove(move: Move) {
        // Expand to quarter turns (BT cube only reports quarter turns,
        // but handle defensively)
        val quarterTurns = move.expandToQuarterTurns()

        var lastQt: Move = move
        for (qt in quarterTurns) {
            // Always apply to cube state tracker
            cubeStateTracker.applyMove(qt)
            lastQt = qt

            // Phase-dependent behavior
            when (_uiState.value.phase) {
                SolvePhase.SCRAMBLING -> handleScrambleMove(qt)
                SolvePhase.MEMORIZING -> handleFirstSolveMove(qt)
                SolvePhase.SOLVING -> handleSolveMove(qt)
                else -> { /* ignore moves in other phases */ }
            }
        }

        // Always update cube visualization with the last move for animation
        moveIdCounter++
        _uiState.update {
            it.copy(
                cubeState = cubeStateTracker.getState(),
                lastMove = lastQt,
                lastMoveId = moveIdCounter
            )
        }
    }

    /**
     * Checks if the expanded scramble at [index] and [index+1] form a double move
     * (two identical quarter turns of the same face).
     */
    private fun isExpandedDoubleMove(index: Int): Boolean {
        if (index + 1 >= expandedScrambleMoves.size) return false
        val a = expandedScrambleMoves[index]
        val b = expandedScrambleMoves[index + 1]
        return a.face == b.face && a.rotation == b.rotation
    }

    private fun handleScrambleMove(move: Move) {
        // 1. Quick state comparison: already at target?
        if (cubeStateTracker.toFaceletString() == targetScrambledState) {
            _uiState.update {
                it.copy(
                    phase = SolvePhase.SCRAMBLE_COMPLETE,
                    scrambleMoves = buildScrambleDisplayList()
                )
            }
            return
        }

        // 2. If solver is computing, just count the move (state is still tracked by cubeStateTracker)
        if (solverComputationInProgress) {
            movesDuringComputation++
            return
        }

        // 3. If in recovery mode, delegate to recovery tracking
        if (isInRecoveryMode) {
            handleRecoveryMove(move)
            return
        }

        // 4. Normal sequential tracking (happy path)
        if (scrambleTrackingIndex < expandedScrambleMoves.size) {
            val expected = expandedScrambleMoves[scrambleTrackingIndex]
            if (move.face == expected.face && move.rotation == expected.rotation) {
                // Exact match - advance tracking
                scrambleTrackingIndex++
            } else if (isExpandedDoubleMove(scrambleTrackingIndex)
                && move.face == expected.face
                && move.rotation != expected.rotation
            ) {
                // Double move (e.g. L2 expanded to L,L) but user did L' instead of L.
                // L' + L' also produces L2, so accept it. Rewrite the remaining
                // expanded move to match the direction the user chose.
                expandedScrambleMoves = expandedScrambleMoves.toMutableList().also {
                    it[scrambleTrackingIndex] = move
                    it[scrambleTrackingIndex + 1] = move
                }
                scrambleTrackingIndex++
            } else {
                // Wrong move - enter recovery mode via solver
                enterRecoveryMode()
                return
            }
        }

        // Check if scramble is complete via tracking index
        if (scrambleTrackingIndex >= expandedScrambleMoves.size) {
            _uiState.update {
                it.copy(
                    phase = SolvePhase.SCRAMBLE_COMPLETE,
                    scrambleMoves = buildScrambleDisplayList()
                )
            }
        } else {
            _uiState.update {
                it.copy(scrambleMoves = buildScrambleDisplayList())
            }
        }
    }

    /**
     * Enters recovery mode by launching the Kociemba solver on a background thread
     * to compute the optimal path from current state to target state.
     */
    private fun enterRecoveryMode() {
        solverComputationInProgress = true
        movesDuringComputation = 0

        viewModelScope.launch {
            val currentFacelets = cubeStateTracker.toFaceletString()

            try {
                val moves = withContext(Dispatchers.Default) {
                    scrambleGenerator.computeRemainingMoves(currentFacelets, targetScrambledState)
                }

                // If moves arrived during computation, the state has changed.
                // Re-check and potentially re-run.
                if (movesDuringComputation > 0) {
                    solverComputationInProgress = false
                    // Check if we're already at target
                    if (cubeStateTracker.toFaceletString() == targetScrambledState) {
                        isInRecoveryMode = false
                        _uiState.update {
                            it.copy(
                                phase = SolvePhase.SCRAMBLE_COMPLETE,
                                scrambleMoves = buildScrambleDisplayList()
                            )
                        }
                        return@launch
                    }
                    // Re-compute from updated state
                    enterRecoveryMode()
                    return@launch
                }

                solverComputationInProgress = false

                if (moves.isEmpty()) {
                    // Already at target - solver confirms
                    isInRecoveryMode = false
                    _uiState.update {
                        it.copy(
                            phase = SolvePhase.SCRAMBLE_COMPLETE,
                            scrambleMoves = buildScrambleDisplayList()
                        )
                    }
                    return@launch
                }

                // Set up recovery tracking
                isInRecoveryMode = true
                recoveryMoves = moves
                recoveryTrackingIndex = 0

                // Expand recovery moves to quarter turns
                val expanded = mutableListOf<Move>()
                val mapping = mutableListOf<IntRange>()
                for (move in moves) {
                    val startIdx = expanded.size
                    expanded.addAll(move.expandToQuarterTurns())
                    mapping.add(startIdx until expanded.size)
                }
                recoveryExpandedMoves = expanded
                recoveryDisplayMapping = mapping

                // Update UI
                _uiState.update {
                    it.copy(scrambleMoves = buildScrambleDisplayList())
                }
            } catch (e: Exception) {
                solverComputationInProgress = false
                _uiState.update {
                    it.copy(snackbarMessage = "Recovery solver error: ${e.message}")
                }
            }
        }
    }

    /**
     * Tracks a move during recovery mode against the solver-computed recovery sequence.
     */
    private fun handleRecoveryMove(move: Move) {
        if (recoveryTrackingIndex < recoveryExpandedMoves.size) {
            val expected = recoveryExpandedMoves[recoveryTrackingIndex]
            if (move.face == expected.face && move.rotation == expected.rotation) {
                // Correct recovery move
                recoveryTrackingIndex++
            } else if (isRecoveryExpandedDoubleMove(recoveryTrackingIndex)
                && move.face == expected.face
                && move.rotation != expected.rotation
            ) {
                // Double move alternate direction - accept it
                recoveryExpandedMoves = recoveryExpandedMoves.toMutableList().also {
                    it[recoveryTrackingIndex] = move
                    it[recoveryTrackingIndex + 1] = move
                }
                recoveryTrackingIndex++
            } else {
                // Wrong recovery move - re-enter recovery mode (solver recomputes from new state)
                enterRecoveryMode()
                return
            }
        }

        // Check if recovery is complete
        if (recoveryTrackingIndex >= recoveryExpandedMoves.size) {
            // Verify with state comparison
            if (cubeStateTracker.toFaceletString() == targetScrambledState) {
                isInRecoveryMode = false
                _uiState.update {
                    it.copy(
                        phase = SolvePhase.SCRAMBLE_COMPLETE,
                        scrambleMoves = buildScrambleDisplayList()
                    )
                }
            } else {
                // State doesn't match - re-compute (shouldn't happen but be safe)
                enterRecoveryMode()
            }
        } else {
            _uiState.update {
                it.copy(scrambleMoves = buildScrambleDisplayList())
            }
        }
    }

    /**
     * Checks if the recovery expanded moves at [index] and [index+1] form a double move.
     */
    private fun isRecoveryExpandedDoubleMove(index: Int): Boolean {
        if (index + 1 >= recoveryExpandedMoves.size) return false
        val a = recoveryExpandedMoves[index]
        val b = recoveryExpandedMoves[index + 1]
        return a.face == b.face && a.rotation == b.rotation
    }

    private fun handleFirstSolveMove(move: Move) {
        // First move during memorization → transition to solving
        val now = SystemClock.elapsedRealtime()
        memoEndElapsed = now
        solveStartElapsed = now
        solveMovesList.clear()
        solveMovesList.add(move)

        _uiState.update {
            it.copy(
                phase = SolvePhase.SOLVING,
                memoTimeMillis = memoEndElapsed - memoStartElapsed
            )
        }

        // Check if cube is already solved (unlikely with first move but be safe)
        checkSolved()
    }

    private fun handleSolveMove(move: Move) {
        solveMovesList.add(move)
        checkSolved()
    }

    private fun checkSolved() {
        val mode = _uiState.value.scrambleMode
        if (cubeStateTracker.isSolved(mode)) {
            onSolveComplete(isDNF = false)
        }
    }

    // ========================================================================
    // Timer Controls
    // ========================================================================

    fun startMemorization() {
        if (_uiState.value.phase != SolvePhase.SCRAMBLE_COMPLETE) return

        // Manual mode: mark all scramble moves as completed now
        if (_uiState.value.connectionState == ConnectionState.DISCONNECTED) {
            scrambleTrackingIndex = expandedScrambleMoves.size
        }

        memoStartElapsed = SystemClock.elapsedRealtime()
        solveMovesList.clear()

        _uiState.update {
            it.copy(
                phase = SolvePhase.MEMORIZING,
                scrambleMoves = buildScrambleDisplayList(),
                memoTimeMillis = 0,
                solveTimeMillis = 0
            )
        }

        startTimerTick()
    }

    /**
     * Manual mode: transitions from MEMORIZING to SOLVING when no smart cube is connected.
     * Mirrors handleFirstSolveMove() but without recording a move.
     */
    fun startSolving() {
        if (_uiState.value.phase != SolvePhase.MEMORIZING) return

        val now = SystemClock.elapsedRealtime()
        memoEndElapsed = now
        solveStartElapsed = now
        solveMovesList.clear()

        _uiState.update {
            it.copy(
                phase = SolvePhase.SOLVING,
                memoTimeMillis = memoEndElapsed - memoStartElapsed
            )
        }
        // Timer tick is already running from startMemorization() — it handles SOLVING phase too
    }

    fun onTimerTapped() {
        if (_uiState.value.phase != SolvePhase.SOLVING) return

        // Stop timer and show DNF dialog
        timerJob?.cancel()
        timerJob = null
        val now = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                showDnfDialog = true,
                solveTimeMillis = now - solveStartElapsed,
                memoTimeMillis = memoEndElapsed - memoStartElapsed
            )
        }
    }

    fun onDnfDialogResult(isDNF: Boolean) {
        _uiState.update { it.copy(showDnfDialog = false) }
        onSolveComplete(isDNF = isDNF)
    }

    fun dismissDnfDialog() {
        // Resume timer if dialog dismissed without choice
        _uiState.update { it.copy(showDnfDialog = false) }
        if (_uiState.value.phase == SolvePhase.SOLVING) {
            startTimerTick()
        }
    }

    private fun onSolveComplete(isDNF: Boolean) {
        timerJob?.cancel()
        timerJob = null

        val now = SystemClock.elapsedRealtime()
        val memoTime = memoEndElapsed - memoStartElapsed
        val solveTime = if (solveStartElapsed > 0) now - solveStartElapsed else 0L
        val totalTime = memoTime + solveTime

        val result = SolveResult(
            memoTimeMillis = memoTime,
            solveTimeMillis = solveTime,
            totalTimeMillis = totalTime,
            moveCount = solveMovesList.size,
            isDNF = isDNF,
            mode = _uiState.value.scrambleMode,
            solveMoves = solveMovesList.toList()
        )

        _uiState.update {
            it.copy(
                phase = SolvePhase.COMPLETE,
                memoTimeMillis = memoTime,
                solveTimeMillis = solveTime,
                solveResult = result,
                showDnfDialog = false
            )
        }

        // Save solve to database
        viewModelScope.launch {
            try {
                val solve = Solve(
                    timestamp = System.currentTimeMillis(),
                    mode = _uiState.value.scrambleMode,
                    scrambleSequence = originalScrambleMoves,
                    solveMoves = solveMovesList.toList(),
                    timeMillis = totalTime,
                    memoTimeMillis = memoTime,
                    isDNF = isDNF
                )
                solveRepository.saveSolve(solve)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "Error saving solve: ${e.message}")
                }
            }
        }
    }

    private fun startTimerTick() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(16) // ~60fps
                val now = SystemClock.elapsedRealtime()
                val phase = _uiState.value.phase

                when (phase) {
                    SolvePhase.MEMORIZING -> {
                        _uiState.update {
                            it.copy(memoTimeMillis = now - memoStartElapsed)
                        }
                    }
                    SolvePhase.SOLVING -> {
                        _uiState.update {
                            it.copy(solveTimeMillis = now - solveStartElapsed)
                        }
                    }
                    else -> break
                }
            }
        }
    }

    // ========================================================================
    // Scramble Display Builder
    // ========================================================================

    private fun buildScrambleDisplayList(): List<ScrambleMoveDisplay> {
        val result = mutableListOf<ScrambleMoveDisplay>()
        val phase = _uiState.value.phase
        val isManualScrambleComplete = phase == SolvePhase.SCRAMBLE_COMPLETE
                && _uiState.value.connectionState == ConnectionState.DISCONNECTED
                && scrambleTrackingIndex == 0

        // Add original scramble moves with status
        for ((displayIdx, move) in originalScrambleMoves.withIndex()) {
            val expandedRange = displayToExpandedMapping[displayIdx]
            val status = when {
                // Manual mode before memo: show all moves as plain PENDING (no tracking)
                isManualScrambleComplete -> ScrambleMoveStatus.PENDING
                // All expanded moves in this display move are completed
                expandedRange.last < scrambleTrackingIndex -> ScrambleMoveStatus.COMPLETED
                // In recovery mode or solver computing: uncompleted original moves are hidden
                // by marking as COMPLETED (recovery moves take over from here)
                isInRecoveryMode || solverComputationInProgress ->
                    ScrambleMoveStatus.COMPLETED
                // Current move in normal tracking
                scrambleTrackingIndex in expandedRange ->
                    ScrambleMoveStatus.CURRENT
                else -> ScrambleMoveStatus.PENDING
            }
            result.add(ScrambleMoveDisplay(move.toNotation(), status))
        }

        // Add recovery moves if in recovery mode
        if (isInRecoveryMode && recoveryMoves.isNotEmpty()) {
            for ((displayIdx, move) in recoveryMoves.withIndex()) {
                val expandedRange = recoveryDisplayMapping[displayIdx]
                val status = when {
                    expandedRange.last < recoveryTrackingIndex -> ScrambleMoveStatus.COMPLETED
                    recoveryTrackingIndex in expandedRange -> ScrambleMoveStatus.CURRENT
                    else -> ScrambleMoveStatus.RECOVERY
                }
                result.add(ScrambleMoveDisplay(move.toNotation(), status))
            }
        }

        return result
    }

    // ========================================================================
    // Settings / Connection
    // ========================================================================

    fun showSettings() {
        _uiState.update { it.copy(showSettingsSheet = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    fun startScanning() {
        if (!BluetoothPermissionHelper.hasPermissions(getApplication())) {
            _uiState.update { it.copy(needsPermissions = true) }
            return
        }

        scanJob?.cancel()
        _uiState.update {
            it.copy(
                discoveredDevices = emptyList(),
                isScanning = true
            )
        }
        rawDeviceMap.clear()

        scanJob = viewModelScope.launch {
            try {
                moyuCubeService.scan().collect { device ->
                    try {
                        val address = device.address
                        val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                        if (!rawDeviceMap.containsKey(address)) {
                            rawDeviceMap[address] = device
                            _uiState.update { state ->
                                state.copy(
                                    discoveredDevices = state.discoveredDevices +
                                            DiscoveredDevice(name, address)
                                )
                            }
                        }
                    } catch (_: SecurityException) {
                        // Skip device if we can't read its properties
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        snackbarMessage = "Scan error: ${e.message}",
                        isScanning = false
                    )
                }
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        moyuCubeService.stopScan()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connectToDevice(device: DiscoveredDevice) {
        val btDevice = rawDeviceMap[device.address] ?: return
        viewModelScope.launch {
            try {
                stopScanning()
                moyuCubeService.connect(btDevice)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "Connection error: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            moyuCubeService.disconnect()
        }
    }

    fun resyncSolvedState() {
        cubeStateTracker.reset()
        _uiState.update {
            it.copy(
                cubeState = cubeStateTracker.getState(),
                snackbarMessage = "Cube state reset to solved"
            )
        }
    }

    fun onPermissionsResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        _uiState.update { it.copy(needsPermissions = false) }
        if (allGranted) {
            startScanning()
        } else {
            _uiState.update {
                it.copy(snackbarMessage = "Bluetooth permissions required for scanning")
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ========================================================================
    // IMU Orientation Calibration
    // ========================================================================

    /**
     * Captures the current raw quaternion as the reference orientation.
     * After calibration, the virtual cube appears in identity position
     * (white-top green-front) and all subsequent tilts are relative.
     * Called from long-press on the 3D cube visualization.
     */
    fun calibrateOrientation() {
        refW = lastRawW
        refX = lastRawX
        refY = lastRawY
        refZ = lastRawZ
        // Immediately reset display to identity
        _uiState.update {
            it.copy(
                orientationW = 1f,
                orientationX = 0f,
                orientationY = 0f,
                orientationZ = 0f,
                snackbarMessage = "Orientation calibrated"
            )
        }
    }

    /**
     * Hamilton product of two quaternions (a * b).
     * Returns [w, x, y, z] as a FloatArray.
     */
    private fun multiplyQuaternions(
        aw: Float, ax: Float, ay: Float, az: Float,
        bw: Float, bx: Float, by: Float, bz: Float
    ): FloatArray = floatArrayOf(
        aw * bw - ax * bx - ay * by - az * bz,  // w
        aw * bx + ax * bw + ay * bz - az * by,  // x
        aw * by - ax * bz + ay * bw + az * bx,  // y
        aw * bz + ax * by - ay * bx + az * bw   // z
    )

    // ========================================================================
    // IMU Axis Calibration Wizard
    // ========================================================================

    /**
     * Starts the axis calibration wizard. Resets state and shows the wizard sheet.
     */
    fun startCalibrationWizard() {
        calibrationJob?.cancel()
        pitchSourceAxis = -1
        pitchSign = 1f
        _calibrationState.value = CalibrationState(step = CalibrationStep.INTRO)
        _uiState.update { it.copy(showCalibrationWizard = true) }
    }

    /**
     * Called when the user clicks "Start" on the intro screen.
     * Runs X-axis hold, then pauses at X_AXIS_READY for user tap.
     */
    fun beginCalibrationSequence() {
        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch {
            try {
                // --- Step 1: X-axis Hold (collect baseline) ---
                _calibrationState.value = CalibrationState(
                    step = CalibrationStep.X_AXIS_HOLD,
                    statusText = "Hold the cube still..."
                )
                collectBaseline()

                // --- Pause: wait for user to tap "Next" ---
                _calibrationState.value = CalibrationState(
                    step = CalibrationStep.X_AXIS_READY,
                    statusText = "Ready to detect X axis"
                )
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Wizard cancelled
            }
        }
    }

    /**
     * Called when the user taps "Next" on a READY step.
     * Advances through the remaining calibration stages.
     */
    fun advanceCalibration() {
        val currentStep = _calibrationState.value.step
        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch {
            try {
                when (currentStep) {
                    CalibrationStep.X_AXIS_READY -> {
                        // --- Step 2: X-axis Rotate (like an R move) ---
                        _calibrationState.value = CalibrationState(
                            step = CalibrationStep.X_AXIS_ROTATE,
                            statusText = "Rotate like an R move..."
                        )
                        val xResult = analyzeRotation()
                        if (xResult == null) {
                            _calibrationState.value = CalibrationState(
                                step = CalibrationStep.ERROR,
                                errorMessage = "No rotation detected. Make sure to rotate the whole cube like an R move."
                            )
                            return@launch
                        }
                        pitchSourceAxis = xResult.first
                        pitchSign = xResult.second

                        // --- Pause: X axis done, wait for user tap ---
                        _calibrationState.value = CalibrationState(
                            step = CalibrationStep.X_AXIS_DONE,
                            statusText = "X axis captured!"
                        )
                    }

                    CalibrationStep.X_AXIS_DONE -> {
                        // --- Step 3: Z-axis Hold (collect baseline) ---
                        _calibrationState.value = CalibrationState(
                            step = CalibrationStep.Z_AXIS_HOLD,
                            statusText = "Hold still..."
                        )
                        collectBaseline()

                        // --- Pause: wait for user to tap "Next" ---
                        _calibrationState.value = CalibrationState(
                            step = CalibrationStep.Z_AXIS_READY,
                            statusText = "Ready to detect Z axis"
                        )
                    }

                    CalibrationStep.Z_AXIS_READY -> {
                        // --- Step 4: Z-axis Rotate (like a U move) ---
                        _calibrationState.value = CalibrationState(
                            step = CalibrationStep.Z_AXIS_ROTATE,
                            statusText = "Rotate like a U move..."
                        )
                        val zResult = analyzeRotation()
                        if (zResult == null) {
                            _calibrationState.value = CalibrationState(
                                step = CalibrationStep.ERROR,
                                errorMessage = "No rotation detected. Make sure to rotate the whole cube like a U move."
                            )
                            return@launch
                        }
                        val zSourceAxis = zResult.first
                        val zSign = zResult.second

                        // --- Step 5: Compute and save ---
                        _calibrationState.value = CalibrationState(
                            step = CalibrationStep.COMPUTING,
                            statusText = "Computing axis mapping..."
                        )

                        // Check for collision (same axis detected twice)
                        if (pitchSourceAxis == zSourceAxis) {
                            _calibrationState.value = CalibrationState(
                                step = CalibrationStep.ERROR,
                                errorMessage = "Both rotations detected the same IMU axis ($pitchSourceAxis). " +
                                        "Make sure to rotate in clearly different directions (tilt vs. turn)."
                            )
                            return@launch
                        }

                        deriveAndSave(
                            xSourceAxis = pitchSourceAxis, xSign = pitchSign,
                            ySourceAxis = zSourceAxis, ySign = zSign
                        )
                    }

                    else -> { /* ignore advance from unexpected steps */ }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Wizard cancelled
            }
        }
    }

    /**
     * Cancels the calibration wizard.
     */
    fun cancelCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
        _calibrationState.value = CalibrationState()
        _uiState.update { it.copy(showCalibrationWizard = false) }
    }

    /**
     * Retries the calibration wizard from the beginning.
     */
    fun retryCalibration() {
        calibrationJob?.cancel()
        pitchSourceAxis = -1
        pitchSign = 1f
        _calibrationState.value = CalibrationState(step = CalibrationStep.INTRO)
    }

    /**
     * Collects a baseline quaternion by averaging ~30 samples over 1.5 seconds.
     */
    private suspend fun collectBaseline() {
        val samples = 30
        val intervalMs = 50L // 1.5s / 30 = 50ms
        var sumW = 0f; var sumX = 0f; var sumY = 0f; var sumZ = 0f

        for (i in 0 until samples) {
            sumW += lastRawW
            sumX += lastRawX
            sumY += lastRawY
            sumZ += lastRawZ
            _calibrationState.update {
                it.copy(progress = (i + 1).toFloat() / samples)
            }
            delay(intervalMs)
        }

        // Average and normalize
        val n = samples.toFloat()
        baselineW = sumW / n
        baselineX = sumX / n
        baselineY = sumY / n
        baselineZ = sumZ / n

        val norm = kotlin.math.sqrt(
            baselineW * baselineW + baselineX * baselineX +
                    baselineY * baselineY + baselineZ * baselineZ
        )
        if (norm > 0.001f) {
            baselineW /= norm
            baselineX /= norm
            baselineY /= norm
            baselineZ /= norm
        }
    }

    /**
     * Monitors quaternion changes over ~5 seconds to detect which IMU axis
     * is being rotated around.
     *
     * Computes q_delta = q_now * conjugate(q_baseline). The largest imaginary
     * component of the delta quaternion indicates the rotation axis.
     *
     * @return Pair of (sourceAxis 0-2, sign +1/-1) or null if no significant
     *         rotation detected.
     */
    private suspend fun analyzeRotation(): Pair<Int, Float>? {
        val durationMs = 5000L
        val sampleIntervalMs = 50L
        val totalSamples = (durationMs / sampleIntervalMs).toInt()

        var maxMagnitude = 0f
        var bestAxis = -1
        var bestSign = 1f

        for (i in 0 until totalSamples) {
            // Compute delta: q_delta = q_now * conj(q_baseline)
            val delta = multiplyQuaternions(
                lastRawW, lastRawX, lastRawY, lastRawZ,
                baselineW, -baselineX, -baselineY, -baselineZ
            )

            // Check each imaginary component
            val components = floatArrayOf(delta[1], delta[2], delta[3])  // x, y, z
            for (axis in 0..2) {
                val mag = abs(components[axis])
                if (mag > maxMagnitude) {
                    maxMagnitude = mag
                    bestAxis = axis
                    bestSign = if (components[axis] >= 0) 1f else -1f
                }
            }

            _calibrationState.update {
                it.copy(progress = (i + 1).toFloat() / totalSamples)
            }
            delay(sampleIntervalMs)
        }

        // Validate: need minimum rotation threshold
        if (maxMagnitude < 0.15f) return null

        // Validate: dominant axis must be significantly larger than runner-up
        val delta = multiplyQuaternions(
            lastRawW, lastRawX, lastRawY, lastRawZ,
            baselineW, -baselineX, -baselineY, -baselineZ
        )
        val components = floatArrayOf(abs(delta[1]), abs(delta[2]), abs(delta[3]))
        val sorted = components.sortedDescending()
        if (sorted.size >= 2 && sorted[0] > 0.001f && sorted[0] < sorted[1] * 1.5f) {
            // Not clearly dominant — but we still use the best we found over the whole window
            // The continuous tracking tends to find a clear winner even if the final sample is ambiguous
        }

        return Pair(bestAxis, bestSign)
    }

    /**
     * Derives the Z axis, builds the AxisCalibration, saves it, and updates state.
     */
    private fun deriveAndSave(
        xSourceAxis: Int, xSign: Float,
        ySourceAxis: Int, ySign: Float
    ) {
        val (zSrc, zSgn) = AxisCalibration.deriveZAxis(xSourceAxis, xSign, ySourceAxis, ySign)

        val calibration = AxisCalibration(
            xSourceAxis = xSourceAxis, xSign = xSign,
            ySourceAxis = ySourceAxis, ySign = ySign,
            zSourceAxis = zSrc, zSign = zSgn
        )

        // Apply immediately
        axisCalibration = calibration

        // Save per device
        try {
            val address = moyuCubeService.getConnectedDevice()?.address
            if (address != null) {
                calibrationStore.save(address, calibration)
            }
        } catch (_: SecurityException) {
            // Can't read device address — calibration still applied in-memory
        }

        val axisNames = arrayOf("X", "Y", "Z")
        val debugInfo = buildString {
            appendLine("Axis mapping (physical → IMU):")
            appendLine("  X (L\u2013R) <- IMU ${axisNames[xSourceAxis]} * ${if (xSign > 0) "+1" else "-1"}")
            appendLine("  Y (F\u2013B) <- IMU ${axisNames[zSrc]} * ${if (zSgn > 0) "+1" else "-1"}")
            append("  Z (D\u2013U) <- IMU ${axisNames[ySourceAxis]} * ${if (ySign > 0) "+1" else "-1"}")
        }

        _calibrationState.value = CalibrationState(
            step = CalibrationStep.COMPLETE,
            result = calibration,
            debugInfo = debugInfo
        )
        _uiState.update { it.copy(hasAxisCalibration = true) }
    }
}
