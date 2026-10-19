package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import javax.net.ssl.SSLSession

class ClaudeCodeAdapterInformationalStatusCoverageTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val executor: AiHttpClientExecutor = mock()

    private fun response(
        status: Int,
        body: String,
    ): HttpResponse<String> =
        object : HttpResponse<String> {
            override fun statusCode(): Int = status

            override fun request(): HttpRequest = throw UnsupportedOperationException()

            override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

            override fun headers(): HttpHeaders = HttpHeaders.of(mapOf<String, List<String>>()) { _, _ -> true }

            override fun body(): String = body

            override fun sslSession(): Optional<SSLSession> = Optional.empty()

            override fun uri(): URI = URI.create("https://claude.invalid/v1/messages")

            override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
        }

    private fun adapter(key: String): ClaudeCodeAdapter {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(claudeCodeApiKey = key))
        whenever(promptLoader.get(any(), any(), any())).thenReturn("system")
        return ClaudeCodeAdapter(
            defaultBaseUrl = "https://claude.invalid",
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = ObjectMapper(),
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.tokenLookup(key),
            settings = ClaudeCodeTestSupport.settings(),
            httpClient = mock<HttpClient>(),
            aiHttpClientExecutor = executor,
        )
    }

    private fun stubReactions(responses: List<HttpResponse<String>>) {
        val first = responses.first()
        val rest = responses.drop(1).toTypedArray()
        whenever(executor.sendWithTimeout<String>(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(first, *rest)
    }

    private fun expectErrorWithStatus(
        status: Int,
        block: () -> Unit,
    ) {
        val failure = assertThrows(IllegalStateException::class.java, block)
        assertEquals("ClaudeCode error (HTTP $status): info", failure.message)
    }

    @Test
    fun `an informational status on the bearer path is an error`() {
        stubReactions(listOf(response(100, "info")))

        expectErrorWithStatus(100) { adapter("sk-ant-oat01-oauth").toolLoop.transport.send("{}", 5L) }
    }

    @Test
    fun `an informational status on the api key path is an error`() {
        stubReactions(listOf(response(100, "info")))

        expectErrorWithStatus(100) { adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L) }
    }

    @Test
    fun `an informational status on the bearer retry is an error`() {
        stubReactions(listOf(response(401, "nope"), response(100, "info")))

        expectErrorWithStatus(100) { adapter("sk-ant-api03-key").toolLoop.transport.send("{}", 5L) }
    }
}
