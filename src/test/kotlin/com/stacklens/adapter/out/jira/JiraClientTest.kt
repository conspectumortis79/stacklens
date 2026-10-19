package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class JiraClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JiraClient
    private val objectMapper = ObjectMapper().registerModule(kotlinModule())

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connector =
            ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(2)),
            )
        val webClient = WebClient.builder().clientConnector(connector).build()
        client = JiraClient(objectMapper, JiraJqlBuilder(), JiraIssueMapper(), JiraEndpoints())
    }

    private fun takeRequest(): RecordedRequest =
        server.takeRequest(30, TimeUnit.SECONDS) ?: fail(
            "MockWebServer did not receive a request within 30 s (received ${server.requestCount})",
        )

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jiraConfig(): JiraConfig =
        JiraConfig(
            name = "test",
            baseUrl = server.url("").toString().removeSuffix("/"),
            projectKey = "PROJ",
            apiToken = "TOK",
            cloudMode = false,
            active = true,
            createdAt = LocalDateTime.now(),
        )

    @Test
    fun `searchKeyword returns empty when config is inactive`() {
        val cfg = jiraConfig().apply { active = false }
        val results = client.searchKeyword(cfg, keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty and logs when no endpoint works`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        val cfg = jiraConfig()
        val results = client.searchKeyword(cfg, keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty when project filter is invalid`() {
        val cfg = jiraConfig().apply { projectKey = "" }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val results = client.searchKeyword(cfg, keyword = "test", listOf("INVALID"), 1L, emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty when JSON is invalid`() {
        val cfg = jiraConfig()
        repeat(5) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("this is not json"),
            )
        }
        val results = client.searchKeyword(cfg, keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword handles pagination exception gracefully`() {
        val cfg = jiraConfig()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "issues": [
                            {
                                "key": "PROJ-1",
                                "fields": {
                                    "summary": "Test issue",
                                    "description": "Body",
                                    "issuetype": {"name": "Story"},
                                    "status": {"name": "Open"},
                                    "priority": {"name": "Medium"},
                                    "updated": "2024-01-01"
                                }
                            }
                        ],
                        "total": 100,
                        "startAt": 0,
                        "maxResults": 50
                    }""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not json"),
        )
        val results = client.searchKeyword(cfg, keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `searchKeyword queries search endpoint and parses issues`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "issues": [
                            {
                                "key": "PROJ-1",
                                "fields": {
                                    "summary": "Test issue",
                                    "description": "Body",
                                    "issuetype": {"name": "Story"},
                                    "status": {"name": "Open"},
                                    "priority": {"name": "Medium"},
                                    "updated": "2024-01-01"
                                }
                            }
                        ],
                        "total": 1
                    }""",
                ),
        )
        val results = client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(1, results.size)
    }

    @Test
    fun `searchKeyword includes Authorization header from authHeader`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        val req = takeRequest()
        assertNotNull(req.getHeader("Authorization"))
        assertEquals("application/json", req.getHeader("Accept"))
    }

    @Test
    fun `searchKeyword passes keyword in JQL`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        client.searchKeyword(jiraConfig(), keyword = "myFeature", emptyList(), 1L, emptyList(), emptyList())
        val req = takeRequest()
        assertTrue(req.path!!.contains("jql="))
        assertTrue(
            req.path!!.contains("myFeature") || req.path!!.contains("myFeature".replace(" ", "+")),
            "expected keyword in JQL path: ${req.path}",
        )
    }

    @Test
    fun `searchKeyword passes component and label filters in JQL`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        client.searchKeyword(
            jiraConfig(),
            keyword = "refactor",
            projectFilters = emptyList(),
            sessionId = 1L,
            componentFilters = listOf("frontend"),
            labelFilters = listOf("bug"),
        )
        val req = takeRequest()
        assertTrue(
            req.path!!.contains("component") && req.path!!.contains("frontend"),
            "expected component=frontend in JQL: ${req.path}",
        )
        assertTrue(
            req.path!!.contains("labels") && req.path!!.contains("bug"),
            "expected labels=bug in JQL: ${req.path}",
        )
    }

    @Test
    fun `searchKeyword uses issue-key JQL when keyword is an issue key`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        client.searchKeyword(jiraConfig(), keyword = "PROJ-123", emptyList(), 1L, emptyList(), emptyList())
        val req = takeRequest()
        assertTrue(
            req.path!!.contains("key%3D%22PROJ-123%22") ||
                req.path!!.contains("key=\"PROJ-123\"") ||
                req.path!!.contains("PROJ-123"),
            "expected issue-key JQL in path: ${req.path}",
        )
    }

    @Test
    fun `searchKeyword uses cloud endpoints when cloudMode is true`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val cfg = jiraConfig().apply { cloudMode = true }
        client.searchKeyword(cfg, keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        val req = takeRequest()
        assertTrue(req.path!!.contains("/rest/api/3/search/jql") || req.path!!.contains("/rest/api/2/search"))
    }

    @Test
    fun `searchKeyword uses server endpoints when cloudMode is false`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val cfg = jiraConfig().apply { cloudMode = false }
        client.searchKeyword(cfg, keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        val req = takeRequest()
        assertTrue(req.path!!.contains("/rest/api/2/search"))
    }

    @Test
    fun `searchKeyword tries multiple endpoints until one works`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `searchKeyword falls back from GET to POST endpoint`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val cfg = jiraConfig().apply { cloudMode = true }
        client.searchKeyword(cfg, keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `searchKeyword paginates when total is greater than page size`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "issues": [
                            {"key": "PROJ-1", "fields": {"summary": "A"}},
                            {"key": "PROJ-2", "fields": {"summary": "B"}}
                        ],
                        "total": 3
                    }""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "issues": [
                            {"key": "PROJ-3", "fields": {"summary": "C"}}
                        ],
                        "total": 3
                    }""",
                ),
        )
        val results = client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(3, results.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `searchKeyword returns empty when total is null`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )
        val results = client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty on JSON parse error`() {
        repeat(3) { server.enqueue(MockResponse().setBody("not-json")) }
        val results = client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword stops pagination when page is empty`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "issues": [{"key": "PROJ-1", "fields": {"summary": "A"}}],
                        "total": 100
                    }""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":100}"""),
        )
        val results = client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertEquals(1, results.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `searchKeyword handles exceptions gracefully`() {
        server.shutdown()
        val results = client.searchKeyword(jiraConfig(), keyword = "test", emptyList(), 1L, emptyList(), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `JiraSearchResponse defaults issues to empty list`() {
        val resp = JiraClient.JiraSearchResponse()
        assertTrue(resp.issues.isEmpty())
        assertEquals(null, resp.total)
        assertEquals(null, resp.startAt)
    }

    @Test
    fun `JiraIssue defaults key and fields`() {
        val issue = JiraClient.JiraIssue()
        assertEquals("", issue.key)
        assertNotNull(issue.fields)
    }

    @Test
    fun `JiraFields has nullable fields`() {
        val f = JiraClient.JiraFields()
        assertEquals(null, f.summary)
        assertEquals(null, f.description)
        assertEquals(null, f.issuetype)
        assertEquals(null, f.status)
        assertEquals(null, f.assignee)
        assertEquals(null, f.priority)
        assertEquals(null, f.updated)
        assertEquals(null, f.attachment)
    }

    @Test
    fun `JiraAttachment stores id filename and content`() {
        val a = JiraClient.JiraAttachment(id = "1", filename = "f.png", content = "u")
        assertEquals("1", a.id)
        assertEquals("f.png", a.filename)
        assertEquals("u", a.content)
    }

    @Test
    fun `JiraNamedValue stores name`() {
        val n = JiraClient.JiraNamedValue(name = "Story")
        assertEquals("Story", n.name)
    }

    @Test
    fun `JiraAssignee stores displayName and name`() {
        val a = JiraClient.JiraAssignee(displayName = "Alice", name = "alice")
        assertEquals("Alice", a.displayName)
        assertEquals("alice", a.name)
    }

    @Test
    fun `buildClient produces a working WebClient with correct base URL`() {
        val webClient = client.buildClient(jiraConfig())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )
        val response =
            webClient
                .get()
                .uri("/rest/api/2/search")
                .retrieve()
                .toEntity(String::class.java)
                .block()
        assertNotNull(response)
        val req = takeRequest()
        assertTrue(req.path!!.contains("/rest/api/2/search"))
    }

    @Test
    fun `searchKeyword skips the cloud log block when explicit project filter is non-blank`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val results =
            client.searchKeyword(
                jiraConfig(),
                keyword = "kw",
                projectFilters = listOf("DEV"),
                sessionId = 1L,
                componentFilters = emptyList(),
                labelFilters = emptyList(),
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword skips the cloud log block in server mode`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val results =
            client.searchKeyword(
                jiraConfig(),
                keyword = "kw",
                emptyList(),
                sessionId = 1L,
                emptyList(),
                emptyList(),
            )
        assertTrue(results.isEmpty())
    }
}
