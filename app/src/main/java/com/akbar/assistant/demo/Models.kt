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
