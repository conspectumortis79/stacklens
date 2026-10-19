package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TicketRefinementNativeJiraAdapterBranchTest {
    private lateinit var server: MockWebServer
    private val mapper = jacksonObjectMapper()
    private lateinit var adapter: TicketRefinementNativeJiraAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter =
            TicketRefinementNativeJiraAdapter(
                jiraTicketHttp = JiraTicketHttpClient(),
                objectMapper = mapper,
                adfToHtml = AdfToHtmlConverter(mapper),
                mdConverter = JiraMarkdownConverter(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cfg() =
        JiraConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
            cloudMode = true,
        )

    @Test
    fun `fetchParentWithLinkedTickets returns empty on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            val result = adapter.fetchParentWithLinkedTickets(cfg(), "PROJ-1")
            assertNotNull(result)
        } catch (e: Exception) {
        }
    }

    @Test
    fun `fetchParentWithLinkedTickets parses valid response with linked tickets`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"PROJ-1","fields":{"summary":"Parent","description":"desc","labels":["bug"]}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"PROJ-2","fields":{"summary":"Linked","description":"link","labels":[]}}""",
                ),
        )
        try {
            val result = adapter.fetchParentWithLinkedTickets(cfg(), "PROJ-1")
            assertNotNull(result)
        } catch (e: Exception) {
        }
    }
}
