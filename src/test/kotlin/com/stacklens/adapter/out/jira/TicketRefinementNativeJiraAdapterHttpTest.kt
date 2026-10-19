package com.stacklens.adapter.out.jira

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.domain.model.JiraConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TicketRefinementNativeJiraAdapterHttpTest {
    private lateinit var server: MockWebServer
    private val mapper = ObjectMapper()
    private lateinit var adapter: TicketRefinementNativeJiraAdapter
    private lateinit var jiraTicketHttp: JiraTicketHttpClient
    private lateinit var mdConverter: JiraMarkdownConverter
    private lateinit var adfToHtml: AdfToHtmlConverter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        jiraTicketHttp = JiraTicketHttpClient()
        mdConverter = JiraMarkdownConverter()
        adfToHtml = AdfToHtmlConverter(mapper)
        adapter =
            TicketRefinementNativeJiraAdapter(
                jiraTicketHttp = jiraTicketHttp,
                objectMapper = mapper,
                adfToHtml = adfToHtml,
                mdConverter = mdConverter,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun cfg(cloudMode: Boolean = false): JiraConfig =
        JiraConfig(
            id = 1L,
            name = "j",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "u",
            apiToken = "t",
            cloudMode = cloudMode,
        )

    private fun issueJson(
        key: String,
        summary: String = "S",
        description: String = "d",
        labels: List<String> = emptyList(),
        subtasks: List<Pair<String, String>> = emptyList(),
        issuelinks: List<Map<String, String>> = emptyList(),
    ): String {
        val subtaskJson =
            subtasks.joinToString(",") { (k, s) ->
                """{"key":"$k","fields":{"summary":"$s"}}"""
            }
        val linkJson =
            issuelinks.joinToString(",") { link ->
                buildString {
                    append("{")
                    link["inward"]?.let { append(""""inwardIssue":{"key":"$it"}""") }
                    link["outward"]?.let { append(""""outwardIssue":{"key":"$it"}""") }
                    append("}")
                }
            }
        val labelsJson = labels.joinToString(",") { """"$it"""" }
        return """{"key":"$key","fields":{"summary":"$summary","description":"$description","labels":[$labelsJson],"subtasks":[$subtaskJson],"issuelinks":[$linkJson]}}"""
    }

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun enqueueEmpty() {
        server.enqueue(MockResponse().setResponseCode(204))
    }

    @Test
    fun `fetchParentWithLinkedTickets returns just the parent when no links`() {
        enqueueJson(issueJson("PARENT-1"))
        val tickets = adapter.fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertEquals(1, tickets.size)
        assertEquals("PARENT-1", tickets[0].key)
        val req = server.takeRequest()
        assertEquals("/rest/api/2/issue/PARENT-1?fields=summary,description,comment,issuelinks,subtasks,labels", req.path)
    }

    @Test
    fun `fetchParentWithLinkedTickets fetches linked issues via issuelinks`() {
        enqueueJson(issueJson("PARENT-1", issuelinks = listOf(mapOf("outward" to "LINK-1"))))
        enqueueJson(issueJson("LINK-1"))
        val tickets = adapter.fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertEquals(2, tickets.size)
        assertEquals("PARENT-1", tickets[0].key)
        assertEquals("LINK-1", tickets[1].key)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `fetchParentWithLinkedTickets follows mentions in description`() {
        enqueueJson(issueJson("PARENT-1", description = "see MENTION-9"))
        enqueueJson(issueJson("MENTION-9"))
        val tickets = adapter.fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertTrue(tickets.any { it.key == "MENTION-9" })
    }

    @Test
    fun `fetchParentWithLinkedTickets uses cloud v3 endpoint`() {
        enqueueJson(issueJson("C-1"))
        adapter.fetchParentWithLinkedTickets(cfg(cloudMode = true), "C-1")
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/rest/api/3/issue/C-1"))
    }

    @Test
    fun `fetchParentWithLinkedTickets skips linked ticket that 404s`() {
        enqueueJson(issueJson("PARENT-1", issuelinks = listOf(mapOf("outward" to "MISSING-1"))))
        server.enqueue(MockResponse().setResponseCode(404))
        val tickets = adapter.fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertEquals(1, tickets.size)
        assertEquals("PARENT-1", tickets[0].key)
    }

    @Test
    fun `fetchParentWithLinkedTickets throws when response is null`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        assertThrows(Exception::class.java) {
            adapter.fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        }
    }

    @Test
    fun `fetchParentWithLinkedTickets throws IllegalStateException when body is empty 200`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val ex =
            assertThrows(IllegalStateException::class.java) {
                adapter.fetchParentWithLinkedTickets(cfg(), "PARENT-1")
            }
        assertTrue(ex.message!!.contains("PARENT-1"))
    }

    @Test
    fun `fetchParentWithLinkedTickets filters out parent key from linkedKeys when it appears in issuelinks`() {
        val parentJson =
            """
            {
              "id":"10000",
              "key":"PROJ-1",
              "fields":{
                "summary":"Parent ticket",
                "description":null,
                "comment":{"comments":[]},
                "issuelinks":[
                  {"type":{"name":"Relates","inward":"relates to","outward":"relates to"},
                   "inwardIssue":{"key":"PROJ-1"}},
                  {"type":{"name":"Relates","inward":"relates to","outward":"relates to"},
                   "inwardIssue":{"key":"PROJ-2"}}
                ],
                "subtasks":[],
                "labels":[]
              }
            }
            """.trimIndent()
        enqueueJson(parentJson)
        enqueueJson(
            """
            {"id":"20000","key":"PROJ-2","fields":{"summary":"Other","description":null,"comment":{"comments":[]},"issuelinks":[],"subtasks":[],"labels":[]}}
            """.trimIndent(),
        )

        val tickets = adapter.fetchParentWithLinkedTickets(cfg(), "PROJ-1")
        assertEquals(2, tickets.size)
        assertEquals("PROJ-1", tickets[0].key)
        assertEquals("PROJ-2", tickets[1].key)
    }

    @Test
    fun `fetchParentWithLinkedTickets requires config`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                adapter.fetchParentWithLinkedTickets(null, "PARENT-1")
            }
        assertTrue(ex.message!!.contains("required"))
    }

    @Test
    fun `fetchSingle fetches subtasks and referenced tickets`() {
        enqueueJson(issueJson("P-1", description = "see REF-1", subtasks = listOf("SUB-1" to "S1")))
        enqueueJson(issueJson("SUB-1"))
        enqueueJson(issueJson("REF-1"))
        val ticket = adapter.fetchSingle(jiraTicketHttp.buildClient(cfg()), "2", "P-1", cfg())
        assertEquals("P-1", ticket.key)
        assertEquals(1, ticket.subtasks.size)
        assertEquals("SUB-1", ticket.subtasks[0].key)
        assertEquals(1, ticket.referencedTickets.size)
        assertEquals("REF-1", ticket.referencedTickets[0].key)
    }

    @Test
    fun `fetchSingle caps subtasks at 10`() {
        val manySubs = (1..15).map { "SUB-$it" to "S$it" }
        enqueueJson(issueJson("P-1", subtasks = manySubs))
        for (i in 1..10) {
            enqueueJson(issueJson("SUB-$i"))
        }
        val ticket = adapter.fetchSingle(jiraTicketHttp.buildClient(cfg()), "2", "P-1", cfg())
        assertEquals(10, ticket.subtasks.size)
    }

    @Test
    fun `fetchSingle caps referenced tickets at 5`() {
        val desc = (1..8).joinToString(" ") { "see REF-$it" }
        enqueueJson(issueJson("P-1", description = desc))
        for (i in 1..5) {
            enqueueJson(issueJson("REF-$i"))
        }
        val ticket = adapter.fetchSingle(jiraTicketHttp.buildClient(cfg()), "2", "P-1", cfg())
        assertEquals(5, ticket.referencedTickets.size)
    }

    @Test
    fun `fetchSingle excludes self from referencedTickets`() {
        enqueueJson(issueJson("P-1", description = "see P-1 mentioned"))
        val ticket = adapter.fetchSingle(jiraTicketHttp.buildClient(cfg()), "2", "P-1", cfg())
        assertTrue(ticket.referencedTickets.none { it.key == "P-1" })
    }

    @Test
    fun `fetchSingle excludes self from subtasks in referencedTickets`() {
        enqueueJson(issueJson("P-1", subtasks = listOf("P-1" to "self")))
        enqueueJson(issueJson("P-1"))
        val ticket = adapter.fetchSingle(jiraTicketHttp.buildClient(cfg()), "2", "P-1", cfg())
        assertTrue(ticket.referencedTickets.none { it.key == "P-1" })
    }

    @Test
    fun `fetchSubtaskDetails returns key summary and description`() {
        enqueueJson("""{"key":"S-1","fields":{"summary":"Sub","description":"D"}}""")
        val sub = adapter.fetchSubtaskDetails(jiraTicketHttp.buildClient(cfg()), "2", "S-1")
        assertEquals("S-1", sub.key)
        assertEquals("Sub", sub.summary)
        assertEquals("D", sub.description)
    }

    @Test
    fun `fetchSubtaskDetails handles missing fields`() {
        enqueueJson("""{"key":"S-2","fields":{}}""")
        val sub = adapter.fetchSubtaskDetails(jiraTicketHttp.buildClient(cfg()), "2", "S-2")
        assertEquals("S-2", sub.key)
        assertEquals("", sub.summary)
        assertEquals("", sub.description)
    }

    @Test
    fun `postComment passes through wiki body for server mode`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"42"}"""),
        )
        val id =
            adapter.postComment(
                cfg(cloudMode = false),
                "S-1",
                "h2. header",
                "wiki",
                "fallback",
            )
        assertEquals("42", id)
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("h2. header", body.path("body").asText())
    }

    @Test
    fun `postComment converts markdown to wiki for server mode`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"43"}"""),
        )
        adapter.postComment(cfg(cloudMode = false), "S-2", "**bold**", "markdown", "fallback")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertNotNull(body.path("body").asText())
        assertTrue(body.path("body").asText().contains("*"))
    }

    @Test
    fun `postComment converts unknown format using fallback for server mode`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"44"}"""),
        )
        adapter.postComment(cfg(cloudMode = false), "S-3", "raw", "unknown", "fallback markdown")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertNotNull(body.path("body").asText())
    }

    @Test
    fun `postComment passes through adf body for cloud mode`() {
        val adf =
            """{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"hi"}]}]}"""
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"45"}"""),
        )
        adapter.postComment(cfg(cloudMode = true), "C-1", adf, "adf", "fallback")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("doc", body.path("body").path("type").asText())
    }

    @Test
    fun `postComment converts markdown to adf for cloud mode`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"46"}"""),
        )
        adapter.postComment(cfg(cloudMode = true), "C-2", "**bold**", "markdown", "fallback")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("doc", body.path("body").path("type").asText())
    }

    @Test
    fun `postComment converts unknown format using fallback for cloud mode`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"47"}"""),
        )
        adapter.postComment(cfg(cloudMode = true), "C-3", "raw", "unknown", "**fallback**")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("doc", body.path("body").path("type").asText())
    }

    @Test
    fun `postComment rejects invalid ADF body and falls back`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"48"}"""),
        )
        adapter.postComment(
            cfg(cloudMode = true),
            "C-4",
            body = """{"type":"paragraph"}""",
            format = "adf",
            fallbackMarkdown = "fallback body",
        )
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("doc", body.path("body").path("type").asText())
    }

    @Test
    fun `postComment falls back to wiki on conversion failure for server`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"49"}"""),
        )
        adapter.postComment(
            cfg(cloudMode = false),
            "S-4",
            body = """{ broken markdown""",
            format = "markdown",
            fallbackMarkdown = "**fallback**",
        )
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertNotNull(body.path("body").asText())
    }

    @Test
    fun `postComment requires config`() {
        assertThrows(IllegalArgumentException::class.java) {
            adapter.postComment(null, "P-1", "body", "markdown", "fb")
        }
    }

    @Test
    fun `postComment throws when response has no id`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"other":"x"}"""),
        )
        val id = adapter.postComment(cfg(), "P-1", "body", "markdown", "fb")
        assertEquals("", id)
    }

    @Test
    fun `renderAdfToHtml delegates to converter`() {
        val adfConverter: AdfToHtmlConverter = mock()
        whenever(adfConverter.render("adf-json")).thenReturn("<p>x</p>")
        val adapter2 =
            TicketRefinementNativeJiraAdapter(
                jiraTicketHttp = jiraTicketHttp,
                objectMapper = mapper,
                adfToHtml = adfConverter,
                mdConverter = JiraMarkdownConverter(),
            )
        assertEquals("<p>x</p>", adapter2.renderAdfToHtml("adf-json"))
    }

    @Test
    fun `renderAdfToHtml returns null when input is null`() {
        val rendered = adapter.renderAdfToHtml(null)
        assertEquals(null, rendered)
    }

    @Test
    fun `adapter implements TicketRefinementJiraPort`() {
        val port: TicketRefinementJiraPort = adapter
        assertNotNull(port)
    }

    @Test
    fun `postComment uses commentVisibilityRole when non-blank`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"100"}"""),
        )
        val config = cfg(cloudMode = false).apply { commentVisibilityRole = "Developers" }
        adapter.postComment(config, "V-1", "body", "markdown", "fallback")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("role", body.path("visibility").path("type").asText())
        assertEquals("Developers", body.path("visibility").path("value").asText())
    }

    @Test
    fun `postComment trims commentVisibilityRole whitespace`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"101"}"""),
        )
        val config = cfg(cloudMode = false).apply { commentVisibilityRole = "  Padded  " }
        adapter.postComment(config, "V-2", "body", "markdown", "fallback")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("Padded", body.path("visibility").path("value").asText())
    }

    @Test
    fun `postComment does not include visibility when role is blank`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"102"}"""),
        )
        val config = cfg(cloudMode = false).apply { commentVisibilityRole = "" }
        adapter.postComment(config, "V-3", "body", "markdown", "fallback")
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertTrue(body.path("visibility").isMissingNode)
    }

    @Test
    fun `postComment rejects malformed ADF json and falls back to markdown`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"103"}"""),
        )
        adapter.postComment(
            cfg(cloudMode = true),
            "C-X",
            body = "{ not valid json",
            format = "adf",
            fallbackMarkdown = "**fallback**",
        )
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertEquals("doc", body.path("body").path("type").asText())
    }

    @Test
    fun `postComment returns empty id when response body has no id field`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"different":"field"}"""),
        )
        val id = adapter.postComment(cfg(cloudMode = false), "N-1", "body", "markdown", "fb")
        assertEquals("", id)
    }

    @Test
    fun `postComment falls back to wiki for server mode when adf rejected`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"104"}"""),
        )
        adapter.postComment(
            cfg(cloudMode = false),
            "S-X",
            body = """{"type":"not_doc"}""",
            format = "adf",
            fallbackMarkdown = "**fallback**",
        )
        val req = server.takeRequest()
        val body: JsonNode = mapper.readTree(req.body.readUtf8())
        assertNotNull(body.path("body").asText())
    }

    @Test
    fun `fetchSingle with empty subtask list caps at 0`() {
        val parentJson = issueJson(key = "PARENT-1", summary = "Parent", description = "desc")
        enqueueJson(parentJson)
        val tickets = adapter.fetchParentWithLinkedTickets(cfg(), "PARENT-1")
        assertEquals(1, tickets.size)
        assertTrue(tickets[0].subtasks.isEmpty())
        assertTrue(tickets[0].referencedTickets.isEmpty())
    }
}
