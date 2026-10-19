package com.stacklens.adapter.out.health

import com.stacklens.application.port.out.health.ConnectionProbeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class DefaultGithubConnectionProbeMessagelessFailureCoverageTest {
    private val probe =
        DefaultGithubConnectionProbe(
            WebClient.builder().exchangeFunction { Mono.error<ClientResponse>(RuntimeException()) },
        )

    @Test
    fun `a failure without a message is named by its class`() {
        val result = probe.probe("acme", "shop", null, "https://api.github.com")

        assertEquals(ConnectionProbeResult.Failure("RuntimeException"), result)
    }
}
