package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestTemplate

class OpenRouterConnectionTesterTest {
    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer
    private val i18n: I18nPort = mock()
    private lateinit var tester: OpenRouterConnectionTester

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        tester = OpenRouterConnectionTester(restTemplate, i18n)
    }

    private val spec =
        ProviderSpec.OpenRouter(
            apiKey = "sk-or-v1-abcdef",
        )

    @Test
    fun `test returns Failure when the api key is null`() {
        val result = tester.test(ProviderSpec.OpenRouter(apiKey = null))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Failure when the api key is blank`() {
        val result = tester.test(ProviderSpec.OpenRouter(apiKey = "   "))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Success with a sorted list of model ids on 200 OK`() {
        whenever(i18n.t(eq("config.be_no_openrouter_key"), any())).thenReturn("no key")
        whenever(i18n.t(eq("config.be_openrouter_unreachable"), any())).thenReturn("unreachable")
        server
            .expect { request ->
                assertEquals(HttpMethod.GET, request.method)
                assertTrue(request.headers.getFirst("Authorization") == "Bearer sk-or-v1-abcdef")
                assertEquals(MediaType.APPLICATION_JSON, request.headers.accept.firstOrNull())
            }.andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"data":[
                      {"id":"openai/gpt-4.1"},
                      {"id":"anthropic/claude-3.5"},
                      {"id":"openai/gpt-4.1-mini"}
                    ]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = tester.test(spec)
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val map = (result as AiConnectionTestPort.TestResult.Success).payload
        assertEquals(true, map["success"])
        assertEquals(3, map["modelCount"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(
            listOf("anthropic/claude-3.5", "openai/gpt-4.1", "openai/gpt-4.1-mini"),
            map["models"] as List<String>,
        )
        server.verify()
    }

    @Test
    fun `test returns Failure when the response is HTTP 500`() {
        whenever(i18n.t(eq("config.be_no_openrouter_key"), any())).thenReturn("no key")
        whenever(i18n.t(eq("config.be_openrouter_unreachable"), any())).thenReturn("unreachable: $1")
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators
                    .withServerError(),
            )

        val result = tester.test(spec)
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals(false, payload["success"])
    }

    @Test
    fun `test masks the api key when reporting account hints`() {
        whenever(i18n.t(eq("config.be_no_openrouter_key"), any())).thenReturn("no key")
        whenever(i18n.t(eq("config.be_openrouter_unreachable"), any())).thenReturn("unreachable: $1")
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    """{"data":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = tester.test(spec)
        val payload = (result as AiConnectionTestPort.TestResult.Success).payload
        val hint = payload["accountHint"] as String
        val apiKey = spec.apiKey!!
        assertTrue(hint != apiKey, "expected the API key to be masked, got: $hint")
        assertTrue(hint.length < apiKey.length, "expected shorter masked hint: $hint")
    }

    @Test
    fun `test returns Failure when the HTTP exchange throws`() {
        whenever(i18n.t(eq("config.be_no_openrouter_key"), any())).thenReturn("no key")
        whenever(i18n.t(eq("config.be_openrouter_unreachable"), any())).thenReturn("unreachable: $1")
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond { request ->
                throw RuntimeException("connection refused")
            }
        val result = tester.test(spec)
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Failure with empty error suffix when exception message is null`() {
        val tester = OpenRouterConnectionTester(restTemplate, i18n)
        server
            .expect { _ -> throw RuntimeException() }
        whenever(i18n.t(any<String>(), any())).thenReturn("unreachable")
        val result = tester.test(ProviderSpec.OpenRouter(apiKey = "sk-or-abc"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unreachable", payload["error"])
    }

    @Test
    fun `test returns Success with empty models when response body is null`() {
        val tester = OpenRouterConnectionTester(restTemplate, i18n)
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                    "",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val result = tester.test(ProviderSpec.OpenRouter(apiKey = "sk-or-abc"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val payload = (result as AiConnectionTestPort.TestResult.Success).payload
        @Suppress("UNCHECKED_CAST")
        assertEquals(emptyList<String>(), payload["models"] as List<String>)
    }
}
