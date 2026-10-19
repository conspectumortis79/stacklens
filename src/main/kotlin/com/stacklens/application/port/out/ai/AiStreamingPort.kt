package com.stacklens.application.port.out.ai

interface AiStreamingPort : AiChatPort {
    fun executeWithStreaming(
        model: String,
        prompt: String,
        timeoutOverride: Long? = null,
        isCancelled: (() -> Boolean)? = null,
        onPartialOutput: (String) -> Unit,
        attachments: List<AiAttachment> = emptyList(),
    ): String
}
