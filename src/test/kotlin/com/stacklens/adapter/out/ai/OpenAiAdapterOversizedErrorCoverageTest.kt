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
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class OpenAiAdapterOversizedErrorCoverageTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val objectMapper = ObjectMapper()

    @TempDir
    lateinit var workDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk-test"))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    private fun adapter() =
        OpenAiAdapter(
            defaultOpenAiBaseUrl = baseUrl,
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
        )

    private val oversized = "e".repeat(430)

    private fun enqueueOversizedError() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Content-Type", "text/plain").setBody(oversized))
    }

    private fun invokePrivate(
        name: String,
        vararg args: Any?,
    ): Any? {
        val method =
            OpenAiAdapter::class.java.declaredMethods.firstOrNull { it.name == name && it.parameterCount == args.size }
                ?: throw NoSuchElementException("no method $name with ${args.size} parameters")
        method.isAccessible = true
        return try {
            method.invoke(adapter(), *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    private fun assertTruncated(action: () -> Unit) {
        val failure = assertThrows(IllegalStateException::class.java, action)
        assertTrue(failure.message!!.endsWith("e".repeat(400)), failure.message)
    }

    @Test
    fun `the chat completion tool request truncates an oversized error body`() {
        enqueueOversizedError()

        assertTruncated { invokePrivate("sendToolRequestWithAuth", baseUrl, "tok", "{}", 5L, null) }
    }

    @Test
    fun `the responses request truncates an oversized error body`() {
        enqueueOversizedError()

        assertTruncated { invokePrivate("sendRequest", baseUrl, "tok", "{}", 5L, null) }
    }

    @Test
    fun `the chat completions request truncates an oversized error body`() {
        enqueueOversizedError()

        assertTruncated { invokePrivate("sendChatCompletionsRequest", baseUrl, "tok", "{}", 5L, null) }
    }

    @Test
    fun `the model listing truncates an oversized error body`() {
        enqueueOversizedError()

        assertTruncated { adapter().fetchAvailableModels("sk-test") }
    }

    @Test
    fun `a non-object entry inside the responses output is skipped`() {
        val json = """{"output":[{"content":[{"text":"kept"}]},"stray",42]}"""

        assertEquals("kept", adapter().parseResponsesApiResponse(json))
    }

    @Test
    fun `a markdown file block in the final answer is extracted into the repository`() {
        val answer =
            "## src/main/kotlin/Generated.kt\n```kotlin\nval generated = 1\n```\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"finish_reason":"stop","message":{"content":${objectMapper.writeValueAsString(answer)}}}]}""",
                ),
        )

        adapter().executeWithStreamingPerLine("gpt-test", "prompt", null, workDir.toFile(), null, {}, emptyList())

        assertTrue(File(workDir.toFile(), "src/main/kotlin/Generated.kt").exists())
    }
}
