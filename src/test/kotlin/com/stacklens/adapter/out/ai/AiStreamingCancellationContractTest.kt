package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import com.stacklens.testsupport.FakeContainerRuntimeProbePort
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit

class AiStreamingCancellationContractTest {
    private lateinit var server: MockWebServer
    private lateinit var aiConfigRepo: AiConfigPort
    private lateinit var promptLoader: PromptReaderPort
    private val objectMapper = ObjectMapper()

    private val baseUrl: String
        get() = server.url("/").toString().trimEnd('/')

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        aiConfigRepo = mock()
        promptLoader = mock()
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system prompt")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(
            AiConfig(
                model = "test-model",
                language = "de",
                openAiApiKey = "sk-test",
                openRouterApiKey = "sk-test",
                claudeCodeApiKey = "sk-test",
                compatibleApiKey = "sk-test",
                ollamaUrl = baseUrl,
                lmStudioUrl = baseUrl,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun hangingDispatcher() =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.endsWith("/api/v1/models") == true) {
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"models":[{"key":"test-model","loaded_instances":[{"id":"1"}]}]}""")
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("{}")
                        .setHeadersDelay(3, TimeUnit.SECONDS)
                }
        }

    private fun answeringDispatcher(body: String) =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
        }

    private fun httpProviders(): List<Pair<String, CopilotStreamingPort>> =
        listOf(
            "OpenAI" to
                OpenAiAdapter(
                    defaultOpenAiBaseUrl = baseUrl,
                    timeoutSeconds = 10L,
                    aiConfigRepo = aiConfigRepo,
                    objectMapper = objectMapper,
                    promptLoader = promptLoader,
                ),
            "ClaudeCode" to
                ClaudeCodeAdapter(
                    defaultBaseUrl = baseUrl,
                    timeoutSeconds = 10L,
                    aiConfigRepo = aiConfigRepo,
                    objectMapper = objectMapper,
                    promptLoader = promptLoader,
                    tokenLookup = ClaudeCodeTestSupport.tokenLookup("sk-ant-oat01-contract"),
                    settings = ClaudeCodeTestSupport.settings(),
                ),
            "OpenAI-compatible" to
                OpenAiCompatibleAdapter(
                    endpoint =
                        OpenAiCompatibleEndpoint(
                            label = "Compat",
                            fallbackBaseUrl = baseUrl,
                            defaultModel = "test-model",
                            apiKeyOf = { config -> config?.compatibleApiKey },
                        ),
                    timeoutSeconds = 10L,
                    aiConfigRepo = aiConfigRepo,
                    objectMapper = objectMapper,
                    promptLoader = promptLoader,
                ),
            "OpenRouter" to
                OpenRouterAdapter(
                    defaultBaseUrl = baseUrl,
                    timeoutSeconds = 10L,
                    aiConfigRepo = aiConfigRepo,
                    objectMapper = objectMapper,
                    promptLoader = promptLoader,
                ),
            "Ollama" to
                OllamaAdapter(
                    defaultOllamaUrl = baseUrl,
                    timeoutSeconds = 10L,
                    aiConfigRepo = aiConfigRepo,
                    objectMapper = objectMapper,
                    promptLoader = promptLoader,
                    containerRuntimeProbe = FakeContainerRuntimeProbePort(),
                ),
            "LM Studio" to
                LmStudioAdapter(
                    defaultLmStudioUrl = baseUrl,
                    timeoutSeconds = 10L,
                    aiConfigRepo = aiConfigRepo,
                    objectMapper = objectMapper,
                    promptLoader = promptLoader,
                    containerRuntimeProbe = FakeContainerRuntimeProbePort(),
                ),
        )

    @Test
    fun `every provider surfaces a cancelled streaming call as InterruptedException without partial output`() {
        server.dispatcher = hangingDispatcher()

        for ((label, provider) in httpProviders()) {
            val emitted = mutableListOf<String>()
            val failure =
                runCatching {
                    provider.executeWithStreaming(
                        model = "test-model",
                        prompt = "prompt",
                        timeoutOverride = 60L,
                        isCancelled = { true },
                        onPartialOutput = { emitted.add(it) },
                    )
                }.exceptionOrNull()

            assertTrue(
                failure is InterruptedException,
                "$label must report cancellation as InterruptedException, got ${failure?.javaClass?.simpleName}: ${failure?.message}",
            )
            assertTrue(emitted.isEmpty(), "$label must not deliver partial output after cancellation")
            Thread.interrupted()
        }
    }

    @Test
    fun `every provider surfaces a cancelled repo-aware streaming call as InterruptedException`() {
        server.dispatcher = hangingDispatcher()

        for ((label, provider) in httpProviders()) {
            val failure =
                runCatching {
                    provider.executeWithStreamingPerLine(
                        model = "test-model",
                        prompt = "prompt",
                        timeoutOverride = 60L,
                        isCancelled = { true },
                        onPartialOutput = { },
                    )
                }.exceptionOrNull()

            assertTrue(
                failure is InterruptedException,
                "$label must honour cancellation on the repo-aware seam, got ${failure?.javaClass?.simpleName}: ${failure?.message}",
            )
            Thread.interrupted()
        }
    }

    @Test
    fun `providers without incremental streaming still deliver the final text once`() {
        server.dispatcher = answeringDispatcher("""{"output_text":"fertig","choices":[{"finish_reason":"stop","message":{"content":"fertig"}}],"content":[{"type":"text","text":"fertig"}]}""")

        val blockProviders =
            listOf(
                "OpenAI",
                "ClaudeCode",
                "OpenAI-compatible",
                "OpenRouter",
            )

        for ((label, provider) in httpProviders().filter { it.first in blockProviders }) {
            val emitted = mutableListOf<String>()
            val result =
                provider.executeWithStreaming(
                    model = "test-model",
                    prompt = "prompt",
                    timeoutOverride = 20L,
                    onPartialOutput = { emitted.add(it) },
                )

            assertEquals("fertig", result, "$label must return the provider text")
            assertEquals(listOf("fertig"), emitted, "$label must emit the final text exactly once")
        }
    }

    @Test
    fun `every provider forwards attachments when an unusable repoDir falls back to plain streaming`() {
        val marker = "ATTACHMENT-MARKER-BASE64"
        server.dispatcher = answeringDispatcher("{}")
        val unusableRepoDir = File(System.getProperty("java.io.tmpdir"), "stacklens-absent-repo-dir-" + System.nanoTime())

        for ((label, provider) in httpProviders().filter { it.first != "OpenRouter" }) {
            runCatching {
                provider.executeWithStreamingPerLine(
                    model = "test-model",
                    prompt = "prompt",
                    timeoutOverride = 20L,
                    repoDir = unusableRepoDir,
                    isCancelled = null,
                    onPartialOutput = { },
                    attachments = listOf(AiAttachment("image/png", marker)),
                )
            }

            val bodies = mutableListOf<String>()
            repeat(4) {
                val recorded = server.takeRequest(3, TimeUnit.SECONDS) ?: return@repeat
                val body = recorded.body
                bodies += body.readUtf8()
            }
            assertTrue(
                bodies.any { it.contains(marker) },
                "$label must send the attachments on the repoDir fallback, saw: $bodies",
            )
        }
    }
}
