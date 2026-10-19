package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SprintIssueLoaderResidualCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var loader: SprintIssueLoader

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 10000)
        loader = SprintIssueLoader(http)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(storyPointsFieldId: String = "") =
        JiraConfig(
            name = "jira",
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiToken = "token",
            cloudMode = true,
            storyPointsFieldId = storyPointsFieldId,
        )

    private fun replyWith(body: String): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
        }

    @Test
    fun `blank label entries are dropped from the ticket`() {
        server.dispatcher =
            replyWith(
                """
                {
                  "total": 1,
                  "issues": [
                    {
                      "key": "PROJ-9",
                      "fields": {
                        "summary": "s",
                        "description": null,
                        "labels": ["", "   ", "real"],
                        "issuetype": {"name": "Story", "subtask": false},
                        "status": {"name": "Open"},
                        "customfield_10016": 1.0
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )
        val result = loader.fetch(config(storyPointsFieldId = "customfield_10016"), sprintId = 1L)
        assertEquals(listOf("real"), result.single().labels)
    }

    @Test
    fun `an explicit story points field id skips the field metadata lookup`() {
        server.dispatcher =
            replyWith(
                """
                {
                  "total": 1,
                  "issues": [
                    {
                      "key": "PROJ-10",
                      "fields": {
                        "summary": "s",
                        "description": null,
                        "labels": [],
                        "issuetype": {"name": "Story", "subtask": false},
                        "status": {"name": "Open"},
                        "customfield_777": 8.0
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )
        val result = loader.fetch(config(storyPointsFieldId = "customfield_777"), sprintId = 1L)
        assertEquals(8.0, result.single().storyPoints)
        assertTrue(server.requestCount >= 1)
        assertTrue(server.requestCount == 1, "only the search call may happen, got ${server.requestCount}")
    }

    @Test
    fun `paging stops when startAt reaches the reported total with a full page`() {
        val pageSize = 50
        val issues =
            (1..pageSize).joinToString(",") { i ->
                """
                {"key":"P-$i","fields":{"summary":"s","description":null,"labels":[],
                "issuetype":{"name":"Story","subtask":false},"status":{"name":"Open"}}}
                """.trimIndent()
            }
        server.dispatcher =
            replyWith("""{"total":$pageSize,"issues":[$issues]}""")
        val result = loader.fetch(config(storyPointsFieldId = "customfield_10016"), sprintId = 1L)
        assertEquals(pageSize, result.size)
        assertEquals(1, server.requestCount)
    }
}
