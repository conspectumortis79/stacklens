package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.service.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class OllamaConnectionTesterTest {
    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer

    private val i18n = FakeI18n()

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
    }

    @Test
    fun `test collects the model names of the tags endpoint`() {
        server
            .expect(requestTo("http://localhost:11434/api/tags"))
            .andRespond(
                withSuccess(
                    """{"models":[{"name":"llama3.1"},{"name":"qwen2.5"},{"name":null},{"other":"x"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = tester().test(ProviderSpec.Ollama(baseUrl = "http://localhost:11434"))

        assertEquals(
            AiConnectionTestPort.TestResult.Success(
                mapOf("success" to true, "modelCount" to 2, "models" to listOf("llama3.1", "qwen2.5")),
            ),
            result,
        )
    }

    @Test
    fun `test reports zero models when the response has no model list`() {
        server
            .expect(requestTo("http://localhost:11434/api/tags"))
            .andRespond(withSuccess("""{"errors":"no models key"}""", MediaType.APPLICATION_JSON))

        val result = tester().test(ProviderSpec.Ollama(baseUrl = "http://localhost:11434"))

        assertEquals(
            AiConnectionTestPort.TestResult.Success(mapOf("success" to true, "modelCount" to 0, "models" to emptyList<String>())),
            result,
        )
    }

    @Test
    fun `test reports zero models when the response body is empty`() {
        server.expect(requestTo("http://localhost:11434/api/tags")).andRespond(withSuccess("null", MediaType.APPLICATION_JSON))

        val result = tester().test(ProviderSpec.Ollama(baseUrl = "http://localhost:11434"))

        assertEquals(0, (result as AiConnectionTestPort.TestResult.Success).payload["modelCount"])
    }

    @Test
    fun `test ignores entries that are not objects`() {
        server
            .expect(requestTo("http://localhost:11434/api/tags"))
            .andRespond(withSuccess("""{"models":["plain string",{"name":"llama3"}]}""", MediaType.APPLICATION_JSON))

        val result = tester().test(ProviderSpec.Ollama(baseUrl = "http://localhost:11434"))

        assertEquals(listOf("llama3"), (result as AiConnectionTestPort.TestResult.Success).payload["models"])
    }

    @Test
    fun `test fails with a localized message when ollama is unreachable`() {
        server.expect(requestTo("http://localhost:11434/api/tags")).andRespond(withServerError())

        val result = tester().test(ProviderSpec.Ollama(baseUrl = "http://localhost:11434"))

        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals(false, payload["success"])
        assertTrue(payload["error"].toString().startsWith("config.be_ollama_unreachable"), "unexpected: ${payload["error"]}")
    }

    private fun tester() = OllamaConnectionTester(restTemplate, i18n)
}
