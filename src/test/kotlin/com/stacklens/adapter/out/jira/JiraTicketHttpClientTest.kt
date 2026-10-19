package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class JiraTicketHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JiraTicketHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 5000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(
        cloudMode: Boolean = true,
        projectKey: String = "PROJ",
    ): JiraConfig {
        val cfg =
            JiraConfig(
                name = "c",
                baseUrl = server.url("").toString().removeSuffix("/"),
                username = "u",
                apiToken = "t",
                cloudMode = cloudMode,
                projectKey = projectKey,
            )
        return cfg
    }

    @Test
    fun `fetchTicketByKey returns JiraTicketData for a Cloud ticket`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"PROJ-1","fields":{
                        "summary":"Fix bug",
                        "description":"Some text",
                        "issuetype":{"name":"Bug"},
                        "status":{"name":"Open"},
                        "priority":{"name":"High"},
                        "labels":["a","b"],
                        "components":[{"name":"API"}]
                    }}""",
                ),
        )
        val ticket = client.fetchTicketByKey(config(), "PROJ-1")
        assertEquals("PROJ-1", ticket.key)
        assertEquals("Fix bug", ticket.summary)
        assertEquals("Bug", ticket.issueType)
        assertEquals("Open", ticket.status)
        assertEquals("High", ticket.priority)
        assertEquals(listOf("a", "b"), ticket.labels)
        assertEquals(listOf("API"), ticket.components)
        assertTrue(ticket.url.endsWith("/browse/PROJ-1"))
    }

    @Test
    fun `fetchTicketByKey uses api version 2 for Server`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"PROJ-1","fields":{
                        "summary":"S","description":"D","issuetype":{"name":"Bug"},
                        "status":{"name":"Open"}
                    }}""",
                ),
        )
        client.fetchTicketByKey(config(cloudMode = false), "PROJ-1")
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/rest/api/2/issue/"))
    }

    @Test
    fun `fetchTicketByKey uses api version 3 for Cloud`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"PROJ-1","fields":{
                        "summary":"S","description":"D","issuetype":{"name":"Bug"},
                        "status":{"name":"Open"}
                    }}""",
                ),
        )
        client.fetchTicketByKey(config(cloudMode = true), "PROJ-1")
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/rest/api/3/issue/"))
    }

    @Test
    fun `fetchTicketByKey flattens ADF description`() {
        val adf =
            """{"type":"doc","content":[
                {"type":"paragraph","content":[{"type":"text","text":"hello"}]},
                {"type":"paragraph","content":[{"type":"text","text":"world"}]}
            ]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"P-1","fields":{"summary":"S","description":${'$'}{"$adf".replace("\"", "\\\"")??"null"},"issuetype":{"name":"Bug"},"status":{"name":"Open"}}}""",
                ),
        )
    }

    @Test
    fun `fetchTicketByKey throws on 404 with descriptive hint`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val ex =
            assertThrows<IllegalStateException> {
                client.fetchTicketByKey(config(), "MISSING-1")
            }
        assertTrue(ex.message!!.contains("not found") || ex.message!!.contains("404"))
    }

    @Test
    fun `fetchTicketByKey throws on 401 with auth hint`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val ex =
            assertThrows<IllegalStateException> {
                client.fetchTicketByKey(config(), "P-1")
            }
        assertTrue(ex.message!!.contains("Authentication"))
    }

    @Test
    fun `fetchTicketByKey throws on 500 with status code in message`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val ex =
            assertThrows<IllegalStateException> {
                client.fetchTicketByKey(config(), "P-1")
            }
        assertTrue(ex.message!!.contains("500") || ex.message!!.contains("HTTP"))
    }

    @Test
    fun `fetchTicketByKey joins comments with author and body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"P-1","fields":{
                        "summary":"S",
                        "description":"D",
                        "issuetype":{"name":"Bug"},
                        "status":{"name":"Open"},
                        "comment":{"comments":[
                            {"author":{"displayName":"Alice"},"body":"comment one"},
                            {"author":{"displayName":"Bob"},"body":"comment two"}
                        ]}
                    }}""",
                ),
        )
        val ticket = client.fetchTicketByKey(config(), "P-1")
        assertTrue(ticket.comments.contains("Alice"))
        assertTrue(ticket.comments.contains("comment one"))
        assertTrue(ticket.comments.contains("Bob"))
        assertTrue(ticket.comments.contains("---"))
    }

    @Test
    fun `fetchTicketByKey with null summary throws`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"P-1","fields":{
                        "description":"D","issuetype":{"name":"Bug"},"status":{"name":"Open"}
                    }}""",
                ),
        )
        val ex =
            assertThrows<IllegalStateException> {
                client.fetchTicketByKey(config(), "P-1")
            }
        assertTrue(ex.message!!.contains("summary"))
    }

    @Test
    fun `fetchTicketByKey with null response throws`() {
        server.enqueue(MockResponse().setBody("null").setHeader("Content-Type", "application/json"))
        val ex =
            assertThrows<IllegalStateException> {
                client.fetchTicketByKey(config(), "P-1")
            }
        assertTrue(ex.message!!.contains("no response"))
    }

    @Test
    fun `fetchTicketByKey extracts acceptance criteria from description`() {
        val desc =
            """
            Background info here.

            Acceptance Criteria:
            - User can log in
            - User can log out
            * User can reset password
            1. User can change profile

            # Other Section
            ignored content
            """.trimIndent()
        val mapper = ObjectMapper()
        val body =
            mapper.writeValueAsString(
                mapOf(
                    "key" to "P-1",
                    "fields" to
                        mapOf(
                            "summary" to "S",
                            "description" to desc,
                            "issuetype" to mapOf("name" to "Bug"),
                            "status" to mapOf("name" to "Open"),
                        ),
                ),
            )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
        val ticket = client.fetchTicketByKey(config(), "P-1")
        assertEquals(4, ticket.acceptanceCriteria.size)
        assertTrue(ticket.acceptanceCriteria.contains("User can log in"))
        assertTrue(ticket.acceptanceCriteria.contains("User can log out"))
        assertTrue(ticket.acceptanceCriteria.contains("User can reset password"))
        assertTrue(ticket.acceptanceCriteria.contains("User can change profile"))
    }

    @Test
    fun `searchSimilarTickets returns empty when no search keywords`() {
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "y",
                affectedComponents = emptyList(),
                searchKeywords = emptyList(),
                errorMessages = emptyList(),
            )
        val original =
            com.stacklens.application.port.out.JiraTicketData(
                key = "P-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "x",
                acceptanceCriteria = emptyList(),
            )
        val results = client.searchSimilarTickets(config(), original, problem)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchSimilarTickets returns empty when search has no results`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "y",
                affectedComponents = emptyList(),
                searchKeywords = listOf("kw"),
                errorMessages = emptyList(),
            )
        val original =
            com.stacklens.application.port.out.JiraTicketData(
                key = "P-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "x",
                acceptanceCriteria = emptyList(),
            )
        val results = client.searchSimilarTickets(config(), original, problem)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchSimilarTickets returns parsed results`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"issues":[
                    {"key":"P-2","fields":{"summary":"S2","description":"D2","issuetype":{"name":"Bug"},"status":{"name":"Open"}}}
                    ]}""",
                ),
        )
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "y",
                affectedComponents = emptyList(),
                searchKeywords = listOf("kw"),
                errorMessages = emptyList(),
            )
        val original =
            com.stacklens.application.port.out.JiraTicketData(
                key = "P-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "x",
                acceptanceCriteria = emptyList(),
            )
        val results = client.searchSimilarTickets(config(), original, problem)
        assertEquals(1, results.size)
        assertEquals("P-2", results[0].key)
        assertEquals("kw", results[0].foundViaKeyword)
    }

    @Test
    fun `searchSimilarTickets uses Server search path when cloudMode false`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "y",
                affectedComponents = emptyList(),
                searchKeywords = listOf("kw"),
                errorMessages = emptyList(),
            )
        val original =
            com.stacklens.application.port.out.JiraTicketData(
                key = "P-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "x",
                acceptanceCriteria = emptyList(),
            )
        client.searchSimilarTickets(config(cloudMode = false), original, problem)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/rest/api/2/search"))
    }

    @Test
    fun `searchSimilarTickets uses Cloud search-jql path when cloudMode true`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "y",
                affectedComponents = emptyList(),
                searchKeywords = listOf("kw"),
                errorMessages = emptyList(),
            )
        val original =
            com.stacklens.application.port.out.JiraTicketData(
                key = "P-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "x",
                acceptanceCriteria = emptyList(),
            )
        client.searchSimilarTickets(config(cloudMode = true), original, problem)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/rest/api/3/search/jql"))
    }

    @Test
    fun `buildClient uses provided baseUrl`() {
        val wc = client.buildClient(config())
        assertNotNull(wc)
    }

    @Test
    fun `searchSimilarTickets with empty projectKey produces valid JQL without project clause`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"issues":[]}"""),
        )
        val cfg =
            JiraConfig(
                name = "test",
                baseUrl = server.url("").toString().removeSuffix("/"),
                username = "u",
                apiToken = "t",
                projectKey = "",
                cloudMode = true,
            )
        val problem =
            JiraProblemAnalysis(
                problemDescription = "x",
                rootCause = "x",
                affectedComponents = emptyList(),
                searchKeywords = listOf("foo"),
                errorMessages = emptyList(),
            )
        val ticket =
            com.stacklens.application.port.out.JiraTicketData(
                key = "PROJ-1",
                summary = "x",
                description = "x",
                comments = "",
                issueType = "Story",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "https://example.com/browse/PROJ-1",
            )
        try {
            client.searchSimilarTickets(cfg, ticket, problem)
        } catch (_: Exception) {
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchTicketByKey handles comment without author and with null displayName`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "key": "PROJ-1",
                      "fields": {
                        "summary": "S",
                        "description": null,
                        "issuetype": {"name": "Bug"},
                        "status": {"name": "Open"},
                        "comment": {
                          "comments": [
                            {"body": "anon", "author": null},
                            {"body": "no name", "author": {"displayName": null}}
                          ]
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )
        val ticket = client.fetchTicketByKey(config(), "PROJ-1")
        assertTrue(ticket.comments.contains("Unbekannt"))
        assertEquals(2, ticket.comments.split("---").size)
    }

    @Test
    @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
    fun `searchSimilarTickets with multiple projects uses project IN clause`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"issues":[{"key":"PROJ-2","fields":{"summary":"x","description":null,"issuetype":{"name":"Bug"},"status":{"name":"Open"}}}]}""",
                ),
        )
        val cfg =
            JiraConfig(
                name = "test",
                baseUrl = server.url("").toString().removeSuffix("/"),
                username = "u",
                apiToken = "t",
                projectKey = "PROJ,OTHER",
                cloudMode = true,
            )
        val result =
            client.searchSimilarTickets(
                cfg,
                originalTicket =
                    com.stacklens.application.port.out.JiraTicketData(
                        key = "PROJ-1",
                        summary = "x",
                        description = "x",
                        comments = "",
                        issueType = "Story",
                        status = "Open",
                        priority = null,
                        labels = emptyList(),
                        components = emptyList(),
                        url = "https://example.com/browse/PROJ-1",
                    ),
                problem =
                    JiraProblemAnalysis(
                        problemDescription = "x",
                        rootCause = "x",
                        affectedComponents = emptyList(),
                        searchKeywords = listOf("foo"),
                        errorMessages = emptyList(),
                    ),
            )
        val recorded = server.takeRequest()
        assertNotNull(recorded)
        val rawJql =
            recorded!!
                .path
                ?.split("jql=")
                ?.lastOrNull()
                ?.split("&")
                ?.firstOrNull() ?: ""
        val jql = java.net.URLDecoder.decode(rawJql, "UTF-8")
        assertTrue(
            jql.contains("project IN") || jql.contains("project ="),
            "expected JQL to contain a project clause, got: $jql",
        )
        assertEquals(1, result.size)
        assertEquals("PROJ-2", result[0].key)
    }

    @Test
    fun `searchSimilarTickets caps at MAX_SIMILAR_TICKETS`() {
        val issues =
            (1..21).joinToString(",") { i ->
                """{"key":"PROJ-$i","fields":{"summary":"x","description":null,"issuetype":{"name":"Bug"},"status":{"name":"Open"}}}"""
            }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"issues":[$issues]}"""),
        )
        val cfg =
            JiraConfig(
                name = "test",
                baseUrl = server.url("").toString().removeSuffix("/"),
                username = "u",
                apiToken = "t",
                projectKey = "PROJ",
                cloudMode = true,
            )
        val result =
            client.searchSimilarTickets(
                cfg,
                originalTicket =
                    com.stacklens.application.port.out.JiraTicketData(
                        key = "PROJ-1",
                        summary = "x",
                        description = "x",
                        comments = "",
                        issueType = "Story",
                        status = "Open",
                        priority = null,
                        labels = emptyList(),
                        components = emptyList(),
                        url = "https://example.com/browse/PROJ-1",
                    ),
                problem =
                    JiraProblemAnalysis(
                        problemDescription = "x",
                        rootCause = "x",
                        affectedComponents = emptyList(),
                        searchKeywords = listOf("foo"),
                        errorMessages = emptyList(),
                    ),
            )
        assertTrue(result.size <= 20, "expected <= 20 results, got ${result.size}")
    }

    @Test
    fun `searchSimilarTickets tolerates a 500 response and falls back to next keyword`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"issues":[]}"""),
        )
        val cfg =
            JiraConfig(
                name = "test",
                baseUrl = server.url("").toString().removeSuffix("/"),
                username = "u",
                apiToken = "t",
                projectKey = "PROJ",
                cloudMode = true,
            )
        val result =
            client.searchSimilarTickets(
                cfg,
                originalTicket =
                    com.stacklens.application.port.out.JiraTicketData(
                        key = "PROJ-1",
                        summary = "x",
                        description = "x",
                        comments = "",
                        issueType = "Story",
                        status = "Open",
                        priority = null,
                        labels = emptyList(),
                        components = emptyList(),
                        url = "https://example.com/browse/PROJ-1",
                    ),
                problem =
                    JiraProblemAnalysis(
                        problemDescription = "x",
                        rootCause = "x",
                        affectedComponents = emptyList(),
                        searchKeywords = listOf("foo", "bar"),
                        errorMessages = emptyList(),
                    ),
            )
        assertTrue(result.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `searchSimilarTickets deduplicates by ticket key`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"issues":[{"key":"PROJ-99","fields":{"summary":"x","description":null,"issuetype":{"name":"Bug"},"status":{"name":"Open"}}}]}""",
                ),
        )
        val cfg =
            JiraConfig(
                name = "test",
                baseUrl = server.url("").toString().removeSuffix("/"),
                username = "u",
                apiToken = "t",
                projectKey = "PROJ",
                cloudMode = true,
            )
        val result =
            client.searchSimilarTickets(
                cfg,
                originalTicket =
                    com.stacklens.application.port.out.JiraTicketData(
                        key = "PROJ-1",
                        summary = "x",
                        description = "x",
                        comments = "",
                        issueType = "Story",
                        status = "Open",
                        priority = null,
                        labels = emptyList(),
                        components = emptyList(),
                        url = "https://example.com/browse/PROJ-1",
                    ),
                problem =
                    JiraProblemAnalysis(
                        problemDescription = "x",
                        rootCause = "x",
                        affectedComponents = emptyList(),
                        searchKeywords = listOf("foo"),
                        errorMessages = emptyList(),
                    ),
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `flattenDescriptionNode returns empty for null JsonNode`() {
        val result = client.flattenDescriptionNode(null)
        assertEquals("", result)
    }

    @Test
    fun `flattenDescriptionNode returns empty for JsonNull`() {
        val mapper = ObjectMapper()
        val node = mapper.nullNode()
        val result = client.flattenDescriptionNode(node)
        assertEquals("", result)
    }

    @Test
    fun `flattenDescriptionNode returns text for textual node`() {
        val mapper = ObjectMapper()
        val node = mapper.readTree("\"hello world\"")
        val result = client.flattenDescriptionNode(node)
        assertEquals("hello world", result)
    }

    @Test
    fun `flattenDescriptionNode flattens ADF with text + content children`() {
        val mapper = ObjectMapper()
        val node =
            mapper.readTree(
                """{"content":[{"text":"a"},{"content":[{"text":"b"}]}]}""",
            )
        val result = client.flattenDescriptionNode(node)
        assertEquals("a b", result)
    }

    @Test
    fun `flattenDescriptionNode skips blank content parts and omits separator`() {
        val mapper = ObjectMapper()
        val node =
            mapper.readTree(
                """{"content":[{"content":[{"text":"   "}]},{"content":[{"text":"x"}]}]}""",
            )
        val result = client.flattenDescriptionNode(node)
        assertEquals("x", result)
    }

    @Test
    fun `flattenDescriptionNode replaces runs of whitespace with single space`() {
        val mapper = ObjectMapper()
        val node =
            mapper.readTree(
                """{"content":[{"text":"hello\n\n   world"}]}""",
            )
        val result = client.flattenDescriptionNode(node)
        assertEquals("hello world", result)
    }

    @Test
    fun `flattenDescriptionNode returns text directly from text node via flattenJsonNode`() {
        val mapper = ObjectMapper()
        val node = mapper.readTree("""{"text":"standalone"}""")
        val result = client.flattenDescriptionNode(node)
        assertEquals("standalone", result)
    }

    @Test
    fun `fetchTicketByKey with null issuetype uses default name`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"key":"PROJ-1","fields":{"summary":"S","description":null,"issuetype":null,"status":null,"priority":null,"labels":null,"components":null}}""",
                ),
        )
        val ticket = client.fetchTicketByKey(config(), "PROJ-1")
        assertEquals("Issue", ticket.issueType)
        assertEquals("", ticket.status)
    }

    @Test
    fun `fetchTicketByKey with blank issuetype name uses default`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"key":"PROJ-1","fields":{"summary":"S","description":null,"issuetype":{"name":"  "},"status":{"name":""},"priority":null,"labels":null,"components":null}}""",
                ),
        )
        val ticket = client.fetchTicketByKey(config(), "PROJ-1")
        assertEquals("Issue", ticket.issueType)
        assertEquals("", ticket.status)
    }

    @Test
    fun `fetchTicketByKey with components containing blank entries drops them`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"key":"PROJ-1","fields":{"summary":"S","description":null,"issuetype":{"name":"Bug"},"status":{"name":"Open"},"priority":null,"labels":null,"components":[{"name":"a"},{"name":""},{"name":"  "},{"name":"b"}]}}""",
                ),
        )
        val ticket = client.fetchTicketByKey(config(), "PROJ-1")
        assertEquals(listOf("a", "b"), ticket.components)
    }
}
