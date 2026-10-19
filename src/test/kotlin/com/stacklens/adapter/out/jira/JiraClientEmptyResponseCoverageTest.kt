package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.domain.model.JiraConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class JiraClientEmptyResponseCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var issueMapper: JiraIssueMapper
    private lateinit var client: JiraClient

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        issueMapper = mock()
        client =
            JiraClient(
                objectMapper = jacksonObjectMapper(),
                jqlBuilder = JiraJqlBuilder(),
                issueMapper = issueMapper,
                endpoints =
                    JiraEndpoints(
                        server =
                            listOf(
                                JiraEndpointSpec("GET", "/rest/api/2/search"),
                                JiraEndpointSpec("POST", "/rest/api/3/search"),
                            ),
                    ),
                httpFactory = ReactiveHttpClientFactory(connectTimeoutMs = 5_000, readTimeoutMs = 5_000),
            )
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
            cloudMode = false,
            active = true,
        )

    @Test
    fun `a search answer without any body is not a usable page`() {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))

        val config = config()
        val results = client.searchIssues(client.buildClient(config), config, "jql", "keyword", 1L)

        assertTrue(results.isEmpty())
        assertEquals(2, server.requestCount)
    }
}
