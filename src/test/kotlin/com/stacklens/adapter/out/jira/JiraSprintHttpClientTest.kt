package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.shared.JiraAssigneeField
import com.stacklens.adapter.out.shared.JiraChangelog
import com.stacklens.adapter.out.shared.JiraChangelogHistory
import com.stacklens.adapter.out.shared.JiraChangelogItem
import com.stacklens.adapter.out.shared.JiraIssueLink
import com.stacklens.adapter.out.shared.JiraIssueLinkType
import com.stacklens.adapter.out.shared.JiraLinkedIssueFields
import com.stacklens.adapter.out.shared.JiraLinkedIssueRef
import com.stacklens.adapter.out.shared.JiraNamedField
import com.stacklens.adapter.out.shared.JiraSprintIssueFieldResolver
import com.stacklens.adapter.out.shared.JiraSprintIssueItem
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.concurrent.TimeUnit

class JiraSprintHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JiraSprintHttpClient
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val resolver = JiraSprintIssueFieldResolver()
    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            JiraSprintHttpClient(
                fieldResolver = resolver,
                connectTimeoutMs = 5_000,
                readTimeoutMs = 5_000,
            )
    }

    @Test
    fun `constructor with default timeouts is exercised`() {
        val c = JiraSprintHttpClient(fieldResolver = resolver)
        org.junit.jupiter.api.Assertions
            .assertNotNull(c)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jiraConfig(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "Test",
            baseUrl = baseUrl,
            username = "user",
            apiToken = "token",
        )

    @Test
    fun `fetchSprintInfo parses URL with sprint id segment`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """{"id":42,"name":"Sprint 42","state":"active","startDate":"2024-01-01","endDate":"2024-01-14","goal":"Ship it"}""",
                ),
        )
        val info = client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/sprints/42")
        assertEquals(42L, info.id)
        assertEquals("Sprint 42", info.name)
        assertEquals("active", info.state)
        assertEquals("Ship it", info.goal)
    }

    @Test
    fun `fetchSprintInfo falls back to defaults on empty body`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(""),
        )
        val info = client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/sprints/99")
        assertEquals(99L, info.id)
        assertEquals("Sprint 99", info.name)
        assertEquals("unknown", info.state)
        assertEquals(null, info.goal)
    }

    @Test
    fun `fetchSprintInfo parses URL with sprint query param`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"id":7,"name":"Sprint 7","state":"future","goal":""}"""),
        )
        val info = client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/rapid?selectedSprint=7")
        assertEquals(7L, info.id)
        assertNull(info.goal)
    }

    @Test
    fun `fetchSprintInfo resolves active sprint for board`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"values":[{"id":11,"name":"Active","state":"active"}]}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"id":11,"name":"Active","state":"active"}"""),
        )
        val info = client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/boards/3")
        assertEquals(11L, info.id)
    }

    @Test
    fun `fetchSprintInfo throws when board has no active sprint`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"values":[]}"""),
        )
        try {
            client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/boards/3")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("No active sprint"))
        }
    }

    @Test
    fun `fetchSprintInfo throws when board endpoint returns 4xx`() {
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/boards/3")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test
    fun `fetchSprintInfo covers fetchSprintById onErrorResume via custom erroring WebClient`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("upstream error"))
        val info = client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/sprints/77")
        assertEquals(77L, info.id)
        assertEquals("Sprint 77", info.name)
        assertEquals("unknown", info.state)
    }

    @Test
    fun `fetchSprintInfo throws when URL has no board or sprint`() {
        try {
            client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/something-else")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Could not determine"))
        }
    }

    @Test
    fun `fetchSprintIssues throws on HTTP 4xx response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"),
        )
        val cfg = jiraConfig()
        val ex =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintIssues(cfg, "/rest/agile/1.0/sprint/42")
            }
        assertTrue(ex.message!!.contains("HTTP 404"))
    }

    @Test
    fun `fetchSprintIssues throws on HTTP 5xx response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Server Error"),
        )
        val cfg = jiraConfig()
        assertThrows(IllegalStateException::class.java) {
            client.fetchSprintIssues(cfg, "/rest/agile/1.0/sprint/42")
        }
    }

    @Test
    fun `fetchSprintIssues fetches multiple pages when total exceeds maxResults`() {
        val page1Issues =
            (1..100).joinToString(",") { i ->
                """{"key":"PROJ-$i","fields":{"summary":"Issue $i","description":"d","issuetype":{"name":"Story"},"status":{"name":"Open"},"priority":{"name":"Medium"},"assignee":{"displayName":"A"},"labels":[],"issuelinks":[]}}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"total": 150, "issues": [$page1Issues]}"""),
        )
        val page2Issues =
            (101..150).joinToString(",") { i ->
                """{"key":"PROJ-$i","fields":{"summary":"Issue $i","description":"d","issuetype":{"name":"Story"},"status":{"name":"Open"},"priority":{"name":"Medium"},"assignee":{"displayName":"A"},"labels":[],"issuelinks":[]}}"""
            }
        server.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"total": 150, "issues": [$page2Issues]}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"total": 150, "issues": []}"""),
        )
        val cfg = jiraConfig()
        val issues = client.fetchSprintIssues(cfg, "/rest/agile/1.0/sprint/42")
        assertEquals(150, issues.size)
        assertEquals("PROJ-150", issues.last().key)
    }

    @Test
    fun `fetchSprintIssues parses one page with linked issues`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-1",
                          "fields": {
                            "summary": "Issue 1",
                            "description": "desc",
                            "issuetype": {"name": "Story"},
                            "status": {"name": "Done"},
                            "priority": {"name": "High"},
                            "assignee": {"displayName": "Alice"},
                            "labels": ["l1"],
                            "issuelinks": [
                              {"type":{"inward":"is blocked by","outward":"blocks"},"inwardIssue":{"key":"PROJ-0","fields":{"summary":"old","status":{"name":"Done"}}}}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        val first = issues[0]
        assertEquals("PROJ-1", first.key)
        assertEquals("Issue 1", first.summary)
        assertEquals("desc", first.description)
        assertEquals("Story", first.issueType)
        assertEquals("Done", first.status)
        assertEquals("High", first.priority)
        assertEquals("Alice", first.assignee)
        assertEquals(listOf("l1"), first.labels)
        assertEquals(1, first.linkedIssues.size)
        assertEquals("PROJ-0", first.linkedIssues[0].key)
        assertEquals("is blocked by", first.linkedIssues[0].relationship)
    }

    @Test
    fun `fetchSprintIssues covers pagination over multiple pages`() {
        val first =
            (1..100).joinToString(",") {
                """{"key":"PROJ-$it","fields":{"summary":"S$it","status":{"name":"Open"}}}"""
            }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"total":150,"issues":[$first]}"""),
        )
        val second =
            (101..150).joinToString(",") {
                """{"key":"PROJ-$it","fields":{"summary":"S$it","status":{"name":"Open"}}}"""
            }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"total":150,"issues":[$second]}"""),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(150, issues.size)
    }

    @Test
    fun `fetchSprintIssues throws on 4xx`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("server error"),
        )
        try {
            client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `fetchSprintIssues breaks on empty response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("null"),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(0, issues.size)
    }

    @Test
    fun `fetchSprintData returns both info and issues`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"id":1,"name":"S","state":"active"}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"total":0,"issues":[]}"""),
        )
        val (info, issues) = client.fetchSprintData(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1L, info.id)
        assertEquals(0, issues.size)
    }

    @Test
    fun `fetchSprintIssues handles missing linked issues gracefully`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-2",
                          "fields": {
                            "summary": "no links",
                            "issuelinks": null
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals(0, issues[0].linkedIssues.size)
    }

    @Test
    fun `fetchSprintIssues handles outward link with missing key`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-3",
                          "fields": {
                            "summary": "outward only",
                            "issuelinks": [
                              {"type":{"outward":"blocks"},"outwardIssue":{"fields":{"summary":"x"}}}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals(0, issues[0].linkedIssues.size)
    }

    @Test
    fun `fetchSprintIssues falls back to 'relates to' when type is missing on inward link`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-4",
                          "fields": {
                            "summary": "no type",
                            "issuelinks": [
                              {"inwardIssue":{"key":"PROJ-9","fields":{"summary":"x","status":{"name":"Open"}}}}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals(1, issues[0].linkedIssues.size)
        assertEquals("relates to", issues[0].linkedIssues[0].relationship)
    }

    @Test
    fun `fetchSprintIssues falls back to 'relates to' when type is missing on outward link`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-5",
                          "fields": {
                            "summary": "no type outward",
                            "issuelinks": [
                              {"outwardIssue":{"key":"PROJ-9","fields":{"summary":"x","status":{"name":"Open"}}}}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals(1, issues[0].linkedIssues.size)
        assertEquals("relates to", issues[0].linkedIssues[0].relationship)
    }

    @Test
    fun `fetchSprintIssues returns empty linkedIssues when both inward and outward are null`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-6",
                          "fields": {
                            "summary": "no ref",
                            "issuelinks": [
                              {"type":{"inward":"blocks","outward":"is blocked by"}}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals(0, issues[0].linkedIssues.size)
    }

    @Test
    fun `fetchSprintIssues returns empty linkedIssues when issuelinks field is missing`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-7",
                          "fields": {
                            "summary": "no issuelinks field"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals(0, issues[0].linkedIssues.size)
    }

    @Test
    fun `fetchSprintIssues flattens textual description nodes`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-4",
                          "fields": {
                            "summary": "rich desc",
                            "description": "plain text"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals("plain text", issues[0].description)
    }

    @Test
    fun `fetchSprintIssues sets URL from base + key`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"total":1,"issues":[{"key":"K-9","fields":{"summary":"s"}}]}"""),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertTrue(issues[0].url.endsWith("/browse/K-9"))
    }

    @Test
    fun `buildClient sends Authorization header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"id":1,"name":"S","state":"active"}"""),
        )
        client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/sprints/1")
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        val auth = request!!.getHeader(HttpHeaders.AUTHORIZATION)
        assertNotNull(auth)
    }

    @Test
    fun `fetchSprintInfo throws on connection refused via board lookup`() {
        val cfg =
            JiraConfig(
                name = "conn-refused",
                baseUrl = "http://127.0.0.1:1",
                username = "u",
                apiToken = "TOK",
                cloudMode = false,
                projectKey = "P",
                active = true,
            )
        val ex =
            assertThrows(Exception::class.java) {
                client.fetchSprintInfo(cfg, "https://jira.example.com/boards/3")
            }
        val msg = ex.message ?: ""
        assertTrue(
            msg.contains("Connection refused") ||
                msg.contains("ConnectException") ||
                msg.isNotBlank(),
            "Expected connection-refused related message, got: $msg",
        )
    }

    @Test
    fun `fetchSprintIssues throws JIRA_CONN_REFUSED on connection refused`() {
        val cfg =
            JiraConfig(
                name = "conn-refused",
                baseUrl = "http://127.0.0.1:1",
                username = "u",
                apiToken = "TOK",
                cloudMode = false,
                projectKey = "P",
                active = true,
            )
        val ex =
            assertThrows(Exception::class.java) {
                client.fetchSprintIssues(cfg, "https://jira.example.com/sprints/1")
            }
        val msg = ex.message ?: ""
        assertTrue(
            msg.startsWith("[JIRA_CONN_REFUSED:"),
            "Expected conn-refused marker, got: $msg",
        )
    }

    @Test
    fun `fetchSprintIssues rethrows non-conn-refused exceptions`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("not valid json at all {"),
        )
        val ex =
            assertThrows(Exception::class.java) {
                client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
            }
        val msg = ex.message ?: ""
        assertFalse(msg.startsWith("[JIRA_CONN_REFUSED:"))
    }

    @Test
    fun `fetchSprintInfo parses URL with non-numeric sprint id falls back to board`() {
        val ex =
            assertThrows(Exception::class.java) {
                client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/sprints/abc")
            }
        assertTrue(
            ex.message!!.contains("Could not determine") ||
                ex.message!!.contains("abc"),
            "Expected error mentioning abc, got: ${ex.message}",
        )
    }

    @Test
    fun `fetchSprintInfo parses URL with non-numeric sprint query param falls back to board`() {
        val ex =
            assertThrows(Exception::class.java) {
                client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/?sprint=abc")
            }
        assertTrue(
            ex.message!!.contains("Could not determine") ||
                ex.message!!.contains("abc"),
            "Expected error mentioning abc, got: ${ex.message}",
        )
    }

    @Test
    fun `fetchSprintInfo parses URL with non-numeric board id falls back to error`() {
        val ex =
            assertThrows(Exception::class.java) {
                client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/boards/abc")
            }
        assertTrue(
            ex.message!!.contains("Could not determine") ||
                ex.message!!.contains("abc"),
            "Expected error mentioning abc, got: ${ex.message}",
        )
    }

    @Test
    fun `fetchSprintInfo throws when board endpoint returns empty values array`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"values":[]}"""),
        )
        val ex =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintInfo(jiraConfig(), "https://jira.example.com/boards/3")
            }
        assertTrue(ex.message!!.contains("No active sprint"))
    }

    @Test
    fun `fetchSprintIssues handles missing comment container`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-7",
                          "fields": {"summary": "no comments field"}
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertEquals("", issues[0].comments)
    }

    @Test
    fun `fetchSprintIssues handles comment where author has no displayName`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-6",
                          "fields": {
                            "summary": "s",
                            "comment": {
                              "comments": [
                                {"body": "anon comment", "author": {"displayName": null}}
                              ]
                            }
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val cfg = JiraConfig(name = "x", baseUrl = server.url("/").toString(), apiToken = "")
        val result = client.fetchSprintIssues(cfg, sprintUrl = "/rest/agile/1.0/sprint/6")
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchSprintIssues handles comment without author`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-5",
                          "fields": {
                            "summary": "s",
                            "comment": {
                              "comments": [
                                {"body": "anon comment", "author": null}
                              ]
                            }
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertTrue(issues[0].comments.contains("Unbekannt"), "Expected Unbekannt for anonymous comment, got: ${issues[0].comments}")
    }

    @Test
    fun `fetchSprintIssues flattens nested ADF description`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-6",
                          "fields": {
                            "summary": "nested",
                            "description": {
                              "type": "doc",
                              "content": [
                                {"type": "paragraph", "content": [{"type": "text", "text": "first "}, {"type": "text", "text": "second"}]},
                                {"type": "paragraph", "text": "third"}
                              ]
                            }
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertTrue(issues[0].description.contains("first"))
        assertTrue(issues[0].description.contains("second"))
        assertTrue(issues[0].description.contains("third"))
    }

    @Test
    fun `fetchSprintIssues flattens ADF description object without content key`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-8",
                          "fields": {
                            "summary": "no content key",
                            "description": {
                              "type": "doc",
                              "fallbackText": "from properties"
                            }
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertNotNull(issues[0].description)
    }

    @Test
    fun `fetchSprintIssues flattens ADF description with has-text node`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "PROJ-9",
                          "fields": {
                            "summary": "has text key",
                            "description": {
                              "type": "doc",
                              "text": "direct text key"
                            }
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val issues = client.fetchSprintIssues(jiraConfig(), "https://jira.example.com/sprints/1")
        assertEquals(1, issues.size)
        assertTrue(issues[0].description.contains("direct text key"))
    }

    @Test
    fun `internal data class default fields and equals are covered`() {
        val a = JiraSprintIssueItem()
        val b = JiraSprintIssueItem(key = "K")
        val c = JiraSprintIssueItem(key = "K")
        assertEquals("K", b.key)
        assertEquals(b, c)
        assertNotNull(a.toString())
        val changelog = JiraChangelog()
        assertNotNull(changelog.toString())
        val history = JiraChangelogHistory()
        assertNotNull(history.toString())
        val item = JiraChangelogItem()
        assertNotNull(item.toString())
        val type = JiraIssueLinkType()
        assertNotNull(type.toString())
        val fields = JiraLinkedIssueFields()
        assertNotNull(fields.toString())
        val ref = JiraLinkedIssueRef()
        assertNotNull(ref.toString())
        val link = JiraIssueLink()
        assertNotNull(link.toString())
        val named = JiraNamedField()
        assertNotNull(named.toString())
        val assignee = JiraAssigneeField()
        assertNotNull(assignee.toString())
    }

    @Test
    fun `JiraSprintResponse primary constructor is covered`() {
        val response = JiraSprintResponse(id = 7L, name = "Sprint 7", state = "active")
        assertEquals(7L, response.id)
        assertEquals("Sprint 7", response.name)
    }

    @Test
    fun `JiraSprintListResponse primary constructor is covered`() {
        val sprint = JiraSprintResponse(id = 1L, name = "S1")
        val response = JiraSprintListResponse(values = listOf(sprint))
        assertEquals(1, response.values.size)
        assertEquals(1L, response.values[0].id)
    }

    @Test
    fun `JiraSprintIssuesResponse primary constructor is covered`() {
        val response = JiraSprintIssuesResponse(total = 3, issues = emptyList())
        assertEquals(3, response.total)
        assertTrue(response.issues.isEmpty())
    }

    @Test
    fun `JiraSprintResponse per-field equals branches`() {
        val a = JiraSprintResponse(id = 1L, name = "S", state = "active", startDate = "2024-01-01", endDate = "2024-01-14", goal = "G")
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(id = 99L))
        assertNotEquals(a, a.copy(name = "X"))
        assertNotEquals(a, a.copy(state = "X"))
        assertNotEquals(a, a.copy(startDate = null))
        assertNotEquals(a, a.copy(endDate = null))
        assertNotEquals(a, a.copy(goal = null))
        val withNulls = JiraSprintResponse()
        assertEquals(withNulls, withNulls.copy())
        assertNotEquals(withNulls, withNulls.copy(startDate = "X"))
    }

    @Test
    fun `JiraSprintListResponse per-field equals branches`() {
        val a = JiraSprintListResponse(values = listOf(JiraSprintResponse()))
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(values = emptyList()))
    }

    @Test
    fun `JiraSprintIssuesResponse per-field equals branches`() {
        val a = JiraSprintIssuesResponse(total = 5, issues = emptyList())
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(total = 99))
        assertNotEquals(a, a.copy(issues = listOf(JiraSprintIssueItem())))
    }

    @Test
    fun `JiraChangelogItem per-field equals branches`() {
        val a = JiraChangelogItem(field = "status", fromString = "Open", toString = "Done")
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(field = null))
        assertNotEquals(a, a.copy(fromString = null))
        assertNotEquals(a, a.copy(toString = null))
        val allNull = JiraChangelogItem()
        assertEquals(allNull, allNull.copy())
        assertNotEquals(allNull, allNull.copy(field = "X"))
    }

    @Test
    fun `JiraIssueLinkType per-field equals branches`() {
        val a = JiraIssueLinkType(name = "Blocks", inward = "is blocked by", outward = "blocks")
        assertNotEquals(a, null)
        assertFalse(a.equals("wrong type"))
        assertNotEquals(a, a.copy(name = null))
        assertNotEquals(a, a.copy(inward = null))
        assertNotEquals(a, a.copy(outward = null))
        val allNull = JiraIssueLinkType()
        assertEquals(allNull, allNull.copy())
        assertNotEquals(allNull, allNull.copy(name = "X"))
    }

    @Test
    fun `JiraSprintResponse destructuring covers componentN`() {
        val r = JiraSprintResponse(id = 1L, name = "S", state = "a", startDate = "sd", endDate = "ed", goal = "g")
        val (id, name, state, start, end, goal) = r
        assertEquals(1L, id)
        assertEquals("S", name)
        assertEquals("a", state)
        assertEquals("sd", start)
        assertEquals("ed", end)
        assertEquals("g", goal)
    }

    @Test
    fun `JiraSprintListResponse destructuring covers componentN`() {
        val r = JiraSprintListResponse()
        val (values) = r
        assertTrue(values.isEmpty())
    }

    @Test
    fun `JiraSprintIssuesResponse destructuring covers componentN`() {
        val r = JiraSprintIssuesResponse(total = 3, issues = emptyList())
        val (total, issues) = r
        assertEquals(3, total)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `JiraChangelogItem destructuring covers componentN`() {
        val c = JiraChangelogItem(field = "f", fromString = "a", toString = "b")
        val (field, from, to) = c
        assertEquals("f", field)
        assertEquals("a", from)
        assertEquals("b", to)
    }

    @Test
    fun `JiraIssueLinkType destructuring covers componentN`() {
        val l = JiraIssueLinkType(name = "n", inward = "i", outward = "o")
        val (name, inward, outward) = l
        assertEquals("n", name)
        assertEquals("i", inward)
        assertEquals("o", outward)
    }

    @Test
    fun `JiraSprintIssuesResponse explicit constructor covers all params`() {
        val r = JiraSprintIssuesResponse(total = 5, issues = emptyList())
        assertEquals(5, r.total)
        assertEquals(emptyList<JiraSprintIssueItem>(), r.issues)
        val d = JiraSprintIssuesResponse()
        assertEquals(0, d.total)
        assertEquals(emptyList<JiraSprintIssueItem>(), d.issues)
    }

    @Test
    fun `JiraSprintResponse explicit constructor covers all params`() {
        val r = JiraSprintResponse(id = 1L, name = "s", state = "active", startDate = "2024-01-01", endDate = "2024-01-14", goal = "g")
        assertEquals(1L, r.id)
        assertEquals("s", r.name)
        assertEquals("active", r.state)
        assertEquals("2024-01-01", r.startDate)
        assertEquals("2024-01-14", r.endDate)
        assertEquals("g", r.goal)
        val d = JiraSprintResponse()
        assertEquals(0L, d.id)
        assertEquals("", d.name)
    }

    @Test
    fun `JiraSprintListResponse explicit constructor covers all params`() {
        val r = JiraSprintListResponse(values = listOf(JiraSprintResponse()))
        assertEquals(1, r.values.size)
        val d = JiraSprintListResponse()
        assertEquals(emptyList<JiraSprintResponse>(), d.values)
    }

    @Test
    fun `JiraSprintIssuesResponse getters cover property accessors`() {
        val r = JiraSprintIssuesResponse(total = 5, issues = emptyList())
        assertEquals(5, r.total)
        assertEquals(emptyList<Any>(), r.issues)
    }

    @Test
    fun `JiraChangelogItem getters cover property accessors`() {
        val c = JiraChangelogItem(field = "f", fromString = "a", toString = "b")
        assertEquals("f", c.field)
        assertEquals("a", c.fromString)
        assertEquals("b", c.toString)
    }

    @Test
    fun `JiraIssueLinkType getters cover property accessors`() {
        val l = JiraIssueLinkType(name = "n", inward = "i", outward = "o")
        assertEquals("n", l.name)
        assertEquals("i", l.inward)
        assertEquals("o", l.outward)
    }
}
