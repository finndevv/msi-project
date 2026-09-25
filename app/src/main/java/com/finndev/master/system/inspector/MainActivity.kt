package com.finndev.master.system.inspector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.finndev.master.system.inspector.core.AppState
import com.finndev.master.system.inspector.core.L
import com.finndev.master.system.inspector.core.MsiAppTheme
import com.finndev.master.system.inspector.ui.MsiNavRoot
import com.finndev.master.system.inspector.ui.OnboardingFlow

class MainActivity : ComponentActivity() {

    private var pendingOpen: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingOpen = intent?.getStringExtra("open")
        setContent {
            val theme by AppState.theme.collectAsState()
            MsiAppTheme(theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val onboarded by AppState.onboardingDone.collectAsState()
                    if (!onboarded) {
                        OnboardingFlow()
                    } else {
                        MsiNavRoot(openTarget = pendingOpen)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // QS tile / notification deep link → terminal
        if (intent.getStringExtra("open") == "terminal") {
            pendingOpen = "terminal:${System.currentTimeMillis()}"
        }
    }
}
