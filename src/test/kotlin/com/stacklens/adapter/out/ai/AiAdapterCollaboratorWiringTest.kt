package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class AiAdapterCollaboratorWiringTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val mapper = ObjectMapper()

    @Test
    fun `the openai adapter uses the retry policy and executor it was given`() {
        val retry = HttpSendRetry(maxAttempts = 2)
        val executor = AiHttpClientExecutor()

        val adapter =
            OpenAiAdapter(
                aiConfigRepo = aiConfigRepo,
                objectMapper = mapper,
                promptLoader = promptLoader,
                httpSendRetry = retry,
                aiHttpClientExecutor = executor,
            )

        assertSame(retry, adapter.httpSendRetry)
        assertSame(executor, adapter.aiHttpClientExecutor)
        assertEquals("OpenAI", adapter.providerLabel)
    }

    @Test
    fun `the openai adapter builds its own tool loop with its provider label`() {
        val adapter =
            OpenAiAdapter(
                aiConfigRepo = aiConfigRepo,
                objectMapper = mapper,
                promptLoader = promptLoader,
            )

        assertEquals("OpenAI", adapter.toolLoop.transport.providerLabel)
    }

    @Test
    fun `adapters built independently do not share their retry policy`() {
        val first = OpenAiAdapter(aiConfigRepo = aiConfigRepo, objectMapper = mapper, promptLoader = promptLoader)
        val second = OpenAiAdapter(aiConfigRepo = aiConfigRepo, objectMapper = mapper, promptLoader = promptLoader)

        assertNotSame(first.httpSendRetry, second.httpSendRetry)
        assertNotSame(first.aiHttpClientExecutor, second.aiHttpClientExecutor)
    }

    @Test
    fun `the claude code adapter exposes its own retry policy and tool loop`() {
        val retry = HttpSendRetry(maxAttempts = 1)
        val executor = AiHttpClientExecutor()
        val adapter =
            ClaudeCodeAdapter(
                aiConfigRepo = aiConfigRepo,
                objectMapper = mapper,
                promptLoader = promptLoader,
                tokenLookup = ClaudeCodeTestSupport.tokenLookup("sk-ant-oat01-test"),
                settings = ClaudeCodeTestSupport.settings(),
                httpSendRetry = retry,
                aiHttpClientExecutor = executor,
            )

        assertSame(retry, adapter.httpSendRetry)
        assertSame(executor, adapter.aiHttpClientExecutor)
        assertEquals("ClaudeCode", adapter.toolLoop.transport.providerLabel)
    }

    @Test
    fun `the compatible adapter keeps the endpoint and its injected transport settings`() {
        val endpoint =
            OpenAiCompatibleEndpoint(
                label = "Gateway",
                fallbackBaseUrl = "https://gateway.test/v1",
                defaultModel = "gateway-model",
            )
        val version = java.net.http.HttpClient.Version.HTTP_2
        val retry = HttpSendRetry(maxAttempts = 1)

        val adapter =
            OpenAiCompatibleAdapter(
                endpoint = endpoint,
                aiConfigRepo = aiConfigRepo,
                objectMapper = mapper,
                promptLoader = promptLoader,
                httpVersion = version,
                httpSendRetry = retry,
            )

        assertEquals("Gateway", adapter.providerLabel)
        assertSame(retry, adapter.httpSendRetry)
        assertEquals(version, adapter.httpVersion)
        assertEquals(version, adapter.httpClient.version())
        assertEquals("Gateway", adapter.toolLoop.transport.providerLabel)
    }
}
