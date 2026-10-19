package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient

class ConfluenceHierarchyAdapterLookupThrowingCoverageTest {
    private val factory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)

    private fun config() =
        ConfluenceConfig(
            name = "demo",
            baseUrl = "http://127.0.0.1:1",
            username = "u",
            apiToken = "t",
        )

    @Test
    fun `a title lookup that fails before any answer drops the page id`() {
        val client = mock<WebClient>()
        whenever(client.get()).thenThrow(IllegalStateException("client already released"))

        val pageId =
            ConfluenceHierarchyAdapter(factory)
                .extractPageId(
                    config(),
                    "http://127.0.0.1:1/display/DEV/Rotationsplan?title=Rotationsplan",
                    "/rest/api",
                    client,
                )

        assertNull(pageId)
    }
}
