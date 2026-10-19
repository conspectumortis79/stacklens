package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient

class TicketRefinementNativeJiraAdapterBranchCoverageTest {
    private lateinit var server: MockWebServer

    private val http = JiraTicketHttpClient()

    private val mapper = ObjectMapper()

    private val adf = AdfToHtmlConverter(mapper)

    private val md = JiraMarkdownConverter()

    private lateinit var adapter: TicketRefinementNativeJiraAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = TicketRefinementNativeJiraAdapter(http, mapper, adf, md)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun adapterWith(markdownConverter: JiraMarkdownConverter): TicketRefinementNativeJiraAdapter = TicketRefinementNativeJiraAdapter(http, mapper, adf, markdownConverter)

    private fun config(cloud: Boolean = true): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
            cloudMode = cloud,
        )

    private fun client(config: JiraConfig): WebClient = http.buildClient(config)

    private fun node(json: String): JsonNode = mapper.readTree(json)

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body).setResponseCode(code))
    }

    @Test
    fun `post comment converts markdown into wiki markup for server instances`() {
        enqueue("""{"id":"comment-7"}""")
        assertEquals("comment-7", adapter.postComment(config(cloud = false), "PRJ-1", "*bold*", "markdown", "fallback"))

        enqueue("""{"id":"comment-8"}""")
        assertEquals("comment-8", adapter.postComment(config(cloud = false), "PRJ-1", "plain wiki", "wiki", "fallback"))

        enqueue("""{"id":"comment-9"}""")
        assertEquals("comment-9", adapter.postComment(config(cloud = false), "PRJ-1", "h1. title", "server", "fallback"))
    }

    @Test
    fun `post comment fails when jira answers without a body`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val failure =
            assertThrows(IllegalStateException::class.java) {
                adapter.postComment(config(), "PRJ-5", "*x*", "markdown", "fallback")
            }
        assertTrue(failure.message!!.contains("Jira returned no comment for PRJ-5"))
    }

    @Test
    fun `post comment falls back to the markdown pipeline for server instances`() {
        val converter = mock<JiraMarkdownConverter>()
        whenever(converter.markdownToJiraWiki("*broken")).thenThrow(IllegalArgumentException("cannot convert"))
        whenever(converter.markdownToJiraWiki("*fallback*")).thenReturn("h1. fallback")
        enqueue("""{"id":"comment-10"}""")

        val commentId = adapterWith(converter).postComment(config(cloud = false), "PRJ-6", "*broken", "markdown", "*fallback*")
        assertEquals("comment-10", commentId)
    }

    @Test
    fun `fetch single reports missing responses`() {
        val config = config()
        server.enqueue(MockResponse().setResponseCode(200))
        val client = client(config)
        assertThrows(IllegalStateException::class.java) {
            adapter.fetchSingle(client, "3", "PRJ-1", config)
        }
    }

    @Test
    fun `fetch subtask details reports missing responses`() {
        val client = client(config())
        server.enqueue(MockResponse().setResponseCode(200))
        assertThrows(IllegalStateException::class.java) {
            adapter.fetchSubtaskDetails(client, "3", "PRJ-2")
        }
    }

    @Test
    fun `fetch single resolves subtasks and mentions while ignoring failures`() {
        val config = config()
        enqueue(
            """
            {"key":"PRJ-1","fields":{
              "summary":"Relates PRJ-2 PRJ-3 PRJ-9 PROJ-1",
              "description":"see PRJ-9 again",
              "subtasks":[{"key":"PRJ-2"},{"key":"PRJ-3"},{"key":""},{"other":1}],
              "labels":["","ready",null]
            }}
            """.trimIndent(),
        )
        enqueue("""{"fields":{"summary":"sub two","description":"desc two"}}""")
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(404))
        enqueue("""{"fields":{"summary":"referenced one","description":"linked notes"}}""")

        val ticket = adapter.fetchSingle(client(config), "3", "PRJ-1", config)
        assertEquals(listOf("PRJ-2"), ticket.subtasks.map { it.key })
        assertEquals(listOf("PROJ-1"), ticket.referencedTickets.map { it.key })
        assertEquals(listOf("ready"), ticket.labels)
        assertEquals("sub two", ticket.subtasks.single().summary)
        assertTrue(
            ticket.referencedTickets
                .single()
                .description
                .contains("linked notes"),
        )
    }

    @Test
    fun `json to ticket renders adf descriptions only for cloud payloads`() {
        val cloud = config()
        val withAdf =
            adapter.jsonToTicket(
                node("""{"key":"PRJ-1","fields":{"summary":"s","description":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"body text"}]}]}}}"""),
                cloud,
            )
        assertTrue(withAdf.descriptionFormat == "adf")
        assertTrue(withAdf.descriptionHtml!!.contains("body text"))

        val missing =
            adapter.jsonToTicket(node("""{"key":"PRJ-2","fields":{"summary":"s"}}"""), cloud)
        assertNull(missing.descriptionHtml)
        assertEquals("text", missing.descriptionFormat)
        assertEquals("", missing.descriptionNative)

        val explicitNull =
            adapter.jsonToTicket(node("""{"key":"PRJ-3","fields":{"summary":"s","description":null}}"""), cloud)
        assertNull(explicitNull.descriptionHtml)

        val serverConfig = config(cloud = false)
        val markdownish =
            adapter.jsonToTicket(node("""{"key":"PRJ-4","fields":{"summary":"s","description":"- first item\n- second item"}}"""), serverConfig)
        assertEquals("text", markdownish.descriptionFormat)
        assertEquals("", markdownish.descriptionNative)

        val wiki =
            adapter.jsonToTicket(node("""{"key":"PRJ-5","fields":{"summary":"s","description":"h1. Heading"}}"""), serverConfig)
        assertEquals("wiki", wiki.descriptionFormat)
        assertEquals("h1. Heading", wiki.descriptionNative)

        val serverWithAdf =
            adapter.jsonToTicket(node("""{"key":"PRJ-6","fields":{"summary":"s","description":{"type":"doc"}}}"""), serverConfig)
        assertNull(serverWithAdf.descriptionHtml)
        assertEquals("text", serverWithAdf.descriptionFormat)
    }

    @Test
    fun `parent lookup keeps only tickets that answer successfully`() {
        val config = config()
        enqueue(
            """
            {"key":"PRJ-1","fields":{"summary":"parent linking PRJ-4 and PRJ-1","description":"also PRJ-4",
            "issuelinks":[{"type":{"name":"Blocks"},"outwardIssue":{"key":"PRJ-4"}}]}}
            """.trimIndent(),
        )
        server.enqueue(MockResponse().setResponseCode(200))
        val tickets = adapter.fetchParentWithLinkedTickets(config, "PRJ-1")
        assertEquals(listOf("PRJ-1"), tickets.map { it.key })
    }

    @Test
    fun `parent lookup requires a configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            adapter.fetchParentWithLinkedTickets(null, "PRJ-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            adapter.postComment(null, "PRJ-1", "body", "markdown", "fallback")
        }
    }

    @Test
    fun `render adf to html forwards the converter result`() {
        assertNull(adapter.renderAdfToHtml(null))
        val html =
            adapter.renderAdfToHtml(
                """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]}""",
            )
        assertEquals("<p class=\"sp-jira-p\">hello</p>", html)
    }
}
