package com.example.threeblindcubers.ui.test

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.threeblindcubers.data.bluetooth.BluetoothPermissionHelper
import com.example.threeblindcubers.domain.models.ConnectionState
import com.example.threeblindcubers.domain.models.ScrambleMode

/**
 * Temporary test screen for verifying Phase 1 and Phase 2 implementations
 * Note: Using viewModel() instead of hiltViewModel() temporarily
 */
@Composable
fun TestScreen(
    modifier: Modifier = Modifier,
    viewModel: TestViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Permission launcher for Bluetooth
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.onPermissionsResult(permissions)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🧪 3x3 Blind Mice - Test UI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Version: 1.0.7 (Fixed Encryption Keys)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Divider()

        // Section 1: Permissions
        TestSection(title = "1. Bluetooth Permissions") {
            val hasPermissions = BluetoothPermissionHelper.hasPermissions(context)

            Text(
                text = "Status: ${if (hasPermissions) "✅ Granted" else "❌ Not Granted"}",
                color = if (hasPermissions) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error
            )

            if (!hasPermissions) {
                val missingPermissions = BluetoothPermissionHelper.getMissingPermissions(context)
                Text(
                    text = "Missing: ${missingPermissions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            BluetoothPermissionHelper.getRequiredPermissions()
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Permissions")
                }
            }
        }

        Divider()

        // Section 2: Scramble Generation
        TestSection(title = "2. Scramble Generator") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.generateScramble(ScrambleMode.FULL) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Full")
                }
                Button(
                    onClick = { viewModel.generateScramble(ScrambleMode.CORNERS_ONLY) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Corners")
                }
                Button(
                    onClick = { viewModel.generateScramble(ScrambleMode.EDGES_ONLY) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edges")
                }
            }

            if (uiState.currentScramble.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Mode: ${uiState.scrambleMode?.displayName ?: "None"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.currentScramble,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.scrambleMoveCount} moves",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Divider()

        // Section 3: Database Operations
        TestSection(title = "3. Database Operations") {
            Button(
                onClick = { viewModel.saveTestSolve() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.currentScramble.isNotEmpty()
            ) {
                Text("Save Test Solve (45.5s)")
            }

            Button(
                onClick = { viewModel.loadSolves() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load All Solves")
            }

            if (uiState.solves.isNotEmpty()) {
                Text(
                    text = "Saved Solves: ${uiState.solves.size}",
                    fontWeight = FontWeight.Bold
                )

                uiState.solves.take(5).forEach { solve ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = solve.mode.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Time: ${solve.formattedTime()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Scramble: ${solve.scrambleNotation()}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (uiState.solves.size > 5) {
                    Text(
                        text = "... and ${uiState.solves.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            if (uiState.solves.isNotEmpty()) {
                Button(
                    onClick = { viewModel.clearAllSolves() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All Solves")
                }
            }
        }

        Divider()

        // Section 4: Bluetooth Scanning & Connection
        TestSection(title = "4. Bluetooth Cube Connection") {
            Text(text = "Connection: ${uiState.connectionState}")

            val connectedDevice = uiState.connectedDevice
            if (connectedDevice != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "✅ Connected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Device: ${connectedDevice.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Address: ${connectedDevice.address}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.startScanning() },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.connectionState != ConnectionState.SCANNING
                    ) {
                        Text("Scan")
                    }
                    Button(
                        onClick = { viewModel.stopScanning() },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.connectionState == ConnectionState.SCANNING
                    ) {
                        Text("Stop")
                    }
                }

                if (uiState.discoveredDevices.isNotEmpty()) {
                    Text(
                        text = "Found ${uiState.discoveredDevices.size} device(s):",
                        fontWeight = FontWeight.Bold
                    )

                    uiState.discoveredDevices.forEach { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name ?: "Unknown",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = { viewModel.connectToDevice(device) }
                                ) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }
            }

            // Cube Events Log
            if (uiState.cubeEvents.isNotEmpty()) {
                Text(
                    text = "Recent Events:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Events",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.cubeEvents.forEach { event ->
                            Text(
                                text = event,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Section 5: Gyro Visualization
            if (uiState.gyroData.isNotEmpty() || uiState.quatW != 0 || uiState.quatX != 0 || uiState.quatY != 0 || uiState.quatZ != 0) {
                Divider()
                GyroVisualizationSection(
                    quatW = uiState.quatW,
                    quatX = uiState.quatX,
                    quatY = uiState.quatY,
                    quatZ = uiState.quatZ,
                    gyroData = uiState.gyroData
                )
            }

            // Section 6: Gyro Capture Tool (always show when connected)
            if (connectedDevice != null) {
                Divider()
                GyroCaptureSection(
                    captureState = uiState.gyroCaptureState,
                    onStartCapture = { phase -> viewModel.startGyroCapture(phase) },
                    onClearCaptures = { viewModel.clearGyroCaptures() }
                )
            }

            // Raw Data Monitor (for debugging cube communication)
            if (connectedDevice != null) {
                Text(
                    text = "📡 Cube Data Monitor (turn cube to see data):",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (uiState.rawDataLog.isEmpty() && uiState.cubeEvents.none { it.contains("Data") }) {
                            Text(
                                text = "⏳ Waiting for cube activity...\n💡 Try turning any face of the cube",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            val dataEvents = uiState.cubeEvents.filter { it.contains("📊") }
                            if (dataEvents.isEmpty()) {
                                Text(
                                    text = "No data packets received yet",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                dataEvents.take(5).forEach { event ->
                                    Text(
                                        text = event,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Divider()
        if (uiState.statusMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = uiState.statusMessage,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun TestSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

/**
 * Gyro visualization section with a single 3D axis view driven by the
 * absolute orientation quaternion from the cube's IMU.
 *
 * The quaternion is Q14 fixed-point (divide by 16384 for normalized -1..+1).
 * Plus numeric readouts below showing quaternion values.
 */
@Composable
private fun GyroVisualizationSection(
    quatW: Int,
    quatX: Int,
    quatY: Int,
    quatZ: Int,
    gyroData: String
) {
    // Normalize Q14 fixed-point to float quaternion (-1 to +1)
    val scale = 16384f
    val w = quatW / scale
    val x = quatX / scale
    val y = quatY / scale
    val z = quatZ / scale

    TestSection(title = "5. Orientation (Quaternion)") {
        // Single OpenGL viewport showing the quaternion orientation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            GyroAxesView(
                quatW = w,
                quatX = x,
                quatY = y,
                quatZ = z,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Numeric readouts below the viewport
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Quaternion (Q14 → normalized)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Raw: W=$quatW  X=$quatX  Y=$quatY  Z=$quatZ",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Norm: W=${"%.4f".format(w)}  X=${"%.4f".format(x)}  Y=${"%.4f".format(y)}  Z=${"%.4f".format(z)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                val norm = w * w + x * x + y * y + z * z
                Text(
                    text = "|q|² = ${"%.6f".format(norm)}  (should be ≈1.0)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Raw hex text card for detailed debugging
        if (gyroData.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Raw Gyro (0xAB)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = gyroData,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Gyro capture tool for reverse-engineering the IMU data format.
 *
 * Provides buttons to record ~3 seconds of gyro packets for each phase:
 * - Baseline (cube held still)
 * - X rotation
 * - Y rotation
 * - Z rotation
 *
 * Then a "Copy All" button to copy the full capture dump to clipboard
 * for pasting into a chat for analysis.
 */
@Composable
private fun GyroCaptureSection(
    captureState: GyroCaptureState,
    onStartCapture: (String) -> Unit,
    onClearCaptures: () -> Unit
) {
    val context = LocalContext.current

    TestSection(title = "6. Gyro Capture Tool") {
        Text(
            text = "Record ${TestViewModel.CAPTURE_DURATION_SECONDS}s of gyro data for each axis, " +
                    "then copy & paste to analyze the byte layout.",
            style = MaterialTheme.typography.bodySmall
        )

        // Recording indicator
        if (captureState.recordingPhase != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RECORDING: ${captureState.recordingPhase}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "${captureState.countdownSeconds}s remaining",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Capture buttons — one per phase
        for (phase in TestViewModel.CAPTURE_PHASES) {
            val sampleCount = captureState.captures[phase]?.size
            val isRecording = captureState.recordingPhase == phase
            val isAnyRecording = captureState.recordingPhase != null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onStartCapture(phase) },
                    modifier = Modifier.weight(1f),
                    enabled = !isAnyRecording,
                    colors = if (isRecording) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(
                        text = if (sampleCount != null) "Re-record $phase" else "Record $phase",
                        maxLines = 1
                    )
                }
                // Show sample count if captured
                if (sampleCount != null) {
                    Text(
                        text = "$sampleCount samples",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Copy and Clear buttons
        if (captureState.captures.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Gyro Capture Data", captureState.clipboardText)
                        )
                        Toast.makeText(context, "Copied ${captureState.captures.values.sumOf { it.size }} samples to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = captureState.recordingPhase == null
                ) {
                    Text("Copy All")
                }
                OutlinedButton(
                    onClick = onClearCaptures,
                    modifier = Modifier.weight(1f),
                    enabled = captureState.recordingPhase == null
                ) {
                    Text("Clear")
                }
            }

            // Show a preview of captured data
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Capture Summary",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    for (phase in TestViewModel.CAPTURE_PHASES) {
                        val samples = captureState.captures[phase] ?: continue
                        Text(
                            text = "$phase: ${samples.size} samples",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        // Show first and last sample as preview
                        if (samples.isNotEmpty()) {
                            val first = samples.first()
                            val last = samples.last()
                            val firstHex = first.rawBytes.joinToString(" ") { "%02x".format(it) }
                            Text(
                                text = "  first: $firstHex",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            if (samples.size > 1) {
                                val lastHex = last.rawBytes.joinToString(" ") { "%02x".format(it) }
                                Text(
                                    text = "  last:  $lastHex",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
