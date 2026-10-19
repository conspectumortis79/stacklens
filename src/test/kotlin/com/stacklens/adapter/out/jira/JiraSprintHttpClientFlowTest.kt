package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraSprintIssueFieldResolver
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class JiraSprintHttpClientFlowTest {
    private val server = MockWebServer()
    private val client = JiraSprintHttpClient(JiraSprintIssueFieldResolver(), connectTimeoutMs = 2000, readTimeoutMs = 4000)

    @BeforeEach
    fun start() {
        server.start()
    }

    @AfterEach
    fun stop() {
        server.shutdown()
    }

    private fun config(baseUrl: String? = null) =
        JiraConfig(
            name = "jira",
            baseUrl = baseUrl ?: server.url("/").toString().trimEnd('/'),
            projectKey = "PR",
            username = "user",
            apiToken = "token",
        )

    private fun json(body: String) = MockResponse().setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(body)

    private fun issueJson(
        key: String,
        fields: String,
    ) = """{"key":"$key","fields":{$fields}}"""

    @Test
    fun `the active sprint of a board is resolved and its issues are mapped completely`() {
        server.enqueue(json("""{"values":[{"id":42,"name":"Sprint 42","state":"active"}]}"""))
        server.enqueue(json("""{"id":42,"name":"Sprint 42","state":"active","startDate":"2026-01-01","endDate":"2026-01-14","goal":"ship"}"""))
        val complete =
            issueJson(
                "PR-1",
                "\"summary\":\"Full\",\"description\":\"Text\",\"issuetype\":{\"name\":\"Story\"}," +
                    "\"status\":{\"name\":\"Done\"},\"priority\":{\"name\":\"Highest\"}," +
                    "\"resolutiondate\":\"2026-01-05\",\"comment\":{\"comments\":" +
                    "[{\"author\":{\"displayName\":\"Ann\"},\"body\":\"plain text\"},{\"body\":\"no author\"}]}",
            )
        val sparse = issueJson("PR-2", "\"comment\":{\"comments\":[]}")
        val linked =
            issueJson(
                "PR-3",
                "\"issuelinks\":[{\"type\":{\"inward\":\"causes\",\"outward\":\"is caused by\"}," +
                    "\"inwardIssue\":{\"key\":\"PR-8\",\"fields\":{\"summary\":\"Linked\"}}}," +
                    "{\"outwardIssue\":{\"key\":\"PR-9\"}},{\"type\":{\"name\":\"x\"}}]",
            )
        server.enqueue(json("""{"total":3,"issues":[$complete,$sparse,$linked]}"""))

        val (info, issues) =
            client.fetchSprintData(config(), "https://jira.example.com/jira/software/projects/PR/boards/7")

        assertEquals(42L, info.id)
        assertEquals("ship", info.goal)
        assertEquals(3, issues.size)
        assertEquals("Full", issues[0].summary)
        assertEquals("Ann: plain text\n---\nUnbekannt: no author", issues[0].comments)
        assertEquals("", issues[1].summary)
        assertEquals("Issue", issues[1].issueType)
        assertEquals("", issues[1].status)
        assertEquals(listOf("PR-8", "PR-9"), issues[2].linkedIssues.map { it.key })
        assertEquals("causes", issues[2].linkedIssues[0].relationship)
        assertEquals("relates to", issues[2].linkedIssues[1].relationship)
        assertEquals(null, issues[2].linkedIssues[1].status)
    }

    @Test
    fun `a sprint url with an explicit id skips the board lookup`() {
        server.enqueue(json("""{"total":1,"issues":[${issueJson("PR-5", "\"summary\":\"x\"")}]}"""))

        val issues = client.fetchSprintIssues(config(), "https://jira.example.com/jira/agile/6100/sprint/7")

        assertEquals(1, issues.size)
        assertEquals(1, server.requestCount)
        assertTrue(
            server
                .takeRequest()
                .path
                .orEmpty()
                .startsWith("/rest/agile/1.0/sprint/7/issue"),
        )
    }

    @Test
    fun `a sprint parameter resolves the id before the board regex is tried`() {
        server.enqueue(json("""{"total":0,"issues":[]}"""))

        assertEquals(0, client.fetchSprintIssues(config(), "https://jira.example.com/boards/9?selectedSprint=12").size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an empty sprint list on the board aborts with a hint`() {
        server.enqueue(json("""{"values":[]}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintIssues(config(), "https://jira.example.com/jira/software/projects/PR/boards/7")
            }

        assertTrue(failure.message!!.contains("No active sprint found for board 7"))
    }

    @Test
    fun `a failing board request keeps the http status in the message`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintInfo(config(), "https://jira.example.com/jira/software/projects/PR/boards/7")
            }

        assertTrue(failure.message!!.contains("HTTP 401"))
    }

    @Test
    fun `a url without any board or sprint marker cannot be resolved`() {
        assertThrows(IllegalArgumentException::class.java) { client.fetchSprintIssues(config(), "https://jira.example.com/browse") }
    }

    @Test
    fun `paging stops at the reported total even when a page stays full`() {
        val full = (1..100).joinToString(",") { issueJson("PR-$it", "\"summary\":\"s$it\"") }
        server.enqueue(json("""{"total":120,"issues":[$full]}"""))
        server.enqueue(json("""{"total":120,"issues":[${(101..120).joinToString(",") { issueJson("PR-$it", "\"summary\":\"s$it\"") }}]}"""))

        val issues = client.fetchSprintIssues(config(), "/jira/agile/sprint/1")

        assertEquals(120, issues.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a connection refusal is translated into a tagged error with the host`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintIssues(config("http://127.0.0.1:1"), "/jira/agile/sprint/1")
            }

        assertTrue(failure.message!!.startsWith("[JIRA_CONN_REFUSED:127.0.0.1:1]"), failure.message)
    }

    @Test
    fun `a connection refusal without an explicit port reports the bare host`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintIssues(config("http://localhost"), "/jira/agile/sprint/1")
            }

        assertTrue(failure.message!!.startsWith("[JIRA_CONN_REFUSED:localhost]"), failure.message)
    }

    @Test
    fun `a sprint detail request that fails falls back to a placeholder info`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val info = client.fetchSprintInfo(config(), "/jira/agile/sprint/3")

        assertEquals(3L, info.id)
        assertEquals("Sprint 3", info.name)
        assertEquals("unknown", info.state)
        assertEquals(null, info.goal)
    }
}
