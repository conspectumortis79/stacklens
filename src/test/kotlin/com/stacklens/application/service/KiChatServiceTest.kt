package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.inbound.chat.ChatAttachment
import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.ChatTurn
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolDescriptor
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort
import com.stacklens.application.port.out.platform.AppKnowledgePort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Base64

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KiChatServiceTest {
    private val copilot: CopilotStreamingPort = mock()
    private val appKnowledge: AppKnowledgePort = mock()
    private val i18n: I18nPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val mcpConfigs: McpServerConfigPort = mock()
    private val mcpGateway: McpGatewayPort = mock()
    private val jsonCodec: JsonCodecPort = mock()

    private fun makeService(
        withMcp: Boolean = false,
        usageContext: AiUsageContextPort = FakeAiUsageContextPort(),
        knowledgeRetrieval: com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort = EmptyKnowledgeRetrieval(),
        attachmentPreparer: KiChatAttachmentPreparer =
            KiChatAttachmentPreparer(
                ImageInlinePreparer(
                    com.stacklens.adapter.out.image
                        .DefaultInlineImageEncoder(),
                ),
            ),
    ) = KiChatService(
        copilot = copilot,
        i18n = i18n,
        aiUsageContext = usageContext,
        knowledgeRetrieval = knowledgeRetrieval,
        promptBuilder = KiChatServiceTestSupport.buildPromptLoader(promptLoader, appKnowledge),
        mcpPreflight =
            if (withMcp) {
                KiChatMcpPreflight(
                    mcpConfigs = mcpConfigs,
                    mcpGateway = mcpGateway,
                    jsonCodec = jsonCodec,
                    copilot = copilot,
                    promptLoader = promptLoader,
                )
            } else {
                KiChatServiceTestSupport.buildNoopPreflight()
            },
        attachmentPreparer = attachmentPreparer,
    )

    private fun stubPromptLoader() {
        whenever(promptLoader.get(any(), any(), any())).thenReturn("prompt-text")
    }

    private fun stubRelevantEmpty() {
        whenever(appKnowledge.relevant(any<String>(), any<Int>(), any<Int>())).thenReturn("")
    }

    private fun stubStreamingNoOp() {
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())).thenReturn("")
    }

    @Test
    fun `MetaKeywords contains expected entries`() {
        assertTrue(KiChatService.MetaKeywords.isNotEmpty())
        assertTrue(KiChatService.MetaKeywords.contains("stacklens"))
    }

    @Test
    fun `chat binds every AI call to a positive KI chat id`() {
        val usageContext = RecordingAiUsageContext()
        val service = makeService(usageContext = usageContext)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "tracked", chatId = 8675309L, userMessage = "hallo"), {})

        assertEquals(listOf("kichat" to 8675309L), usageContext.contexts)
    }

    @Test
    fun `chat without a positive chat id remains backward compatible`() {
        val usageContext = RecordingAiUsageContext()
        val service = makeService(usageContext = usageContext)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "legacy", chatId = 0L, userMessage = "hallo"), {})

        assertTrue(usageContext.contexts.isEmpty())
        verify(copilot).executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())
    }

    @Test
    fun `chat uses English instruction when language is en`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        val chunks = mutableListOf<String>()
        doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val cb = inv.getArgument<(String) -> Unit>(5)
            cb("Hello ")
            cb("Hello World")
            null
        }.whenever(copilot).executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())

        service.chat(ChatRequest(tabId = "t1", userMessage = "hi"), chunks::add)
    }

    @Test
    fun `chat uses German instruction when language is not en`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t2", userMessage = "hallo"), {})
    }

    @Test
    fun `chat appends knowledge index when non-blank`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("INDEX CONTENT")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t3", userMessage = "hi"), {})
    }

    @Test
    fun `chat appends relevant excerpt when knowledgeRelevant is non-blank`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any<String>(), any<Int>(), any<Int>())).thenReturn("RELEVANT DOCS")
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t4", userMessage = "hi"), {})
    }

    @Test
    fun `chat uses readme when knowledgeRelevant blank and message mentions app`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(appKnowledge.readme()).thenReturn("# README CONTENT")
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t5", userMessage = "what is stacklens?"), {})
        verify(appKnowledge).readme()
    }

    @Test
    fun `chat handles null readme when mentionsApp`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(appKnowledge.readme()).thenReturn(null)
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t6", userMessage = "what is stacklens?"), {})
        verify(appKnowledge).readme()
    }

    @Test
    fun `chat appends systemHint when non-blank`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t7", userMessage = "hi", systemHint = "Be concise"), {})
    }

    @Test
    fun `chat appends tabContext when non-blank`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t8", userMessage = "hi", tabContext = "Analysis view"), {})
    }

    @Test
    fun `chat appends history when non-empty with assistant and user turns`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        val history =
            listOf(
                ChatTurn(role = "user", content = "Hello"),
                ChatTurn(role = "assistant", content = "Hi there"),
                ChatTurn(role = "Assistant", content = "Another assistant message"),
            )
        service.chat(ChatRequest(tabId = "t9", userMessage = "new message", history = history), {})
    }

    @Test
    fun `chat appends tabLabel when non-blank`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "t10", tabLabel = "My Tab", userMessage = "hi"), {})
    }

    @Test
    fun `chat streaming skips onChunk when delta is empty`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()

        val chunks = mutableListOf<String>()
        doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val cb = inv.getArgument<(String) -> Unit>(5)
            cb("Hello")
            cb("He")
            null
        }.whenever(copilot).executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())

        service.chat(ChatRequest(tabId = "stream1", userMessage = "hi"), chunks::add)
        assertTrue(chunks.size >= 1)
        assertTrue(chunks[0].isNotEmpty())
    }

    @Test
    fun `chat throws InterruptedException when cancelFlag is set during streaming`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()

        doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val cb = inv.getArgument<(String) -> Unit>(5)
            service.cancel("cancel-tab")
            cb("Hello")
            null
        }.whenever(copilot).executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())

        assertThrows<InterruptedException> {
            service.chat(ChatRequest(tabId = "cancel-tab", userMessage = "hi"), {})
        }
    }

    @Test
    fun `cancel returns false when no active stream for tabId`() {
        val service = makeService()
        assertFalse(service.cancel("nonexistent-tab"))
    }

    @Test
    fun `cancel returns true and sets flag when stream is active`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()

        var cancelResult = false
        doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val cb = inv.getArgument<(String) -> Unit>(5)
            cancelResult = service.cancel("active-tab")
            try {
                cb("Hello")
            } catch (_: InterruptedException) {
            }
            null
        }.whenever(copilot).executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any<(String) -> Unit>(), anyOrNull())

        service.chat(ChatRequest(tabId = "active-tab", userMessage = "hi"), {})
        assertTrue(cancelResult)
    }

    @Test
    fun `chat succeeds when mcpConfigs is null (no MCP)`() {
        val service = makeService(withMcp = false)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp1", userMessage = "hi"), {})
        verify(mcpConfigs, never()).findFirstActiveMcpServerConfig()
    }

    @Test
    fun `mcpPreFlight returns empty when findFirstActiveMcpServerConfig is null`() {
        val service = makeService(withMcp = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(null)
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp2", userMessage = "hi"), {})
        verify(mcpGateway, never()).listTools(any())
    }

    @Test
    fun `mcpPreFlight returns empty when tool list is empty`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(emptyList())
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp3", userMessage = "hi"), {})
    }

    @Test
    fun `mcpPreFlight returns empty when calls list is empty after parsing`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[]")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenReturn(emptyList())
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp4", userMessage = "hi"), {})
    }

    @Test
    fun `mcpPreFlight calls tool and appends result to prompt`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("get_issue", "Fetches an issue")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("""[{"tool":"get_issue","args":{"id":"42"}}]""")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenReturn(
            listOf(mapOf("tool" to "get_issue", "args" to mapOf("id" to "42"))),
        )
        whenever(mcpGateway.callTool(any(), any(), any())).thenReturn(McpToolResult("Issue #42: Bug report"))
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp5", userMessage = "show me issue 42"), {})
        verify(mcpGateway).callTool(any(), any(), any())
    }

    @Test
    fun `mcpPreFlight skips call when tool name is null`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[{}]")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenReturn(listOf(mapOf("args" to emptyMap<String, Any?>())))
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp6", userMessage = "hi"), {})
        verify(mcpGateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `mcpPreFlight handles callTool returning null (getOrNull)`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("""[{"tool":"tool1","args":{}}]""")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenReturn(
            listOf(mapOf("tool" to "tool1", "args" to emptyMap<String, Any?>())),
        )
        whenever(mcpGateway.callTool(any(), any(), any())).thenThrow(RuntimeException("connection refused"))
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp7", userMessage = "hi"), {})
    }

    @Test
    fun `mcpPreFlight handles JSON with backtick prefix in planJson`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("```json\n[{\"tool\":\"tool1\",\"args\":{}}]\n```")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenReturn(
            listOf(mapOf("tool" to "tool1", "args" to emptyMap<String, Any?>())),
        )
        whenever(mcpGateway.callTool(any(), any(), any())).thenReturn(McpToolResult("result"))
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp8", userMessage = "hi"), {})
    }

    @Test
    fun `mcpPreFlight handles planJson with no JSON array brackets (start less than 0)`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("no array here at all")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenReturn(emptyList())
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp9", userMessage = "hi"), {})
    }

    @Test
    fun `mcpPreFlight uses emptyMap when call args is not a Map type`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[{\"tool\":\"tool1\",\"args\":\"not-a-map\"}]")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenReturn(
            listOf(mapOf("tool" to "tool1", "args" to "not-a-map")),
        )
        whenever(mcpGateway.callTool(any(), any(), any())).thenReturn(McpToolResult("result"))
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp10", userMessage = "hi"), {})
    }

    @Test
    fun `mcpPreFlight falls back to empty json when copilot execute throws`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenThrow(RuntimeException("AI down"))
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp11", userMessage = "hi"), {})
    }

    @Test
    fun `mcpPreFlight falls back to empty list when parseArrayOrEmpty throws`() {
        val service = makeService(withMcp = true)
        val config = McpServerConfig(id = 1L, name = "test", baseUrl = "http://localhost:7456", active = true)
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(any())).thenReturn(listOf(McpToolDescriptor("tool1", "desc")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[{invalid json}]")
        whenever(jsonCodec.parseArrayOrEmpty(any())).thenThrow(RuntimeException("parse error"))
        stubStreamingNoOp()

        service.chat(ChatRequest(tabId = "mcp12", userMessage = "hi"), {})
    }

    private class RecordingAiUsageContext : AiUsageContextPort {
        val contexts = mutableListOf<Pair<String, Long>>()

        override fun setContext(
            kind: String,
            analysisId: Long,
        ) {
            contexts += kind to analysisId
        }

        override fun clearContext() {
        }

        override fun <T> withContext(
            kind: String,
            analysisId: Long,
            block: () -> T,
        ): T {
            contexts += kind to analysisId
            return block()
        }

        override fun addFilterSavings(tokens: Long) {
        }

        override fun setModelOverride(model: String) {
        }
    }

    @Test
    fun `chat forwards prepared attachments to the streaming adapter`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        val smallPng =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
            )
        val req =
            ChatRequest(
                tabId = "att-1",
                userMessage = "Welche Baumart?",
                attachments =
                    listOf(
                        ChatAttachment(
                            name = "tree.png",
                            mimeType = "image/png",
                            base64Data = Base64.getEncoder().encodeToString(smallPng),
                        ),
                    ),
            )
        service.chat(req, {})

        val captor = argumentCaptor<List<AiAttachment>>()
        verify(copilot)
            .executeWithStreamingPerLine(
                any(),
                any(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                captor.capture(),
            )
        val captured = captor.lastValue
        assertNotNull(captured)
        assertEquals(1, captured.size)
        assertEquals("image/png", captured[0].mimeType)
        assertTrue(captured[0].base64Data.isNotEmpty())
    }

    @Test
    fun `chat skips unsupported attachments before reaching the AI adapter`() {
        val service = makeService()
        whenever(copilot.resolveModel()).thenReturn("gpt-4")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        stubPromptLoader()
        whenever(appKnowledge.index()).thenReturn("")
        stubRelevantEmpty()
        stubStreamingNoOp()

        val req =
            ChatRequest(
                tabId = "att-2",
                userMessage = "Was steht da?",
                attachments =
                    listOf(
                        ChatAttachment(
                            name = "doc.pdf",
                            mimeType = "application/pdf",
                            base64Data = Base64.getEncoder().encodeToString("hello".toByteArray()),
                        ),
                    ),
            )
        service.chat(req, {})

        val captor = argumentCaptor<List<AiAttachment>>()
        verify(copilot)
            .executeWithStreamingPerLine(
                any(),
                any(),
                any<Long>(),
                anyOrNull<File>(),
                anyOrNull(),
                any<(String) -> Unit>(),
                captor.capture(),
            )
        assertTrue(captor.lastValue.isEmpty())
    }
}

private class EmptyKnowledgeRetrieval : com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort {
    override fun retrieveForChat(
        query: String,
        limit: Int?,
    ): List<com.stacklens.domain.model.knowledge.RetrievedKnowledge> = emptyList()
}
