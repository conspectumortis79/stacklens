package com.stacklens.adapter.out.jira

import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SprintResolverResidualCoverageTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: SprintResolver

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = SprintResolver(JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 10000))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "jira",
            baseUrl = server.url("/").toString().trimEnd('/'),
            projectKey = "DEV",
            username = "user",
            apiToken = "tok",
            cloudMode = true,
        )

    @Test
    fun `zero-id entries are skipped even when a duplicate of a valid id follows`() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "issues":[
                                {"key":"DEV-1","fields":{"project":{"key":"DEV"},"customfield_10020":[
                                  {"id":0,"name":"Sprint 42","state":"active","boardId":4},
                                  {"id":7,"name":"Sprint 42","state":"active","boardId":4}
                                ]}},
                                {"key":"DEV-2","fields":{"project":{"key":"DEV"},"customfield_10020":[
                                  {"id":-3,"name":"Sprint 42","state":"active","boardId":5},
                                  {"id":7,"name":"Sprint 42","state":"active","boardId":6}
                                ]}}
                              ]
                            }
                            """.trimIndent(),
                        )
            }
        val resolved = resolver.resolve(config(), "Sprint 42")
        assertEquals(7L, resolved.id)
        assertThrows(IllegalArgumentException::class.java) { resolver.resolve(config(), "Sprint 43") }
    }
}
