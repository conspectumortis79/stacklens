package com.stacklens.adapter.out.confluence

import com.stacklens.adapter.out.confluence.ConfluenceClient.AttachmentContainer
import com.stacklens.adapter.out.confluence.ConfluenceClient.AttachmentItem
import com.stacklens.adapter.out.confluence.ConfluenceClient.AttachmentMeta
import com.stacklens.adapter.out.confluence.ConfluenceClient.ChildrenMap
import com.stacklens.adapter.out.confluence.ConfluenceClient.ContentItem
import com.stacklens.adapter.out.confluence.ConfluenceClient.LinkMap
import com.stacklens.adapter.out.confluence.ConfluenceClient.SpaceRef
import com.stacklens.domain.model.ConfluenceConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class ConfluenceClientAttachmentTest {
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

    private fun cfg(cloudMode: Boolean = false): ConfluenceConfig =
        ConfluenceConfig(
            name = "c",
            baseUrl = serverBaseUrl,
            username = "u",
            apiToken = "t",
            cloudMode = cloudMode,
        )

    @Test
    fun `findDrawIo returns empty list when cloudMode is true`() {
        val item =
            ContentItem(
                children =
                    ChildrenMap(
                        attachment =
                            AttachmentContainer(
                                results =
                                    listOf(
                                        AttachmentItem(
                                            id = "a1",
                                            title = "diagram.drawio",
                                            links = LinkMap(download = "/download/a1"),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = true),
                item,
                "diagram",
                1L,
                "https://example/wiki/spaces/D/pages/1",
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIo returns empty when children attachment is null`() {
        val item = ContentItem(children = null)
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "kw",
                1L,
                "https://example/wiki/Page",
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIo returns empty when attachments list is empty`() {
        val item =
            ContentItem(
                children =
                    ChildrenMap(attachment = AttachmentContainer(results = emptyList())),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "kw",
                1L,
                "https://example/wiki/Page",
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIo skips non-drawio attachments`() {
        val item =
            ContentItem(
                children =
                    ChildrenMap(
                        attachment =
                            AttachmentContainer(
                                results =
                                    listOf(
                                        AttachmentItem(id = "x", title = "image.png"),
                                        AttachmentItem(id = "y", title = "doc.pdf"),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "kw",
                1L,
                "https://example/wiki/Page",
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIo detects via drawio-xml filename suffix`() {
        val item =
            ContentItem(
                title = "Architecture",
                space = SpaceRef(key = "DEV", name = "Dev"),
                children =
                    ChildrenMap(
                        attachment =
                            AttachmentContainer(
                                results =
                                    listOf(
                                        AttachmentItem(
                                            id = "a1",
                                            title = "arch.drawio.xml",
                                            links = LinkMap(download = "/download/a1"),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "arch",
                7L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
        val fp = (results[0].filePath ?: "").substringAfterLast("/").trim()
        assertEquals("arch.drawio.xml", fp)
        assertTrue(results[0].codeSnippet.orEmpty().contains("filename"))
    }

    @Test
    fun `findDrawIo detects via drawio mediaType`() {
        val item =
            ContentItem(
                title = "Flow",
                space = SpaceRef(key = "DEV", name = "Dev"),
                children =
                    ChildrenMap(
                        attachment =
                            AttachmentContainer(
                                results =
                                    listOf(
                                        AttachmentItem(
                                            id = "a2",
                                            title = "diagram",
                                            metadata = AttachmentMeta(mediaType = "application/drawio"),
                                            links = LinkMap(download = "/download/a2"),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "diagram",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
    }

    @Test
    fun `findDrawIo detects via vnd-jgraph mediaType`() {
        val item =
            ContentItem(
                title = "Chart",
                space = SpaceRef(key = "DEV", name = "Dev"),
                children =
                    ChildrenMap(
                        attachment =
                            AttachmentContainer(
                                results =
                                    listOf(
                                        AttachmentItem(
                                            id = "a3",
                                            title = "chart",
                                            metadata = AttachmentMeta(mediaType = "application/vnd.jgraph.mxfile"),
                                            links = LinkMap(download = "/download/a3"),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "chart",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
    }

    @Test
    fun `findDrawIo skips drawio attachment when neither filename nor content match keyword`() {
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
                                            id = "a4",
                                            title = "different.drawio",
                                            links = LinkMap(download = "/download/a4"),
                                        ),
                                    ),
                            ),
                    ),
            )
        server.enqueue(MockResponse().setBody(""))
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "absentkeyword",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findDrawIo returns filename hit when filename contains keyword`() {
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
                                            id = "a5",
                                            title = "matching-flow.drawio",
                                            links = LinkMap(download = "/download/a5"),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "matching",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
        assertTrue(results[0].codeSnippet.orEmpty().contains("filename"))
    }

    @Test
    fun `findDrawIo returns content hit when filename does not match but content does`() {
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
                                            id = "a6",
                                            title = "arch.drawio",
                                            links = LinkMap(download = "/download/a6"),
                                        ),
                                    ),
                            ),
                    ),
            )
        server.enqueue(MockResponse().setBody("<diagram>some KEYWORD content</diagram>"))
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "KEYWORD",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
        assertTrue(results[0].codeSnippet.orEmpty().contains("diagram contains"))
    }

    @Test
    fun `findDrawIo falls back to webui link when download missing`() {
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
                                            id = "a7",
                                            title = "arch.drawio",
                                            links = LinkMap(webui = "/download/a7/webui"),
                                        ),
                                    ),
                            ),
                    ),
            )
        server.enqueue(MockResponse().setBody("KEYWORD body"))
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "KEYWORD",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
    }

    @Test
    fun `findDrawIo falls back to base link when download and webui missing`() {
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
                                            id = "a8",
                                            title = "arch.drawio",
                                            links = LinkMap(base = "/download/a8/base"),
                                        ),
                                    ),
                            ),
                    ),
            )
        server.enqueue(MockResponse().setBody("KEYWORD body"))
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "KEYWORD",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
    }

    @Test
    fun `findDrawIo uses 'Unknown Space' when space is null`() {
        val item =
            ContentItem(
                title = "Page",
                space = null,
                children =
                    ChildrenMap(
                        attachment =
                            AttachmentContainer(
                                results =
                                    listOf(
                                        AttachmentItem(
                                            id = "a9",
                                            title = "arch.drawio",
                                            links = LinkMap(download = "/download/a9"),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "arch",
                1L,
                "https://example/wiki/Page",
            )
        assertEquals(1, results.size)
        assertEquals("Unknown Space", results[0].repoName)
    }

    @Test
    fun `findDrawIo uses space key when name is blank`() {
        val item =
            ContentItem(
                title = "Page",
                space = SpaceRef(key = "DEV", name = ""),
                children =
                    ChildrenMap(
                        attachment =
                            AttachmentContainer(
                                results =
                                    listOf(
                                        AttachmentItem(
                                            id = "a10",
                                            title = "arch.drawio",
                                            links = LinkMap(download = "/download/a10"),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "arch",
                1L,
                "https://example/wiki/spaces/DEV/pages/1",
            )
        assertEquals(1, results.size)
        assertEquals("DEV", results[0].repoName)
    }

    @Test
    fun `findDrawIo returns empty when download path is null and filename does not match`() {
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
                                            id = "a11",
                                            title = "diagram.drawio",
                                            links = LinkMap(),
                                        ),
                                    ),
                            ),
                    ),
            )
        val results =
            client.findDrawIoAttachmentResults(
                webClient,
                cfg(cloudMode = false),
                item,
                "absent",
                1L,
                "https://example/wiki/Page",
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `fetchAttachmentText returns body text`() {
        server.enqueue(MockResponse().setBody("hello attachment"))
        val text = client.fetchAttachmentText(webClient, serverBaseUrl, "/d/a1")
        assertEquals("hello attachment", text)
    }

    @Test
    fun `fetchAttachmentText returns empty on error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val text = client.fetchAttachmentText(webClient, serverBaseUrl, "/d/a1")
        assertEquals("", text)
    }

    @Test
    fun `fetchAttachmentText uses absolute URL when provided`() {
        val absolute = server.url("/absolute").toString()
        server.enqueue(MockResponse().setBody("abs body"))
        val text = client.fetchAttachmentText(webClient, "http://ignored", absolute)
        assertEquals("abs body", text)
    }

    @Test
    fun `blockWithTimeout default label returns null on timeout via internal call`() {
        val client = ConfluenceClient(readTimeoutMs = 200)
        val neverMono: Mono<String> = Mono.never()
        val result: String? = invokeBlockWithTimeoutNoLabel(client, neverMono)
        assertNull(result)
    }

    @Test
    fun `blockWithTimeout returns the Mono value when the Mono completes in time`() {
        val client = ConfluenceClient(readTimeoutMs = 200)
        val justMono: Mono<String> = Mono.just("ok")
        val result: String? = invokeBlockWithTimeoutNoLabel(client, justMono)
        assertEquals("ok", result)
    }

    @Test
    fun `blockWithTimeout returns null when the Mono errors`() {
        val client = ConfluenceClient(readTimeoutMs = 200)
        val errorMono: Mono<String> = Mono.error(RuntimeException("boom"))
        val result: String? = invokeBlockWithTimeoutNoLabel(client, errorMono)
        assertNull(result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> invokeBlockWithTimeoutNoLabel(
        client: ConfluenceClient,
        mono: Mono<T>,
    ): T? {
        val method =
            client.javaClass.getDeclaredMethod(
                "blockWithTimeout\$stacklens\$default",
                ConfluenceClient::class.java,
                Mono::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, client, mono, null, 0b1, null) as T?
    }

    @Test
    fun `fetchAttachmentText returns null on timeout via blockWithTimeout`() {
        val text = client.fetchAttachmentText(webClient, "http://127.0.0.1:1", "/d/a1")
        assertEquals("", text)
    }

    @Test
    fun `searchKeyword Server mode finds drawio attachment via filename`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"results":[{"id":"1","type":"page","title":"Architecture",
                        "space":{"key":"DEV","name":"Dev"},
                        "body":{"view":{"value":"<p>some diagram</p>"}},
                        "children":{"attachment":{"results":[{"id":"a1","title":"arch.drawio","_links":{"download":"/d/a1"}}]}},
                        "_links":{"webui":"/display/DEV/Architecture"}}],"start":0,"limit":25,"size":1}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(""),
        )
        val results = client.searchKeyword(cfg(cloudMode = false), "arch", emptyList(), sessionId = 1, offset = 0)
        assertNotNull(results)
        assertTrue(results.any { (it.filePath ?: "").contains("arch.drawio") })
    }
}
