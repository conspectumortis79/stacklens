package com.stacklens.adapter.out.knowledge.confluence

import com.fasterxml.jackson.databind.JsonNode
import com.stacklens.shared.http.ReactiveHttpClientFactory
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfluenceCalendarFetcherBrokenEventCoverageTest {
    private val fetcher = ConfluenceCalendarFetcher(ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 10_000))

    @Test
    fun `an event node that cannot be read is reported and skipped`() {
        val broken = mock<JsonNode>()
        whenever(broken.path(any<String>())).thenThrow(IllegalStateException("unreadable node"))

        assertNull(
            PrivateInvocation.invoke(fetcher, "parseEvent", broken, "cal-9"),
        )
    }
}
