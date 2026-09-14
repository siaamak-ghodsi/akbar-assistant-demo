package com.akbar.assistant.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.akbar.assistant.demo.handoff.HandoffCoordinator
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
        if (granted) requestNotificationPermissionIfNeeded()
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionsResult(micGranted = micGranted, cameraGranted = granted)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional for handoff banner */ }

    private val ttsDataLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.clearPersianTtsInstallPrompt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        HandoffCoordinator.attachActivity(this)

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
                    onInstallPersianTts = { openPersianTtsInstaller() },
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openPersianTtsInstaller() {
        viewModel.clearPersianTtsInstallPrompt()
        val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        val check = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        when {
            install.resolveActivity(packageManager) != null -> ttsDataLauncher.launch(install)
            check.resolveActivity(packageManager) != null -> ttsDataLauncher.launch(check)
            else -> {
                val market = Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = android.net.Uri.parse("market://details?id=com.google.android.tts")
                }
                runCatching { startActivity(market) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        HandoffCoordinator.attachActivity(this)
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.onPermissionsResult(micGranted = true, cameraGranted = cameraGranted)
            viewModel.onAppForeground()
            requestNotificationPermissionIfNeeded()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        HandoffCoordinator.attachActivity(this)
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
