package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.domain.model.AiConfig
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional

class AiStatusBelowTwoHundredCoverageTest {
    private val aiConfigRepo: AiConfigPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val executor: AiHttpClientExecutor = mock()

    private fun response(status: Int): HttpResponse<String> =
        object : HttpResponse<String> {
            override fun statusCode(): Int = status

            override fun request(): HttpRequest = throw UnsupportedOperationException()

            override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

            override fun headers(): HttpHeaders = HttpHeaders.of(mapOf<String, List<String>>()) { _, _ -> true }

            override fun body(): String = "informational"

            override fun sslSession(): Optional<javax.net.ssl.SSLSession> = Optional.empty()

            override fun uri(): URI = URI.create("https://ai.invalid/v1/chat/completions")

            override fun version(): java.net.http.HttpClient.Version = java.net.http.HttpClient.Version.HTTP_1_1
        }

    private fun stub(status: Int) {
        whenever(executor.sendWithTimeout<String>(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(response(status))
        whenever(executor.sendWithTimeout<String>(any(), any(), any(), any()))
            .thenReturn(response(status))
    }

    private fun openAi() =
        OpenAiAdapter(
            defaultOpenAiBaseUrl = "https://ai.invalid",
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = ObjectMapper(),
            promptLoader = promptLoader,
            aiHttpClientExecutor = executor,
        )

    private fun invoke(
        target: Any,
        name: String,
        vararg args: Any?,
    ) {
        val method = target.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true
        try {
            method.invoke(target, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    @Test
    fun `an informational status on the tool request fails the range check`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk"))
        stub(100)
        assertThrows(IllegalStateException::class.java) {
            invoke(openAi(), "sendToolRequestWithAuth", "https://ai.invalid", "tok", "{}", 5L, null)
        }
    }

    @Test
    fun `an informational status on the responses endpoint fails the range check`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk"))
        stub(100)
        assertThrows(IllegalStateException::class.java) {
            invoke(openAi(), "sendRequest", "https://ai.invalid", "tok", "{}", 5L, null)
        }
    }

    @Test
    fun `an informational status on chat completions fails the range check`() {
        whenever(aiConfigRepo.findAiConfig()).thenReturn(AiConfig(openAiApiKey = "sk"))
        stub(100)
        assertThrows(IllegalStateException::class.java) {
            invoke(openAi(), "sendChatCompletionsRequest", "https://ai.invalid", "tok", "{}", 5L, null)
        }
    }

    @Test
    fun `an informational status on the model listing fails the range check`() {
        stub(100)
        assertThrows(IllegalStateException::class.java) { openAi().fetchAvailableModels("sk") }
    }

    private fun claude(key: String) =
        ClaudeCodeAdapter(
            defaultBaseUrl = "https://claude.invalid",
            timeoutSeconds = 5L,
            aiConfigRepo = aiConfigRepo,
            objectMapper = ObjectMapper(),
            promptLoader = promptLoader,
            tokenLookup = ClaudeCodeTestSupport.tokenLookup(key),
            settings = ClaudeCodeTestSupport.settings(),
            aiHttpClientExecutor = executor,
        )

    private fun stubAll(vararg responses: HttpResponse<String>) {
        val rest = responses.drop(1).toTypedArray()
        whenever(executor.sendWithTimeout<String>(any(), any(), any(), any(), anyOrNull()))
            .thenReturn(responses.first(), *rest)
    }
}
