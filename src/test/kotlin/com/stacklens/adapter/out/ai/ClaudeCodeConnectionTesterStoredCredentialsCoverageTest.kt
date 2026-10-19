package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

class ClaudeCodeConnectionTesterStoredCredentialsCoverageTest {
    private val restTemplate = mock<RestTemplate>()

    private val i18n = FakeI18n()

    private val lookupPort = mock<ClaudeCodeTokenLookupPort>()

    private val settings = ClaudeCodeTestSupport.settings()

    private val objectMapper = ObjectMapper()

    private val tester = ClaudeCodeConnectionTester(restTemplate, i18n, settings, lookupPort, objectMapper)

    private val probed = mutableListOf<String>()

    private val credit =
        """{"error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""

    private fun tokenOf(invocation: InvocationOnMock): String {
        val headers = invocation.getArgument<HttpEntity<*>>(2).headers.toSingleValueMap()
        return headers["x-api-key"] ?: headers["Authorization"]?.removePrefix("Bearer ").orEmpty()
    }

    private fun stub(
        rejectedTokens: Set<String> = emptySet(),
        rejectionBody: String = credit,
    ) {
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation ->
                val url = invocation.getArgument<String>(0)
                if (url != ClaudeCodeConnectionTester.MESSAGES_URL) {
                    return@thenAnswer ResponseEntity.ok(objectMapper.readTree("""{"data":[{"id":"claude-sonnet-5"}]}"""))
                }
                val token = tokenOf(invocation)
                probed += token
                if (token in rejectedTokens) {
                    throw HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders(), rejectionBody.toByteArray(), null)
                }
                ResponseEntity.ok(mapOf("id" to "msg"))
            }
    }

    @Test
    fun `a lookup port that throws still probes the key of the request`() {
        whenever(lookupPort.resolveToken()).thenThrow(IllegalStateException("keychain unreadable"))
        whenever(lookupPort.tokenCandidateSources()).thenThrow(IllegalStateException("settings unreadable"))
        stub()

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = "  sk-ant-api03-request  "))

        assertInstanceOf(TestResult.Success::class.java, result)
        assertEquals(listOf("sk-ant-api03-request"), probed)
    }

    @Test
    fun `failing and blank stored credentials never reach the probe`() {
        whenever(lookupPort.resolveToken()).thenReturn(null)
        whenever(lookupPort.tokenCandidateSources())
            .thenReturn(listOf({ error("settings file gone") }, { null }, { "   " }, { " sk-ant-oat01-login " }, { "sk-ant-oat01-login" }))
        stub()

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = null))

        assertInstanceOf(TestResult.Success::class.java, result)
        assertEquals(listOf("sk-ant-oat01-login"), probed)
    }

    @Test
    fun `a lone credential without credit keeps its own error`() {
        whenever(lookupPort.resolveToken()).thenReturn("sk-ant-api03-solo")
        whenever(lookupPort.tokenCandidateSources()).thenReturn(listOf({ "sk-ant-api03-solo" }))
        stub(rejectedTokens = setOf("sk-ant-api03-solo"))

        val result = tester.test(ProviderSpec.ClaudeCode(apiKey = null))

        val failure = assertInstanceOf(TestResult.Failure::class.java, result)
        assertTrue((failure.payload["error"] as String).contains("credit balance is too low"), failure.payload.toString())
        assertFalse(i18n.translatedKeys.contains("common.be_ai_credits_exhausted"), i18n.translatedKeys.toString())
    }
}
