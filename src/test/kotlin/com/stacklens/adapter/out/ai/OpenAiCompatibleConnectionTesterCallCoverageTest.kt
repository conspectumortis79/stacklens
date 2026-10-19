package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.service.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate

class OpenAiCompatibleConnectionTesterCallCoverageTest {
    private val i18n = FakeI18n()

    @Test
    fun `a forbidden listing with a body is reported as an invalid key`() {
        val restTemplate = nonThrowingRestTemplate()
        MockRestServiceServer
            .createServer(restTemplate)
            .expect(requestTo("http://localhost:9999/models"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""{"error":"no"}"""))

        val result =
            OpenAiCompatibleConnectionTester(restTemplate, i18n)
                .test(ProviderSpec.Compatible(apiKey = "key", baseUrl = "http://localhost:9999", model = null))

        assertEquals(false, (result as AiConnectionTestPort.TestResult.Failure).payload["success"])
    }

    @Test
    fun `a forbidden listing without a body is reported as an invalid key`() {
        val restTemplate = nonThrowingRestTemplate()
        MockRestServiceServer
            .createServer(restTemplate)
            .expect(requestTo("http://localhost:9999/models"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val result =
            OpenAiCompatibleConnectionTester(restTemplate, i18n)
                .test(ProviderSpec.Compatible(apiKey = "key", baseUrl = "http://localhost:9999", model = null))

        assertEquals(false, (result as AiConnectionTestPort.TestResult.Failure).payload["success"])
    }

    private fun nonThrowingRestTemplate(): RestTemplate =
        RestTemplate().apply {
            errorHandler =
                object : DefaultResponseErrorHandler() {
                    override fun hasError(response: HttpStatusCode): Boolean = false
                }
        }

    @Test
    fun `a transport failure without a message is reported with an empty detail`() {
        val restTemplate =
            RestTemplate().apply {
                interceptors.add(ClientHttpRequestInterceptor { _, _, _ -> throw IllegalStateException() })
            }

        val result =
            OpenAiCompatibleConnectionTester(restTemplate, i18n)
                .test(ProviderSpec.Compatible(apiKey = "key", baseUrl = "http://localhost:9999", model = null))

        assertEquals(false, (result as AiConnectionTestPort.TestResult.Failure).payload["success"])
    }
}
