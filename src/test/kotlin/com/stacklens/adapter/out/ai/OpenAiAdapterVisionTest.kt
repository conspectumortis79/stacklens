package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class OpenAiAdapterVisionTest {
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
        whenever(promptLoader.context(any())).thenReturn(PromptContext(mapOf("commontools.systemPrompt" to "system prompt")))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(baseUrl: String = server.url("/").toString().trimEnd('/')) =
        OpenAiAdapter(
            defaultOpenAiBaseUrl = baseUrl,
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
        )

    private val attachment = AiAttachment(mimeType = "image/png", base64Data = "AAA")

    @Test
    fun `execute with attachments uses chat completions vision body`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"vision result"}}]}"""),
        )
        val result = adapter().execute("gpt-4o", "describe", attachments = listOf(attachment))
        assertEquals("vision result", result)
        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        val body = recorded.body.readUtf8()
        assertTrue(recorded.path!!.endsWith("/v1/chat/completions"))
        assertTrue(body.contains("image_url"))
        assertTrue(body.contains("data:image/png;base64,AAA"))
    }

    @Test
    fun `execute with multiple attachments adds all image parts`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[]}"""),
        )
        val result =
            adapter().execute(
                "gpt-4o",
                "describe",
                attachments = listOf(attachment, AiAttachment("image/jpeg", "BBB")),
            )
        assertEquals("", result)
    }

    @Test
    fun `execute with attachments propagates http error`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        val e =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-4o", "describe", attachments = listOf(attachment))
            }
        assertTrue(e.message!!.contains("OpenAI error (HTTP 429)"))
    }

    @Test
    fun `parseChatCompletionsResponse returns empty for blank input`() {
        assertEquals("", adapter().parseChatCompletionsResponse(""))
        assertEquals("", adapter().parseChatCompletionsResponse("   "))
    }

    @Test
    fun `parseChatCompletionsResponse returns empty for invalid json`() {
        assertEquals("", adapter().parseChatCompletionsResponse("{not json"))
    }

    @Test
    fun `parseChatCompletionsResponse returns empty when choices missing or empty`() {
        assertEquals("", adapter().parseChatCompletionsResponse("""{}"""))
        assertEquals("", adapter().parseChatCompletionsResponse("""{"choices":[]}"""))
    }

    @Test
    fun `parseChatCompletionsResponse handles malformed choice entries`() {
        assertEquals("", adapter().parseChatCompletionsResponse("""{"choices":["x"]}"""))
        assertEquals("", adapter().parseChatCompletionsResponse("""{"choices":[{}]}"""))
        assertEquals("", adapter().parseChatCompletionsResponse("""{"choices":[{"message":{}}]}"""))
        assertEquals("", adapter().parseChatCompletionsResponse("""{"choices":[{"message":{"content":42}}]}"""))
    }

    @Test
    fun `buildRequestBody routes attachments to vision body`() {
        val body = adapter().buildRequestBody("gpt-4o", "prompt", listOf(attachment))
        assertTrue(body.contains("image_url"))
    }

    @Test
    fun `fetchAvailableModels filters non gpt models`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"id":"gpt-5.2"},{"id":"o3"},{"id":"dall-e-3"},{"id":"gpt-4o"},{"id":"babbage"}]}"""),
        )
        val models = adapter().fetchAvailableModels("sk-test")
        assertEquals(listOf("gpt-5.2", "gpt-4o"), models)
    }

    @Test
    fun `fetchAvailableModels returns empty when data missing`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        assertEquals(emptyList<String>(), adapter().fetchAvailableModels("sk-test"))
    }

    @Test
    fun `fetchAvailableModels errors on non success`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val e =
            assertThrows(IllegalStateException::class.java) {
                adapter().fetchAvailableModels("sk-test")
            }
        assertTrue(e.message!!.contains("/v1/models error"))
    }
}
