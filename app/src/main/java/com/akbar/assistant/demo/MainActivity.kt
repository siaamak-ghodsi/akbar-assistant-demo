package com.akbar.assistant.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import com.akbar.assistant.demo.wake.WakeWordForegroundService

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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* FGS still starts; notification may be limited if denied on API 33+ */ }

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

        // Critical for wake-from-lock-screen: show Activity over the keyguard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

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
                    onRequestMicPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }

        ensureNotificationPermission()
        ensureMicPermission()
        handleWakeIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground()
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

    private fun handleWakeIntent(intent: android.content.Intent?) {
        if (intent?.action != WakeWordForegroundService.ACTION_WAKE_DETECTED) return
        val text = intent.getStringExtra(WakeWordForegroundService.EXTRA_WAKE_TEXT).orEmpty()
        if (text.isBlank()) return
        val languageName = intent.getStringExtra(WakeWordForegroundService.EXTRA_WAKE_LANGUAGE)
        val language = runCatching { AppLanguage.valueOf(languageName ?: "PERSIAN") }
            .getOrDefault(AppLanguage.PERSIAN)
        // Clear action so rotation / recreate does not re-fire wake.
        intent.action = null
        viewModel.onWakeWordFromService(text, language)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureMicPermission() {
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (micGranted) {
            viewModel.onPermissionsResult(micGranted = true, cameraGranted = cameraGranted)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
