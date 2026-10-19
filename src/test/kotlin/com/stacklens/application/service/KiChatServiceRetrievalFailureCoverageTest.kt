package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.KiChatEvent
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort
import com.stacklens.application.port.out.platform.AppKnowledgePort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KiChatServiceRetrievalFailureCoverageTest {
    private val copilot: CopilotStreamingPort = mock()
    private val appKnowledge: AppKnowledgePort = mock()
    private val i18n: I18nPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val aiUsageContext: AiUsageContextPort = mock()
    private val knowledgeRetrieval: KnowledgeRetrievalPort = mock()

    private val service =
        KiChatService(
            copilot = copilot,
            i18n = i18n,
            aiUsageContext = aiUsageContext,
            knowledgeRetrieval = knowledgeRetrieval,
            promptBuilder = KiChatServiceTestSupport.buildPromptLoader(promptLoader, appKnowledge),
            mcpPreflight = KiChatServiceTestSupport.buildNoopPreflight(),
            attachmentPreparer =
                KiChatAttachmentPreparer(
                    ImageInlinePreparer(
                        com.stacklens.adapter.out.image
                            .DefaultInlineImageEncoder(),
                    ),
                ),
        )

    @Test
    fun `a failing knowledge retrieval still answers without source chips`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any<String>(), any<Int>(), any<Int>())).thenReturn("")
        whenever(appKnowledge.readme()).thenReturn(null)
        whenever(knowledgeRetrieval.retrieveForChat(any(), anyOrNull())).thenThrow(RuntimeException("index is locked"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())).thenReturn("")

        val events = mutableListOf<KiChatEvent>()
        service.chatEvents(ChatRequest(tabId = "tab", chatId = 0L, userMessage = "wie ist das wetter")) { events += it }

        assertTrue(events.none { it is KiChatEvent.Sources }, events.toString())
        val prompt = argumentCaptor<String>()
        verify(copilot).executeWithStreamingPerLine(eq("gpt-4o"), prompt.capture(), anyOrNull(), anyOrNull(), anyOrNull(), any(), any())
        assertTrue(prompt.firstValue.isNotEmpty())
    }
}
