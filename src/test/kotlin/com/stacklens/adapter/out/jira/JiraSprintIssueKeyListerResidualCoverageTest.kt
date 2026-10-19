package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class JiraSprintIssueKeyListerResidualCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var lister: JiraSprintIssueKeyLister

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        lister = JiraSprintIssueKeyLister(JiraTicketHttpClient(connectTimeoutMs = 3000, readTimeoutMs = 5000))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config() =
        JiraConfig(
            name = "jira",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "u",
            apiToken = "t",
            cloudMode = true,
        )

    private fun body(json: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).setBody(json)

    @Test
    fun `a full page that reaches the total stops without a short page`() {
        server.enqueue(body("""{"total":2,"issues":[{"key":"PROJ-1"},{"key":"PROJ-2"}]}"""))

        assertEquals(listOf("PROJ-1", "PROJ-2"), lister.listTopLevelIssueKeys(config(), 7L, pageSize = 2))
        assertEquals(1, server.requestCount)
    }
}
