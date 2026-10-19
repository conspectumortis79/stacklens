package com.stacklens.application.service

import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort
import com.stacklens.testsupport.SyntheticDefaultBridges
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicBoolean

class KiChatServiceStreamingBridgeTest {
    private val copilot: CopilotStreamingPort = mock()
    private val service =
        KiChatService(
            copilot = copilot,
            i18n = mock<I18nPort>(),
            aiUsageContext = FakeAiUsageContextPort(),
            knowledgeRetrieval = mock<KnowledgeRetrievalPort>(),
            promptBuilder = mock<KiChatPromptBuilder>(),
            mcpPreflight = mock<KiChatMcpPreflight>(),
            attachmentPreparer = mock<KiChatAttachmentPreparer>(),
        )

    @Test
    fun `the streaming helper answers without attachments when the caller omits them`() {
        whenever(
            copilot.executeWithStreamingPerLine(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            invocation.getArgument<(String) -> Unit>(5).invoke("answer <mm:think>hidden</mm:think> visible")
            "answer visible"
        }
        val chunks = mutableListOf<String>()
        val onEvent: (com.stacklens.application.port.inbound.chat.KiChatEvent) -> Unit = { chunks.add(it.toString()) }

        SyntheticDefaultBridges.invoke(
            service,
            "streamChatResponse",
            listOf("model", "prompt", AtomicBoolean(false), onEvent),
            1 shl 4,
        )

        assertEquals(1, chunks.size)
        assertEquals(false, chunks.single().contains("hidden"))
        assertEquals(true, chunks.single().contains("visible"))
    }

    @Test
    fun `a cancelled flag stops the streaming helper`() {
        whenever(
            copilot.executeWithStreamingPerLine(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            invocation.getArgument<(String) -> Unit>(5).invoke("partial")
            "partial"
        }
        val cancelled = AtomicBoolean(true)
        val ignore: (com.stacklens.application.port.inbound.chat.KiChatEvent) -> Unit = { }

        val failure =
            org.junit.jupiter.api.Assertions.assertThrows(InterruptedException::class.java) {
                SyntheticDefaultBridges.invoke(
                    service,
                    "streamChatResponse",
                    listOf("model", "prompt", cancelled, ignore),
                    1 shl 4,
                )
            }

        assertEquals("cancelled", failure.message)
    }
}
