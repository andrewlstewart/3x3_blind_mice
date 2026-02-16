package com.example.threeblindcubers.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Modal bottom sheet wizard for IMU axis calibration.
 * Guides the user through two rotation steps (pitch and yaw)
 * to determine the correct axis mapping from IMU to display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationWizardSheet(
    state: CalibrationState,
    onStart: () -> Unit,
    onAdvance: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "IMU Axis Calibration",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when (state.step) {
                CalibrationStep.INTRO -> IntroContent(onStart = onStart, onCancel = onDismiss)
                CalibrationStep.X_AXIS_HOLD -> HoldContent(
                    label = "Hold the cube still...",
                    sublabel = "Collecting baseline orientation",
                    progress = state.progress
                )
                CalibrationStep.X_AXIS_READY -> ReadyContent(
                    label = "Step 1: X axis",
                    sublabel = "When you tap Next, rotate the whole cube like an R move (tilt the green face up toward where white is). Direction matters!",
                    buttonText = "Next",
                    onNext = onAdvance
                )
                CalibrationStep.X_AXIS_ROTATE -> RotateContent(
                    label = "Rotate like an R move",
                    sublabel = "Tilt green face up toward white. Direction matters!",
                    progress = state.progress
                )
                CalibrationStep.X_AXIS_DONE -> ReadyContent(
                    label = "X axis captured!",
                    sublabel = "First rotation recorded. When you\u2019re ready, tap Next to continue to the second axis.",
                    buttonText = "Next",
                    onNext = onAdvance
                )
                CalibrationStep.Z_AXIS_HOLD -> HoldContent(
                    label = "Hold the cube still...",
                    sublabel = "Collecting new baseline",
                    progress = state.progress
                )
                CalibrationStep.Z_AXIS_READY -> ReadyContent(
                    label = "Step 2: Z axis",
                    sublabel = "When you tap Next, rotate the whole cube like a U move (turn the green face to where orange is). Direction matters!",
                    buttonText = "Next",
                    onNext = onAdvance
                )
                CalibrationStep.Z_AXIS_ROTATE -> RotateContent(
                    label = "Rotate like a U move",
                    sublabel = "Turn green face toward orange. Direction matters!",
                    progress = state.progress
                )
                CalibrationStep.COMPUTING -> ComputingContent()
                CalibrationStep.COMPLETE -> CompleteContent(
                    debugInfo = state.debugInfo,
                    onDismiss = onDismiss
                )
                CalibrationStep.ERROR -> ErrorContent(
                    errorMessage = state.errorMessage ?: "Unknown error",
                    onRetry = onRetry,
                    onCancel = onDismiss
                )
            }
        }
    }
}

@Composable
private fun IntroContent(onStart: () -> Unit, onCancel: () -> Unit) {
    Text(
        text = "This wizard will determine how your cube's IMU axes " +
                "map to the 3D display. You'll rotate the whole cube " +
                "in two specific directions:",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Text(
        text = "1. Like an R move (tilt green up toward white)\n" +
                "2. Like a U move (turn green toward orange)",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Text(
        text = "Direction matters! Start from green-front, white-top and " +
                "rotate in the correct direction each time. " +
                "Make sure the cube is connected and sending gyro data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 24.dp)
    )
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Start Calibration")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Cancel")
    }
}

@Composable
private fun HoldContent(label: String, sublabel: String, progress: Float) {
    Spacer(modifier = Modifier.height(24.dp))
    CircularProgressIndicator(
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = sublabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ReadyContent(label: String, sublabel: String, buttonText: String, onNext: () -> Unit) {
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = sublabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(buttonText)
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun RotateContent(label: String, sublabel: String, progress: Float) {
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = sublabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Detecting rotation...",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ComputingContent() {
    Spacer(modifier = Modifier.height(32.dp))
    CircularProgressIndicator(modifier = Modifier.size(48.dp))
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Computing axis mapping...",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun CompleteContent(debugInfo: String?, onDismiss: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = "Success",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Calibration saved!",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "The 3D cube should now track your physical cube correctly.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    if (debugInfo != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = debugInfo,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Done")
    }
}

@Composable
private fun ErrorContent(errorMessage: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = "Error",
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(48.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Calibration Failed",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = errorMessage,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(modifier = Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        ) {
            Text("Cancel")
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.weight(1f)
        ) {
            Text("Retry")
        }
    }
}
