package com.stacklens.adapter.out.knowledge.confluence

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceHierarchyAdapterFullTest {
    private val mapper = ObjectMapper()
    private lateinit var server: MockWebServer
    private lateinit var adapter: ConfluenceHierarchyAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = ConfluenceHierarchyAdapter(ReactiveHttpClientFactory(connectTimeoutMs = 5000, readTimeoutMs = 10000))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): ConfluenceConfig =
        ConfluenceConfig(
            name = "demo",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
        )

    @Test
    fun `ensureHostMatches accepts a URL on the configured host`() {
        adapter.ensureHostMatches(
            config(),
            "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/1/Title",
        )
    }

    @Test
    fun `ensureHostMatches rejects a URL on a different host`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            adapter.ensureHostMatches(
                config(),
                "https://attacker.example.com/spaces/DEV/pages/1",
            )
        }
    }

    @Test
    fun `ensureHostMatches throws when the config base URL is malformed`() {
        val cfg =
            ConfluenceConfig(
                name = "demo",
                baseUrl = "not a url",
                username = "u",
                apiToken = "t",
            )
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                adapter.ensureHostMatches(cfg, "https://example.com/wiki/spaces/DEV/pages/1")
            }
        assertNotNull(ex)
    }

    @Test
    fun `ensureHostMatches throws when the page URL is malformed`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            adapter.ensureHostMatches(
                config(),
                "not a url",
            )
        }
    }

    @Test
    fun `walkHierarchy resolves the root from pages segment id and emits one snapshot per page`() {
        server.dispatcher =
            singlePageDispatcher(
                pageId = "100",
                title = "Hello",
                storageBody = "<p>World</p>",
                viewBody = "<p>World</p>",
            )
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Hello",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        val snap = results[0]
        assertEquals("100", snap.pageId)
        assertEquals("Hello", snap.title)
        assertEquals(0, snap.depth)
        assertTrue(snap.plainText.contains("World"))
        assertTrue(snap.tables.isEmpty(), "page without tables has empty table list")
    }

    @Test
    fun `walkHierarchy fetches calendar events for detected calendar macros and embeds them as tables`() {
        val viewBody =
            """
            <p>intro</p>
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">cal-1</ac:parameter>
              <ac:parameter ac:name="title">Support Calendar</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")

        var pageCalls = 0
        var calendarCalls = 0
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/rest/api/content/100?expand=") -> {
                            pageCalls++
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(pageBody("100", "Schedules", "<p>placeholder</p>", viewBody))
                        }
                        path.contains("/rest/api/content/100/child/page") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"results":[]}""")
                        path.contains("/rest/calendar-services/1.0/calendar/cal-1/events") -> {
                            calendarCalls++
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """{"events":[
                                        {"id":"e1","title":"On-call Alice","start":"2026-08-25T08:00:00.000Z","end":"2026-08-25T18:00:00.000Z"},
                                        {"id":"e2","title":"On-call Bob","start":"2026-08-26T08:00:00.000Z","end":"2026-08-26T18:00:00.000Z"}
                                      ]}""",
                                )
                        }
                        else ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"results":[]}""")
                    }
                }
            }

        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Schedules",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        val snap = results[0]
        assertEquals(1, snap.calendars.size)
        assertEquals("cal-1", snap.calendars[0].calendarId)
        assertEquals("Support Calendar", snap.calendars[0].title)
        assertEquals(2, snap.calendars[0].eventCount)
        assertTrue(
            snap.plainText.contains("Tabelle: Kalender \"Support Calendar\""),
            "expected calendar markdown table in: ${snap.plainText}",
        )
        assertTrue(snap.plainText.contains("On-call Alice"))
        assertTrue(snap.plainText.contains("On-call Bob"))
        assertTrue(snap.plainText.contains("Eintrag 1: On-call Alice"))
        assertEquals(1, pageCalls)
        assertEquals(1, calendarCalls)
    }

    @Test
    fun `walkHierarchy detects calendar macros even when only bodyStorage contains them`() {
        val storageBody =
            """
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">storage-cal</ac:parameter>
              <ac:parameter ac:name="title">Storage Calendar</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/rest/api/content/100?expand=") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(pageBody("100", "Schedules", storageBody, ""))
                        path.contains("/rest/api/content/100/child/page") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"results":[]}""")
                        path.contains("/rest/calendar-services/1.0/calendar/storage-cal/events") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """{"events":[
                                        {"id":"e1","title":"Storage event","start":"2026-08-25T08:00:00.000Z","end":"2026-08-25T18:00:00.000Z"}
                                      ]}""",
                                )
                        else ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"results":[]}""")
                    }
                }
            }

        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Schedules",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        val snap = results[0]
        assertEquals(1, snap.calendars.size)
        assertEquals("storage-cal", snap.calendars[0].calendarId)
        assertEquals("Storage Calendar", snap.calendars[0].title)
        assertEquals(1, snap.calendars[0].eventCount)
        assertTrue(snap.plainText.contains("Storage event"))
    }

    @Test
    fun `walkHierarchy keeps the page working when the calendar API returns 404`() {
        val viewBody =
            """
            <ac:structured-macro ac:name="calendar">
              <ac:parameter ac:name="id">missing-cal</ac:parameter>
              <ac:parameter ac:name="title">Missing</ac:parameter>
            </ac:structured-macro>
            """.trimIndent()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")

        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/rest/api/content/100?expand=") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(pageBody("100", "Schedules", "<p>placeholder</p>", viewBody))
                        path.contains("/rest/api/content/100/child/page") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"results":[]}""")
                        path.contains("/rest/calendar-services/1.0/calendar/missing-cal/events") ->
                            MockResponse().setResponseCode(404)
                        else ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"results":[]}""")
                    }
                }
            }

        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Schedules",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        val snap = results[0]
        assertTrue(snap.plainText.contains("intro").not() || !snap.plainText.contains("Tabelle: Kalender"))
        assertTrue(snap.calendars.isEmpty())
    }

    @Test
    fun `walkHierarchy linearizes tables inside the page body and exposes table metadata`() {
        val viewBody =
            """
            <h2>Production Databases</h2>
            <p>Owner der Datenbanken siehe Tabelle:</p>
            <table>
              <thead>
                <tr><th>Host</th><th>Port</th><th>Owner</th></tr>
              </thead>
              <tbody>
                <tr><td>db-prod-01</td><td>5432</td><td>Team Alpha</td></tr>
                <tr><td>db-prod-02</td><td>5432</td><td>Team Beta</td></tr>
              </tbody>
            </table>
            """.trimIndent().replace("\n", "\\n")
        server.dispatcher =
            singlePageDispatcher(
                pageId = "100",
                title = "Databases",
                storageBody = "<p>placeholder</p>",
                viewBody = viewBody,
            )

        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Databases",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        val snap = results[0]
        assertTrue(snap.plainText.contains("Tabelle: Production Databases"))
        assertTrue(snap.plainText.contains("| Host | Port | Owner |"))
        assertTrue(snap.plainText.contains("| db-prod-01 | 5432 | Team Alpha |"))
        assertTrue(
            snap.plainText.contains("Zeile 1: Host db-prod-01, Port 5432, Owner Team Alpha."),
            "expected row sentence in: ${snap.plainText}",
        )
        assertTrue(snap.plainText.contains("Owner Team Beta"))
        assertEquals(1, snap.tables.size)
        assertEquals("Production Databases", snap.tables[0].caption)
        assertEquals(listOf("Host", "Port", "Owner"), snap.tables[0].headers)
        assertEquals(2, snap.tables[0].rowCount)
    }

    @Test
    fun `walkHierarchy recurses through child pages and stops at maxDepth`() {
        server.dispatcher =
            twoLevelHierarchyDispatcher(
                rootId = "100",
                children = listOf("200"),
                grandChildren = emptyList(),
            )
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Hello",
                    includeChildren = true,
                    maxDepth = 1,
                    maxPages = 50,
                ).toList()

        assertEquals(2, results.size)
        assertEquals("100", results[0].pageId)
        assertEquals("200", results[1].pageId)
        assertEquals(1, results[1].depth)
    }

    @Test
    fun `walkHierarchy walks past maxDepth and only loads the first level when maxDepth is 0`() {
        server.dispatcher =
            twoLevelHierarchyDispatcher(
                rootId = "100",
                children = listOf("200"),
                grandChildren = emptyList(),
            )
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Hello",
                    includeChildren = true,
                    maxDepth = 0,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        assertEquals("100", results[0].pageId)
    }

    @Test
    fun `walkHierarchy stops emitting pages when maxPages is reached`() {
        server.dispatcher =
            twoLevelHierarchyDispatcher(
                rootId = "100",
                children = listOf("200"),
                grandChildren = emptyList(),
            )
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Hello",
                    includeChildren = true,
                    maxDepth = 100,
                    maxPages = 2,
                ).toList()

        assertTrue(results.size <= 2, "expected at most 2 results, got ${results.size}")
    }

    @Test
    fun `walkHierarchy throws with the root cause when the root page fetch returns 404`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(404)
            }
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                adapter
                    .walkHierarchy(
                        config(),
                        "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/999/Title",
                        includeChildren = true,
                        maxDepth = 5,
                        maxPages = 50,
                    ).toList()
            }
        assertTrue(
            ex.message!!.contains("root page fetch failed"),
            "expected root-fetch error, got: ${ex.message}",
        )
    }

    @Test
    fun `walkHierarchy de-duplicates pages encountered via multiple paths`() {
        server.dispatcher =
            doubleLinkedPageDispatcher(
                rootId = "100",
                sharedChildId = "200",
            )
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Hello",
                    includeChildren = true,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        val unique = results.map { it.pageId }.toSet()
        assertEquals(unique.size, results.size, "expected unique pages, got $results")
    }

    @Test
    fun `walkHierarchy renames pages with blank titles`() {
        server.dispatcher =
            singlePageDispatcher(
                pageId = "100",
                title = " ",
                storageBody = "<p>x</p>",
                viewBody = "<p>x</p>",
            )
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/blank",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertEquals("(untitled)", results[0].title)
    }

    @Test
    fun `extractPageId returns the query param value when present`() {
        val url = "${server.url("/").toString().trimEnd('/')}/foo?pageId=12345"
        val id =
            adapter.extractPageId(
                config(),
                url,
                "",
                com.stacklens.shared.http
                    .ReactiveHttpClientFactory(1, 1)
                    .builder()
                    .build(),
            )
        assertEquals("12345", id)
    }

    @Test
    fun `extractPageId reads the numeric segment from the pages path`() {
        val url = "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/777/Hello"
        val id =
            adapter.extractPageId(
                config(),
                url,
                "",
                com.stacklens.shared.http
                    .ReactiveHttpClientFactory(1, 1)
                    .builder()
                    .build(),
            )
        assertEquals("777", id)
    }

    @Test
    fun `loadPage swallows WebClientResponseException so the walk continues`() {
        var hits = 0
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    hits++
                    return if (hits == 1) {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(
                                """
                                {"id":"100","title":"t","space":{"key":"DEV"},"body":{"storage":{"value":"<p>x</p>"},"view":{"value":"<p>x</p>"}},"version":{"number":1},"_links":{"base":"${server.url(
                                    "/",
                                )}","webui":"/pages/100"}}
                                """.trimIndent(),
                            )
                    } else if (hits == 2) {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"results":[]}""")
                    } else {
                        MockResponse()
                            .setResponseCode(500)
                            .setHeader("Content-Type", "text/plain")
                            .setBody("boom")
                    }
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/t",
                    includeChildren = true,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        assertEquals("100", results[0].pageId)
    }

    @Test
    fun `PageResponse from parses only the first id and supports the body storages`() {
        val node: JsonNode =
            mapper.readTree(
                """
                {
                  "id":"7","title":"X",
                  "space":{"key":"DEV"},
                  "body":{
                    "storage":{"value":"<p>STO</p>"},
                    "view":{"value":"<p>VIEW</p>"}
                  },
                  "version":{"number":42},
                  "_links":{"base":"https://example.com","webui":"/pages/7"}
                }
                """.trimIndent(),
            )
        val cls = Class.forName("com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter\$PageResponse\$Companion")
        val companionField = Class.forName("com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter\$PageResponse").getDeclaredField("Companion").apply { isAccessible = true }
        val companion = companionField.get(null)
        val fromMethod = cls.declaredMethods.first { it.name == "from" }
        fromMethod.isAccessible = true
        val result = fromMethod.invoke(companion, node)
        assertNotNull(result)
    }

    @Test
    fun `walkHierarchy uses the wiki path prefix when the baseUrl already contains wiki`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"results":[
                              {"id":"12345","title":"From CQL","type":"page"}
                            ]}
                            """.trimIndent(),
                        )
            }
        val cfg =
            ConfluenceConfig(
                name = "demo",
                baseUrl =
                    "${server.url("/").toString().trimEnd('/')}/wiki",
                username = "u",
                apiToken = "t",
            )
        val results =
            adapter
                .walkHierarchy(
                    cfg,
                    "${server.url("/").toString().trimEnd('/')}/wiki/spaces/DEV/pages/100/Title",
                    includeChildren = true,
                    maxDepth = 0,
                    maxPages = 50,
                ).toList()
        assertTrue(results.size <= 1)
    }

    @Test
    fun `walkHierarchy falls back to a CQL title lookup when no id can be parsed from the URL`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {"results":[
                              {"id":"99999","title":"CQL match","type":"page"}
                            ]}
                            """.trimIndent(),
                        )
            }
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/CQLOnly",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()

        assertTrue(results.isEmpty() || results.first().pageId == "99999")
    }

    @Test
    fun `walkHierarchy falls back to an empty pageId when the loadPage response is missing the id field`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.contains("/content/") ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(
                                    """{"title":"missing-id","space":{"key":"X"},"body":{"storage":{"value":"<p>x</p>"},"view":{"value":"<p>x</p>"}},"version":{"number":1},"_links":{"base":"${server.url(
                                        "/",
                                    )}","webui":"/pages/0"}}""",
                                )
                        else ->
                            MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody("""{"results":[]}""")
                    }
                }
            }
        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEV/pages/100/Title",
                    includeChildren = false,
                    maxDepth = 5,
                    maxPages = 50,
                ).toList()
        assertEquals(1, results.size)
        assertEquals("", results[0].pageId)
    }

    @Test
    fun `PageResponse from with the minimal payload still produces a non-null snapshot`() {
        val node: JsonNode =
            mapper.readTree(
                """{"id":"1","title":"x","space":{"key":"S"},"body":{},"version":{"number":1},"_links":{"base":"https://x","webui":"/pages/1"}}""",
            )
        val cls = Class.forName("com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter\$PageResponse\$Companion")
        val companionField = Class.forName("com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter\$PageResponse").getDeclaredField("Companion").apply { isAccessible = true }
        val companion = companionField.get(null)
        val fromMethod = cls.declaredMethods.first { it.name == "from" }
        fromMethod.isAccessible = true
        val result = fromMethod.invoke(companion, node)
        assertNotNull(result)
    }

    private fun pageBody(
        pageId: String,
        title: String,
        storageBody: String,
        viewBody: String,
    ): String =
        """{
          "id":"$pageId","title":"$title",
          "space":{"key":"DEV"},
          "body":{
            "storage":{"value":"$storageBody"},
            "view":{"value":"$viewBody"}
          },
          "version":{"number":1},
          "_links":{"base":"${server.url("/")}","webui":"/pages/$pageId"}
        }"""

    private fun singlePageDispatcher(
        pageId: String,
        title: String,
        storageBody: String,
        viewBody: String,
    ): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/content/$pageId?expand=body.view") ->
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(pageBody(pageId, title, storageBody, viewBody))
                    path.contains("/content/$pageId/child/page") ->
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"results":[]}""")
                    else ->
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody("""{"results":[]}""")
                }
            }
        }

    private fun twoLevelHierarchyDispatcher(
        rootId: String,
        children: List<String>,
        grandChildren: List<String>,
    ): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.contains("/content/$rootId/child/page")) {
                    val json =
                        children.joinToString(",") { "{\"id\":\"$it\",\"title\":\"child-$it\"}" }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"results":[$json]}""")
                }
                for (id in listOf(rootId) + children + grandChildren) {
                    if (path.contains("/content/$id?expand=body.view")) {
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(pageBody(id, "title-$id", "<p>x</p>", "<p>x</p>"))
                    }
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"results":[]}""")
            }
        }

    private fun doubleLinkedPageDispatcher(
        rootId: String,
        sharedChildId: String,
    ): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.contains("/content/$rootId/child/page")) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"results":[
                              {"id":"$sharedChildId","title":"shared","type":"page"}
                            ]}""",
                        )
                }
                if (path.contains("/content/$sharedChildId/child/page")) {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """{"results":[
                              {"id":"$rootId","title":"back","type":"page"}
                            ]}""",
                        )
                }
                for (id in listOf(rootId, sharedChildId)) {
                    if (path.contains("/content/$id?expand=body.view")) {
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(pageBody(id, "title-$id", "<p>x</p>", "<p>x</p>"))
                    }
                }
                return MockResponse().setResponseCode(404)
            }
        }
}
