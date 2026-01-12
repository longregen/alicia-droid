package com.alicia.assistant.model

data class VoiceCommand(
    val text: String,
    val response: String,
    val timestamp: Long
)

data class VoiceNote(
    val id: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val duration: Int = 0
)

data class AppSettings(
    val wakeWordEnabled: Boolean = false,
    val wakeWordSensitivity: Float = 0.7f,
    val voiceFeedbackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true
)
