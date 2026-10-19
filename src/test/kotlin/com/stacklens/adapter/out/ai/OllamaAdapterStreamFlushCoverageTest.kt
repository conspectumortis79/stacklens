package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import javax.net.ssl.SSLSession

class OllamaAdapterStreamFlushCoverageTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val httpExecutor: AiHttpClientExecutor = mock()
    private val objectMapper = ObjectMapper()

    private fun adapter() =
        OllamaAdapter(
            defaultOllamaUrl = "http://ollama.invalid",
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            httpClient = mock<HttpClient>(),
            aiHttpClientExecutor = httpExecutor,
            containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
        )

    @BeforeEach
    fun stubConfig() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system")
    }

    private class StreamResponse(
        private val status: Int,
        private val payload: String,
    ) : HttpResponse<ByteArrayInputStream> {
        override fun statusCode(): Int = status

        override fun request(): HttpRequest = throw UnsupportedOperationException()

        override fun previousResponse(): Optional<HttpResponse<ByteArrayInputStream>> = Optional.empty()

        override fun headers(): HttpHeaders = HttpHeaders.of(mapOf<String, List<String>>()) { _, _ -> true }

        override fun body(): ByteArrayInputStream = ByteArrayInputStream(payload.toByteArray())

        override fun sslSession(): Optional<SSLSession> = Optional.empty()

        override fun uri(): URI = URI.create("http://ollama.invalid/v1/chat/completions")

        override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
    }

    private fun stub(
        status: Int,
        payload: String,
    ) {
        val response: HttpResponse<ByteArrayInputStream> = StreamResponse(status, payload)
        doReturn(response)
            .whenever(httpExecutor)
            .sendWithTimeout<ByteArrayInputStream>(any(), any(), any(), any(), anyOrNull())
    }

    private fun chunk(text: String) = """data: {"choices":[{"delta":{"content":"$text"}}]}"""

    @Test
    fun `a non 200 answer reports the error body`() {
        stub(500, "model not found")

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter().executeWithStreamingPerLine("llama", "prompt", null, null, null, {}, emptyList())
            }

        assertTrue(failure.message!!.startsWith("Ollama error (HTTP 500): model not found"), failure.message)
    }

    @Test
    fun `a cancellation observed between the streamed lines aborts the reader`() {
        stub(200, "${chunk("a")}\n${chunk("b")}\n")

        var polls = 0
        assertThrows(InterruptedException::class.java) {
            adapter().executeWithStreamingPerLine("llama", "prompt", null, null, {
                ++polls
                polls > 1
            }, {}, emptyList())
        }
        assertTrue(polls >= 2)
    }

    @Test
    fun `a polling canceller that never fires streams the whole answer`() {
        stub(200, "${chunk("Hel")}\n${chunk("lo")}\ndata: [DONE]\n")

        val answer = adapter().executeWithStreamingPerLine("llama", "prompt", null, null, { false }, {}, emptyList())

        assertEquals("Hello", answer)
    }

    @Test
    fun `a zero flush interval emits every partial buffer`() {
        stub(200, "${chunk("Hel")}\n${chunk("lo")}\n")

        val partials = mutableListOf<String>()
        adapter().executeWithStreamingPerLine("llama", "prompt", null, null, null, { partials += it }, emptyList())

        assertEquals(listOf("Hel", "Hello"), partials)
    }

    @Test
    fun `a positive flush interval suppresses partials that arrive too early`() {
        stub(200, "${chunk("Hel")}\n${chunk("lo")}\n")

        val partials = mutableListOf<String>()
        val answer =
            adapter().executeWithStreaming(
                model = "llama",
                prompt = "prompt",
                timeoutOverride = null,
                isCancelled = null,
                onPartialOutput = { partials += it },
                attachments = emptyList(),
            )

        assertEquals("Hello", answer)
        assertTrue(partials.isEmpty(), "no partial should be pushed inside the flush interval: $partials")
    }

    @Test
    fun `a throwing partial callback is swallowed and a broken json line is dropped`() {
        stub(200, "${chunk("Hel")}\ndata: {not json}\n${chunk("lo")}\n")

        val answer =
            adapter().executeWithStreamingPerLine("llama", "prompt", null, null, null, { throw IllegalStateException("boom") }, emptyList())

        assertEquals("Hello", answer)
    }
}
