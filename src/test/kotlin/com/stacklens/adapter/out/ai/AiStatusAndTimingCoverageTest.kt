package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptContext
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.DelayedInputStream
import com.stacklens.testsupport.FlakyInputStream
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.Optional
import javax.net.ssl.SSLSession

class AiStatusAndTimingCoverageTest {
    private val aiConfigRepo: AiConfigPort = mock()

    private val promptLoader: PromptReaderPort = mock()

    private val executor: AiHttpClientExecutor = mock()

    private val objectMapper = ObjectMapper()

    @TempDir
    lateinit var workDir: Path

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
        whenever(promptLoader.context(any()))
            .thenReturn(PromptContext(mapOf("commontools.systemPrompt" to "system prompt")))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun <T> response(
        status: Int,
        body: T,
    ): HttpResponse<T> =
        object : HttpResponse<T> {
            override fun statusCode(): Int = status

            override fun request(): HttpRequest = throw UnsupportedOperationException()

            override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()

            override fun headers(): HttpHeaders = HttpHeaders.of(mapOf<String, List<String>>()) { _, _ -> true }

            override fun body(): T = body

            override fun sslSession(): Optional<SSLSession> = Optional.empty()

            override fun uri(): URI = URI.create("http://ai.invalid/v1/chat/completions")

            override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
        }

    private fun stubString(status: Int) {
        whenever(executor.sendWithTimeout<String>(any(), any(), any(), any(), anyOrNull())).thenReturn(response(status, "informational"))
        whenever(executor.sendWithTimeout<String>(any(), any(), any(), any())).thenReturn(response(status, "informational"))
    }

    private fun stubStream(
        status: Int,
        body: InputStream,
    ) {
        whenever(executor.sendWithTimeout<InputStream>(any(), any(), any(), any(), anyOrNull())).thenReturn(response(status, body))
        whenever(executor.sendWithTimeout<InputStream>(any(), any(), any(), any())).thenReturn(response(status, body))
    }

    private fun invoke(
        target: Any,
        name: String,
        vararg args: Any?,
    ): Any? {
        val method = target.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true
        return try {
            method.invoke(target, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    private fun lmStudioClient(): LmStudioApiClient {
        val client = LmStudioApiClient(objectMapper)
        val field = LmStudioApiClient::class.java.getDeclaredField("aiHttpClientExecutor")
        field.isAccessible = true
        field.set(client, executor)
        return client
    }

    @Test
    fun `a status below the success range still warns during model loading`() {
        stubString(100)

        lmStudioClient().loadModel("http://ai.invalid", "llama", 5L)
    }

    @Test
    fun `a status below the success range fails a compatible chat request`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(compatibleApiKey = "key"))
        stubString(100)

        val adapter =
            OpenAiCompatibleAdapter(
                endpoint =
                    OpenAiCompatibleEndpoint(
                        label = "Compatible",
                        fallbackBaseUrl = "http://ai.invalid",
                        defaultModel = "provider/model",
                        apiKeyOf = { config -> config?.compatibleApiKey },
                        baseUrlOf = { config -> config?.compatibleBaseUrl },
                    ),
                timeoutSeconds = 5L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
                aiHttpClientExecutor = executor,
            )

        val failure =
            assertThrows(IllegalStateException::class.java) {
                invoke(adapter, "sendChatRequest", "{}", "key", 5L, null)
            }

        assertTrue(failure.message!!.contains("Compatible error (HTTP 100)"), failure.message)
    }

    @Test
    fun `an error body that breaks while it is read is reported as an io failure`() {
        stubStream(500, FlakyInputStream(failOnRead = true))

        assertThrows(IOException::class.java) {
            invoke(lmStudioClient(), "streamRequest", "http://ai.invalid", "{}", 5L, 0L, null, { _: String -> })
        }
    }

    @Test
    fun `a stream that slows down flushes the buffered text again`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(null)
        val newline = "\n".toByteArray()
        stubStream(
            200,
            DelayedInputStream(
                chunks =
                    listOf(
                        """data: {"choices":[{"delta":{"content":"Hel"}}]}""".toByteArray() + newline,
                        """data: {"choices":[{"delta":{"content":"lo"}}]}""".toByteArray() + newline,
                    ),
                pauseFrom = 1,
                pauseMillis = 1_100L,
            ),
        )

        val partials = mutableListOf<String>()
        val answer =
            OllamaAdapter(
                defaultOllamaUrl = "http://ai.invalid",
                timeoutSeconds = 5L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
                httpClient = mock<HttpClient>(),
                aiHttpClientExecutor = executor,
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            ).executeWithStreaming("llama", "prompt", null, null, { partials += it }, emptyList())

        assertEquals("Hello", answer)
        assertEquals(listOf("Hello"), partials)
    }

    @Test
    fun `a repo tool run loads the model before the first iteration`() {
        val baseUrl = server.url("/").toString().trimEnd('/')
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(lmStudioUrl = baseUrl, model = "local-model", language = "en"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"done"},"finish_reason":"stop"}]}"""),
        )

        val adapter =
            LmStudioAdapter(
                defaultLmStudioUrl = baseUrl,
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = promptLoader,
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )

        val answer =
            adapter.executeWithStreamingPerLine(
                model = "local-model",
                prompt = "prompt",
                timeoutOverride = null,
                repoDir = workDir.toFile(),
                isCancelled = null,
                onPartialOutput = { },
                attachments = emptyList(),
            )

        assertEquals("done", answer)
        assertEquals(3, server.requestCount)
    }
}
