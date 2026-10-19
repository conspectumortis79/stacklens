package com.stacklens.application.service

import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.KiChatEvent
import com.stacklens.application.port.inbound.chat.KiChatUseCase
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.AiUsageKind
import com.stacklens.domain.model.knowledge.RetrievedKnowledge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@ApplicationService
class KiChatService(
    private val copilot: CopilotStreamingPort,
    private val i18n: I18nPort,
    private val aiUsageContext: AiUsageContextPort,
    private val knowledgeRetrieval: com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort,
    private val promptBuilder: KiChatPromptBuilder,
    private val mcpPreflight: McpChatPreflight,
    private val attachmentPreparer: KiChatAttachmentPreparer,
) : KiChatUseCase {
    companion object {
        val MetaKeywords: List<String> =
            listOf(
                "stacklens",
                "applikation",
                "anwendung",
                "the app",
                "this app",
                "agent",
                "pipeline",
                "prompt",
                "readme",
                "feature",
                "funktion",
                "analyse",
                "analysis",
                "dokumentation",
                "documentation",
                "wofür",
                "wofur",
                "what does",
                "was kann",
                "can you do",
            )
    }

    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()

    override fun chat(
        req: ChatRequest,
        onChunk: (String) -> Unit,
    ) {
        chatEvents(req) { event ->
            when (event) {
                is KiChatEvent.Chunk -> onChunk(event.text)
                else -> Unit
            }
        }
    }

    override fun chatEvents(
        req: ChatRequest,
        onEvent: (KiChatEvent) -> Unit,
    ) {
        if (req.chatId > 0) {
            aiUsageContext.withContext(AiUsageKind.KICHAT.id, req.chatId) {
                executeChatEvents(req, onEvent)
            }
            return
        }
        executeChatEvents(req, onEvent)
    }

    private fun executeChatEvents(
        req: ChatRequest,
        onEvent: (KiChatEvent) -> Unit,
    ) {
        val cancelFlag = AtomicBoolean(false)
        cancelFlags[req.tabId] = cancelFlag

        val model = copilot.resolveModel()
        val languageInstruction = resolveLanguageInstruction(i18n.resolveLanguage())
        val mcpResults = runCatching { mcpPreflight.run(model, req) }.getOrDefault("")

        val retrieved: List<RetrievedKnowledge> =
            runCatching { knowledgeRetrieval.retrieveForChat(req.userMessage, null) }
                .getOrDefault(emptyList())
        val chips = promptBuilder.chipsFor(retrieved)
        if (chips.isNotEmpty()) {
            onEvent(KiChatEvent.Sources(sources = chips))
        }

        val prompt = promptBuilder.build(req, languageInstruction, mcpResults, retrieved)
        val aiAttachments = attachmentPreparer.prepare(req.attachments)

        try {
            streamChatResponse(model, prompt, cancelFlag, onEvent, aiAttachments)
        } finally {
            cancelFlags.remove(req.tabId, cancelFlag)
        }
    }

    private fun resolveLanguageInstruction(lang: String): String =
        when (lang) {
            "en" -> "Always respond in English, regardless of the language the user writes in."
            else -> "Antworte immer auf Deutsch, unabhängig von der Sprache der Nutzeranfrage."
        }

    private fun streamChatResponse(
        model: String,
        prompt: String,
        cancelFlag: AtomicBoolean,
        onEvent: (KiChatEvent) -> Unit,
        attachments: List<com.stacklens.application.port.out.ai.AiAttachment> = emptyList(),
    ) {
        val lastSentRef = AtomicReference("")
        copilot.executeWithStreamingPerLine(
            model = model,
            prompt = prompt,
            timeoutOverride = 300L,
            onPartialOutput = { rawPartial ->
                if (cancelFlag.get()) throw InterruptedException("cancelled")
                val partial = stripReasoningBlocks(rawPartial)
                val lastSent = lastSentRef.get()
                val delta = if (partial.length > lastSent.length) partial.substring(lastSent.length) else ""
                if (delta.isNotEmpty()) {
                    onEvent(KiChatEvent.Chunk(delta))
                    lastSentRef.set(partial)
                }
            },
            attachments = attachments,
        )
    }

    internal fun stripReasoningBlocks(text: String): String {
        if (text.isEmpty()) return text
        val patterns =
            listOf(
                Regex("<\\u200B?mm:think>[\\s\\S]*?</\\u200B?mm:think>"),
                Regex("<thinking>[\\s\\S]*?</thinking>"),
                Regex("<think>[\\s\\S]*?</think>"),
                Regex("<reasoning>[\\s\\S]*?</reasoning>"),
                Regex("<reflection>[\\s\\S]*?</reflection>"),
            )
        var out = text
        for (p in patterns) {
            out = p.replace(out, "")
        }
        return out
    }

    override fun cancel(tabId: String): Boolean {
        val flag = cancelFlags[tabId] ?: return false
        flag.set(true)
        return true
    }
}
