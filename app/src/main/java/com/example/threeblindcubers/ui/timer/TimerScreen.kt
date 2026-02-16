package com.example.threeblindcubers.ui.timer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.threeblindcubers.data.bluetooth.BluetoothPermissionHelper
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.ui.settings.SettingsSheet
import com.example.threeblindcubers.ui.calibration.CalibrationWizardSheet
import com.example.threeblindcubers.ui.test.TestScreen
import com.example.threeblindcubers.ui.timer.components.CubeVisualization
import com.example.threeblindcubers.ui.timer.components.DnfDialog
import com.example.threeblindcubers.ui.timer.components.ScrambleControls
import com.example.threeblindcubers.ui.timer.components.ScrambleDisplay
import com.example.threeblindcubers.ui.timer.components.SolveResultCard
import com.example.threeblindcubers.ui.timer.components.TimerDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDebugDialog by remember { mutableStateOf(false) }

    // Keep screen on while app is visible
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.onPermissionsResult(permissions)
    }

    // Request permissions when needed
    LaunchedEffect(uiState.needsPermissions) {
        if (uiState.needsPermissions) {
            permissionLauncher.launch(BluetoothPermissionHelper.getRequiredPermissions())
        }
    }

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("3x3 Blind Mice") },
                actions = {
                    // Connection indicator dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (uiState.connectionState) {
                                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                    ConnectionState.CONNECTING,
                                    ConnectionState.SCANNING -> Color(0xFFFFC107)
                                    ConnectionState.ERROR -> Color(0xFFF44336)
                                    ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E)
                                }
                            )
                    )

                    // Settings gear
                    IconButton(onClick = { viewModel.showSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scramble controls (always visible)
            ScrambleControls(
                selectedMode = uiState.scrambleMode,
                phase = uiState.phase,
                onModeSelected = viewModel::setScrambleMode,
                onGenerateScramble = viewModel::generateScramble
            )

            // Phase-dependent content
            when (uiState.phase) {
                SolvePhase.IDLE -> {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "Generate a scramble to begin",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                }

                SolvePhase.SCRAMBLING -> {
                    ScrambleDisplay(
                        moves = uiState.scrambleMoves,
                        onLongPress = viewModel::cancelScramble
                    )
                    Spacer(modifier = Modifier.weight(1f, fill = false))
                }

                SolvePhase.SCRAMBLE_COMPLETE -> {
                    ScrambleDisplay(
                        moves = uiState.scrambleMoves,
                        onLongPress = viewModel::cancelScramble
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = viewModel::startMemorization,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(64.dp)
                    ) {
                        Text(
                            text = "Start Memorization",
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                SolvePhase.MEMORIZING -> {
                    TimerDisplay(
                        timeMillis = uiState.memoTimeMillis,
                        label = "MEMO",
                        isLarge = true
                    )
                    // Show "Start Solve" button when no cube is connected (manual mode)
                    if (uiState.connectionState == ConnectionState.DISCONNECTED) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = viewModel::startSolving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .height(64.dp)
                        ) {
                            Text(
                                text = "Start Solve",
                                fontSize = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                SolvePhase.SOLVING -> {
                    // Frozen memo time (small)
                    TimerDisplay(
                        timeMillis = uiState.memoTimeMillis,
                        label = "MEMO",
                        isLarge = false
                    )
                    // Active solve timer (large)
                    TimerDisplay(
                        timeMillis = uiState.solveTimeMillis,
                        label = "SOLVE",
                        isLarge = true
                    )
                    // Stop button
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = viewModel::onTimerTapped,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(64.dp)
                    ) {
                        Text(
                            text = "Stop",
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                SolvePhase.COMPLETE -> {
                    ScrambleDisplay(moves = uiState.scrambleMoves)
                    uiState.solveResult?.let { result ->
                        SolveResultCard(
                            result = result,
                            opSequence = uiState.opSequence,
                            onNewScramble = viewModel::generateScramble
                        )
                    }
                }
            }

            // Cube visualization (always visible)
            CubeVisualization(
                cubeState = uiState.cubeState,
                modifier = Modifier.padding(horizontal = 16.dp),
                orientationW = uiState.orientationW,
                orientationX = uiState.orientationX,
                orientationY = uiState.orientationY,
                orientationZ = uiState.orientationZ,
                lastMove = uiState.lastMove,
                lastMoveId = uiState.lastMoveId,
                onLongPress = viewModel::calibrateOrientation
            )
        }
    }

    // Settings sheet overlay
    if (uiState.showSettingsSheet) {
        SettingsSheet(
            connectionState = uiState.connectionState,
            connectedDeviceName = uiState.connectedDeviceName,
            discoveredDevices = uiState.discoveredDevices,
            isScanning = uiState.isScanning,
            hasAxisCalibration = uiState.hasAxisCalibration,
            onStartScan = viewModel::startScanning,
            onStopScan = viewModel::stopScanning,
            onConnectDevice = viewModel::connectToDevice,
            onDisconnect = viewModel::disconnect,
            onResync = viewModel::resyncSolvedState,
            onCalibrateAxes = {
                viewModel.hideSettings()
                viewModel.startCalibrationWizard()
            },
            onOpenDebugTools = {
                viewModel.hideSettings()
                showDebugDialog = true
            },
            onDismiss = viewModel::hideSettings
        )
    }

    // Calibration wizard overlay
    if (uiState.showCalibrationWizard) {
        val calibrationState by viewModel.calibrationState.collectAsState()
        CalibrationWizardSheet(
            state = calibrationState,
            onStart = viewModel::beginCalibrationSequence,
            onAdvance = viewModel::advanceCalibration,
            onRetry = viewModel::retryCalibration,
            onDismiss = viewModel::cancelCalibration
        )
    }

    // DNF dialog overlay
    if (uiState.showDnfDialog) {
        DnfDialog(
            onConfirmDnf = { viewModel.onDnfDialogResult(isDNF = true) },
            onConfirmSolved = { viewModel.onDnfDialogResult(isDNF = false) },
            onDismiss = viewModel::dismissDnfDialog
        )
    }

    // Debug tools full-screen dialog
    if (showDebugDialog) {
        Dialog(
            onDismissRequest = { showDebugDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    IconButton(onClick = { showDebugDialog = false }) {
                        Text("Close", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TestScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
