package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.TicketRefinementJiraPort
import com.stacklens.application.port.out.TicketRefinementTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class McpAwareTicketRefinementJiraAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<TicketRefinementJiraPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareTicketRefinementJiraAdapter

    private val cfg =
        JiraConfig(
            name = "c",
            baseUrl = "https://jira.example.com",
            apiToken = "t",
            projectKey = "PROJ",
            username = "u",
        )
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeJira = true,
            jiraBaseUrl = "https://jira.example.com",
            jiraApiToken = "t",
        )

    private val fallbackParent =
        TicketRefinementTicket(
            key = "P-1",
            summary = "fb",
            description = "D",
            url = "https://jira/browse/P-1",
        )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareTicketRefinementJiraAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `fetchParentWithLinkedTickets falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchParentWithLinkedTickets(cfg, "P-1")).thenReturn(listOf(fallbackParent))
        val result = adapter.fetchParentWithLinkedTickets(cfg, "P-1")
        assertEquals(listOf(fallbackParent), result)
    }

    @Test
    fun `fetchParentWithLinkedTickets routes via MCP and parses JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"key":"PROJ-1","fields":{"summary":"S","description":"D","linked":[]}}""",
            ),
        )
        val result = adapter.fetchParentWithLinkedTickets(cfg, "PROJ-1")
        assertEquals(1, result.size)
        assertEquals("PROJ-1", result[0].key)
        assertEquals("S", result[0].summary)
    }

    @Test
    fun `fetchParentWithLinkedTickets falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.fetchParentWithLinkedTickets(cfg, "P-1")).thenReturn(listOf(fallbackParent))
        val result = adapter.fetchParentWithLinkedTickets(cfg, "P-1")
        assertEquals(listOf(fallbackParent), result)
    }

    @Test
    fun `fetchParentWithLinkedTickets falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.fetchParentWithLinkedTickets(cfg, "P-1")).thenReturn(listOf(fallbackParent))
        val result = adapter.fetchParentWithLinkedTickets(cfg, "P-1")
        assertEquals(listOf(fallbackParent), result)
    }

    @Test
    fun `postComment falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.postComment(cfg, "P-1", "body", "adf", "fb-md")).thenReturn("https://delegate/c/1")
        val result = adapter.postComment(cfg, "P-1", "body", "adf", "fb-md")
        assertEquals("https://delegate/c/1", result)
    }

    @Test
    fun `postComment routes via MCP`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"10001","self":"https://jira/rest/api/2/comment/10001"}"""),
        )
        val result = adapter.postComment(cfg, "P-1", "body", "adf", "fb-md")
        assertTrue(result.contains("10001"))
    }

    @Test
    fun `postComment forwards the ADF body to MCP, not the markdown fallback`() {
        val adfBody =
            """{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]}"""
        val fallbackMd = "# h1\n\nnot the adf"
        val argsCaptor = argumentCaptor<Map<String, Any?>>()
        whenever(gateway.callTool(any(), any(), argsCaptor.capture())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"10002"}"""),
        )
        adapter.postComment(cfg, "P-1", adfBody, "adf", fallbackMd)
        val sentArgs = argsCaptor.lastValue
        assertEquals(
            adfBody,
            sentArgs["body"],
            "MCP must receive the ADF body, not the markdown fallback",
        )
        assertTrue(
            !sentArgs.containsValue(fallbackMd),
            "MCP must never receive the markdown fallback when format='adf'",
        )
    }

    @Test
    fun `postComment prepends the greeting to the ADF body sent to MCP`() {
        val adfBody =
            """{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"hello"}]}]}"""
        val argsCaptor = argumentCaptor<Map<String, Any?>>()
        whenever(gateway.callTool(any(), any(), argsCaptor.capture())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"10005"}"""),
        )
        adapter.postComment(cfg, "P-1", adfBody, "adf", "fb", "Hi", "Open Points", listOf("P1"))
        val sentBody = argsCaptor.lastValue["body"].toString()
        assertTrue(sentBody.contains("Hi"), "MCP body must contain the greeting")
        assertTrue(sentBody.contains("Open Points"), "MCP body must contain the open points label")
        assertTrue(sentBody.contains("P1"), "MCP body must contain the open point")
    }

    @Test
    fun `postComment forwards the wiki body to MCP for wiki format`() {
        val wikiBody = "h2. header\n\n* item"
        val fallbackMd = "# h1"
        val argsCaptor = argumentCaptor<Map<String, Any?>>()
        whenever(gateway.callTool(any(), any(), argsCaptor.capture())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"10003"}"""),
        )
        adapter.postComment(cfg, "P-2", wikiBody, "wiki", fallbackMd)
        val sentArgs = argsCaptor.lastValue
        assertEquals(wikiBody, sentArgs["body"])
        assertTrue(!sentArgs.containsValue(fallbackMd))
    }

    @Test
    fun `postComment forwards the markdown body to MCP for markdown format`() {
        val mdBody = "**bold** step"
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"id":"10004"}"""),
        )
        adapter.postComment(cfg, "P-3", mdBody, "markdown", "ignored")
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(gateway).callTool(any(), any(), captor.capture())
        assertEquals(mdBody, captor.lastValue["body"])
    }

    @Test
    fun `postComment falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.postComment(cfg, "P-1", "body", "adf", "fb-md")).thenReturn("https://delegate/c/1")
        val result = adapter.postComment(cfg, "P-1", "body", "adf", "fb-md")
        assertEquals("https://delegate/c/1", result)
    }

    @Test
    fun `postComment falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.postComment(cfg, "P-1", "body", "adf", "fb-md")).thenReturn("https://delegate/c/1")
        val result = adapter.postComment(cfg, "P-1", "body", "adf", "fb-md")
        assertEquals("https://delegate/c/1", result)
    }

    @Test
    fun `renderAdfToHtml delegates`() {
        whenever(delegate.renderAdfToHtml("<p/>")).thenReturn("<p></p>")
        assertEquals("<p></p>", adapter.renderAdfToHtml("<p/>"))
    }

    private fun flattenAdf(node: JsonNode): String {
        val func = McpAwareTicketRefinementJiraAdapter::class.declaredFunctions.first { it.name == "flattenAdf" }
        func.javaMethod!!.isAccessible = true
        return func.call(adapter, node) as String
    }

    @Test
    fun `flattenAdf returns text directly when node is textual`() {
        val n = mapper.readTree("\"hello world\"")
        assertEquals("hello world", flattenAdf(n))
    }

    @Test
    fun `flattenAdf returns empty string for primitive non-text node`() {
        val n = mapper.readTree("42")
        assertEquals("", flattenAdf(n))
    }

    @Test
    fun `flattenAdf flattens container node with text children`() {
        val n =
            mapper.readTree(
                """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"hello "},{"type":"text","text":"world"}]}]}""",
            )
        val out = flattenAdf(n)
        assertTrue(out.contains("hello"))
        assertTrue(out.contains("world"))
    }

    @Test
    fun `flattenAdf returns empty for empty container node`() {
        val n = mapper.readTree("""{"type":"doc","content":[]}""")
        val out = flattenAdf(n)
        assertTrue(out.isNotEmpty())
        assertTrue(out.contains("doc"))
    }

    @Test
    fun `flattenAdf handles array node`() {
        val n = mapper.readTree("""["a", "b", "c"]""")
        val out = flattenAdf(n)
        assertTrue(out.contains("a"))
        assertTrue(out.contains("b"))
        assertTrue(out.contains("c"))
    }
}
