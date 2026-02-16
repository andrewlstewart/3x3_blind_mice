package com.example.threeblindcubers.ui.timer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.threeblindcubers.domain.cube.OpSequence
import com.example.threeblindcubers.domain.cube.SegmentType
import com.example.threeblindcubers.domain.cube.SolveMoveAnalyzer
import com.example.threeblindcubers.domain.cube.SolveSegment
import com.example.threeblindcubers.domain.models.Move

/**
 * Bottom sheet displaying a detailed breakdown of solve moves,
 * grouped by recognized Old Pochmann algorithms (Y-perm, T-perm, Ra-perm)
 * with all other moves shown as simple "Moves" segments between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolveMovesSheet(
    solveMoves: List<Move>,
    opSequence: OpSequence?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val segments = SolveMoveAnalyzer.analyze(solveMoves, opSequence)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Solve Moves (${solveMoves.size} moves)",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (segments.isEmpty()) {
                Text(
                    text = "No moves recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                LazyColumn {
                    itemsIndexed(segments) { index, segment ->
                        when (segment.type) {
                            SegmentType.Y_PERM, SegmentType.T_PERM, SegmentType.RA_PERM -> {
                                PermCard(segment = segment)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            SegmentType.MOVES -> {
                                MovesSegment(segment)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            SegmentType.UNKNOWN -> {
                                MovesSegment(segment)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card showing a recognized perm algorithm, highlighted in the primary color.
 */
@Composable
private fun PermCard(segment: SolveSegment) {
    val permColor = MaterialTheme.colorScheme.primary
    val permName = when (segment.type) {
        SegmentType.Y_PERM -> "Y-perm"
        SegmentType.T_PERM -> "T-perm"
        SegmentType.RA_PERM -> "Ra-perm"
        else -> "Perm"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with label
            Text(
                text = segment.label ?: permName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = permColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Perm moves
            Text(
                text = segment.moves.joinToString(" ") { it.toNotation() },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = permColor
            )
        }
    }
}

/**
 * Renders a MOVES or UNKNOWN segment as a simple muted text block
 * showing the actual moves performed between perms.
 */
@Composable
private fun MovesSegment(segment: SolveSegment) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        if (segment.label != null) {
            Text(
                text = segment.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Text(
            text = segment.moves.joinToString(" ") { it.toNotation() },
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
