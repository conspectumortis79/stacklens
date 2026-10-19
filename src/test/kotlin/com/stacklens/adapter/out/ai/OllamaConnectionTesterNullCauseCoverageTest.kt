package com.stacklens.adapter.out.ai

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.service.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestTemplate

class OllamaConnectionTesterNullCauseCoverageTest {
    private val i18n = FakeI18n()

    @Test
    fun `an ollama failure without a cause message still renders the localized error`() {
        val result =
            OllamaConnectionTester(restTemplateFailingSilently(), i18n)
                .test(ProviderSpec.Ollama(baseUrl = "http://localhost:11434"))

        assertEquals(
            AiConnectionTestPort.TestResult.Failure(
                ConnectionFailureKind.CONNECTION,
                mapOf("success" to false, "error" to "config.be_ollama_unreachable()"),
            ),
            result,
        )
    }

    private fun restTemplateFailingSilently(): RestTemplate =
        RestTemplate().apply {
            interceptors.add(ClientHttpRequestInterceptor { _, _, _ -> throw IllegalStateException() })
        }
}
