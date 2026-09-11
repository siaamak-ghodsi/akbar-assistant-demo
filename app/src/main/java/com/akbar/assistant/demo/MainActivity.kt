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
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mic = result[Manifest.permission.RECORD_AUDIO] == true
        val camera = result[Manifest.permission.CAMERA] == true
        viewModel.onPermissionsResult(micGranted = mic, cameraGranted = camera)
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

        ensurePermissions()
    }

    override fun onPause() {
        // Keep torch state; user may want flashlight while app is briefly paused.
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            viewModel.releaseHardware()
        }
        super.onDestroy()
    }

    private fun ensurePermissions() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.RECORD_AUDIO
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.CAMERA
        }
        if (need.isEmpty()) {
            viewModel.onPermissionsResult(micGranted = true, cameraGranted = true)
        } else {
            permissionLauncher.launch(need.toTypedArray())
        }
    }
}
