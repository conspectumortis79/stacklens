package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class ClaudeCodeAdapterHelpersTest {
    private lateinit var server: MockWebServer
    private lateinit var aiConfigRepo: AiConfigPort
    private lateinit var promptLoader: PromptReaderPort
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        aiConfigRepo = mock()
        promptLoader = mock()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(baseUrl: String = server.url("/").toString().trimEnd('/')) =
        ClaudeCodeAdapter(
            defaultBaseUrl = baseUrl,
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.lookupOfStoredKey(aiConfigRepo),
            settings = ClaudeCodeTestSupport.settings(),
        )

    private fun aiConfig(
        claudeCodeApiKey: String = "sk-ant-test",
        model: String = "claude-sonnet-4-5",
        language: String = "de",
    ) = AiConfig(claudeCodeApiKey = claudeCodeApiKey, model = model, language = language)

    @Test
    fun `parseMessagesResponse concatenates text blocks from content array`() {
        val json =
            """{"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world"}]}"""
        assertEquals("Hello world", adapter().parseMessagesResponse(json))
    }

    @Test
    fun `parseMessagesResponse returns empty string when content array is empty`() {
        val json = """{"content":[]}"""
        assertEquals("", adapter().parseMessagesResponse(json))
    }

    @Test
    fun `parseMessagesResponse returns empty string when content is missing`() {
        val json = """{"other":"value"}"""
        assertEquals("", adapter().parseMessagesResponse(json))
    }

    @Test
    fun `parseMessagesResponse ignores non-text content blocks`() {
        val json =
            """{"content":[{"type":"text","text":"answer"},{"type":"tool_use","id":"x","name":"y","input":{}}]}"""
        assertEquals("answer", adapter().parseMessagesResponse(json))
    }

    @Test
    fun `parseMessagesResponse returns empty string for blank body`() {
        assertEquals("", adapter().parseMessagesResponse(""))
    }

    @Test
    fun `parseMessagesResponse returns empty string for non-JSON body`() {
        assertEquals("", adapter().parseMessagesResponse("not json at all"))
    }

    @Test
    fun `parseMessagesResponse ignores text fields inside non-text blocks`() {
        val json =
            """{"content":[{"type":"tool_use","text":"should not appear","id":"x","name":"y","input":{}}]}"""
        assertEquals("", adapter().parseMessagesResponse(json))
    }

    @Test
    fun `buildRequestBody includes model, system, max_tokens and user message`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val body = adapter().buildRequestBody("claude-sonnet-4-5", "hello")
        val parsed = objectMapper.readTree(body)
        assertEquals("claude-sonnet-4-5", parsed.path("model").asText())
        assertNotNull(parsed.path("max_tokens"))
        assertTrue(parsed.path("max_tokens").asInt() > 0)
        assertEquals("system prompt", parsed.path("system").asText())
        val messages = parsed.path("messages")
        assertTrue(messages.isArray)
        assertEquals("user", messages[0].path("role").asText())
        assertEquals("hello", messages[0].path("content").asText())
    }

    @Test
    fun `buildToolRequestBody includes tools and omits the leading system message`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("SYS")
        val body =
            adapter().buildToolRequestBody(
                "claude-sonnet-4-5",
                listOf(mapOf("role" to "user", "content" to "hi")),
            )
        val parsed = objectMapper.readTree(body)
        assertEquals("SYS", parsed.path("system").asText())
        assertTrue(parsed.path("tools").isArray)
        assertTrue(parsed.path("tools").size() > 0)
        val messages = parsed.path("messages")
        assertEquals("user", messages[0].path("role").asText())
    }

    @Test
    fun `resolveSystemPrompt delegates to promptLoader`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(language = "en"))
        whenever(promptLoader.get("en", "commontools.systemPrompt")).thenReturn("the prompt")
        assertEquals("the prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `resolveSystemPrompt uses default language when config missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        whenever(promptLoader.get("de", "commontools.systemPrompt")).thenReturn("der Prompt")
        assertEquals("der Prompt", adapter().resolveSystemPrompt())
    }

    @Test
    fun `execute returns concatenated text on success`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"content":[{"type":"text","text":"the "},{"type":"text","text":"answer"}],"usage":{"input_tokens":4,"output_tokens":2}}""",
                ),
        )
        val result = adapter().execute("claude-sonnet-4-5", "hello")
        assertEquals("the answer", result)
    }

    @Test
    fun `execute returns empty string when content missing in response`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":[]}"""),
        )
        val result = adapter().execute("claude-sonnet-4-5", "hello")
        assertEquals("", result)
    }

    @Test
    fun `execute throws on HTTP 500`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-test"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        try {
            adapter().execute("claude-sonnet-4-5", "hello")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("Expected an exception but none was thrown")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("500") || e.message!!.contains("error"))
        }
    }

    @Test
    fun `execute sends x-api-key header and anthropic-version header`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-secret"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":[{"type":"text","text":"ok"}]}"""),
        )
        adapter().execute("claude-sonnet-4-5", "hello")
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("sk-ant-secret", request!!.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
    }

    @Test
    fun `execute retries with Authorization Bearer when x-api-key returns 401`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-oauth01-setup-token-xyz"))
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody(""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":[{"type":"text","text":"ok"}]}"""),
        )
        val result = adapter().execute("claude-sonnet-4-5", "hello")
        assertEquals("ok", result)

        val first = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(first)
        assertEquals("sk-ant-oauth01-setup-token-xyz", first!!.getHeader("x-api-key"))

        val second = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(second)
        assertEquals("Bearer sk-ant-oauth01-setup-token-xyz", second!!.getHeader("Authorization"))
        assertEquals(null, second.getHeader("x-api-key"))
    }

    @Test
    fun `execute skips x-api-key and goes straight to Authorization Bearer for sk-ant-oat01- tokens`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-oat01-verylongtoken1234"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":[{"type":"text","text":"ok"}]}"""),
        )
        val result = adapter().execute("claude-sonnet-4-5", "hello")
        assertEquals("ok", result)

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("Bearer sk-ant-oat01-verylongtoken1234", request!!.getHeader("Authorization"))
        assertEquals(null, request.getHeader("x-api-key"))
        assertEquals(1, server.requestCount, "exactly one request must be made for OAuth setup-tokens")
    }

    @Test
    fun `execute surfaces the error when both x-api-key and Bearer return 401`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-revoked"))
        server.enqueue(MockResponse().setResponseCode(401).setBody(""))
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""),
        )
        try {
            adapter().execute("claude-sonnet-4-5", "hello")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("Expected an exception but none was thrown")
        } catch (e: Exception) {
            val msg = e.message ?: ""
            assertTrue(
                msg.contains("401"),
                "Error must surface the HTTP status so the user can diagnose, got: $msg",
            )
        }
    }

    @Test
    fun `execute does not retry on non-401 errors`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"type":"error","error":{"type":"invalid_request_error","message":"model: claude-sonnet-4-5 not found"}}"""),
        )
        try {
            adapter().execute("claude-sonnet-4-5", "hello")
            org.junit.jupiter.api.Assertions
                .fail<Unit>("Expected an exception but none was thrown")
        } catch (e: Exception) {
            val msg = e.message ?: ""
            assertTrue(
                msg.contains("400"),
                "Error must surface the HTTP status, got: $msg",
            )
        }
        val first = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(first)
        assertEquals("sk-ant-test", first!!.getHeader("x-api-key"))
        assertEquals(null, first.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `execute posts to v1 messages endpoint`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig(claudeCodeApiKey = "sk-ant-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":[{"type":"text","text":"ok"}]}"""),
        )
        adapter().execute("claude-sonnet-4-5", "hello")
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("POST", request!!.method)
        assertTrue(request.path!!.endsWith("/v1/messages"))
    }

    @Test
    fun `processToolLoopIteration continues loop when tool_use block present`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val respJson =
            """{"content":[{"type":"text","text":"reading"},{"type":"tool_use","id":"tu_1","name":"read_file","input":{"path":"README.md"}}],"stop_reason":"tool_use"}"""
        val messages =
            mutableListOf<Map<String, Any>>(
                mapOf("role" to "user", "content" to "show me README"),
            )
        val allText = StringBuilder()
        val keepGoing =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = respJson,
                messages = messages,
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertTrue(keepGoing)
        assertEquals(3, messages.size)
        val assistant = messages[1]
        assertEquals("assistant", assistant["role"])
        @Suppress("UNCHECKED_CAST")
        val content = assistant["content"] as List<Map<String, Any>>
        assertEquals(2, content.size)
        assertEquals("text", content[0]["type"])
        assertEquals("tool_use", content[1]["type"])
        val toolResultMsg = messages[2]
        assertEquals("user", toolResultMsg["role"])
        @Suppress("UNCHECKED_CAST")
        val toolResultContent = toolResultMsg["content"] as List<Map<String, Any>>
        assertEquals(1, toolResultContent.size)
        assertEquals("tool_result", toolResultContent[0]["type"])
        assertEquals("tu_1", toolResultContent[0]["tool_use_id"])
    }

    @Test
    fun `processToolLoopIteration terminates loop on end_turn without tool_use`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig())
        val respJson =
            """{"content":[{"type":"text","text":"done"}],"stop_reason":"end_turn"}"""
        val messages =
            mutableListOf<Map<String, Any>>(
                mapOf("role" to "user", "content" to "hi"),
            )
        val allText = StringBuilder()
        val keepGoing =
            adapter().processToolLoopIteration(
                iteration = 1,
                respJson = respJson,
                messages = messages,
                allText = allText,
                repoDir = java.io.File("."),
                onPartialOutput = {},
            )
        assertEquals(false, keepGoing)
        assertEquals(1, messages.size)
        assertTrue(allText.toString().contains("done"))
    }

    @Test
    fun `buildRequestBody converts attachments into Anthropic image content blocks`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(aiConfig("sk-ant-test"))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("MY_SYSTEM")
        val attachment =
            com.stacklens.application.port.out.ai.AiAttachment(
                mimeType = "image/jpeg",
                base64Data = "BASE64DATA",
            )
        val body = adapter().buildRequestBody("claude-sonnet-4-5", "describe this", listOf(attachment))
        val parsed =
            com.fasterxml.jackson.databind
                .ObjectMapper()
                .readTree(body)
        assertEquals("claude-sonnet-4-5", parsed.path("model").asText())
        val userMessage = parsed.path("messages").path(0)
        assertEquals("user", userMessage.path("role").asText())
        val content = userMessage.path("content")
        assertTrue(content.isArray, "user.content must be an array when attachments are present")
        assertEquals(2, content.size())
        assertEquals("text", content.path(0).path("type").asText())
        assertEquals("describe this", content.path(0).path("text").asText())
        assertEquals("image", content.path(1).path("type").asText())
        assertEquals(
            "base64",
            content
                .path(1)
                .path("source")
                .path("type")
                .asText(),
        )
        assertEquals(
            "image/jpeg",
            content
                .path(1)
                .path("source")
                .path("media_type")
                .asText(),
        )
        assertEquals(
            "BASE64DATA",
            content
                .path(1)
                .path("source")
                .path("data")
                .asText(),
        )
    }
}
