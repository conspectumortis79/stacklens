package com.stacklens.application.port.inbound.chat

data class ChatTurn(
    val role: String,
    val content: String,
)

data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val base64Data: String,
)

data class ChatRequest(
    val tabId: String = "general",
    val tabLabel: String = "",
    val chatId: Long = 0,
    val userMessage: String = "",
    val tabContext: String = "",
    val history: List<ChatTurn> = emptyList(),
    val systemHint: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
)

interface KiChatUseCase {
    fun chat(
        req: ChatRequest,
        onChunk: (String) -> Unit,
    )

    fun chatEvents(
        req: ChatRequest,
        onEvent: (KiChatEvent) -> Unit,
    )

    fun cancel(tabId: String): Boolean
}

sealed interface KiChatEvent {
    data class Chunk(
        val text: String,
    ) : KiChatEvent

    data class Sources(
        val sources: List<KnowledgeSourceChip>,
    ) : KiChatEvent

    data object Done : KiChatEvent
}

data class KnowledgeSourceChip(
    val citation: String,
    val title: String,
    val sourceType: String,
    val sourceUri: String?,
    val score: Double,
    val documentId: Long,
    val chunkId: Long,
    val preview: String,
)

interface KiChatUsageUseCase {
    fun clearUsage(chatId: Long)
}
