package com.akbar.assistant.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.akbar.assistant.demo.ui.AkbarAssistantTheme
import com.akbar.assistant.demo.ui.AssistantScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AssistantViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val state by viewModel.uiState.collectAsState()
            AkbarAssistantTheme {
                AssistantScreen(
                    state = state,
                    onToggleTestMode = viewModel::toggleTestMode,
                    onSimulateWake = viewModel::simulateWake,
                    onTestCommand = viewModel::runTestCommand
                )
            }
        }

        ensureMicPermission()
    }

    private fun ensureMicPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
