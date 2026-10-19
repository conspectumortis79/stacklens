package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraClientBranchCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JiraClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            JiraClient(
                objectMapper = jacksonObjectMapper(),
                jqlBuilder = JiraJqlBuilder(),
                issueMapper = JiraIssueMapper(),
                httpFactory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 5_000),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cfg(
        cloudMode: Boolean = false,
        projectKey: String = "",
        active: Boolean = true,
    ) = JiraConfig(
        name = "jira",
        baseUrl = server.url("/").toString().removeSuffix("/"),
        apiToken = "tok",
        username = "u",
        projectKey = projectKey,
        cloudMode = cloudMode,
        active = active,
    )

    @Test
    fun `searchKeyword with blank projectFilters and cloudMode triggers the cloud log block`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val results =
            client.searchKeyword(
                config = cfg(cloudMode = true),
                keyword = "kw",
                projectFilters = listOf("  ", ""),
                sessionId = 1L,
                componentFilters = emptyList(),
                labelFilters = emptyList(),
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword with blank projectFilters and cloudMode false skips the log block`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val results =
            client.searchKeyword(
                config = cfg(cloudMode = false),
                keyword = "kw",
                projectFilters = listOf(""),
                sessionId = 1L,
                componentFilters = emptyList(),
                labelFilters = emptyList(),
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword with non-blank projectFilters avoids the log block`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[],"total":0}"""),
        )
        val results =
            client.searchKeyword(
                config = cfg(cloudMode = true),
                keyword = "kw",
                projectFilters = listOf("  DEV  ", ""),
                sessionId = 1L,
                componentFilters = emptyList(),
                labelFilters = emptyList(),
            )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchKeyword returns empty list for inactive config`() {
        val cfg = cfg(active = false)
        val results =
            client.searchKeyword(
                config = cfg,
                keyword = "kw",
                projectFilters = emptyList(),
                sessionId = 1L,
                componentFilters = emptyList(),
                labelFilters = emptyList(),
            )
        assertTrue(results.isEmpty())
    }
}
