package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest
import org.springframework.web.client.RestTemplate

class ClaudeCodeConnectionTesterModelsTest {
    private lateinit var restTemplate: RestTemplate
    private lateinit var server: MockRestServiceServer
    private val i18n = FakeI18n()
    private val lookup: ClaudeCodeTokenLookupPort = mock()
    private val settings = ClaudeCodeTestSupport.settings()
    private lateinit var tester: ClaudeCodeConnectionTester

    @BeforeEach
    fun setUp() {
        restTemplate = RestTemplate()
        server = MockRestServiceServer.createServer(restTemplate)
        tester = ClaudeCodeConnectionTester(restTemplate, i18n, settings, lookup, ObjectMapper())
    }

    private fun probePayload(apiKey: String): Map<String, Any> {
        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = apiKey))
        return assertInstanceOf(TestResult.Success::class.java, result).payload
    }

    private fun expectMessagesPing() {
        server
            .expect(requestTo(MESSAGES_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"content":[{"type":"text","text":"pong"}]}""", MediaType.APPLICATION_JSON))
    }

    private fun expectModelsListing(body: String) {
        server
            .expect(requestTo(MODELS_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("anthropic-version", ANTHROPIC_VERSION))
            .andExpect(headerDoesNotExist("Content-Type"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `an api key probe lists the account models with the same credentials`() {
        expectMessagesPing()
        server
            .expect(requestTo(MODELS_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("x-api-key", "sk-ant-api03-supplied"))
            .andExpect(header("anthropic-version", ANTHROPIC_VERSION))
            .andExpect(headerDoesNotExist("Content-Type"))
            .andRespond(
                withSuccess(
                    """{"data":[{"type":"model","id":"claude-opus-4-1"},{"type":"model","id":"claude-sonnet-4-5"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val payload = probePayload("sk-ant-api03-supplied")

        assertEquals(2, payload["modelCount"])
        assertEquals(listOf("claude-opus-4-1", "claude-sonnet-4-5"), payload["models"])
        server.verify()
    }

    @Test
    fun `an oauth probe lists the account models as bearer`() {
        expectMessagesPing()
        server
            .expect(requestTo(MODELS_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer sk-ant-oat01-abc"))
            .andExpect(headerDoesNotExist("x-api-key"))
            .andExpect(headerDoesNotExist("Content-Type"))
            .andRespond(withSuccess("""{"data":[{"id":"claude-haiku-4-5"}]}""", MediaType.APPLICATION_JSON))

        val payload = probePayload("sk-ant-oat01-abc")

        assertEquals(1, payload["modelCount"])
        assertEquals(listOf("claude-haiku-4-5"), payload["models"])
        server.verify()
    }

    @Test
    fun `a rejected model listing keeps the successful connectivity probe`() {
        expectMessagesPing()
        server
            .expect(requestTo(MODELS_URL))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withUnauthorizedRequest().body("""{"type":"error","error":{"type":"authentication_error","message":"x-api-key header is required"}}"""))

        val payload = probePayload("sk-ant-api03-nolist")

        assertEquals(true, payload["success"])
        assertEquals(0, payload["modelCount"])
        assertEquals(emptyList<Any>(), payload["models"])
        server.verify()
    }

    @Test
    fun `a broken model listing transport keeps the successful connectivity probe`() {
        expectMessagesPing()
        server
            .expect(requestTo(MODELS_URL))
            .andRespond { throw java.io.IOException("connection reset") }

        val payload = probePayload("sk-ant-api03-broken")

        assertEquals(true, payload["success"])
        assertEquals(0, payload["modelCount"])
    }

    @Test
    fun `only distinct claude ids survive and the api order is kept`() {
        expectMessagesPing()
        expectModelsListing(
            """{"data":[
              {"id":"claude-sonnet-4-5"},
              {"id":"gpt-5.2"},
              {"id":"claude-opus-4-1"},
              {"id":"claude-sonnet-4-5"},
              {"id":"  "},
              {"name":"without-id"},
              {"id":"claude-3-5-haiku"}
            ]}""",
        )

        val payload = probePayload("sk-ant-api03-mixed")

        assertEquals(3, payload["modelCount"])
        assertEquals(listOf("claude-sonnet-4-5", "claude-opus-4-1", "claude-3-5-haiku"), payload["models"])
        server.verify()
    }

    @Test
    fun `the listing asks past the twenty model page default`() {
        expectMessagesPing()
        expectModelsListing("""{"data":[{"id":"claude-opus-4-1"}],"has_more":false}""")

        probePayload("sk-ant-api03-page")

        server.verify()
    }

    @Test
    fun `a listing body without a data array yields no models`() {
        expectMessagesPing()
        expectModelsListing("""{"type":"model","data":{"id":"claude-opus-4-1"}}""")

        val payload = probePayload("sk-ant-api03-nodata")

        assertEquals(0, payload["modelCount"])
        assertTrue((payload["models"] as List<*>).isEmpty())
    }

    @Test
    fun `an empty listing response yields no models`() {
        expectMessagesPing()
        expectModelsListing("")

        val payload = probePayload("sk-ant-api03-empty")

        assertEquals(0, payload["modelCount"])
        assertEquals(emptyList<Any>(), payload["models"])
        server.verify()
    }

    @Test
    fun `a rejected connectivity probe never asks for the model list`() {
        server
            .expect(requestTo(MESSAGES_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withUnauthorizedRequest().body("""{"error":{"message":"bad token"}}"""))
        server
            .expect(requestTo(MESSAGES_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withUnauthorizedRequest().body("""{"error":{"message":"bad token"}}"""))

        assertInstanceOf(TestResult.Failure::class.java, tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-rejected")))
        server.verify()
    }

    @Test
    fun `the bearer fallback also lists models with the header that worked`() {
        server
            .expect(requestTo(MESSAGES_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-api-key", "sk-ant-api03-fallback"))
            .andRespond(withUnauthorizedRequest().body("""{"error":{"message":"forbidden"}}"""))
        server
            .expect(requestTo(MESSAGES_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer sk-ant-api03-fallback"))
            .andRespond(withSuccess("""{"ok":true}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo(MODELS_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer sk-ant-api03-fallback"))
            .andRespond(withSuccess("""{"data":[{"id":"claude-opus-4-1"}]}""", MediaType.APPLICATION_JSON))

        val payload = probePayload("sk-ant-api03-fallback")

        assertEquals(listOf("claude-opus-4-1"), payload["models"])
        server.verify()
    }

    private companion object {
        const val MESSAGES_URL = ClaudeCodeConnectionTester.MESSAGES_URL
        const val MODELS_URL = ClaudeCodeConnectionTester.MODELS_URL
        const val ANTHROPIC_VERSION = ClaudeCodeConnectionTester.ANTHROPIC_VERSION
    }
}
