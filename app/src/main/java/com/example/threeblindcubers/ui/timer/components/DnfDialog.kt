package com.example.threeblindcubers.ui.timer.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DnfDialog(
    onConfirmDnf: () -> Unit,
    onConfirmSolved: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Did you finish the solve?") },
        text = {
            Text("If you stopped the timer before solving the cube, mark it as DNF (Did Not Finish).")
        },
        confirmButton = {
            TextButton(onClick = onConfirmSolved) {
                Text("Yes (Save)")
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirmDnf) {
                Text("No (DNF)")
            }
        }
    )
}
