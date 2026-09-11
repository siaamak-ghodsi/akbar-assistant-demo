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

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String
)

data class AssistantUiState(
    val state: AssistantState = AssistantState.IDLE,
    val language: AppLanguage = AppLanguage.PERSIAN,
    val statusText: String = "",
    val hintText: String = "",
    val lastHeard: String = "",
    val lastReply: String = "",
    val lightOn: Boolean = false,
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
    val permissionGranted: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val testModeVisible: Boolean = false,
    val needsCameraPermission: Boolean = false,
    /** Ask Activity to open Google TTS language download for Persian. */
    val needsPersianTtsInstall: Boolean = false,
    /** After first wake (or text), stay in command session without re-waking. */
    val sessionActive: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    val draftText: String = ""
)
