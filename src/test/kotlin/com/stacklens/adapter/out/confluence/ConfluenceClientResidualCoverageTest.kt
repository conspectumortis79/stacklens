package com.stacklens.adapter.out.confluence

import com.stacklens.adapter.out.confluence.ConfluenceClient.AttachmentContainer
import com.stacklens.adapter.out.confluence.ConfluenceClient.AttachmentItem
import com.stacklens.adapter.out.confluence.ConfluenceClient.ChildrenMap
import com.stacklens.adapter.out.confluence.ConfluenceClient.ContentItem
import com.stacklens.adapter.out.confluence.ConfluenceClient.SpaceRef
import com.stacklens.domain.model.ConfluenceConfig
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class ConfluenceClientResidualCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var serverBaseUrl: String
    private lateinit var client: ConfluenceClient
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        serverBaseUrl = server.url("").toString().removeSuffix("/")
        client = ConfluenceClient(connectTimeoutMs = 5000, readTimeoutMs = 5000)
        webClient =
            WebClient
                .builder()
                .baseUrl(server.url("/").toString().trimEnd('/'))
                .build()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cfg(): ConfluenceConfig =
        ConfluenceConfig(
            name = "c",
            baseUrl = serverBaseUrl,
            username = "u",
            apiToken = "t",
            cloudMode = false,
        )

    private fun drawioItem(links: com.stacklens.adapter.out.confluence.ConfluenceClient.LinkMap?) =
        ContentItem(
            title = "Page",
            space = SpaceRef(key = "DEV", name = "Dev"),
            children =
                ChildrenMap(
                    attachment =
                        AttachmentContainer(
                            results =
                                listOf(
                                    AttachmentItem(
                                        id = "a1",
                                        title = "arch.drawio",
                                        links = links,
                                    ),
                                ),
                        ),
                ),
        )

    @Test
    fun `drawio attachment with null links and filename hit is reported without a fetch`() {
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(),
                drawioItem(links = null),
                "arch",
                1L,
                "https://example/wiki/Page",
            )
        assertEquals(1, results.size)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `drawio attachment with null links and no filename hit yields no result`() {
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(),
                drawioItem(links = null),
                "absent",
                1L,
                "https://example/wiki/Page",
            )
        assertTrue(results.isEmpty())
        assertEquals(0, server.requestCount)
    }
}
