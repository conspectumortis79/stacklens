package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.JiraCreateField
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareTicketCreationJiraAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<TicketCreationJiraPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareTicketCreationJiraAdapter

    private val cfg = JiraConfig(name = "c", baseUrl = "https://jira", username = "u", apiToken = "t")
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeJira = true,
            jiraBaseUrl = "https://jira",
            jiraApiToken = "t",
        )

    private val fallbackTicket =
        CreatedJiraTicket(
            key = "PROJ-1",
            id = "10001",
            url = "https://jira/browse/PROJ-1",
        )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareTicketCreationJiraAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `createTicket falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.createTicket(cfg, "PROJ", "S", "D", "Bug", listOf("a"), "High")).thenReturn(fallbackTicket)
        val result = adapter.createTicket(cfg, "PROJ", "S", "D", "Bug", listOf("a"), "High")
        assertEquals(fallbackTicket, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `createTicket routes via MCP with full args including description labels priority`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"key":"PROJ-42","id":"42","self":"https://jira/rest/api/2/issue/42"}"""),
        )
        val result = adapter.createTicket(cfg, "PROJ", "Summary", "Description", "Bug", listOf("a", "b"), "High")
        assertEquals("PROJ-42", result.key)
        assertEquals("42", result.id)
    }

    @Test
    fun `createTicket omits empty description labels priority from MCP args`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"key":"P","id":"1"}"""),
        )
        val result = adapter.createTicket(cfg, "PROJ", "S", "", "Task", emptyList(), "")
        assertEquals("P", result.key)
    }

    @Test
    fun `createTicket falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = true, text = "upstream failed"),
        )
        whenever(delegate.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "")).thenReturn(fallbackTicket)
        val result = adapter.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "")
        assertEquals(fallbackTicket, result)
    }

    @Test
    fun `createTicket with custom fields bypasses MCP because the tool cannot set them`() {
        val customFields = mapOf<String, Any>("customfield_10120" to "Tax")
        whenever(delegate.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "", customFields)).thenReturn(fallbackTicket)

        val result = adapter.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "", customFields)

        assertEquals(fallbackTicket, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `requiredCreateFields delegates to the native adapter`() {
        val fields = listOf(JiraCreateField("customfield_1", "Dept", multiple = false, allowedValues = listOf("A")))
        whenever(delegate.requiredCreateFields(cfg, "PROJ", "Bug")).thenReturn(fields)

        assertEquals(fields, adapter.requiredCreateFields(cfg, "PROJ", "Bug"))
    }

    @Test
    fun `createTicket calls native fallback only once when it fails after an MCP error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = true, text = "unknown tool: jira_create_issue"),
        )
        whenever(delegate.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), ""))
            .doThrow(IllegalStateException("400 Bad Request"))

        assertThrows(IllegalStateException::class.java) {
            adapter.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "")
        }
        verify(delegate, times(1)).createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "")
    }

    @Test
    fun `createSubtask calls native fallback only once when it fails after an MCP error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = true, text = "unknown tool: jira_create_issue"),
        )
        whenever(delegate.createSubtask(cfg, "PROJ-1", "PROJ", "S", "D", "Subtask", emptyList(), ""))
            .doThrow(IllegalStateException("400 Bad Request"))

        assertThrows(IllegalStateException::class.java) {
            adapter.createSubtask(cfg, "PROJ-1", "PROJ", "S", "D", "Subtask", emptyList(), "")
        }
        verify(delegate, times(1)).createSubtask(cfg, "PROJ-1", "PROJ", "S", "D", "Subtask", emptyList(), "")
    }

    @Test
    fun `createTicket returns empty ticket when MCP returns invalid JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        val result = adapter.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "")
        assertEquals("", result.key)
        assertEquals("", result.id)
        verify(delegate, never()).createTicket(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createTicket falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "")).thenReturn(fallbackTicket)
        val result = adapter.createTicket(cfg, "PROJ", "S", "D", "Bug", emptyList(), "")
        assertEquals(fallbackTicket, result)
    }

    @Test
    fun `createSubtask falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.createSubtask(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(fallbackTicket)
        val result = adapter.createSubtask(cfg, "PROJ-1", "PROJ", "Sub", "Desc", "Subtask", listOf("a"), "Minor")
        assertEquals(fallbackTicket, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `createSubtask routes via MCP with parent_key and subtask issue type`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"key":"PROJ-42","id":"42","self":"https://jira/rest/api/2/issue/42"}"""),
        )
        val result = adapter.createSubtask(cfg, "PROJ-1", "PROJ", "Subtitle", "Subbody", "Subtask", listOf("a", "b"), "High")
        assertEquals("PROJ-42", result.key)
        assertEquals("42", result.id)
    }

    @Test
    fun `createSubtask omits empty description labels priority from MCP args`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"key":"P","id":"1"}"""),
        )
        val result = adapter.createSubtask(cfg, "PROJ-1", "PROJ", "S", "", "Subtask", emptyList(), "")
        assertEquals("P", result.key)
    }

    @Test
    fun `createSubtask falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = true, text = "upstream failed"),
        )
        whenever(delegate.createSubtask(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(fallbackTicket)
        val result = adapter.createSubtask(cfg, "PROJ-1", "PROJ", "S", "D", "Subtask", emptyList(), "")
        assertEquals(fallbackTicket, result)
    }

    @Test
    fun `createSubtask returns empty ticket when MCP returns invalid JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        val result = adapter.createSubtask(cfg, "PROJ-1", "PROJ", "S", "D", "Subtask", emptyList(), "")
        assertEquals("", result.key)
        assertEquals("", result.id)
        verify(delegate, never()).createSubtask(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createSubtask falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.createSubtask(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(fallbackTicket)
        val result = adapter.createSubtask(cfg, "PROJ-1", "PROJ", "S", "D", "Subtask", emptyList(), "")
        assertEquals(fallbackTicket, result)
    }
}
