package com.example.threeblindcubers.ui.timer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.threeblindcubers.ui.timer.formatTime

@Composable
fun TimerDisplay(
    timeMillis: Long,
    label: String,
    isLarge: Boolean = true,
    isTappable: Boolean = false,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isTappable) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(vertical = if (isLarge) 16.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (isLarge) 14.sp else 11.sp
        )

        Text(
            text = formatTime(timeMillis),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (isLarge) 56.sp else 28.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
