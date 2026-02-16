package com.example.threeblindcubers

import android.app.Application
import cs.min2phase.Search
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Temporarily removed @HiltAndroidApp to get app running
// TODO: Re-enable Hilt once KSP compatibility is resolved
class CubeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Pre-build the solver's pruning tables (~1MB, ~200ms) on a background thread
        // so the first scramble has no cold-start delay. init() is synchronized and idempotent.
        applicationScope.launch {
            Search.init()
        }
    }
}
