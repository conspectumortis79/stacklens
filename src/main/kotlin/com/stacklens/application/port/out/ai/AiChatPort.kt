package com.stacklens.application.port.out.ai

interface AiChatPort {
    fun resolveToken(): String?

    fun resolveModel(): String

    fun resolveLanguage(): String

    val providerLabel: String
        get() = "Unknown"

    val supportsAttachments: Boolean
        get() = true

    fun isReady(): Boolean = true

    fun execute(
        model: String,
        prompt: String,
        timeoutOverride: Long? = null,
        attachments: List<AiAttachment> = emptyList(),
    ): String
}
