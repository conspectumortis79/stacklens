package com.stacklens.adapter.out.ai

import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.ai.AiConnectionTestPort
import com.stacklens.application.port.out.ai.ProviderSpec
import com.stacklens.application.service.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestTemplate

class GithubTokenConnectionTesterNullCauseCoverageTest {
    private val i18n = FakeI18n()

    @Test
    fun `a github failure without a cause message still renders the localized error`() {
        val restTemplate =
            RestTemplate().apply {
                interceptors.add(ClientHttpRequestInterceptor { _, _, _ -> throw IllegalStateException() })
            }

        val result = GithubTokenConnectionTester(restTemplate, i18n).test(ProviderSpec.GithubToken(token = "pat"))

        assertEquals(
            AiConnectionTestPort.TestResult.Failure(
                ConnectionFailureKind.CONNECTION,
                mapOf("success" to false, "error" to "config.be_github_unreachable()"),
            ),
            result,
        )
    }
}
