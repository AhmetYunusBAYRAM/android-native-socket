package com.offline.chat.model

data class ChatMessage(
    val sender: String,
    val text: String,
    val timestamp: String,
    val isLocal: Boolean,
    val isAudio: Boolean = false,
    val audioBytes: ByteArray? = null
)
