package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
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

class OpenAiAdapterHttpErrorTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo = mock<AiConfigPort>()

    private val promptLoader = mock<PromptReaderPort>()

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun adapter() =
        OpenAiAdapter(
            defaultOpenAiBaseUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = ObjectMapper(),
            promptLoader = promptLoader,
        )

    private fun failure(body: String) = MockResponse().setResponseCode(500).setHeader("Content-Type", "application/json").setBody(body)

    private fun sendToolRequest(
        target: OpenAiAdapter,
        body: String,
    ): String {
        val method =
            OpenAiAdapter::class.java.getDeclaredMethod(
                "sendToolRequest",
                String::class.java,
                Long::class.javaPrimitiveType,
            )
        method.isAccessible = true
        return try {
            method.invoke(target, body, 10L) as String
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    @Test
    fun `a failing chat completion answer is reported with its status and body`() {
        server.enqueue(failure("""{"error":{"message":"quota"}}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("gpt-test", "prompt", null, emptyList())
            }

        assertTrue(failure.message!!.contains("OpenAI error (HTTP 500)"), failure.message)
    }

    @Test
    fun `a failing model listing is reported with its status and body`() {
        server.enqueue(failure("""{"error":"nope"}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().fetchAvailableModels("sk-test")
            }

        assertTrue(failure.message!!.contains("HTTP 500"), failure.message)
    }

    @Test
    fun `a failing tool request is reported with its status and body`() {
        server.enqueue(failure("""{"error":"tool gateway"}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) { sendToolRequest(adapter(), "{}") }

        assertTrue(failure.message!!.contains("OpenAI error (HTTP 500)"), failure.message)
    }

    @Test
    fun `a tool request without any api key refuses to run`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        if (!System.getenv("OPENAI_API_KEY").isNullOrBlank()) return

        val failure =
            assertThrows(IllegalStateException::class.java) { sendToolRequest(adapter(), "{}") }

        assertEquals("No OpenAI API key set.", failure.message)
    }

    @Test
    fun `the responses api tolerates output items that are no objects`() {
        val json =
            """{"output":[5,{"content":"kein array"},
               {"content":[{"text":"textausgabe"}]},{"content":[{"other":1}]}]}"""

        assertEquals("textausgabe", adapter().parseResponsesApiResponse(json))
    }
}
