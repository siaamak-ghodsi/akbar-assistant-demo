package com.akbar.assistant.demo

enum class AppLanguage {
    PERSIAN,
    ENGLISH
}

enum class AssistantState {
    IDLE,
    LISTENING_WAKE,
    ACTIVATED,
    LISTENING_COMMAND,
    PROCESSING,
    SPEAKING
}

data class AssistantUiState(
    val state: AssistantState = AssistantState.IDLE,
    val language: AppLanguage = AppLanguage.PERSIAN,
    val statusText: String = "بگو هی اکبر",
    val hintText: String = "Say Hey Akbar",
    val lastHeard: String = "",
    val lastReply: String = "",
    val lightOn: Boolean = false,
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
    val permissionGranted: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val testModeVisible: Boolean = false,
    val needsCameraPermission: Boolean = false
)
