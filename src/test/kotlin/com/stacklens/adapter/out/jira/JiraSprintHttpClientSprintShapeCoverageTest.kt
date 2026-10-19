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

class JiraSprintHttpClientSprintShapeCoverageTest {
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

    private fun issue(key: String) = """{"key":"$key","fields":{"summary":"s"}}"""

    @Test
    fun `a board without any active sprint is reported`() {
        server.enqueue(json("""{"values":[]}"""))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintData(config(), "${server.url("/")}/jira/software/projects/PR/boards/7")
            }

        assertTrue(failure.message!!.contains("No active sprint found"), failure.message)
    }

    @Test
    fun `a board lookup that answers with an empty body is reported too`() {
        server.enqueue(MockResponse().setResponseCode(204))

        val failure =
            assertThrows(IllegalStateException::class.java) {
                client.fetchSprintData(config(), "${server.url("/")}/jira/software/projects/PR/boards/8")
            }

        assertTrue(failure.message!!.contains("No active sprint found"), failure.message)
    }

    @Test
    fun `a short page ends paging before the total is reached`() {
        server.enqueue(json("""{"total":500,"issues":[${issue("PR-1")},${issue("PR-2")}]}"""))

        val issues = client.fetchSprintIssues(config(), "/jira/agile/sprint/9")

        assertEquals(2, issues.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the refusal hint falls back to the raw url when it cannot be parsed`() {
        assertEquals("127.0.0.1:1", client.refusedTargetHost(config("http://127.0.0.1:1")))
        assertEquals("localhost", client.refusedTargetHost(config("http://localhost")))
        assertEquals("http://not a jira url", client.refusedTargetHost(config("http://not a jira url")))
    }
}
