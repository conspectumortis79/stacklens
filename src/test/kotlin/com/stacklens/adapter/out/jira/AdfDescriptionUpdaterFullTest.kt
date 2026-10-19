package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.lang.reflect.Method

class AdfDescriptionUpdaterFullTest {
    private val mapper = ObjectMapper()

    private fun cloudConfig(baseUrl: String): JiraConfig =
        JiraConfig(
            name = "c",
            baseUrl = baseUrl,
            projectKey = "PROJ",
            username = "u@example.com",
            apiToken = "tok",
            cloudMode = true,
        )

    private fun dcConfig(baseUrl: String): JiraConfig =
        JiraConfig(
            name = "c",
            baseUrl = baseUrl,
            projectKey = "PROJ",
            username = "",
            apiToken = "pat-tok",
            cloudMode = false,
        )

    private fun updaterWith(
        jiraTicketHttp: JiraTicketHttpClient,
        md: JiraMarkdownConverter = mock(),
    ): AdfDescriptionUpdater =
        AdfDescriptionUpdater(
            jiraTicketHttp = jiraTicketHttp,
            objectMapper = mapper,
            mdConverter = md,
        )

    private fun callPrivate(
        target: Any,
        name: String,
        vararg args: Any?,
    ): Any? {
        val method: Method = target.javaClass.declaredMethods.first { it.name == name }
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    @Test
    fun `append merges ADF content on Cloud and PUTs merged payload`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(
                        """{"fields":{"description":{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"old"}]}]}}}""",
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val updater = updaterWith(http)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to listOf(mapOf<String, Any?>("type" to "paragraph", "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "new")))),
                )
            val result = updater.append(cloudConfig(server.url("/").toString()), "PROJ-1", adfDoc)
            assertNotNull(result)
            assertTrue(result.contains("\"type\":\"doc\""))
            val get = server.takeRequest()
            assertEquals("/rest/api/3/issue/PROJ-1?fields=description", get.path)
            val put = server.takeRequest()
            assertEquals("/rest/api/3/issue/PROJ-1", put.path)
            assertTrue(put.body.readUtf8().contains("\"description\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `append uses v2 endpoint for Server DC and renders wiki markup`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"fields":{"description":"old wiki text"}}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val md = mock<JiraMarkdownConverter>()
            whenever(md.markdownToJiraWiki(any())).thenAnswer { "WIKI:${it.arguments[0]}" }
            val updater = updaterWith(http, md)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to
                        listOf(
                            mapOf<String, Any?>(
                                "type" to "heading",
                                "attrs" to mapOf<String, Any>("level" to 2),
                                "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "h")),
                            ),
                        ),
                )
            val merged = updater.append(dcConfig(server.url("/").toString()), "PROJ-2", adfDoc)
            assertTrue(merged.startsWith("old wiki text\n\n----\n\n"))
            assertTrue(merged.contains("WIKI:"))
            val get = server.takeRequest()
            assertEquals("/rest/api/2/issue/PROJ-2?fields=description", get.path)
            val put = server.takeRequest()
            assertEquals("/rest/api/2/issue/PROJ-2", put.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `append handles empty existing Server DC description without separator`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"fields":{}}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val md = mock<JiraMarkdownConverter>()
            whenever(md.markdownToJiraWiki(any())).thenAnswer { "WIKI:${it.arguments[0]}" }
            val updater = updaterWith(http, md)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to listOf(mapOf<String, Any?>("type" to "paragraph", "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "p")))),
                )
            val merged = updater.append(dcConfig(server.url("/").toString()), "PROJ-3", adfDoc)
            assertTrue(merged.startsWith("WIKI:"))
            assertTrue(!merged.contains("----"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `append on Server DC with null description in JSON works`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"fields":{"description":null}}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val updater = updaterWith(http)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to listOf(mapOf<String, Any?>("type" to "paragraph", "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "x")))),
                )
            val merged = updater.append(dcConfig(server.url("/").toString()), "PROJ-4", adfDoc)
            assertNotNull(merged)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `append handles empty Cloud description`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"fields":{}}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val updater = updaterWith(http)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to listOf(mapOf<String, Any?>("type" to "paragraph", "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "z")))),
                )
            val merged = updater.append(cloudConfig(server.url("/").toString()), "PROJ-5", adfDoc)
            assertTrue(merged.contains("\"content\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `append falls back to empty existing description when GET returns non-2xx`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"errorMessages":["boom"]}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val updater = updaterWith(http)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to listOf(mapOf<String, Any?>("type" to "paragraph", "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "n")))),
                )
            val merged = updater.append(cloudConfig(server.url("/").toString()), "PROJ-GETFAIL", adfDoc)
            assertNotNull(merged)
            assertTrue(merged.contains("\"text\":\"n\""))
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `putDescription throws IllegalStateException with status code on PUT failure`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"fields":{"description":""}}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"errorMessages":["boom"]}"""),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val updater = updaterWith(http)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to emptyList<Map<String, Any?>>(),
                )
            val ex =
                runCatching {
                    updater.append(dcConfig(server.url("/").toString()), "PROJ-6", adfDoc)
                }.exceptionOrNull()
            assertNotNull(ex)
            assertTrue(ex is IllegalStateException)
            assertTrue(ex!!.message!!.contains("500"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `adfContentAsMapList handles null input`() {
        val updater = updaterWith(mock())

        @Suppress("UNCHECKED_CAST")
        val out = callPrivate(updater, "adfContentAsMapList", null as Any?) as List<Map<String, Any?>>
        assertTrue(out.isEmpty())
    }

    @Test
    fun `adfContentAsMapList handles non-array JsonNode`() {
        val updater = updaterWith(mock())
        val node: JsonNode = mapper.readTree("""{"type":"doc"}""")

        @Suppress("UNCHECKED_CAST")
        val out = callPrivate(updater, "adfContentAsMapListFromArrayNode", node) as List<Map<String, Any?>>
        assertTrue(out.isEmpty())
    }

    @Test
    fun `adfContentAsMapListFromArrayNode skips non-object entries`() {
        val updater = updaterWith(mock())
        val node: JsonNode = mapper.readTree("""[{"type":"paragraph"}, "raw", 42]""")

        @Suppress("UNCHECKED_CAST")
        val out = callPrivate(updater, "adfContentAsMapListFromArrayNode", node) as List<Map<String, Any?>>
        assertEquals(1, out.size)
        assertEquals("paragraph", out[0]["type"])
    }

    @Test
    fun `adfContentAsMapList filters out non-Map entries in raw list`() {
        val updater = updaterWith(mock())
        val raw: List<Any> = listOf(mapOf<String, Any?>("type" to "paragraph"), "string-not-map", 99)

        @Suppress("UNCHECKED_CAST")
        val out = callPrivate(updater, "adfContentAsMapList", raw) as List<Map<String, Any?>>
        assertEquals(1, out.size)
    }

    @Test
    fun `adfContentAsMapList returns empty for unsupported types`() {
        val updater = updaterWith(mock())

        @Suppress("UNCHECKED_CAST")
        val out = callPrivate(updater, "adfContentAsMapList", 42) as List<Map<String, Any?>>
        assertTrue(out.isEmpty())
    }

    @Test
    fun `adfToMarkdown renders heading, paragraph, rule, bulletList, table, hardBreak, marks`() {
        val updater = updaterWith(mock())
        val attrsForLink: Map<String, Any> = mapOf<String, Any>("href" to "https://x")
        val doc: Map<String, Any?> =
            mapOf(
                "type" to "doc",
                "content" to
                    listOf<Map<String, Any?>>(
                        mapOf<String, Any?>(
                            "type" to "heading",
                            "attrs" to mapOf<String, Any>("level" to 1),
                            "content" to
                                listOf<Map<String, Any?>>(
                                    mapOf<String, Any?>(
                                        "type" to "text",
                                        "text" to "Title",
                                        "marks" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "strong")),
                                    ),
                                ),
                        ),
                        mapOf<String, Any?>(
                            "type" to "paragraph",
                            "content" to
                                listOf<Map<String, Any?>>(
                                    mapOf<String, Any?>("type" to "text", "text" to "em-", "marks" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "em"))),
                                    mapOf<String, Any?>(
                                        "type" to "text",
                                        "text" to "link",
                                        "marks" to
                                            listOf<Map<String, Any?>>(
                                                mapOf<String, Any?>("type" to "em"),
                                                mapOf<String, Any?>("type" to "link", "attrs" to attrsForLink),
                                            ),
                                    ),
                                ),
                        ),
                        mapOf<String, Any?>("type" to "rule"),
                        mapOf<String, Any?>(
                            "type" to "bulletList",
                            "content" to
                                listOf<Map<String, Any?>>(
                                    mapOf<String, Any?>(
                                        "type" to "listItem",
                                        "content" to
                                            listOf<Map<String, Any?>>(
                                                mapOf<String, Any?>(
                                                    "type" to "paragraph",
                                                    "content" to
                                                        listOf<Map<String, Any?>>(
                                                            mapOf<String, Any?>("type" to "text", "text" to "first"),
                                                            mapOf<String, Any?>("type" to "hardBreak"),
                                                            mapOf<String, Any?>("type" to "text", "text" to "second"),
                                                        ),
                                                ),
                                            ),
                                    ),
                                    mapOf<String, Any?>(
                                        "type" to "listItem",
                                        "content" to
                                            listOf<Map<String, Any?>>(
                                                mapOf<String, Any?>("type" to "paragraph", "content" to emptyList<Map<String, Any?>>()),
                                            ),
                                    ),
                                ),
                        ),
                        mapOf<String, Any?>(
                            "type" to "table",
                            "content" to
                                listOf<Map<String, Any?>>(
                                    mapOf<String, Any?>(
                                        "type" to "tableRow",
                                        "content" to
                                            listOf<Map<String, Any?>>(
                                                mapOf<String, Any?>(
                                                    "type" to "tableCell",
                                                    "content" to
                                                        listOf<Map<String, Any?>>(
                                                            mapOf<String, Any?>(
                                                                "type" to "paragraph",
                                                                "content" to
                                                                    listOf<Map<String, Any?>>(
                                                                        mapOf<String, Any?>("type" to "text", "text" to "A"),
                                                                    ),
                                                            ),
                                                        ),
                                                ),
                                                mapOf<String, Any?>(
                                                    "type" to "tableCell",
                                                    "content" to
                                                        listOf<Map<String, Any?>>(
                                                            mapOf<String, Any?>(
                                                                "type" to "paragraph",
                                                                "content" to
                                                                    listOf<Map<String, Any?>>(
                                                                        mapOf<String, Any?>("type" to "text", "text" to "B"),
                                                                    ),
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                                    mapOf<String, Any?>(
                                        "type" to "tableRow",
                                        "content" to
                                            listOf<Map<String, Any?>>(
                                                mapOf<String, Any?>("type" to "tableCell", "content" to emptyList<Map<String, Any?>>()),
                                            ),
                                    ),
                                ),
                        ),
                        mapOf<String, Any?>(
                            "type" to "unknown",
                            "content" to
                                listOf<Map<String, Any?>>(
                                    mapOf<String, Any?>(
                                        "type" to "paragraph",
                                        "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "nested")),
                                    ),
                                ),
                        ),
                    ),
            )

        @Suppress("UNCHECKED_CAST")
        val md = callPrivate(updater, "adfToMarkdown", doc) as String
        assertTrue(md.contains("# **Title**"))
        assertTrue(md.contains("**Title**"))
        assertTrue(md.contains("_em-_"))
        assertTrue(md.contains("[_link_](https://x)"))
        assertTrue(md.contains("---"))
        assertTrue(md.contains("- first"))
        assertTrue(md.contains("| A | B |"))
        assertTrue(md.contains("nested"))
    }

    @Test
    fun `renderAdfNode clamps heading level to 1-3 and renders empty paragraph`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val doc1: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "heading",
                "attrs" to mapOf<String, Any>("level" to 99),
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "x")),
            )
        callPrivate(updater, "renderAdfNode", doc1, sb)
        assertTrue(sb.toString().startsWith("###"))
        sb.clear()
        val doc2: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "heading",
                "attrs" to mapOf<String, Any>("level" to -3),
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "y")),
            )
        callPrivate(updater, "renderAdfNode", doc2, sb)
        assertTrue(sb.toString().startsWith("#"))
    }

    @Test
    fun `renderAdfNode paragraph with no inline content`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> = mapOf<String, Any?>("type" to "paragraph", "content" to emptyList<Map<String, Any?>>())
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("\n\n", sb.toString())
    }

    @Test
    fun `renderAdfNode unknown type recurses into content`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "custom",
                "content" to
                    listOf<Map<String, Any?>>(
                        mapOf<String, Any?>(
                            "type" to "paragraph",
                            "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "deep")),
                        ),
                    ),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertTrue(sb.toString().contains("deep"))
    }

    @Test
    fun `appendInlineNode handles text, hardBreak, em, strong, link`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node1: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "text",
                "text" to "x",
                "marks" to
                    listOf<Map<String, Any?>>(
                        mapOf<String, Any?>("type" to "strong"),
                        mapOf<String, Any?>("type" to "em"),
                        mapOf<String, Any?>("type" to "link", "attrs" to mapOf<String, Any>("href" to "u")),
                    ),
            )
        callPrivate(updater, "appendInlineNode", node1, sb)
        assertEquals("[_**x**_](u)", sb.toString())
        sb.clear()
        val node2: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "text",
                "text" to "y",
                "marks" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "link", "attrs" to mapOf<String, Any>("href" to ""))),
            )
        callPrivate(updater, "appendInlineNode", node2, sb)
        assertEquals("y", sb.toString())
        sb.clear()
        val node3: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "text",
                "text" to "z",
                "marks" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "unknown")),
            )
        callPrivate(updater, "appendInlineNode", node3, sb)
        assertEquals("z", sb.toString())
        sb.clear()
        callPrivate(updater, "appendInlineNode", mapOf<String, Any?>("type" to "hardBreak"), sb)
        assertEquals("\n", sb.toString())
        sb.clear()
        val node5: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "custom",
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "re")),
            )
        callPrivate(updater, "appendInlineNode", node5, sb)
        assertEquals("re", sb.toString())
    }

    @Test
    fun `appendInline walks content list`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "content" to
                    listOf<Map<String, Any?>>(
                        mapOf<String, Any?>("type" to "text", "text" to "a"),
                        mapOf<String, Any?>("type" to "hardBreak"),
                        mapOf<String, Any?>("type" to "text", "text" to "b"),
                    ),
            )
        callPrivate(updater, "appendInline", node, sb)
        assertEquals("a\nb", sb.toString())
    }

    @Test
    fun `flattenInline trims output`() {
        val updater = updaterWith(mock())
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "  hi  ")),
            )

        @Suppress("UNCHECKED_CAST")
        val s = callPrivate(updater, "flattenInline", node) as String
        assertEquals("hi", s)
    }

    @Test
    fun `appendInlineNode link with missing attrs keeps text unlinked`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "text",
                "text" to "h",
                "marks" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "link")),
            )
        callPrivate(updater, "appendInlineNode", node, sb)
        assertEquals("h", sb.toString())
    }

    @Test
    fun `adfContentAsMapListFromArrayNode skips non-JsonNode entries via mapList path`() {
        val updater = updaterWith(mock())
        val node: JsonNode = mapper.readTree("""[null]""")

        @Suppress("UNCHECKED_CAST")
        val out = callPrivate(updater, "adfContentAsMapList", node) as List<Map<String, Any?>>
        assertTrue(out.isEmpty())
    }

    @Test
    fun `adfContentAsMapListFromArrayNode skips entries when treeToValue fails`() {
        val failingMapper = mock<com.fasterxml.jackson.databind.ObjectMapper>()
        whenever(failingMapper.treeToValue(any<JsonNode>(), any<Class<*>>())).thenAnswer { throw RuntimeException("boom") }
        val updater =
            AdfDescriptionUpdater(
                jiraTicketHttp = mock(),
                objectMapper = failingMapper,
                mdConverter = mock(),
            )
        val node: JsonNode = mapper.readTree("""[{"type":"paragraph","content":[]}]""")

        @Suppress("UNCHECKED_CAST")
        val out = callPrivate(updater, "adfContentAsMapListFromArrayNode", node) as List<Map<String, Any?>>
        assertTrue(out.isEmpty())
    }

    @Test
    fun `append on Cloud with arrayNode existing content not convertible is skipped`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(
                        """{"fields":{"description":{"type":"doc","version":1,"content":[null]}}}""",
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val updater = updaterWith(http)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to listOf(mapOf<String, Any?>("type" to "paragraph", "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "n")))),
                )
            val merged = updater.append(cloudConfig(server.url("/").toString()), "PROJ-NULL", adfDoc)
            assertNotNull(merged)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `adfToMarkdown handles doc without content key`() {
        val updater = updaterWith(mock())
        val doc: Map<String, Any?> = mapOf<String, Any?>("type" to "doc")

        @Suppress("UNCHECKED_CAST")
        val md = callPrivate(updater, "adfToMarkdown", doc) as String
        assertEquals("", md)
    }

    @Test
    fun `adfToMarkdown handles doc with null content`() {
        val updater = updaterWith(mock())
        val doc: Map<String, Any?> = mapOf<String, Any?>("type" to "doc", "content" to null)

        @Suppress("UNCHECKED_CAST")
        val md = callPrivate(updater, "adfToMarkdown", doc) as String
        assertEquals("", md)
    }

    @Test
    fun `adfToMarkdown filters non-Map entries from content`() {
        val updater = updaterWith(mock())
        val doc: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "doc",
                "content" to listOf<Any>("raw-string-not-map", 42),
            )

        @Suppress("UNCHECKED_CAST")
        val md = callPrivate(updater, "adfToMarkdown", doc) as String
        assertEquals("", md)
    }

    @Test
    fun `renderAdfNode heading without attrs defaults level to 2`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "heading",
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "h")),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertTrue(sb.toString().startsWith("## h"))
    }

    @Test
    fun `renderAdfNode heading with attrs but no level defaults to 2`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "heading",
                "attrs" to mapOf<String, Any>("foo" to "bar"),
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "h")),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertTrue(sb.toString().startsWith("## h"))
    }

    @Test
    fun `renderAdfNode heading with non-Number level defaults to 2`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "heading",
                "attrs" to mapOf<String, Any>("level" to "not-a-number"),
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "h")),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertTrue(sb.toString().startsWith("## h"))
    }

    @Test
    fun `renderAdfNode heading with non-Map attrs defaults level to 2`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "heading",
                "attrs" to "raw-string-not-map",
                "content" to listOf<Map<String, Any?>>(mapOf<String, Any?>("type" to "text", "text" to "h")),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertTrue(sb.toString().startsWith("## h"))
    }

    @Test
    fun `renderAdfNode bulletList without content renders dashes`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> = mapOf<String, Any?>("type" to "bulletList")
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("\n", sb.toString())
    }

    @Test
    fun `renderAdfNode bulletList items without inner content yield empty dash lines`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "bulletList",
                "content" to
                    listOf<Map<String, Any?>>(
                        mapOf<String, Any?>("type" to "listItem", "content" to emptyList<Map<String, Any?>>()),
                        mapOf<String, Any?>("type" to "listItem"),
                    ),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertTrue(sb.toString().contains("- "))
    }

    @Test
    fun `renderAdfNode bulletList filters non-Map items`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "bulletList",
                "content" to listOf<Any>("raw-not-map", 42),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("\n", sb.toString())
    }

    @Test
    fun `renderAdfNode table without content returns nothing`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> = mapOf<String, Any?>("type" to "table")
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `renderAdfNode table with empty content array returns nothing`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>("type" to "table", "content" to emptyList<Map<String, Any?>>())
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `renderAdfNode table with non-Map rows yields nothing`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "table",
                "content" to listOf<Any>("raw-not-map", 42),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `renderAdfNode table row without content key yields empty cell text`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>(
                "type" to "table",
                "content" to
                    listOf<Map<String, Any?>>(
                        mapOf<String, Any?>("type" to "tableRow"),
                        mapOf<String, Any?>(
                            "type" to "tableRow",
                            "content" to
                                listOf<Map<String, Any?>>(
                                    mapOf<String, Any?>("type" to "tableCell"),
                                ),
                        ),
                    ),
            )
        callPrivate(updater, "renderAdfNode", node, sb)
        assertTrue(sb.toString().contains("|  |"))
    }

    @Test
    fun `renderAdfNode else branch with no content recurses to nothing`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> = mapOf<String, Any?>("type" to "custom")
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `renderAdfNode else branch filters non-Map content`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> =
            mapOf<String, Any?>("type" to "custom", "content" to listOf<Any>("raw-not-map", 42))
        callPrivate(updater, "renderAdfNode", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `appendInline without content yields nothing`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> = mapOf<String, Any?>()
        callPrivate(updater, "appendInline", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `appendInlineNode text without text key yields empty string`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> = mapOf<String, Any?>("type" to "text")
        callPrivate(updater, "appendInlineNode", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `appendInlineNode text with null text yields empty string`() {
        val updater = updaterWith(mock())
        val sb = StringBuilder()
        val node: Map<String, Any?> = mapOf<String, Any?>("type" to "text", "text" to null)
        callPrivate(updater, "appendInlineNode", node, sb)
        assertEquals("", sb.toString())
    }

    @Test
    fun `append works when existing description object has empty content array`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"fields":{"description":{"type":"doc","version":1,"content":[]}}}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(204)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
            )
            val http =
                JiraTicketHttpClient(
                    httpFactory =
                        com.stacklens.shared.http
                            .ReactiveHttpClientFactory(2000, 2000),
                )
            val updater = updaterWith(http)
            val adfDoc: Map<String, Any?> =
                mapOf(
                    "type" to "doc",
                    "version" to 1,
                    "content" to listOf(mapOf<String, Any?>("type" to "paragraph", "content" to listOf(mapOf<String, Any?>("type" to "text", "text" to "z")))),
                )
            val merged = updater.append(cloudConfig(server.url("/").toString()), "PROJ-X", adfDoc)
            assertTrue(merged.contains("\"content\":[{\"type\":\"paragraph\""))
        } finally {
            server.shutdown()
        }
    }
}
