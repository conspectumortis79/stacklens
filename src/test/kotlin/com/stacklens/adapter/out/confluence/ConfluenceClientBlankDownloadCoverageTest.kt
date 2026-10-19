package com.stacklens.adapter.out.confluence

import com.stacklens.adapter.out.confluence.ConfluenceClient.AttachmentContainer
import com.stacklens.adapter.out.confluence.ConfluenceClient.AttachmentItem
import com.stacklens.adapter.out.confluence.ConfluenceClient.ChildrenMap
import com.stacklens.adapter.out.confluence.ConfluenceClient.ContentItem
import com.stacklens.adapter.out.confluence.ConfluenceClient.LinkMap
import com.stacklens.adapter.out.confluence.ConfluenceClient.SpaceRef
import com.stacklens.domain.model.ConfluenceConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class ConfluenceClientBlankDownloadCoverageTest {
    private val client = ConfluenceClient(connectTimeoutMs = 1000, readTimeoutMs = 1000)

    private fun config() =
        ConfluenceConfig(
            name = "c",
            baseUrl = "http://127.0.0.1:1",
            username = "u",
            apiToken = "t",
            cloudMode = false,
        )

    @Test
    fun `a drawio attachment whose download link is blank is never fetched`() {
        val item =
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
                                            title = "ablaufplan.drawio",
                                            links = LinkMap(download = "   "),
                                        ),
                                    ),
                            ),
                    ),
            )

        val results =
            client.findDrawIoAttachmentResults(
                WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                config(),
                item,
                "architekt",
                1L,
                "https://example/wiki/Page",
            )

        assertTrue(results.isEmpty())
    }
}
