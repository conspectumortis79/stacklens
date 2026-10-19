package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets

class OpenAiCompatibleConnectionTesterTest {
    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer
    private val i18n: I18nPort = mock()
    private lateinit var tester: OpenAiCompatibleConnectionTester

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        tester = OpenAiCompatibleConnectionTester(restTemplate, i18n)
        whenever(i18n.t("config.be_no_compatible_key")).thenReturn("no-key")
        whenever(i18n.t("config.be_compatible_missing_url")).thenReturn("missing-url")
        whenever(i18n.t("config.be_compatible_invalid_key")).thenReturn("invalid-key")
        whenever(i18n.t("config.be_compatible_rate_limited")).thenReturn("rate-limited")
        whenever(i18n.t(eq("config.be_compatible_model_not_found"), any<String>())).thenReturn("model-not-found")
        whenever(i18n.t(eq("config.be_compatible_unreachable"), any<String>())).thenReturn("unreachable")
    }

    private fun spec(
        apiKey: String? = "sk-compat-abcdef1234",
        baseUrl: String = "https://endpoint.test/v1",
        model: String? = "qwen3.8-flash",
    ) = ProviderSpec.Compatible(apiKey = apiKey, baseUrl = baseUrl, model = model)

    private fun payload(result: TestResult): Map<String, Any> =
        when (result) {
            is TestResult.Success -> result.payload
            is TestResult.Failure -> result.payload
        }

    private fun modelsBody(vararg ids: String): String = ids.joinToString(separator = ",", prefix = """{"data":[""", postfix = "]}") { """{"id":"$it"}""" }

    private fun statusBody(
        status: HttpStatus,
        body: String,
    ) = org.springframework.test.web.client.ResponseCreator {
        org.springframework.mock.http.client
            .MockClientHttpResponse(body.toByteArray(StandardCharsets.UTF_8), status)
    }

    private fun noRoute() = statusBody(HttpStatus.NOT_FOUND, """{"error":{"code":"no_route"}}""")

    private val pong = """{"choices":[{"message":{"content":"pong"}}]}"""

    @Test
    fun `a missing api key fails before any request is sent`() {
        val result = tester.test(spec(apiKey = null))
        assertTrue(result is TestResult.Failure)
        assertEquals("no-key", payload(result)["error"])
        assertEquals("error", payload(result)["severity"])
    }

    @Test
    fun `a blank api key fails as missing key`() {
        val result = tester.test(spec(apiKey = "   "))
        assertTrue(result is TestResult.Failure)
        assertEquals("no-key", payload(result)["error"])
    }

    @Test
    fun `a missing base url is reported separately from an unreachable server`() {
        val result = tester.test(spec(baseUrl = "   "))
        assertTrue(result is TestResult.Failure)
        assertEquals("missing-url", payload(result)["error"])
    }

    @Test
    fun `a base url without scheme is upgraded to https`() {
        server
            .expect { request ->
                assertEquals("https://endpoint.test/v1/models", request.uri.toString())
                assertEquals(HttpMethod.GET, request.method)
            }.andRespond(withSuccess(modelsBody("b-model", "a-model"), MediaType.APPLICATION_JSON))

        val result = tester.test(spec(baseUrl = "endpoint.test/v1/"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("a-model", "b-model"), payload(result)["models"] as List<String>)
        server.verify()
    }

    @Test
    fun `a model list is returned sorted together with a masked key hint`() {
        server
            .expect { request ->
                assertEquals(HttpMethod.GET, request.method)
                assertEquals("Bearer sk-compat-abcdef1234", request.headers.getFirst("Authorization"))
            }.andRespond(withSuccess(modelsBody("zeta", "alpha"), MediaType.APPLICATION_JSON))

        val result = tester.test(spec())
        val map = payload(result)
        assertTrue(result is TestResult.Success)
        assertTrue(map["success"] as Boolean)
        assertEquals(2, map["modelCount"] as Int)
        assertFalse(map["modelsUnavailable"] as Boolean)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("alpha", "zeta"), map["models"] as List<String>)
        assertTrue((map["accountHint"] as String).startsWith("sk-"))
        server.verify()
    }

    @Test
    fun `an empty model list still verifies the key with a chat probe`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(withSuccess(modelsBody(), MediaType.APPLICATION_JSON))
        server
            .expect { request ->
                assertEquals(HttpMethod.POST, request.method)
                assertEquals("https://endpoint.test/v1/chat/completions", request.uri.toString())
            }.andRespond(withSuccess(pong, MediaType.APPLICATION_JSON))

        val result = tester.test(spec())
        assertTrue(result is TestResult.Success)
        assertEquals(0, payload(result)["modelCount"] as Int)
        assertTrue(payload(result)["modelsUnavailable"] as Boolean)
        server.verify()
    }

    @Test
    fun `an unsupported model listing falls back to the chat probe`() {
        server.expect { request -> assertEquals(HttpMethod.GET, request.method) }.andRespond(noRoute())
        server
            .expect { request -> assertEquals(HttpMethod.POST, request.method) }
            .andRespond(withSuccess(pong, MediaType.APPLICATION_JSON))

        val result = tester.test(spec())
        assertTrue(result is TestResult.Success)
        assertTrue(payload(result)["modelsUnavailable"] as Boolean)
        server.verify()
    }

    @Test
    fun `a rejected key is reported as an invalid key and not as a network problem`() {
        server.expect { request -> assertEquals(HttpMethod.GET, request.method) }.andRespond(withUnauthorizedRequest())

        val result = tester.test(spec())
        assertTrue(result is TestResult.Failure)
        assertEquals("invalid-key", payload(result)["error"])
        assertEquals("error", payload(result)["severity"])
    }

    @Test
    fun `a rate limited endpoint reports the quota without blaming the key`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(statusBody(HttpStatus.TOO_MANY_REQUESTS, """{"error":{"code":"Throttling.RateQuota"}}"""))

        val result = tester.test(spec())
        assertTrue(result is TestResult.Failure)
        assertEquals("rate-limited", payload(result)["error"])
        assertEquals("info", payload(result)["severity"])
    }

    @Test
    fun `a failed chat probe after an unsupported listing reports the model as unknown`() {
        server.expect { request -> assertEquals(HttpMethod.GET, request.method) }.andRespond(noRoute())
        server
            .expect { request -> assertEquals(HttpMethod.POST, request.method) }
            .andRespond(statusBody(HttpStatus.NOT_FOUND, """{"error":{"code":"model_not_found"}}"""))

        val result = tester.test(spec(model = "qwen3.8-flash"))
        assertTrue(result is TestResult.Failure)
        assertEquals("model-not-found", payload(result)["error"])
        assertEquals("warning", payload(result)["severity"])
    }

    @Test
    fun `a server error is reported as unreachable`() {
        server.expect { request -> assertEquals(HttpMethod.GET, request.method) }.andRespond(withServerError())

        val result = tester.test(spec())
        assertTrue(result is TestResult.Failure)
        assertEquals("unreachable", payload(result)["error"])
        assertFalse(payload(result)["success"] as Boolean)
    }

    @Test
    fun `a transport failure is reported as unreachable`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond { throw RuntimeException("connection refused") }

        val result = tester.test(spec())
        assertTrue(result is TestResult.Failure)
        assertEquals("unreachable", payload(result)["error"])
    }

    @Test
    fun `the chat probe body carries the given model and a minimal prompt`() {
        val body = tester.chatProbeBody("deepseek-v4-pro")
        assertEquals("deepseek-v4-pro", body["model"])
        assertEquals(1, body["max_tokens"])
        @Suppress("UNCHECKED_CAST")
        val messages = body["messages"] as List<Map<String, String>>
        assertEquals(listOf("user"), messages.map { it["role"] })
    }

    @Test
    fun `the chat probe falls back to the default model when none is given`() {
        assertEquals("qwen3.8-flash", tester.chatProbeBody(null)["model"])
        assertEquals("qwen3.8-flash", tester.chatProbeBody("   ")["model"])
    }

    @Test
    fun `a successful listing is not followed by a chat probe`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(withSuccess(modelsBody("only-model"), MediaType.APPLICATION_JSON))

        val result = tester.test(spec())
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("only-model"), payload(result)["models"] as List<String>)
        assertFalse(payload(result)["modelsUnavailable"] as Boolean)
        server.verify()
    }

    @Test
    fun `an unreadable listing body yields no models and triggers the chat probe`() {
        server
            .expect { request -> assertEquals(HttpMethod.GET, request.method) }
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))
        server
            .expect { request -> assertEquals(HttpMethod.POST, request.method) }
            .andRespond(withSuccess(pong, MediaType.APPLICATION_JSON))

        assertTrue(tester.test(spec()) is TestResult.Success)
        server.verify()
    }
}
