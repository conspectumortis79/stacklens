package com.stacklens.adapter.out.jira

import com.stacklens.application.port.out.JiraProblemAnalysis
import com.stacklens.application.port.out.JiraTicketData
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JiraTicketHttpClientSimilarTicketsTest {
    private lateinit var server: MockWebServer

    private lateinit var client: JiraTicketHttpClient

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
        client = JiraTicketHttpClient(connectTimeoutMs = 5000, readTimeoutMs = 5000)
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun config(): JiraConfig =
        JiraConfig(
            name = "c",
            baseUrl = server.url("").toString().removeSuffix("/"),
            username = "u",
            apiToken = "t",
            cloudMode = true,
            projectKey = "PROJ, OTHER",
            createdAt = LocalDateTime.now(),
        )

    private fun problem(keywords: List<String>) =
        JiraProblemAnalysis(
            problemDescription = "p",
            rootCause = "r",
            affectedComponents = emptyList(),
            searchKeywords = keywords,
            errorMessages = emptyList(),
        )

    private val original =
        JiraTicketData(
            key = "P-1",
            summary = "S",
            description = "D",
            comments = "",
            issueType = "Bug",
            status = "Open",
            priority = null,
            labels = emptyList(),
            components = emptyList(),
            url = "u",
            acceptanceCriteria = emptyList(),
        )

    private fun issue(
        key: String,
        fields: String,
    ): String = """{"key":"$key","fields":$fields}"""

    @Test
    fun `similar ticket search stops at the hard limit and skips duplicates`() {
        val generated = (1..25).joinToString(",") { index -> issue("PROJ-$index", """{"summary":"s$index"}""") }
        val body = """{"issues":[${issue("P-1", """{"summary":"duplicate"}""")},$generated]}"""
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[]}"""),
        )

        val results = client.searchSimilarTickets(config(), original, problem(listOf("first", "second")))

        assertEquals(20, results.size)
        assertEquals("PROJ-1", results.first().key)
        assertEquals("first", results.first().foundViaKeyword)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `similar ticket search keeps defaults for missing issue metadata`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"issues":[
                       ${issue("PROJ-2", "{}")},
                       ${issue("PROJ-3", """{"summary":"mit","issuetype":{"name":"Task"},"status":{"name":"Done"},"description":{"type":"doc","content":[{"type":"text","text":4242}]}}""")}
                    ]}""",
                ),
        )

        val results = client.searchSimilarTickets(config(), original, problem(listOf("kw")))

        assertEquals(2, results.size)
        assertEquals("", results[0].summary)
        assertEquals("Issue", results[0].issueType)
        assertEquals("", results[0].status)
        assertEquals("mit", results[1].summary)
        assertEquals("Task", results[1].issueType)
        assertEquals("Done", results[1].status)
        assertTrue(results[1].description.isBlank())
    }

    @Test
    fun `similar ticket search survives a malformed issue payload`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[null]}"""),
        )

        val results = client.searchSimilarTickets(config(), original, problem(listOf("kw")))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `similar ticket search skips keywords whose request fails`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"issues":[${issue("PROJ-4", """{"summary":"nach fehler"}""")}]}"""),
        )

        val results = client.searchSimilarTickets(config(), original, problem(listOf("kaputt", "funktioniert")))

        assertEquals(listOf("PROJ-4"), results.map { it.key })
    }
}
