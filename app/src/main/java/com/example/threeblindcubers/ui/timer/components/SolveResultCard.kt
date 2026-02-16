package com.example.threeblindcubers.ui.timer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.threeblindcubers.domain.cube.OpSequence
import com.example.threeblindcubers.ui.timer.SolveResult

@Composable
fun SolveResultCard(
    result: SolveResult,
    opSequence: OpSequence?,
    onNewScramble: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMovesSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // DNF badge
            if (result.isDNF) {
                Text(
                    text = "DNF",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Total time (large)
            Text(
                text = result.formattedTotalTime(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = if (result.isDNF)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TimeBreakdownItem("Memo", result.formattedMemoTime())
                TimeBreakdownItem("Solve", result.formattedSolveTime())
                TimeBreakdownItem(
                    label = "Moves",
                    value = result.moveCount.toString(),
                    onClick = if (result.solveMoves.isNotEmpty()) {
                        { showMovesSheet = true }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result.mode.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // OP Memo display
            if (opSequence != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                OpSequenceDisplay(opSequence = opSequence)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onNewScramble) {
                Text("New Scramble")
            }
        }
    }

    // Solve moves bottom sheet
    if (showMovesSheet) {
        SolveMovesSheet(
            solveMoves = result.solveMoves,
            opSequence = opSequence,
            onDismiss = { showMovesSheet = false }
        )
    }
}

@Composable
private fun OpSequenceDisplay(
    opSequence: OpSequence,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "OP Memo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Corners
        Text(
            text = "Corners",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = if (opSequence.cornerMemo.isEmpty()) "(solved)"
            else opSequence.formattedCornerMemo(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            color = if (opSequence.cornerMemo.isEmpty())
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Parity indicator
        if (opSequence.hasParity) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Parity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Edges
        Text(
            text = "Edges",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = if (opSequence.edgeMemo.isEmpty()) "(solved)"
            else opSequence.formattedEdgeMemo(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            color = if (opSequence.edgeMemo.isEmpty())
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeBreakdownItem(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (onClick != null) TextDecoration.Underline else TextDecoration.None
        )
    }
}
