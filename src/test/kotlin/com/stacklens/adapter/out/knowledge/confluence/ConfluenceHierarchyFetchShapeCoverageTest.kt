package com.stacklens.adapter.out.knowledge.confluence

import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient

class ConfluenceHierarchyFetchShapeCoverageTest {
    private lateinit var server: MockWebServer

    private val factory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 10_000)

    private lateinit var config: ConfluenceConfig

    private val adapter = ConfluenceHierarchyAdapter(ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 10_000))

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
        config =
            ConfluenceConfig(
                name = "wiki",
                baseUrl = server.url("/").toString().trimEnd('/'),
                username = "u",
                apiToken = "t",
            )
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun apiBase(): String = "${config.baseUrl}/wiki/rest/api"

    private fun client(): WebClient = factory.builder().baseUrl(config.baseUrl).build()

    private fun serve(dispatcher: Dispatcher) {
        server.dispatcher = dispatcher
    }

    @Test
    fun `a title lookup that the server rejects yields no page id`() {
        serve(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(503).setBody("unavailable")
            },
        )

        val pageId = adapter.extractPageId(config, "?title=Somewhere", apiBase(), client())

        assertNull(pageId)
    }

    @Test
    fun `a title lookup without results yields no page id`() {
        serve(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"results":[]}""")
            },
        )

        assertNull(adapter.extractPageId(config, "?title=Somewhere", apiBase(), client()))
    }

    @Test
    fun `a title lookup resolves the first hit`() {
        serve(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"results":[{"id":"77"}]}""")
            },
        )

        assertEquals("77", adapter.extractPageId(config, "?title=Somewhere", apiBase(), client()))
    }

    @Test
    fun `a page url without any id or title is dropped`() {
        assertNull(adapter.extractPageId(config, "https://wiki.example.com/spaces/X/overview", apiBase(), client()))
    }

    @Test
    fun `a page url with a query fragment without content is ignored`() {
        assertNull(adapter.extractPageId(config, "https://wiki.example.com/display?=5&x", apiBase(), client()))
    }

    @Test
    fun `a transport that dies before answering is reported without a message`() {
        val broken = mock<WebClient>()
        whenever(broken.get()).thenThrow(NullPointerException())

        val failure =
            assertThrows(IllegalStateException::class.java) {
                com.stacklens.testsupport.PrivateInvocation
                    .invoke(adapter, "loadPage", broken, "http://wiki.invalid/rest/api/", "42", true)
            }

        assertTrue(failure.message!!.contains("Cause: NullPointerException"), failure.message)
    }

    @Test
    fun `a non root page that dies only yields no page`() {
        val broken = mock<WebClient>()
        whenever(broken.get()).thenThrow(IllegalStateException("transport is gone"))

        assertNull(
            com.stacklens.testsupport.PrivateInvocation
                .invoke(adapter, "loadPage", broken, "http://wiki.invalid/rest/api/", "42", false),
        )
    }
}
