package com.stacklens.adapter.out.gitlab

import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class PipelineSearchAdapterTreeFailureCoverageTest {
    private val adapter =
        PipelineSearchAdapter(
            connectTimeoutMs = 5_000,
            readTimeoutMs = 5_000,
            httpFactory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 5_000),
        )

    @Test
    fun `a tree request that cannot be answered degrades to no entries`() {
        val client =
            WebClient
                .builder()
                .baseUrl("http://127.0.0.1:1")
                .exchangeFunction { Mono.error<ClientResponse>(IllegalStateException("gitlab unreachable")) }
                .build()

        val items =
            PrivateInvocation.invoke(adapter, "listTree", client, 11L, "src", "main") as List<*>

        assertTrue(items.isEmpty())
    }
}
