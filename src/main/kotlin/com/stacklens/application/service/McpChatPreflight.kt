package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.ChatRequest

fun interface McpChatPreflight {
    fun run(
        model: String,
        req: ChatRequest,
    ): String
}
