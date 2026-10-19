package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraSprintIssueKeyListerTest {
    private lateinit var server: MockWebServer
    private lateinit var lister: JiraSprintIssueKeyLister

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 10000)
        lister = JiraSprintIssueKeyLister(http)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listTopLevelIssueKeys returns keys from a single page`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"total":3,"issues":[{"key":"PROJ-1"},{"key":"PROJ-2"},{"key":"PROJ-3"}]}""",
                ),
        )
        val keys = lister.listTopLevelIssueKeys(cloudConfig(), sprintId = 42L)
        assertEquals(listOf("PROJ-1", "PROJ-2", "PROJ-3"), keys)
    }

    @Test
    fun `listTopLevelIssueKeys paginates until all issues are returned`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"total":3,"issues":[{"key":"PROJ-1"},{"key":"PROJ-2"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"total":3,"issues":[{"key":"PROJ-3"}]}""",
                ),
        )
        val keys = lister.listTopLevelIssueKeys(cloudConfig(), sprintId = 42L, pageSize = 2)
        assertEquals(listOf("PROJ-1", "PROJ-2", "PROJ-3"), keys)
    }

    @Test
    fun `listTopLevelIssueKeys returns empty list when total is zero`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"total":0,"issues":[]}"""),
        )
        val keys = lister.listTopLevelIssueKeys(cloudConfig(), sprintId = 42L)
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `listTopLevelIssueKeys skips duplicate keys across pages`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"total":4,"issues":[{"key":"PROJ-1"},{"key":"PROJ-2"}]}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"total":4,"issues":[{"key":"PROJ-2"},{"key":"PROJ-3"}]}""",
                ),
        )
        val keys = lister.listTopLevelIssueKeys(cloudConfig(), sprintId = 42L, pageSize = 2)
        assertEquals(listOf("PROJ-1", "PROJ-2", "PROJ-3"), keys)
    }

    @Test
    fun `listTopLevelIssueKeys throws IllegalStateException on HTTP 400`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorMessages":["Bad JQL"]}"""),
        )
        val ex =
            org.junit.jupiter.api.Assertions
                .assertThrows(IllegalStateException::class.java) {
                    lister.listTopLevelIssueKeys(cloudConfig(), sprintId = 42L)
                }
        assertTrue(ex.message!!.contains("HTTP 400"))
    }

    @Test
    fun `listTopLevelIssueKeys queries the cloud search-jql endpoint when cloudMode is true`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"total":1,"issues":[{"key":"PROJ-9"}]}"""),
        )
        lister.listTopLevelIssueKeys(cloudConfig(), sprintId = 42L)
        val recorded = server.takeRequest()
        assertEquals("/rest/api/3/search/jql", recorded.path!!.substringBefore('?'))
        assertTrue(recorded.path!!.contains("jql=sprint"))
    }

    @Test
    fun `listTopLevelIssueKeys queries the v2 search endpoint when cloudMode is false`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"total":1,"issues":[{"key":"PROJ-9"}]}"""),
        )
        lister.listTopLevelIssueKeys(serverConfig(), sprintId = 42L)
        val recorded = server.takeRequest()
        assertEquals("/rest/api/2/search", recorded.path!!.substringBefore('?'))
    }

    private fun cloudConfig(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "",
            username = "user",
            apiToken = "token",
            cloudMode = true,
        )

    private fun serverConfig(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "",
            username = "user",
            apiToken = "token",
            cloudMode = false,
        )
}
