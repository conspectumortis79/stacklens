package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.ResolvedSprint
import com.stacklens.application.port.out.SprintReviewAdfDocument
import com.stacklens.application.port.out.SprintReviewJiraPort
import com.stacklens.application.port.out.SprintReviewTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareSprintReviewJiraAdapterFetchTest {
    private val mcpRepo: McpServerConfigPort = mock()
    private val gateway: McpGatewayPort = mock()
    private val mapper = ObjectMapper()
    private val delegate: SprintReviewJiraPort = mock()
    private lateinit var adapter: McpAwareSprintReviewJiraAdapter

    @BeforeEach
    fun setUp() {
        adapter = McpAwareSprintReviewJiraAdapter(mcpRepo, gateway, mapper, delegate)
    }

    @Test
    fun `resolveSprintByName always falls back to the native adapter`() {
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        val expected = ResolvedSprint(id = 7L, name = "Sprint 1", state = "active", boardId = 1, boardName = null, url = "url")
        whenever(delegate.resolveSprintByName(cfg, "Sprint 1")).thenReturn(expected)
        val result = adapter.resolveSprintByName(cfg, "Sprint 1")
        assertEquals(expected, result)
    }

    @Test
    fun `fetchSprintTickets falls back to the native adapter when no MCP is configured`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        val tickets = listOf(issue(key = "P-1"))
        whenever(delegate.fetchSprintTickets(cfg, 7L)).thenReturn(tickets)
        val result = adapter.fetchSprintTickets(cfg, 7L)
        assertEquals(tickets, result)
    }

    @Test
    fun `fetchSprintTickets falls back to the native adapter when MCP is not Jira-ready`() {
        val mcp = mcpConfig(baseUrl = "")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(delegate.fetchSprintTickets(cfg, 7L)).thenReturn(emptyList())
        adapter.fetchSprintTickets(cfg, 7L)
        verify(delegate).fetchSprintTickets(cfg, 7L)
    }

    @Test
    fun `fetchSprintTickets falls back to the native adapter when the tenant guard rejects the call`() {
        val mcp = mcpConfig(baseUrl = "https://other.atlassian.net")
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(delegate.fetchSprintTickets(cfg, 7L)).thenReturn(emptyList())
        adapter.fetchSprintTickets(cfg, 7L)
        verify(delegate).fetchSprintTickets(cfg, 7L)
    }

    @Test
    fun `fetchSprintTickets falls back when MCP returns an error response`() {
        val mcp = mcpConfig(baseUrl = "https://demo.atlassian.net")
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(eq(mcp), eq("jira_list_sprint_issues"), any())).thenReturn(
            McpToolResult(text = "boom", isError = true),
        )
        whenever(delegate.fetchSprintTickets(cfg, 7L)).thenReturn(emptyList())
        adapter.fetchSprintTickets(cfg, 7L)
        verify(delegate).fetchSprintTickets(cfg, 7L)
    }

    @Test
    fun `fetchSprintTickets falls back when MCP returns invalid JSON`() {
        val mcp = mcpConfig(baseUrl = "https://demo.atlassian.net")
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(eq(mcp), eq("jira_list_sprint_issues"), any())).thenReturn(
            McpToolResult(text = "not json", isError = false),
        )
        whenever(delegate.fetchSprintTickets(cfg, 7L)).thenReturn(emptyList())
        adapter.fetchSprintTickets(cfg, 7L)
        verify(delegate).fetchSprintTickets(cfg, 7L)
    }

    @Test
    fun `fetchSprintTickets walks paginated MCP pages and excludes sub-tasks`() {
        val mcp = mcpConfig(baseUrl = "https://demo.atlassian.net")
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(eq(mcp), eq("jira_list_sprint_issues"), any()))
            .thenReturn(
                McpToolResult(
                    text =
                        """
                        {
                          "total":1,
                          "issues":[
                            {"key":"P-1","fields":{
                              "summary":"x","description":{"type":"doc"},
                              "issuetype":{"name":"Story","subtask":false},
                              "status":{"name":"Done"},
                              "assignee":{"displayName":"Alice"},
                              "priority":{"name":"High"},
                              "labels":["x",""],
                              "customfield_10016":3
                            }},
                            {"key":"P-2","fields":{
                              "summary":"sub","description":null,
                              "issuetype":{"name":"Sub-task","subtask":true},
                              "status":{"name":"Done"},
                              "labels":[],
                              "customfield_10016":1
                            }}
                          ]
                        }
                        """.trimIndent(),
                    isError = false,
                ),
            ).thenReturn(
                McpToolResult(
                    text = """{"total":1,"issues":[]}""",
                    isError = false,
                ),
            )
        val result = adapter.fetchSprintTickets(cfg, 7L)
        assertEquals(1, result.size)
        assertEquals("P-1", result[0].key)
        assertEquals("Story", result[0].issueType)
        assertEquals("Done", result[0].status)
        assertEquals("Alice", result[0].assignee)
        assertEquals("High", result[0].priority)
        assertEquals(listOf("x"), result[0].labels)
        assertEquals(3.0, result[0].storyPoints)
        assertTrue(result.none { it.key == "P-2" })
    }

    @Test
    fun `fetchSprintTickets also walks pages when total is unspecified`() {
        val mcp = mcpConfig(baseUrl = "https://demo.atlassian.net")
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(eq(mcp), eq("jira_list_sprint_issues"), any()))
            .thenReturn(
                McpToolResult(
                    text = """{"total":2,"issues":[{"key":"A","fields":{"summary":"a","description":"","issuetype":{"name":"x","subtask":false},"status":{"name":"S"},"labels":[]}}]}""",
                    isError = false,
                ),
            ).thenReturn(
                McpToolResult(
                    text = """{"total":2,"issues":[]}""",
                    isError = false,
                ),
            )
        val result = adapter.fetchSprintTickets(cfg, 7L)
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchSprintTickets uses the configured base URL to build ticket links`() {
        val mcp = mcpConfig(baseUrl = "https://demo.atlassian.net")
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net/", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        whenever(gateway.callTool(eq(mcp), eq("jira_list_sprint_issues"), any()))
            .thenReturn(
                McpToolResult(
                    text = """{"total":1,"issues":[
                      {"key":"KEY-1","fields":{"summary":"x","description":"",
                       "issuetype":{"name":"Story","subtask":false},"status":{"name":"Done"},
                       "labels":[]}}
                    ]}""",
                    isError = false,
                ),
            ).thenReturn(
                McpToolResult(text = """{"total":1,"issues":[]}""", isError = false),
            )
        val result = adapter.fetchSprintTickets(cfg, 7L)
        assertEquals(1, result.size)
        assertEquals("https://demo.atlassian.net/browse/KEY-1", result[0].url)
    }

    @Test
    fun `appendAdfToDescription falls back to native adapter regardless of MCP availability`() {
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        val mcp = mcpConfig(baseUrl = "https://demo.atlassian.net")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val adfDoc = SprintReviewAdfDocument("""{"version":1}""")
        whenever(delegate.appendAdfToDescription(cfg, "PROJ-1", adfDoc)).thenReturn("updated")
        val result = adapter.appendAdfToDescription(cfg, "PROJ-1", adfDoc)
        assertEquals("updated", result)
        verify(delegate).appendAdfToDescription(cfg, "PROJ-1", adfDoc)
    }

    @Test
    fun `appendAdfToDescription falls back when no MCP is configured`() {
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val adfDoc = SprintReviewAdfDocument("""{"version":1}""")
        whenever(delegate.appendAdfToDescription(cfg, "PROJ-1", adfDoc)).thenReturn("updated")
        val result = adapter.appendAdfToDescription(cfg, "PROJ-1", adfDoc)
        assertEquals("updated", result)
    }

    @Test
    fun `appendAdfToDescription falls back when the tenant host does not match`() {
        val mcp = mcpConfig(baseUrl = "https://other.atlassian.net")
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcp)
        val adfDoc = SprintReviewAdfDocument("""{"version":1}""")
        whenever(delegate.appendAdfToDescription(cfg, "PROJ-1", adfDoc)).thenReturn("updated")
        val result = adapter.appendAdfToDescription(cfg, "PROJ-1", adfDoc)
        assertEquals("updated", result)
    }

    @Test
    fun `parseStoryPointsWithFallbacks reads direct numeric customfield`() {
        val json =
            """
            {"fields":{"customfield_10016":3.0}}
            """.trimIndent()
        val node = mapper.readTree(json).path("fields")
        val result =
            invokePrivate("parseStoryPointsWithFallbacks", node)
        assertEquals(3.0, result)
    }

    @Test
    fun `parseStoryPointsWithFallbacks reads from a non-default customfield`() {
        val json =
            """
            {"fields":{"customfield_10026":42.0}}
            """.trimIndent()
        val node = mapper.readTree(json).path("fields")
        val result = invokePrivate("parseStoryPointsWithFallbacks", node)
        assertEquals(42.0, result)
    }

    @Test
    fun `parseStoryPointsWithFallbacks unwraps object-wrapped values`() {
        val json =
            """
            {"fields":{"customfield_10028":{"value":7.5}}}
            """.trimIndent()
        val node = mapper.readTree(json).path("fields")
        val result = invokePrivate("parseStoryPointsWithFallbacks", node)
        assertEquals(7.5, result)
    }

    @Test
    fun `parseStoryPointsWithFallbacks parses German-style decimal string`() {
        val json =
            """
            {"fields":{"customfield_10002":"1,5"}}
            """.trimIndent()
        val node = mapper.readTree(json).path("fields")
        val result = invokePrivate("parseStoryPointsWithFallbacks", node)
        assertEquals(1.5, result)
    }

    @Test
    fun `parseStoryPointsWithFallbacks returns null when no field has a value`() {
        val json =
            """
            {"fields":{"summary":"x"}}
            """.trimIndent()
        val node = mapper.readTree(json).path("fields")
        val result = invokePrivate("parseStoryPointsWithFallbacks", node)
        assertEquals(null, result)
    }

    @Test
    fun `extractNumericCustomFields filters numeric values only`() {
        val json =
            """
            {"fields":{
              "customfield_10010":5.0,
              "customfield_10011":"2.5",
              "customfield_10012":"not a number",
              "customfield_10013":{"value":42.0},
              "summary":"x"
            }}
            """.trimIndent()
        val node = mapper.readTree(json).path("fields")

        @Suppress("UNCHECKED_CAST")
        val result = invokePrivate("extractNumericCustomFields", node) as List<String>
        assertTrue(result.contains("customfield_10010: 5.0"))
        assertTrue(result.contains("customfield_10011: 2.5"))
        assertTrue(result.contains("customfield_10013: 42.0"))
        assertTrue(result.none { it.startsWith("customfield_10012") })
        assertTrue(result.none { it.startsWith("summary") })
    }

    @Test
    fun `mapIssue renders a complete ticket with status fields coerced from null`() {
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        val json =
            """
            {
              "key":"KEY-1",
              "fields":{
                "summary":"Hello",
                "description":"plain text",
                "issuetype":{"name":"Story","subtask":false},
                "status":{"name":"Done"},
                "assignee":{"displayName":""},
                "priority":{"name":""},
                "labels":[]
              }
            }
            """.trimIndent()
        val node = mapper.readTree(json)
        val ticket =
            invokePrivate(
                "mapIssue",
                node,
                cfg,
            ) as com.stacklens.application.port.out.SprintReviewTicket
        assertEquals("KEY-1", ticket.key)
        assertEquals("Hello", ticket.summary)
        assertEquals("plain text", ticket.description)
        assertEquals("Story", ticket.issueType)
        assertEquals("Done", ticket.status)
        assertEquals(null, ticket.assignee)
        assertEquals(null, ticket.priority)
        assertEquals("https://demo.atlassian.net/browse/KEY-1", ticket.url)
    }

    @Test
    fun `mapIssue falls back to JSON string representation for ADF descriptions`() {
        val cfg = JiraConfig(name = "demo", baseUrl = "https://demo.atlassian.net", apiToken = "t")
        val json =
            """
            {
              "key":"KEY-2",
              "fields":{
                "summary":"s",
                "description":{"type":"doc","version":1,"content":[]},
                "issuetype":{"name":"Story","subtask":false},
                "status":{"name":"Open"},
                "labels":[]
              }
            }
            """.trimIndent()
        val node = mapper.readTree(json)
        val ticket =
            invokePrivate("mapIssue", node, cfg)
                as com.stacklens.application.port.out.SprintReviewTicket
        assertNotNull(ticket.description)
    }

    private fun issue(key: String): SprintReviewTicket =
        SprintReviewTicket(
            key = key,
            summary = "Test",
            description = "",
            url = "url",
            issueType = "Story",
            status = "Open",
            assignee = null,
            priority = null,
            labels = emptyList(),
            storyPoints = null,
            numericCustomFields = emptyList(),
        )

    private fun mcpConfig(baseUrl: String): McpServerConfig {
        val mcp = mock<McpServerConfig>()
        whenever(mcp.id).thenReturn(1L)
        whenever(mcp.name).thenReturn("demo")
        whenever(mcp.baseUrl).thenReturn("https://mcp.example.com")
        whenever(mcp.jiraBaseUrl).thenReturn(baseUrl)
        whenever(mcp.jiraApiToken).thenReturn("t")
        whenever(mcp.active).thenReturn(true)
        whenever(mcp.routeJira).thenReturn(true)
        return mcp
    }

    private fun invokePrivate(
        name: String,
        vararg args: Any?,
    ): Any? {
        val method =
            McpAwareSprintReviewJiraAdapter::class.java.declaredMethods
                .first { it.name == name }
        method.isAccessible = true
        return try {
            method.invoke(adapter, *args)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
