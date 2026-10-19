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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class LmStudioAdapterBranchTest {
    private lateinit var server: MockWebServer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapter(): LmStudioAdapter {
        val aiConfigRepo = mock<AiConfigPort>()
        val promptLoader = mock<PromptReaderPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.lmStudioUrl).thenReturn(server.url("/").toString())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        return LmStudioAdapter(
            defaultLmStudioUrl = server.url("/").toString().trimEnd('/'),
            timeoutSeconds = 10L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = objectMapper,
            promptLoader = promptLoader,
            containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
        )
    }

    private fun enqueueModelLoaded(model: String = "test-model") {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"models":[{"key":"$model","loaded_instances":[{"id":"i1"}]}]}""",
                ),
        )
    }

    @Test
    fun `execute returns content from valid response`() {
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"hello"}}]}"""),
        )
        val result = adapter().execute("test-model", "hi")
        assertEquals("hello", result)
    }

    @Test
    fun `execute returns empty when response has no content`() {
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[]}"""),
        )
        val result = adapter().execute("test-model", "hi")
        assertEquals("", result)
    }

    @Test
    fun `execute throws on non-2xx response`() {
        enqueueModelLoaded()
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter().execute("test-model", "hi")
            }
        assertNotNull(ex)
    }

    @Test
    fun `isReady returns true when URL configured`() {
        assertEquals(true, adapter().isReady())
    }

    @Test
    fun `resolveModel returns the configured model`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.lmStudioUrl).thenReturn(server.url("/").toString())
        whenever(cfg.model).thenReturn("custom-model")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            LmStudioAdapter(
                defaultLmStudioUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals("custom-model", adp.resolveModel())
    }

    @Test
    fun `resolveLanguage returns the configured language`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.lmStudioUrl).thenReturn(server.url("/").toString())
        whenever(cfg.language).thenReturn("en")
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            LmStudioAdapter(
                defaultLmStudioUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals("en", adp.resolveLanguage())
    }

    @Test
    fun `resolveToken returns null (LM Studio is local)`() {
        val aiConfigRepo = mock<AiConfigPort>()
        val cfg = mock<AiConfig>()
        whenever(cfg.lmStudioUrl).thenReturn(server.url("/").toString())
        whenever(aiConfigRepo.findAiConfig()).thenReturn(cfg)
        val adp =
            LmStudioAdapter(
                defaultLmStudioUrl = server.url("/").toString().trimEnd('/'),
                timeoutSeconds = 10L,
                aiConfigRepo = aiConfigRepo,
                objectMapper = objectMapper,
                promptLoader = mock<PromptReaderPort>(),
                containerRuntimeProbe = com.stacklens.testsupport.FakeContainerRuntimeProbePort(),
            )
        assertEquals(null, adp.resolveToken())
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir is null`() {
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"chat.end\",\"result\":{\"output\":\"plain-null\"}}\n\n"),
        )
        val result = adapter().executeWithStreamingPerLine("test-model", "hi", null, null, onPartialOutput = { })
        assertEquals("plain-null", result)
    }

    @Test
    fun `executeWithStreamingPerLine falls back when repoDir does not exist`() {
        enqueueModelLoaded()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"chat.end\",\"result\":{\"output\":\"plain-missing\"}}\n\n"),
        )
        val result =
            adapter().executeWithStreamingPerLine("test-model", "hi", null, java.io.File("/nonexistent"), onPartialOutput = { })
        assertEquals("plain-missing", result)
    }
}
