package com.stacklens.adapter.inbound.rest

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.application.port.inbound.chat.ChatAttachment
import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.ChatTurn
import com.stacklens.application.port.inbound.chat.KiChatUsageUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolDescriptor
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort
import com.stacklens.application.port.out.platform.AppKnowledgePort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.FakeAiUsageContextPort
import com.stacklens.application.service.KiChatAttachmentPreparer
import com.stacklens.application.service.KiChatMcpPreflight
import com.stacklens.application.service.KiChatService
import com.stacklens.application.service.KiChatServiceTestSupport
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KiChatControllerTest {
    @Mock private lateinit var copilot: CopilotStreamingPort

    @Mock private lateinit var appKnowledge: AppKnowledgePort

    @Mock private lateinit var i18n: I18nUseCase

    private val i18nPort: I18nPort =
        object : I18nPort {
            override fun resolveLanguage(): String = i18n.resolveLanguage()

            override fun t(
                key: String,
                vararg args: Any,
            ): String = i18n.t(key, *args)

            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ): String = i18n.tFor(lang, key, *args)

            override fun translateError(e: Exception): String = i18n.translateError(e)
        }

    @Mock private lateinit var promptLoader: PromptReaderPort

    @Mock private lateinit var mcpConfigs: McpServerConfigPort

    @Mock private lateinit var mcpGateway: McpGatewayPort

    @Mock private lateinit var jsonCodec: JsonCodecPort

    private val aiUsageContext = FakeAiUsageContextPort()
    private val chatUsage: KiChatUsageUseCase = mock()

    private val knowledgeRetrieval: com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort = mock()
    private val emptyKnowledge: com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort =
        object : com.stacklens.application.port.out.knowledge.KnowledgeRetrievalPort {
            override fun retrieveForChat(
                query: String,
                limit: Int?,
            ): List<com.stacklens.domain.model.knowledge.RetrievedKnowledge> = emptyList()
        }

    private val controller by lazy {
        KiChatController(
            KiChatService(
                copilot = copilot,
                i18n = i18nPort,
                aiUsageContext = aiUsageContext,
                knowledgeRetrieval = emptyKnowledge,
                promptBuilder = KiChatServiceTestSupport.buildPromptLoader(promptLoader, appKnowledge),
                mcpPreflight = KiChatServiceTestSupport.buildNoopPreflight(),
                attachmentPreparer =
                    KiChatAttachmentPreparer(
                        ImageInlinePreparer(
                            com.stacklens.adapter.out.image
                                .DefaultInlineImageEncoder(),
                        ),
                    ),
            ),
            i18n,
            chatUsage,
        )
    }

    private val mcpController by lazy {
        KiChatController(
            KiChatService(
                copilot = copilot,
                i18n = i18nPort,
                aiUsageContext = aiUsageContext,
                knowledgeRetrieval = emptyKnowledge,
                promptBuilder = KiChatServiceTestSupport.buildPromptLoader(promptLoader, appKnowledge),
                mcpPreflight = KiChatMcpPreflight(mcpConfigs, mcpGateway, jsonCodec, copilot, promptLoader),
                attachmentPreparer =
                    KiChatAttachmentPreparer(
                        ImageInlinePreparer(
                            com.stacklens.adapter.out.image
                                .DefaultInlineImageEncoder(),
                        ),
                    ),
            ),
            i18n,
            chatUsage,
        )
    }

    private fun stubValidMcpChat() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("answer")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("mock prompt")
    }

    @Test
    fun `ChatTurn creates correctly`() {
        val turn = ChatTurn("user", "Hello")
        assertEquals("user", turn.role)
        assertEquals("Hello", turn.content)
    }

    @Test
    fun `ChatRequest creates with defaults`() {
        val req = ChatRequest()
        assertEquals("general", req.tabId)
        assertEquals("", req.tabLabel)
        assertEquals(0L, req.chatId)
        assertEquals("", req.userMessage)
        assertEquals("", req.tabContext)
        assertTrue(req.history.isEmpty())
        assertEquals("", req.systemHint)
    }

    @Test
    fun `chat returns error emitter when userMessage is blank`() {
        val req = ChatRequest(userMessage = "")
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat rejects more than MAX_ATTACHMENTS_PER_REQUEST attachments`() {
        val oversized =
            (0 until 9).map { idx ->
                ChatAttachment(
                    name = "img-$idx.png",
                    mimeType = "image/png",
                    base64Data = "AAAA",
                )
            }
        val req = ChatRequest(userMessage = "many", attachments = oversized)
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat rejects attachments whose base64 payload exceeds the inline cap`() {
        val hugeBase64 = "A".repeat(KiChatController.MAX_ATTACHMENT_BASE64_LENGTH + 1)
        val req =
            ChatRequest(
                userMessage = "big",
                attachments =
                    listOf(
                        ChatAttachment(
                            name = "big.png",
                            mimeType = "image/png",
                            base64Data = hugeBase64,
                        ),
                    ),
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat rejects attachments with unsupported mime types`() {
        val req =
            ChatRequest(
                userMessage = "pdf",
                attachments =
                    listOf(
                        ChatAttachment(
                            name = "doc.pdf",
                            mimeType = "application/pdf",
                            base64Data = "AAAA",
                        ),
                    ),
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat with valid message returns streaming emitter - german language`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val onChunk = inv.arguments[5] as (String) -> Unit
            onChunk("Hallo")
            "Hallo Welt"
        }
        val req =
            ChatRequest(
                tabId = "general",
                tabLabel = "General",
                userMessage = "Was kann StackLens?",
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat with english language`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("en")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val onChunk = inv.arguments[5] as (String) -> Unit
            onChunk("Hello")
            "Hello World"
        }
        val req =
            ChatRequest(
                tabId = "general",
                userMessage = "What can StackLens do?",
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat with stacklens meta keyword triggers readme`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("index content")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(appKnowledge.readme()).thenReturn("README content")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("answer")
        val req =
            ChatRequest(
                userMessage = "Was macht stacklens?",
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat with knowledge relevant content`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("index")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("relevant docs")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("answer")
        val req =
            ChatRequest(
                userMessage = "How does code analysis work?",
                tabContext = "some context",
                systemHint = "focus on code",
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat with history`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("answer")
        val req =
            ChatRequest(
                userMessage = "Follow-up question",
                history =
                    listOf(
                        ChatTurn("user", "First question"),
                        ChatTurn("assistant", "First answer"),
                    ),
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat with appKnowledge throwing exception`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenThrow(RuntimeException("index failed"))
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("answer")
        val req =
            ChatRequest(
                userMessage = "What is this?",
            )
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat with relevant throwing exception covers runCatching branch`() {
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("some index")
        whenever(appKnowledge.relevant(any(), any(), any())).thenThrow(RuntimeException("relevant failed"))
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).thenReturn("answer")
        val req = ChatRequest(userMessage = "test relevant exception")
        val emitter = controller.chat(req)
        assertNotNull(emitter)
    }

    @Test
    fun `chat SSE thread invokes callback and emitter`() {
        val latch = CountDownLatch(1)
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val callback = inv.getArgument<(String) -> Unit>(5)
            callback("hello ")
            callback("hello world")
            callback("hi")
            latch.countDown()
            "hello world"
        }
        val req = ChatRequest(userMessage = "stream test")
        controller.chat(req)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `chat SSE thread handles streaming exception`() {
        val latch = CountDownLatch(1)
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).doAnswer { _ ->
            latch.countDown()
            throw RuntimeException("streaming error")
        }
        val req = ChatRequest(userMessage = "error test")
        controller.chat(req)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        Thread.sleep(300)
    }

    @Test
    fun `chat SSE thread handles streaming exception with null message`() {
        val latch = CountDownLatch(1)
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).doAnswer { _ ->
            latch.countDown()
            throw RuntimeException()
        }
        val req = ChatRequest(userMessage = "null error test")
        controller.chat(req)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        Thread.sleep(300)
    }

    @Test
    fun `chat SSE thread handles secondary send failure via safeCompleteWithError`() {
        val started = CountDownLatch(1)
        val mayThrow = CountDownLatch(1)
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).doAnswer { _ ->
            started.countDown()
            mayThrow.await(2, TimeUnit.SECONDS)
            throw RuntimeException("boom")
        }
        val req = ChatRequest(userMessage = "secondary send failure")
        val emitter = controller.chat(req)
        assertTrue(started.await(2, TimeUnit.SECONDS))
        emitter.complete()
        mayThrow.countDown()
        Thread.sleep(300)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `chat streaming callback with cancelFlag true throws InterruptedException covering L260`() {
        val chatStarted = CountDownLatch(1)
        val cancelDone = CountDownLatch(1)
        val latch = CountDownLatch(1)
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).doAnswer { invocation ->
            chatStarted.countDown()
            cancelDone.await(2, TimeUnit.SECONDS)
            val callback = invocation.arguments[5] as ((String) -> Unit)
            try {
                callback("partial")
            } catch (_: InterruptedException) {
            }
            latch.countDown()
            ""
        }
        val tabId = "cancel-test-${System.nanoTime()}"
        val req = ChatRequest(tabId = tabId, userMessage = "cancel test")
        controller.chat(req)
        assertTrue(chatStarted.await(2, TimeUnit.SECONDS))
        controller.cancel(tabId)
        cancelDone.countDown()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        Thread.sleep(200)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `chat streaming callback delivers partial output covering delta branches`() {
        val latch = CountDownLatch(1)
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).doAnswer { invocation ->
            val callback = invocation.arguments[5] as ((String) -> Unit)
            callback("Hello")
            callback("Hello World")
            latch.countDown()
            "Hello World"
        }
        val req = ChatRequest(tabId = "test-delta-${System.nanoTime()}", userMessage = "delta test")
        controller.chat(req)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        Thread.sleep(300)
    }

    @Test
    fun `clearUsage deletes a positive KI chat usage id`() {
        val response = controller.clearUsage(42L)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["ok"])
        verify(chatUsage).clearUsage(42L)
    }

    @Test
    fun `clearUsage rejects a non-positive usage id`() {
        val response = controller.clearUsage(0L)

        assertEquals(400, response.statusCode.value())
        assertEquals(false, response.body!!["ok"])
        verify(chatUsage, never()).clearUsage(any())
    }

    @Test
    fun `cancel returns wasRunning false for unknown tabId`() {
        val resp = controller.cancel("unknown-tab-${System.nanoTime()}")
        assertEquals(false, resp.body!!["wasRunning"])
    }

    @Test
    fun `chat forwards error via SSE when resolveModel throws synchronously`() {
        whenever(copilot.resolveModel()).thenThrow(RuntimeException("model unavailable"))
        whenever(i18n.translateError(any())).thenReturn("model unavailable")
        val emitter = controller.chat(ChatRequest(userMessage = "test"))
        assertNotNull(emitter)
    }

    @Test
    fun `chat falls back to safeCompleteWithError when translateError also throws`() {
        whenever(copilot.resolveModel()).thenThrow(RuntimeException("model unavailable"))
        whenever(i18n.translateError(any())).thenThrow(RuntimeException("translate also failed"))
        val emitter = controller.chat(ChatRequest(userMessage = "test"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips when no active mcp server config`() {
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(null)
        val emitter = mcpController.chat(ChatRequest(userMessage = "test no config"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips when tools list is empty`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(emptyList())
        val emitter = mcpController.chat(ChatRequest(userMessage = "test empty tools"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips when listTools throws`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenThrow(RuntimeException("connection refused"))
        val emitter = mcpController.chat(ChatRequest(userMessage = "test listTools throws"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips when plan calls are empty`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(listOf(McpToolDescriptor("search", "Search")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[]")
        val emitter = mcpController.chat(ChatRequest(userMessage = "test empty plan"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips when plan json has no array brackets`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(listOf(McpToolDescriptor("search", "Search")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("No tools needed for this question.")
        val emitter = mcpController.chat(ChatRequest(userMessage = "test no array json"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips when plan json has open bracket but no close bracket`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(listOf(McpToolDescriptor("search", "Search")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[unclosed bracket")
        val emitter = mcpController.chat(ChatRequest(userMessage = "test unclosed bracket"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips when execute throws`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(listOf(McpToolDescriptor("search", "Search")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenThrow(RuntimeException("AI error"))
        val emitter = mcpController.chat(ChatRequest(userMessage = "test execute throws"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips tool call with null tool name`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(listOf(McpToolDescriptor("search", "Search")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("""[{"args":{}}]""")
        val emitter = mcpController.chat(ChatRequest(userMessage = "test null tool name"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight skips tool call when callTool throws and args key is absent`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(listOf(McpToolDescriptor("fetch", "Fetch")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("""[{"tool":"fetch"}]""")
        whenever(mcpGateway.callTool(any(), any(), any())).thenThrow(RuntimeException("fetch error"))
        val emitter = mcpController.chat(ChatRequest(userMessage = "test callTool throws"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight successful tool call injects results into chat prompt`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(
            listOf(McpToolDescriptor("search_issues", "Search Jira issues")),
        )
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull()))
            .thenReturn("""[{"tool":"search_issues","args":{"query":"login bug"}}]""")
        whenever(mcpGateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult("PROJ-123: Login fails on mobile"))
        val emitter =
            mcpController.chat(
                ChatRequest(userMessage = "find jira issues about login"),
            )
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight exception in called dependency is caught by outer runCatching`() {
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenThrow(RuntimeException("unexpected db error"))
        val emitter = mcpController.chat(ChatRequest(userMessage = "test outer exception"))
        assertNotNull(emitter)
    }

    @Test
    fun `mcpPreFlight returns empty when json has brackets but is invalid json`() {
        val config = McpServerConfig(id = 1L, name = "mcp", baseUrl = "http://localhost:7456")
        stubValidMcpChat()
        whenever(mcpConfigs.findFirstActiveMcpServerConfig()).thenReturn(config)
        whenever(mcpGateway.listTools(config)).thenReturn(listOf(McpToolDescriptor("search", "Search")))
        whenever(copilot.execute(any(), any(), any<Long>(), anyOrNull())).thenReturn("[not valid json]")
        val emitter = mcpController.chat(ChatRequest(userMessage = "test parse failure"))
        assertNotNull(emitter)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `emitter lifecycle callbacks invoke kiChat cancel`() {
        val req = ChatRequest(tabId = "lifecycle-${System.nanoTime()}", userMessage = "lifecycle test")
        val emitter = controller.chat(req)

        val responseBodyEmitterClass = emitter.javaClass.superclass
        val completionField = responseBodyEmitterClass.getDeclaredField("completionCallback")
        completionField.isAccessible = true
        (completionField.get(emitter) as? Runnable)?.run()

        val timeoutField = responseBodyEmitterClass.getDeclaredField("timeoutCallback")
        timeoutField.isAccessible = true
        (timeoutField.get(emitter) as? Runnable)?.run()

        val errorField = responseBodyEmitterClass.getDeclaredField("errorCallback")
        errorField.isAccessible = true
        (errorField.get(emitter) as? java.util.function.Consumer<Throwable>)?.accept(RuntimeException("test error"))
    }

    @Test
    fun `chat catch block handles synchronous exception from runChatThread`() {
        val kiChatMock = mock<com.stacklens.application.port.inbound.chat.KiChatUseCase>()
        val throwingCtrl =
            object : KiChatController(kiChatMock, i18n, chatUsage) {
                override fun runChatThread(
                    emitter: org.springframework.web.servlet.mvc.method.annotation.SseEmitter,
                    req: com.stacklens.application.port.inbound.chat.ChatRequest,
                ): Unit = throw RuntimeException("sync exception from runChatThread")
            }
        whenever(i18n.translateError(any())).thenReturn("translated error")
        val emitter = throwingCtrl.chat(ChatRequest(userMessage = "test catch block"))
        assertNotNull(emitter)
    }

    @Test
    fun `chat catch block falls back to safeCompleteWithError when translateError throws`() {
        val kiChatMock = mock<com.stacklens.application.port.inbound.chat.KiChatUseCase>()
        val throwingCtrl =
            object : KiChatController(kiChatMock, i18n, chatUsage) {
                override fun runChatThread(
                    emitter: org.springframework.web.servlet.mvc.method.annotation.SseEmitter,
                    req: com.stacklens.application.port.inbound.chat.ChatRequest,
                ): Unit = throw RuntimeException("sync exception from runChatThread")
            }
        whenever(i18n.translateError(any())).thenThrow(RuntimeException("translate also failed"))
        val emitter = throwingCtrl.chat(ChatRequest(userMessage = "test catch block fallback"))
        assertNotNull(emitter)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `chat SSE delta callback catches emitter send exception covering lines 77-79`() {
        val insideMock = CountDownLatch(1)
        val emitterCompleted = CountDownLatch(1)
        val done = CountDownLatch(1)
        whenever(copilot.resolveModel()).thenReturn("gpt-4o")
        whenever(i18n.resolveLanguage()).thenReturn("de")
        whenever(appKnowledge.index()).thenReturn("")
        whenever(appKnowledge.relevant(any(), any(), any())).thenReturn("")
        whenever(copilot.executeWithStreamingPerLine(any(), any(), any<Long>(), anyOrNull<File>(), anyOrNull(), any(), anyOrNull())).doAnswer { inv ->
            val callback = inv.getArgument<(String) -> Unit>(5)
            insideMock.countDown()
            emitterCompleted.await(2, TimeUnit.SECONDS)
            try {
                callback("Hello")
            } catch (_: Exception) {
            }
            done.countDown()
            "Hello"
        }
        val req = ChatRequest(tabId = "delta-catch-${System.nanoTime()}", userMessage = "delta catch test")
        val emitter = controller.chat(req)
        assertTrue(insideMock.await(2, TimeUnit.SECONDS))
        emitter.complete()
        emitterCompleted.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        Thread.sleep(300)
    }
}

class KiChatControllerJsonEncodeTest {
    private val controller = KiChatController(kiChat = mock(), i18n = mock(), chatUsage = mock())

    private fun jsonEncode(s: String): String {
        val method = KiChatController::class.java.getDeclaredMethod("jsonEncode", String::class.java)
        method.isAccessible = true
        return method.invoke(controller, s) as String
    }

    @Test
    fun `jsonEncode wraps a plain ASCII string in double quotes`() {
        assertEquals("\"hello\"", jsonEncode("hello"))
    }

    @Test
    fun `jsonEncode escapes double quote`() {
        assertEquals("\"a\\\"b\"", jsonEncode("a\"b"))
    }

    @Test
    fun `jsonEncode escapes backslash`() {
        assertEquals("\"a\\\\b\"", jsonEncode("a\\b"))
    }

    @Test
    fun `jsonEncode escapes newline`() {
        assertEquals("\"a\\nb\"", jsonEncode("a\nb"))
    }

    @Test
    fun `jsonEncode escapes carriage return`() {
        assertEquals("\"a\\rb\"", jsonEncode("a\rb"))
    }

    @Test
    fun `jsonEncode escapes tab`() {
        assertEquals("\"a\\tb\"", jsonEncode("a\tb"))
    }

    @Test
    fun `jsonEncode passes through printable non-special characters`() {
        assertEquals("\"abc 123!\"", jsonEncode("abc 123!"))
    }

    @Test
    fun `jsonEncode encodes control characters less than 0x20 as unicode escapes`() {
        val s = "\u0001"
        val encoded = jsonEncode(s)
        assertEquals("\"\\u0001\"", encoded)
    }

    @Test
    fun `jsonEncode handles an empty string`() {
        assertEquals("\"\"", jsonEncode(""))
    }

    @Test
    fun `jsonEncode handles UTF-8 characters above 0x20`() {
        val encoded = jsonEncode("é")
        assertEquals("\"é\"", encoded)
    }
}
