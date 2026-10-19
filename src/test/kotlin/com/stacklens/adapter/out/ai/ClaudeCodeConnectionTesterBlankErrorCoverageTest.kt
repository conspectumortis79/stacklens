package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.ai.AiConnectionTestPort.TestResult
import com.stacklens.application.port.out.ai.ClaudeCodeTokenLookupPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.testsupport.HttpStatusCodeExceptionStubs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
import org.springframework.web.client.RestTemplate

class ClaudeCodeConnectionTesterBlankErrorCoverageTest {
    private val restTemplate = mock<RestTemplate>()
    private val lookupPort = mock<ClaudeCodeTokenLookupPort>()

    private val requestedHeaders = mutableListOf<String>()

    private fun tester(i18n: I18nPort) = ClaudeCodeConnectionTester(restTemplate, i18n, ClaudeCodeTestSupport.settings(), lookupPort, ObjectMapper())

    private fun stubThrowing(vararg exceptions: Throwable) {
        val queue = exceptions.toMutableList()
        whenever(restTemplate.exchange(any<String>(), any<HttpMethod>(), any<HttpEntity<*>>(), any<Class<*>>()))
            .thenAnswer { invocation: InvocationOnMock ->
                val entity = invocation.getArgument<HttpEntity<*>>(2)
                requestedHeaders +=
                    entity.headers
                        .toSingleValueMap()
                        .keys
                        .sorted()
                        .joinToString(",")
                if (queue.isEmpty()) ResponseEntity.ok(mapOf("id" to "msg")) else throw queue.removeAt(0)
            }
    }

    private fun httpFailure(status: HttpStatus) =
        org.springframework.web.client.HttpClientErrorException.create(
            status,
            status.reasonPhrase,
            org.springframework.http.HttpHeaders(),
            "boom".toByteArray(),
            null,
        )

    private object BlankI18n : I18nPort {
        override fun t(
            key: String,
            vararg args: Any,
        ): String = ""

        override fun tFor(
            lang: String,
            key: String,
            vararg args: Any,
        ): String = ""

        override fun translateError(e: Exception): String = ""

        override fun resolveLanguage(): String = "de"
    }

    @Test
    fun `a blank bearer error falls back to the x-api-key diagnosis`() {
        val i18n = BlankI18n
        stubThrowing(httpFailure(HttpStatus.UNAUTHORIZED), httpFailure(HttpStatus.UNAUTHORIZED))

        val failure =
            assertInstanceOf(
                TestResult.Failure::class.java,
                tester(i18n).test(ProviderSpec.ClaudeCode(apiKey = "tok")),
            )

        assertEquals("x-api-key", failure.payload["auth"])
    }

    @Test
    fun `an http rejection without any body still names the status`() {
        val bodyless = HttpStatusCodeExceptionStubs.withoutResponseBody(HttpStatus.BAD_GATEWAY)
        stubThrowing(bodyless, bodyless)

        val failure =
            assertInstanceOf(
                TestResult.Failure::class.java,
                tester(com.stacklens.testsupport.FakeI18n()).test(ProviderSpec.ClaudeCode(apiKey = "tok")),
            )

        assertEquals("HTTP 502: ", failure.payload["error"])
        assertEquals(2, requestedHeaders.size)
        assertTrue(requestedHeaders.last().contains("Authorization"), requestedHeaders.toString())
    }
}
