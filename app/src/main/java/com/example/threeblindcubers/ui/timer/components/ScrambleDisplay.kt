package com.example.threeblindcubers.ui.timer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.threeblindcubers.ui.timer.ScrambleMoveDisplay
import com.example.threeblindcubers.ui.timer.ScrambleMoveStatus

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScrambleDisplay(
    moves: List<ScrambleMoveDisplay>,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    FlowRow(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = onLongPress
                    )
                } else Modifier
            )
    ) {
        for (move in moves) {
            val color = when (move.status) {
                ScrambleMoveStatus.COMPLETED ->
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                ScrambleMoveStatus.CURRENT ->
                    MaterialTheme.colorScheme.primary
                ScrambleMoveStatus.PENDING ->
                    MaterialTheme.colorScheme.onSurface
                ScrambleMoveStatus.CORRECTION ->
                    MaterialTheme.colorScheme.error
                ScrambleMoveStatus.RECOVERY ->
                    MaterialTheme.colorScheme.tertiary
            }

            val fontWeight = when (move.status) {
                ScrambleMoveStatus.CURRENT -> FontWeight.Bold
                ScrambleMoveStatus.CORRECTION -> FontWeight.Bold
                else -> FontWeight.Normal
            }

            val textDecoration = when (move.status) {
                ScrambleMoveStatus.COMPLETED -> TextDecoration.LineThrough
                else -> TextDecoration.None
            }

            Text(
                text = move.originalNotation,
                color = color,
                fontWeight = fontWeight,
                textDecoration = textDecoration,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
