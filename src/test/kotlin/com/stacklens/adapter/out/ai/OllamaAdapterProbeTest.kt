package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.platform.ContainerRuntime
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
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

class OllamaAdapterProbeTest {
    private lateinit var server: MockWebServer

    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("Systemprompt")
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(
        config: AiConfig? = null,
        containerProbe: FakeContainerRuntimeProbePort = FakeContainerRuntimeProbePort(),
    ): OllamaAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(config)
        return OllamaAdapter(
            defaultOllamaUrl = server.url("/").toString().removeSuffix("/"),
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = ObjectMapper(),
            promptLoader = promptLoader,
            containerRuntimeProbe = containerProbe,
        )
    }

    private fun sse(chunks: List<String>): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(chunks.joinToString("") { "data: $it\n" } + "data: [DONE]\n")

    @Test
    fun `buildRequestBody uses the default no attachment shape`() {
        val body = adapter().buildRequestBody("llama3", "frag", stream = false)

        assertTrue(body.contains("\"stream\":false"), body)
        assertTrue(body.contains("\"content\":\"frag\""), body)
        assertTrue(!body.contains("image_url"), body)
    }

    @Test
    fun `buildRequestBody attaches images as data uris and keeps num_ctx from the config`() {
        val body =
            adapter(config = AiConfig(ollamaNumCtx = 4096))
                .buildRequestBody(
                    "llama3",
                    "frag",
                    stream = true,
                    attachments = listOf(AiAttachment(mimeType = "image/png", base64Data = "AA==")),
                )

        assertTrue(body.contains("\"images\":[\"AA==\"]"), body)
        assertTrue(body.contains("\"num_ctx\":4096"), body)
    }

    @Test
    fun `a streaming call with a nearly full context still returns the answer`() {
        server.enqueue(sse(listOf("""{"choices":[{"delta":{"content":"ok"}}]}""")))
        val hugePrompt = "x".repeat(4000)

        val answer =
            adapter(config = AiConfig(ollamaNumCtx = 1000))
                .executeWithStreaming("llama3", hugePrompt, onPartialOutput = { })

        assertEquals("ok", answer)
    }

    @Test
    fun `streaming stops at a cancellation between the chunks`() {
        server.enqueue(sse(listOf("""{"choices":[{"delta":{"content":"teil"}}]}""")))

        assertThrows(InterruptedException::class.java) {
            adapter().executeWithStreamingPerLine(
                model = "llama3",
                prompt = "frag",
                timeoutOverride = 5L,
                repoDir = null,
                isCancelled = { true },
                onPartialOutput = { },
            )
        }
    }

    @Test
    fun `a failing status code is reported with the response body`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("ollama ist tot"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().executeWithStreaming("llama3", "frag", onPartialOutput = { })
            }

        assertTrue(failure.message!!.contains("HTTP 500"), "unexpected: ${failure.message}")
        assertTrue(failure.message!!.contains("ollama ist tot"), "unexpected: ${failure.message}")
    }

    @Test
    fun `a broken json chunk is dropped without failing the stream`() {
        server.enqueue(sse(listOf("{kein json}", """{"choices":[{"delta":{"content:"text"}}]}""")))

        assertEquals("", adapter().executeWithStreaming("llama3", "frag", onPartialOutput = { }))
    }

    @Test
    fun `the repo tool loop writes markdown blocks of the final answer`(
        @TempDir tmp: Path,
    ) {
        val toolCall =
            """{"choices":[{"message":{"tool_calls":[{"id":"c1","function":{"name":"write","arguments":"{\"path\":\"src/Report.kt\",\"content\":\"fun report() = 1\"}"}}]}}]}"""
        val finalAnswer =
            """{"choices":[{"message":{"content":"## src/Summary.kt\n```kotlin\nfun summary() = 2\n```"}}]}"""
        server.enqueue(jsonBody(toolCall))
        server.enqueue(jsonBody(finalAnswer))

        val result =
            adapter().executeWithStreamingPerLine(
                model = "llama3",
                prompt = "baue zwei dateien",
                timeoutOverride = 10L,
                repoDir = tmp.toFile(),
                isCancelled = null,
                onPartialOutput = { },
            )

        assertTrue(result.contains("Summary.kt"), result)
        assertEquals("fun report() = 1", File(tmp.toFile(), "src/Report.kt").readText().trim())
    }

    @Test
    fun `the container probe decides whether the localhost url is rewritten`() {
        val config = AiConfig(ollamaUrl = "http://localhost:11434")

        val outside =
            adapter(containerProbe = FakeContainerRuntimeProbePort(ContainerRuntime.NONE))
                .let {
                    whenever(aiConfigRepo.findAiConfig()).thenReturn(config)
                    it
                }.resolveOllamaUrl()
        val inside =
            adapter(containerProbe = FakeContainerRuntimeProbePort(ContainerRuntime.DOCKER))
                .let {
                    whenever(aiConfigRepo.findAiConfig()).thenReturn(config)
                    it
                }.resolveOllamaUrl()

        assertEquals("http://localhost:11434", outside)
        assertEquals("http://host.docker.internal:11434", inside)
    }

    private fun jsonBody(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
