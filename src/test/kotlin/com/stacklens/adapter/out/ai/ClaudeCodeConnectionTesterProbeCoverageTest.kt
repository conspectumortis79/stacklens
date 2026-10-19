package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

class ClaudeCodeConnectionTesterProbeCoverageTest {
    private val restTemplate = mock<RestTemplate>()
    private val i18n = FakeI18n()
    private val lookupPort = mock<ClaudeCodeTokenLookupPort>()

    private val settings = ClaudeCodeTestSupport.settings()
    private val tester = ClaudeCodeConnectionTester(restTemplate, i18n, settings, lookupPort, ObjectMapper())

    private val requestedCalls = mutableListOf<Pair<String, String>>()
    private val requestedHeaders: List<String>
        get() = requestedCalls.map { it.second }

    private fun stubExchange(
        response: ResponseEntity<*>? = null,
        failure: Throwable? = null,
    ) {
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation: InvocationOnMock ->
                requestedCalls += invocation.getArgument<String>(0) to headerOf(invocation)
                failure?.let { throw it }
                checkNotNull(response)
            }
    }

    private fun headerOf(invocation: InvocationOnMock): String {
        val entity = invocation.getArgument<HttpEntity<*>>(2)
        return entity.headers
            .toSingleValueMap()
            .entries
            .joinToString(";") { "${it.key}=${it.value}" }
    }

    @Test
    fun `oauth style tokens are probed with the bearer header only`() {
        stubExchange(ResponseEntity.ok(mapOf("id" to "msg")))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-oat01-abc"))

        assertInstanceOf(TestResult.Success::class.java, result)
        val probes = requestedCalls.filter { it.first == ClaudeCodeConnectionTester.MESSAGES_URL }
        assertEquals(1, probes.size)
        assertTrue(probes.single().second.contains("Authorization=Bearer sk-ant-oat01-abc"))
        assertTrue(probes.single().second.contains("anthropic-version=2023-06-01"))
        assertEquals(
            listOf(ClaudeCodeConnectionTester.MODELS_URL),
            requestedCalls.filter { it.first != ClaudeCodeConnectionTester.MESSAGES_URL }.map { it.first },
        )
    }

    @Test
    fun `the bearer probe is used when x-api-key is rejected`() {
        var bearerAttempt = false
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation ->
                if (headerOf(invocation).contains("x-api-key")) {
                    throw HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN,
                        "forbidden",
                        org.springframework.http.HttpHeaders(),
                        "rejected by x-api-key".toByteArray(),
                        null,
                    )
                }
                bearerAttempt = true
                ResponseEntity.ok(mapOf("id" to "msg"))
            }

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "  plain-token  "))

        assertTrue(bearerAttempt)
        assertInstanceOf(TestResult.Success::class.java, result)
    }

    @Test
    fun `both probes failing keeps the bearer error message`() {
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation ->
                val status = if (headerOf(invocation).contains("x-api-key")) HttpStatus.FORBIDDEN else HttpStatus.NOT_FOUND
                throw HttpClientErrorException.create(
                    status,
                    status.reasonPhrase,
                    org.springframework.http.HttpHeaders(),
                    status.name.toByteArray(),
                    null,
                )
            }

        val result = assertInstanceOf(TestResult.Failure::class.java, tester.test(ProviderSpec.ClaudeCode(apiKey = "tok")))

        assertEquals("HTTP 404: NOT_FOUND", result.payload.getValue("error"))
    }

    @Test
    fun `an empty bearer error body falls back to the x-api-key failure`() {
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation ->
                val body = if (headerOf(invocation).contains("x-api-key")) "rate limited" else ""
                throw HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "too many",
                    org.springframework.http.HttpHeaders(),
                    body.toByteArray(),
                    null,
                )
            }

        val result = assertInstanceOf(TestResult.Failure::class.java, tester.test(ProviderSpec.ClaudeCode(apiKey = "tok")))

        assertEquals("i18n:config.be_claudecode_rate_limited", result.payload.getValue("error"))
    }

    @Test
    fun `a non two hundred response without a usable body reports no response body`() {
        stubExchange(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build<Any>())

        val result = assertInstanceOf(TestResult.Failure::class.java, tester.test(ProviderSpec.ClaudeCode(apiKey = "tok")))

        assertEquals("HTTP 503: no response body", result.payload.getValue("error"))
    }

    @Test
    fun `a transport failure without a message is localised with an empty detail`() {
        stubExchange(failure = RuntimeException())

        val failure = assertInstanceOf(TestResult.Failure::class.java, tester.test(ProviderSpec.ClaudeCode(apiKey = "tok")))

        assertEquals("i18n:config.be_claudecode_unreachable|", failure.payload.getValue("error"))
        assertEquals("error", failure.payload.getValue("severity"))
    }

    @Test
    fun `the lookup port supplies the token when the spec has none`() {
        whenever(lookupPort.resolveToken()).thenReturn("lookup-token")
        stubExchange(ResponseEntity.ok(mapOf("id" to "msg")))

        assertInstanceOf(TestResult.Success::class.java, tester.test(ProviderSpec.ClaudeCode(apiKey = null)))
    }

    @Test
    fun `an unsupported auth header is reported by the probe`() {
        val probe = ClaudeCodeConnectionTester::class.java.getDeclaredMethod("probeAnthropic", String::class.java, String::class.java, String::class.java)
        probe.isAccessible = true

        val failure = assertInstanceOf(TestResult.Failure::class.java, probe.invoke(tester, "tok", "Basic", "claude-sonnet-4-5"))

        assertEquals("i18n:config.be_claudecode_unreachable|Unsupported auth header: Basic", failure.payload["error"])
    }

    @Test
    fun `unusable payloads yield no anthropic error message`() {
        val extract = ClaudeCodeConnectionTester::class.java.getDeclaredMethod("extractAnthropicErrorMessage", Any::class.java)
        extract.isAccessible = true

        assertNull(extract.invoke(tester, 42))
        assertNull(extract.invoke(tester, "   "))
        assertNull(extract.invoke(tester, "not json"))
        assertNull(extract.invoke(tester, mapOf("error" to "plain")))
        assertEquals("nested message", extract.invoke(tester, """{"error":{"message":"nested message"}}"""))
    }

    @Test
    fun `a missing token without a stored credential is rejected`() {
        val testerWithoutLookup = ClaudeCodeConnectionTester(restTemplate, i18n, settings, lookupPort)
        val failure =
            assertInstanceOf(
                TestResult.Failure::class.java,
                testerWithoutLookup.test(ProviderSpec.ClaudeCode(apiKey = "   ")),
            )

        assertEquals("i18n:config.be_no_claudecode_key", failure.payload["error"])
    }

    @Test
    fun `the probe asks for the model of the spec`() {
        val probedModels = stubBodyCapture(ResponseEntity.ok(mapOf("id" to "msg")))

        tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-oat01-abc", model = "  claude-opus-4-5  "))

        assertEquals(listOf("claude-opus-4-5"), probedModels)
    }

    @Test
    fun `a probe without a model falls back to the configured default model`() {
        val probedModels = stubBodyCapture(ResponseEntity.ok(mapOf("id" to "msg")))
        val defaulting = ClaudeCodeConnectionTester(restTemplate, i18n, ClaudeCodeTestSupport.settings(defaultModel = "claude-haiku-4-5"), lookupPort, ObjectMapper())

        defaulting.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-oat01-abc"))

        assertEquals(listOf("claude-haiku-4-5"), probedModels)
    }

    @Test
    fun `a model that is only whitespace falls back to the configured default model`() {
        val probedModels = stubBodyCapture(ResponseEntity.ok(mapOf("id" to "msg")))
        val defaulting = ClaudeCodeConnectionTester(restTemplate, i18n, ClaudeCodeTestSupport.settings(defaultModel = "claude-haiku-4-5"), lookupPort, ObjectMapper())

        defaulting.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-oat01-abc", model = "   "))
        defaulting.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-plain", model = ""))

        assertEquals(listOf("claude-haiku-4-5", "claude-haiku-4-5"), probedModels)
    }

    @Test
    fun `an api key probe is retried with the same model when the key is rejected`() {
        val probedModels = stubBodyCapture(ResponseEntity.ok(mapOf("id" to "msg")), rejectFirst = true)

        tester.test(ProviderSpec.ClaudeCode(apiKey = "sk-ant-api03-abc", model = "claude-sonnet-4-5"))

        assertEquals(listOf("claude-sonnet-4-5", "claude-sonnet-4-5"), probedModels)
    }

    private fun stubBodyCapture(
        response: ResponseEntity<*>,
        rejectFirst: Boolean = false,
    ): List<Any?> {
        val probedModels = mutableListOf<Any?>()
        var attempts = 0
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation ->
                attempts++
                @Suppress("UNCHECKED_CAST")
                probedModels += (invocation.getArgument<HttpEntity<*>>(2).body as Map<String, Any?>)["model"]
                if (rejectFirst && attempts == 1) {
                    throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "unauthorized", org.springframework.http.HttpHeaders(), ByteArray(0), null)
                }
                response
            }
        return probedModels
    }

    @Test
    fun `the anthropic messages endpoint is called with post and a json body`() {
        val requests = mutableListOf<Pair<String, HttpMethod>>()
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation ->
                requests += invocation.getArgument<String>(0) to invocation.getArgument<HttpMethod>(1)
                ResponseEntity.ok(mapOf("id" to "msg"))
            }

        tester.test(ProviderSpec.ClaudeCode(apiKey = "tok"))

        assertEquals(ClaudeCodeConnectionTester.MESSAGES_URL, requests.first { it.second == HttpMethod.POST }.first)
        assertEquals(
            listOf(ClaudeCodeConnectionTester.MESSAGES_URL, ClaudeCodeConnectionTester.MODELS_URL),
            requests.map { it.first },
        )
    }
}
