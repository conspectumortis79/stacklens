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

class ConfluenceHierarchyAdapterWalkProbeTest {
    private lateinit var server: MockWebServer

    private lateinit var adapter: ConfluenceHierarchyAdapter

    private val factory = ReactiveHttpClientFactory(connectTimeoutMs = 5000, readTimeoutMs = 10000)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = ConfluenceHierarchyAdapter(factory)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): ConfluenceConfig =
        ConfluenceConfig(
            name = "probe",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
        )

    private fun json(body: String): MockResponse = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun pageJson(
        id: String,
        title: String,
        spaceKey: String? = "DEV",
        view: String = "<p>view</p>",
    ): String {
        val space = if (spaceKey == null) "" else "\"space\":{\"key\":\"$spaceKey\"},"
        return """{"id":"$id",$space"title":"$title","body":{"view":{"value":"$view"}},"version":{"number":1}}"""
    }

    @Test
    fun `the walk stops before loading anything when no page may be emitted`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = json(pageJson("101", "Root"))
            }

        val pages = adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 3, maxPages = 0).toList()

        assertEquals(emptyList<String>(), pages.map { it.pageId })
    }

    @Test
    fun `a child page that cannot be loaded is skipped while the walk continues`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/content/101/child/page") -> json("""{"results":[{"id":"202"},{"id":"303"}]}""")
                        path.contains("/content/202?expand=body.view") -> MockResponse().setResponseCode(404).setBody("gone")
                        path.contains("/content/303?expand=body.view") -> json(pageJson("303", "Dreissig"))
                        else -> json(pageJson("101", "Hundert"))
                    }
                }
            }

        val pages = adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 2, maxPages = 10).toList()

        assertEquals(listOf("101", "303"), pages.map { it.pageId })
    }

    @Test
    fun `child page ids are read from every page of the paginated result`() {
        val manyChildren = (1..50).joinToString(",") { """{"id":"c$it"}""" }
        val requestedPaths = mutableListOf<String>()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    requestedPaths += path
                    return when {
                        path.contains("/content/101/child/page") && path.contains("start=0") -> json("""{"results":[$manyChildren]}""")
                        path.contains("/content/101/child/page") -> json("""{"results":[{"id":"last"}]}""")
                        path.contains("/content/101?expand=body.view") -> json(pageJson("101", "Root"))
                        else -> json(pageJson("nothing", "nothing"))
                    }
                }
            }

        val pages = adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 1, maxPages = 1).toList()

        assertEquals(1, pages.size)
        assertTrue(requestedPaths.any { it.contains("start=50") }, "expected a second page request: $requestedPaths")
    }

    @Test
    fun `blank child ids and non array results are ignored`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/child/page") -> json("""{"results":[{"id":"  "},{"noId":1},{"id":"valid"}]}""")
                        path.contains("/content/valid/child/page") -> json("""{"results":"not-an-array"}""")
                        path.contains("/content/valid?expand=body.view") -> json(pageJson("valid", "Kind"))
                        else -> json(pageJson("101", "Root"))
                    }
                }
            }

        val pages = adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 2, maxPages = 5).toList()

        assertEquals(listOf("101", "valid"), pages.map { it.pageId })
    }

    @Test
    fun `a failing child page lookup ends the recursion without failing the walk`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/child/page") -> MockResponse().setResponseCode(500).setBody("boom")
                        else -> json(pageJson("101", "Root"))
                    }
                }
            }

        val pages = adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 2, maxPages = 5).toList()

        assertEquals(listOf("101"), pages.map { it.pageId })
    }

    @Test
    fun `a broken child page payload is treated as a transport error`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/child/page") -> json("""{"results": [ this is not json ]}""")
                        else -> json(pageJson("101", "Root"))
                    }
                }
            }

        val pages = adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 2, maxPages = 5).toList()

        assertEquals(listOf("101"), pages.map { it.pageId })
    }

    @Test
    fun `the root page failure keeps the cause in the raised error`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(503).setBody("unavailable")
            }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 1, maxPages = 5).toList()
            }

        assertTrue(failure.message!!.contains("cannot start hierarchy walk"), "unexpected: ${failure.message}")
        assertTrue(failure.cause != null, "the transport failure must be kept as the cause")
    }

    @Test
    fun `a root page response without a body yields a snapshot with empty text`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/child/page") -> json("""{"results":[]}""")
                        else -> json("""{"id":"101","title":"Root","body":{},"version":{"number":1}}""")
                    }
                }
            }

        val page = adapter.walkHierarchy(config(), "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 1, maxPages = 5).single()

        assertEquals("101", page.pageId)
        assertEquals("", page.plainText)
    }

    @Test
    fun `pages without a space or id fall back to the configured base url`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/child/page") -> json("""{"results":[{"id":"202"},{"id":"303"}]}""")
                        path.contains("/content/202?expand=body.view") -> json("""{"id":"202","title":"Ohne Space","body":{"view":{"value":"<p>x</p>"}}}""")
                        path.contains("/content/303?expand=body.view") -> json(pageJson("303", "Ohne Titel", spaceKey = "  "))
                        else -> json(pageJson("101", "Root"))
                    }
                }
            }

        val cfg = config()
        val pages = adapter.walkHierarchy(cfg, "${server.url("/")}/pages/101/Root", includeChildren = true, maxDepth = 1, maxPages = 5).toList()

        assertEquals(cfg.baseUrl, pages.first { it.pageId == "202" }.sourceUri)
        assertEquals(cfg.baseUrl, pages.first { it.pageId == "303" }.sourceUri)
    }

    @Test
    fun `pages with a space build a wiki link and reuse the configured wiki prefix`() {
        val withWiki =
            ConfluenceConfig(
                name = "probe",
                baseUrl = "${server.url("/")}/wiki",
                username = "u",
                apiToken = "t",
            )
        val withoutWiki = config()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/child/page") -> json("""{"results":[]}""")
                        else -> json("""{"id":"101","title":"Root","space":{"key":"DEV"},"version":{"number":1}}""")
                    }
                }
            }

        val plain = adapter.walkHierarchy(withoutWiki, "${server.url("/")}/pages/101/Root", false, 1, 5).single()
        val prefixed = adapter.walkHierarchy(withWiki, "${server.url("/")}/wiki/pages/101/Root", false, 1, 5).single()

        assertTrue(plain.sourceUri.endsWith("/wiki/spaces/DEV/pages/101"), plain.sourceUri)
        assertTrue(prefixed.sourceUri.endsWith("/wiki/spaces/DEV/pages/101"), prefixed.sourceUri)
    }

    @Test
    fun `extractPageId prefers a page id query param over the path`() {
        val client = factory.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()
        val cfg = config()

        assertEquals("777", adapter.extractPageId(cfg, "https://cf.example/pages/101/X?pageId=777", "/rest/api", client))
    }

    @Test
    fun `extractPageId ignores a blank page id and query parts without a value`() {
        val client = factory.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()

        assertNull(adapter.extractPageId(config(), "https://cf.example/other?pageId=&novalue&&=x", "/rest/api", client))
    }

    @Test
    fun `extractPageId looks the title up when the url carries no id`() {
        server.enqueue(json("""{"results":[{"id":"555","title":"Handbuch"}]}"""))
        val client = factory.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()

        assertEquals("555", adapter.extractPageId(config(), "https://cf.example/display/DEV/Handbuch?title=Handbuch", "/rest/api", client))
        assertEquals("/rest/api/content?title=Handbuch&limit=1&expand=version", server.takeRequest().path)
    }

    @Test
    fun `extractPageId returns null when the title lookup finds nothing`() {
        server.enqueue(json("""{"results":[]}"""))
        val client = factory.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()

        assertNull(adapter.extractPageId(config(), "https://cf.example/x?title=Fehlanzeige", "/rest/api", client))
    }

    @Test
    fun `extractPageId returns null when the title lookup fails`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("broken"))
        val client = factory.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()

        assertNull(adapter.extractPageId(config(), "https://cf.example/x?title=Kaputt", "/rest/api", client))
    }

    @Test
    fun `extractPageId returns null when the lookup answer cannot be read`() {
        server.enqueue(json("""{"results":"kein array"}"""))
        val client = factory.builder().baseUrl(server.url("/").toString().trimEnd('/')).build()

        assertEquals(null, adapter.extractPageId(config(), "https://cf.example/x?title=Komisch", "/rest/api", client))
    }
}
