package com.akbar.assistant.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.akbar.assistant.demo.ui.AkbarAssistantTheme
import com.akbar.assistant.demo.ui.AssistantScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AssistantViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionsResult(micGranted = granted, cameraGranted = cameraGranted)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionsResult(micGranted = micGranted, cameraGranted = granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val state by viewModel.uiState.collectAsState()

            LaunchedEffect(state.needsCameraPermission) {
                if (state.needsCameraPermission && !state.cameraPermissionGranted) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            AkbarAssistantTheme {
                AssistantScreen(
                    state = state,
                    onToggleTestMode = viewModel::toggleTestMode,
                    onSimulateWake = viewModel::simulateWake,
                    onTestCommand = viewModel::runTestCommand,
                    onDraftChanged = viewModel::onDraftChanged,
                    onSendText = viewModel::sendTextCommand,
                    onRequestMicPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.onPermissionsResult(micGranted = true, cameraGranted = cameraGranted)
            viewModel.onAppForeground()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        viewModel.onAppBackground()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            viewModel.releaseHardware()
        }
        super.onDestroy()
    }
}
