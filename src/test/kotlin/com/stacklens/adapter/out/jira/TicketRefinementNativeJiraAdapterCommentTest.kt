package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TicketRefinementNativeJiraAdapterCommentTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: TicketRefinementNativeJiraAdapter
    private val mapper = ObjectMapper()
    private val mdConverter = JiraMarkdownConverter()

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

    private fun cfg(
        cloudMode: Boolean = true,
        role: String = "",
    ) = JiraConfig(
        id = 1L,
        name = "j",
        baseUrl = server.url("/").toString().trimEnd('/'),
        username = "u",
        apiToken = "t",
        cloudMode = cloudMode,
    ).also { it.commentVisibilityRole = role }

    private fun enqueueOk(commentId: String = "10000") {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"$commentId"}"""),
        )
    }

    @Test
    fun `cloud comment payload contains visibility role when configured`() {
        enqueueOk()
        val id = adapter.postComment(cfg(cloudMode = true, role = "Developers"), "PROJ-1", "**hi**", "markdown", "**hi**")
        assertEquals("10000", id)

        val req = server.takeRequest()
        assertEquals("/rest/api/3/issue/PROJ-1/comment", req.path)
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertTrue(body.has("body"), "payload must keep its body field")
        assertEquals("role", body.path("visibility").path("type").asText())
        assertEquals("Developers", body.path("visibility").path("value").asText())
    }

    @Test
    fun `server payload contains visibility role when configured`() {
        enqueueOk()
        adapter.postComment(cfg(cloudMode = false, role = "Team Leads"), "PROJ-2", "h2. T", "wiki", "T")

        val req = server.takeRequest()
        assertEquals("/rest/api/2/issue/PROJ-2/comment", req.path)
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("Team Leads", body.path("visibility").path("value").asText())
    }

    @Test
    fun `payload omits visibility when role is blank`() {
        enqueueOk()
        adapter.postComment(cfg(cloudMode = true, role = ""), "PROJ-3", "hi", "markdown", "hi")

        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertFalse(body.has("visibility"), "no visibility key should be sent for blank role")
    }

    @Test
    fun `payload trims surrounding whitespace from visibility role`() {
        enqueueOk()
        adapter.postComment(cfg(cloudMode = true, role = "  Developers  "), "PROJ-4", "hi", "markdown", "hi")

        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("Developers", body.path("visibility").path("value").asText())
    }

    @Test
    fun `fallback path on broken native body still includes visibility role`() {
        enqueueOk()
        adapter.postComment(
            cfg(cloudMode = true, role = "Developers"),
            "PROJ-5",
            body = "{ this is not valid ADF",
            format = "adf",
            fallbackMarkdown = "fallback",
        )

        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertNotNull(body.path("body"))
        assertEquals("Developers", body.path("visibility").path("value").asText())
    }

    @Test
    fun `looksLikeMarkdown returns false for blank input`() {
        assertFalse(mdConverter.looksLikeMarkdown(""))
        assertFalse(mdConverter.looksLikeMarkdown("   \n  "))
    }

    @Test
    fun `looksLikeMarkdown returns true for ATX headings`() {
        assertTrue(mdConverter.looksLikeMarkdown("## Ziel\n\nText folgt"))
        assertTrue(mdConverter.looksLikeMarkdown("# Title\n\nbody"))
    }

    @Test
    fun `looksLikeMarkdown returns true for fenced code`() {
        assertTrue(mdConverter.looksLikeMarkdown("Hier ist Code:\n```\nkotlin\n```"))
    }

    @Test
    fun `looksLikeMarkdown returns true for markdown bullet list`() {
        assertTrue(mdConverter.looksLikeMarkdown("Liste:\n- Punkt 1\n- Punkt 2"))
    }

    @Test
    fun `looksLikeMarkdown returns true for bold markers`() {
        assertTrue(mdConverter.looksLikeMarkdown("Das ist **wichtig** und gut."))
    }

    @Test
    fun `looksLikeMarkdown returns true for inline link`() {
        assertTrue(mdConverter.looksLikeMarkdown("Siehe [Doku](https://example.com)."))
    }

    @Test
    fun `looksLikeMarkdown returns false for plain wiki markup`() {
        assertFalse(mdConverter.looksLikeMarkdown("h2. Ziel\n\nfoo bar baz\nnur text"))
    }

    @Test
    fun `cloud comment prepends greeting and open points for adf format`() {
        enqueueOk("10010")
        val adf =
            """{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]}"""

        val id = adapter.postComment(cfg(cloudMode = true), "PROJ-9", adf, "adf", "fallback", "Hallo", "Offene Punkte", listOf("P1"))

        assertEquals("10010", id)
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        val text = body.path("body").toString()
        assertTrue(text.contains("Hallo"))
        assertTrue(text.contains("Offene Punkte"))
        assertTrue(text.contains("P1"))
    }
}
