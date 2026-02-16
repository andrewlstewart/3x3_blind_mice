package com.example.threeblindcubers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.threeblindcubers.ui.theme.BlindMiceTheme
import com.example.threeblindcubers.ui.timer.TimerScreen

// Temporarily removed @AndroidEntryPoint to get app running
// TODO: Re-enable Hilt once KSP compatibility is resolved
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlindMiceTheme {
                TimerScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
