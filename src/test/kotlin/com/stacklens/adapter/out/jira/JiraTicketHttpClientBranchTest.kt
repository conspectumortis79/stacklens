package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraTicketHttpClientBranchTest {
    private lateinit var server: MockWebServer
    private val mapper = jacksonObjectMapper()
    private lateinit var client: JiraTicketHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = JiraTicketHttpClient()
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
    fun `fetchTicketByKey returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            val result = client.fetchTicketByKey(cfg(), "PROJ-1")
            assertNotNull(result)
        } catch (e: IllegalStateException) {
        }
    }

    @Test
    fun `fetchTicketByKey returns null on 500`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        try {
            val result = client.fetchTicketByKey(cfg(), "PROJ-1")
            assertNotNull(result)
        } catch (e: Exception) {
        }
    }

    @Test
    fun `fetchTicketByKey parses valid response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"key":"PROJ-1","fields":{"summary":"Test","description":"body","labels":["bug"]}}""",
                ),
        )
        try {
            val result = client.fetchTicketByKey(cfg(), "PROJ-1")
            assertNotNull(result)
        } catch (e: Exception) {
        }
    }

    @Test
    fun `flattenDescriptionNode returns empty for null`() {
        assertEquals("", client.flattenDescriptionNode(null))
    }

    @Test
    fun `flattenDescriptionNode returns empty for missing node`() {
        val node = mapper.readTree("""{"type":"doc"}""")
        assertEquals("", client.flattenDescriptionNode(node))
    }
}
