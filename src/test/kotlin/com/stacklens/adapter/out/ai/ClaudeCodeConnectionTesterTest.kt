package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.RestTemplate

class ClaudeCodeConnectionTesterTest {
    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer
    private val i18n: I18nPort = mock()
    private val lookup: ClaudeCodeTokenLookupPort = mock()

    private val settings = ClaudeCodeTestSupport.settings()
    private lateinit var tester: ClaudeCodeConnectionTester

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        tester = ClaudeCodeConnectionTester(restTemplate, i18n, settings, lookup, ObjectMapper())
    }

    private fun mockI18n(value: String) {
        whenever(i18n.t(any<String>())).thenReturn(value)
        whenever(i18n.t(any<String>(), any())).thenReturn(value)
        whenever(i18n.t(any<String>(), any(), any())).thenReturn(value)
    }

    @Test
    fun `test returns Failure when apiKey is null and the lookup port has no token`() {
        val noLookupTester = ClaudeCodeConnectionTester(restTemplate, i18n, settings, lookup, ObjectMapper())
        mockI18n("missing key")
        val result = noLookupTester.test(ProviderSpec.ClaudeCode(apiKey = null))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals(false, payload["success"])
        assertEquals("missing key", payload["error"])
    }

    @Test
    fun `test returns Failure when apiKey is blank and lookup port returns null`() {
        whenever(lookup.resolveToken()).thenReturn(null)
        mockI18n("missing key")
        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "   "))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test returns Failure when apiKey is blank and lookup port returns blank`() {
        whenever(lookup.resolveToken()).thenReturn("   ")
        mockI18n("missing key")
        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = ""))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    @Test
    fun `test rotates to the next stored credential when the api key has no credit balance`() {
        whenever(lookup.resolveToken()).thenReturn("sk-ant-api03-empty")
        whenever(lookup.tokenCandidateSources()).thenReturn(listOf({ "sk-ant-api03-empty" }, { "sk-ant-oat01-login" }))
        val credit = """{"error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST).body(credit).contentType(MediaType.APPLICATION_JSON))
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST).body(credit).contentType(MediaType.APPLICATION_JSON))
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = null))

        assertTrue(result is AiConnectionTestPort.TestResult.Success, (result as? AiConnectionTestPort.TestResult.Failure)?.payload.toString())
    }

    @Test
    fun `test names the exhausted credit balance once every stored credential was tried`() {
        whenever(lookup.resolveToken()).thenReturn("sk-ant-api03-empty")
        whenever(lookup.tokenCandidateSources()).thenReturn(listOf({ "sk-ant-api03-empty" }, { "sk-ant-oat01-login" }))
        mockI18n("credits exhausted")
        val credit = """{"error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""
        repeat(4) {
            server.expect { _ -> }.andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST).body(credit).contentType(MediaType.APPLICATION_JSON))
        }

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = null))

        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("credits exhausted", payload["error"])
    }

    @Test
    fun `test uses lookup port token when apiKey is null`() {
        whenever(lookup.resolveToken()).thenReturn("sk-ant-api03-from-lookup")
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withSuccess("""{"content":[]}""", MediaType.APPLICATION_JSON))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = null))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val payload = (result as AiConnectionTestPort.TestResult.Success).payload
        assertEquals(true, payload["success"])
        assertEquals(0, payload["modelCount"])
    }

    @Test
    fun `test uses supplied apiKey when provided and non-blank`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-supplied"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
    }

    @Test
    fun `test uses Bearer auth header for setup-token prefix sk-ant-oat01-`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-oat01-xxxxxx"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
    }

    @Test
    fun `test returns Success from x-api-key probe without falling back to Bearer`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-direct"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
    }

    @Test
    fun `test falls back to Bearer when x-api-key returns 401 and Bearer succeeds`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withUnauthorizedRequest())
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-fallback"))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
    }

    @Test
    fun `test returns Failure with bearer error when both auth modes fail with 401`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withUnauthorizedRequest())
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withUnauthorizedRequest().body("""{"error":{"message":"bad token"}}"""))

        mockI18n("claudecode-401")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-double"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("claudecode-401", payload["error"])
    }

    @Test
    fun `test returns Success with masked account hint`() {
        val token = "sk-ant-api03-supplied-1234567890"
        server.expect { _ -> }.andRespond(withJson("""{"ok":true}"""))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = token))
        assertTrue(result is AiConnectionTestPort.TestResult.Success)
        val payload = (result as AiConnectionTestPort.TestResult.Success).payload
        val hint = payload["accountHint"] as String
        assertNotEquals(token, hint)
        assertNotNull(hint)
    }

    @Test
    fun `test returns Failure with 5xx server error using i18n unreachable`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withServerError().body("""{"error":{"message":"boom"}}"""))

        mockI18n("unreachable")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-500"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unreachable", payload["error"])
        assertEquals("error", payload["severity"])
    }

    @Test
    fun `test returns Failure with 5xx and no body using i18n unreachable`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withServerError())

        mockI18n("unreachable")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-500c"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unreachable", payload["error"])
    }

    @Test
    fun `test returns Failure with 429 using i18n rate-limited`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("""{"error":{"message":"slow down"}}"""),
        )

        mockI18n("rate-limited")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-429"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("rate-limited", payload["error"])
        assertEquals("error", payload["severity"])
    }

    @Test
    fun `test returns Failure with 401 using i18n and error severity`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.UNAUTHORIZED)
                .body("""{"error":{"message":"bad"}}"""),
        )

        mockI18n("claudecode-401")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-401"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("claudecode-401", payload["error"])
        assertEquals("error", payload["severity"])
    }

    @Test
    fun `test returns Failure with non-HttpClientErrorException using i18n unreachable`() {
        server.expect { _ -> throw java.io.IOException("connection reset") }

        mockI18n("unreachable-net")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-net"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unreachable-net", payload["error"])
        assertEquals("error", payload["severity"])
    }

    @Test
    fun `test returns Failure when 401 body is not parseable JSON`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.UNAUTHORIZED)
                .body("not json at all"),
        )

        mockI18n("claudecode-401")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-401b"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("claudecode-401", payload["error"])
    }

    @Test
    fun `test returns Failure when 401 body has no error message field`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.UNAUTHORIZED)
                .body("""{"error":{"type":"auth"}}"""),
        )

        mockI18n("claudecode-401")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-401c"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("claudecode-401", payload["error"])
    }

    @Test
    fun `test returns Failure with 502 using i18n unreachable`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY).body("   "))

        mockI18n("unreachable")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-502"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unreachable", payload["error"])
    }

    @Test
    fun `test returns Failure with 502 and empty body using i18n unreachable`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY).body(""))

        mockI18n("unreachable")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-502b"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("unreachable", payload["error"])
    }

    @Test
    fun `test uses defaults when the lookup port has no stored token`() {
        val defaultTester = ClaudeCodeConnectionTester(restTemplate, i18n, settings, lookup)
        whenever(lookup.resolveToken()).thenReturn(null)
        mockI18n("missing key")
        val result = defaultTester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-default"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("missing key", payload["error"])
    }

    @Test
    fun `test specType is ProviderSpec_ClaudeCode`() {
        assertEquals(ProviderSpec.ClaudeCode::class, tester.specType)
    }

    @Test
    fun `test extractAnthropicErrorMessage returns null for non-map error field`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.BAD_REQUEST)
                .body("""{"error":"not-a-map"}"""),
        )

        mockI18n("bad-req")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-400"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("bad-req", payload["error"])
    }

    @Test
    fun `test handles 4xx error other than 401 and 429 with HTTP detail and error severity`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.FORBIDDEN)
                .body("""{"error":{"message":"forbidden"}}""")
                .contentType(MediaType.APPLICATION_JSON),
        )

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-403"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        val error = payload["error"] as String
        assertTrue(error.contains("HTTP 403"))
        assertTrue(error.contains("forbidden"))
    }

    @Test
    fun `test returns bearer failure with empty error falls back to xApiKey failure`() {
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withUnauthorizedRequest())
        server.expect { _ -> }.andRespond(MockRestResponseCreators.withUnauthorizedRequest())

        whenever(i18n.t("config.be_claudecode_401")).thenReturn("")
        whenever(i18n.t("config.be_claudecode_unreachable", "")).thenReturn("ignored")

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-mixed"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        assertEquals("x-api-key", payload["auth"])
    }

    @Test
    fun `test non-2xx response without exception uses HTTP detail branch`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.I_AM_A_TEAPOT)
                .body("""{"error":{"message":"teapot"}}""")
                .contentType(MediaType.APPLICATION_JSON),
        )

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-418"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        val error = payload["error"] as String
        assertTrue(error.contains("HTTP 418"))
        assertTrue(error.contains("teapot"))
    }

    @Test
    fun `test non-2xx response with no body returns no response body`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.I_AM_A_TEAPOT)
                .body("""{"something":"else"}""")
                .contentType(MediaType.APPLICATION_JSON),
        )

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-418b"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
        val payload = (result as AiConnectionTestPort.TestResult.Failure).payload
        val error = payload["error"] as String
        assertTrue(error.contains("HTTP 418"))
    }

    @Test
    fun `test extractAnthropicErrorMessage returns null for non-map input`() {
        server.expect { _ -> }.andRespond(
            MockRestResponseCreators
                .withStatus(HttpStatus.MULTIPLE_CHOICES)
                .body("""{"error":{"message":"some message"}}""")
                .contentType(MediaType.APPLICATION_JSON),
        )

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-300"))
        assertTrue(result is AiConnectionTestPort.TestResult.Failure)
    }

    private fun withJson(body: String) =
        MockRestResponseCreators.withSuccess(
            body,
            MediaType.APPLICATION_JSON,
        )
}
