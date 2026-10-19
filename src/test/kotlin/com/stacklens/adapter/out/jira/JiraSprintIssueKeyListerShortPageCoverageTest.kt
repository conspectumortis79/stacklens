package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class JiraSprintIssueKeyListerShortPageCoverageTest {
    private lateinit var server: MockWebServer
    private val lister = JiraSprintIssueKeyLister(JiraTicketHttpClient(connectTimeoutMs = 3000, readTimeoutMs = 5000))

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun config() =
        JiraConfig(
            name = "jira",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            apiToken = "tok",
            username = "u",
            projectKey = "PROJ",
            cloudMode = true,
            active = true,
        )

    private fun body(json: String) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(json)

    @Test
    fun `a page smaller than the request size ends the walk even though more issues exist`() {
        server.enqueue(body("""{"total":50,"issues":[{"key":"PROJ-1"},{"key":"PROJ-2"}]}"""))

        val keys = lister.listTopLevelIssueKeys(config(), 7L, pageSize = 3)

        assertEquals(listOf("PROJ-1", "PROJ-2"), keys)
        assertEquals(1, server.requestCount)
    }
}
