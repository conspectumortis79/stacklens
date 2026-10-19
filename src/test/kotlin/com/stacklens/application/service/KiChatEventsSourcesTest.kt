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
import com.stacklens.domain.model.knowledge.KnowledgeSourceType
import com.stacklens.domain.model.knowledge.RetrievedKnowledge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KiChatEventsSourcesTest {
    private val copilot: CopilotStreamingPort = mock()
    private val appKnowledge: AppKnowledgePort = mock()
    private val i18n: I18nPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val aiUsageContext: AiUsageContextPort = FakeAiUsageContextPort()
    private val knowledgeRetrieval: KnowledgeRetrievalPort = mock()

    private fun makeService(): KiChatService =
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

    private fun stubBasicMocks() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("p")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any<String>(), any<Int>(), any<Int>())).thenReturn("")
        whenever(appKnowledge.readme()).thenReturn(null)
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), any(), anyOrNull())).thenReturn("")
    }

    @Test
    fun `chatEvents emits Sources when retrieval finds matches`() {
        val source =
            RetrievedKnowledge(
                chunkId = 42L,
                documentId = 9L,
                knowledgeBaseId = 1L,
                title = "Deployment Guide",
                sourceType = KnowledgeSourceType.CONFLUENCE,
                sourceUri = "https://example.atlassian.net/wiki/pages/1",
                content = "Run kubectl apply -f deployment.yaml",
                score = 0.91,
                citation = "KB:document-9#chunk-0",
            )
        whenever(knowledgeRetrieval.retrieveForChat(any<String>(), anyOrNull())).thenReturn(listOf(source))
        stubBasicMocks()

        val events = mutableListOf<KiChatEvent>()
        val req = ChatRequest(tabId = "t1", userMessage = "how do I deploy?")
        makeService().chatEvents(req) { events.add(it) }
        val sourcesEvent = events.filterIsInstance<KiChatEvent.Sources>().single()
        assertEquals(1, sourcesEvent.sources.size)
        val chip = sourcesEvent.sources[0]
        assertEquals("Deployment Guide", chip.title)
        assertEquals("https://example.atlassian.net/wiki/pages/1", chip.sourceUri)
        assertEquals("CONFLUENCE", chip.sourceType)
        assertEquals("KB:document-9#chunk-0", chip.citation)
        assertEquals(42L, chip.chunkId)
        assertEquals(9L, chip.documentId)
        verify(copilot).executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `chatEvents emits no Sources event when retrieval returns empty`() {
        whenever(knowledgeRetrieval.retrieveForChat(any<String>(), anyOrNull())).thenReturn(emptyList())
        stubBasicMocks()

        val events = mutableListOf<KiChatEvent>()
        val req = ChatRequest(tabId = "t2", userMessage = "what is X?")
        makeService().chatEvents(req) { events.add(it) }

        assertTrue(events.none { it is KiChatEvent.Sources }) {
            "Sources event must not be emitted when retrieval is empty"
        }
    }

    @Test
    fun `chat still works as backward-compatible chunk callback`() {
        val source =
            RetrievedKnowledge(
                chunkId = 1L,
                documentId = 1L,
                knowledgeBaseId = 1L,
                title = "T",
                sourceType = KnowledgeSourceType.MANUAL,
                sourceUri = null,
                content = "x",
                score = 0.5,
                citation = "KB:document-1#chunk-0",
            )
        whenever(knowledgeRetrieval.retrieveForChat(any<String>(), anyOrNull())).thenReturn(listOf(source))
        stubBasicMocks()

        val chunks = mutableListOf<String>()
        val req = ChatRequest(tabId = "t3", userMessage = "hi")
        makeService().chat(req) { chunks.add(it) }

        assertEquals(0, chunks.size)
    }
}
