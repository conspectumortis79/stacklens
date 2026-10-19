package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.domain.model.CreatedJiraTicket
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareTicketCreationJiraAdapterFullTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val mapper = ObjectMapper()

    private class RecordingDelegate : TicketCreationJiraPort {
        var lastCreateResult: CreatedJiraTicket = CreatedJiraTicket(key = "NATIVE-1", id = "10001", url = "https://jira/NATIVE-1")
        var lastSubtaskResult: CreatedJiraTicket = CreatedJiraTicket(key = "NATIVE-2", id = "10002", url = "https://jira/NATIVE-2")

        override fun createTicket(
            config: JiraConfig,
            projectKey: String,
            summary: String,
            description: String,
            issueType: String,
            labels: List<String>,
            priority: String,
            customFields: Map<String, Any>,
        ): CreatedJiraTicket = lastCreateResult

        override fun requiredCreateFields(
            config: JiraConfig,
            projectKey: String,
            issueType: String,
        ): List<com.stacklens.domain.model.JiraCreateField> = emptyList()

        override fun createSubtask(
            config: JiraConfig,
            parentKey: String,
            projectKey: String,
            summary: String,
            description: String,
            issueType: String,
            labels: List<String>,
            priority: String,
        ): CreatedJiraTicket = lastSubtaskResult
    }

    private val delegate = RecordingDelegate()

    private val cfg =
        JiraConfig(id = 1L, name = "c", baseUrl = "https://jira.example.com", apiToken = "tok", projectKey = "PROJ", username = "u")
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeJira = true,
            jiraBaseUrl = "https://jira.example.com",
            jiraApiToken = "tok",
        )

    private fun adapter() =
        McpAwareTicketCreationJiraAdapter(
            mcpRepo = mcpRepo,
            gateway = gateway,
            objectMapper = mapper,
            delegate = delegate,
        )

    @Test
    fun `createTicket delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val result = adapter().createTicket(cfg, "PROJ", "s", "d", "Story", listOf("a"), "High")
        assertEquals("NATIVE-1", result.key)
    }

    @Test
    fun `createTicket uses MCP with full args`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"key":"PROJ-100","id":"10100"}""", isError = false),
        )
        val result =
            adapter().createTicket(
                cfg,
                "PROJ",
                "Summary",
                "Description",
                "Story",
                listOf("l1", "l2"),
                "High",
            )
        assertEquals("PROJ-100", result.key)
        assertEquals("10100", result.id)
        assertEquals("https://jira.example.com/browse/PROJ-100", result.url)
    }

    @Test
    fun `createTicket uses MCP with minimal args (no description, labels, priority)`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"key":"PROJ-200","id":"10200"}""", isError = false),
        )
        val result = adapter().createTicket(cfg, "PROJ", "s", "", "Bug", emptyList(), "")
        assertEquals("PROJ-200", result.key)
    }

    @Test
    fun `createTicket returns empty key when MCP response has no key field`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":"10300"}""", isError = false),
        )
        val result = adapter().createTicket(cfg, "PROJ", "s", "d", "Story", emptyList(), "")
        assertEquals("", result.key)
        assertEquals("10300", result.id)
        assertEquals("", result.url)
    }

    @Test
    fun `createTicket returns empty when MCP response is invalid JSON`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "not json", isError = false),
        )
        val result = adapter().createTicket(cfg, "PROJ", "s", "d", "Story", emptyList(), "")
        assertEquals("", result.key)
        assertEquals("", result.id)
    }

    @Test
    fun `createTicket falls back on MCP error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "fail", isError = true),
        )
        val result = adapter().createTicket(cfg, "PROJ", "s", "d", "Story", emptyList(), "")
        assertEquals("NATIVE-1", result.key)
    }

    @Test
    fun `createTicket falls back on MCP exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        val result = adapter().createTicket(cfg, "PROJ", "s", "d", "Story", emptyList(), "")
        assertEquals("NATIVE-1", result.key)
    }

    @Test
    fun `createSubtask delegates when MCP not active`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val result = adapter().createSubtask(cfg, "PARENT", "PROJ", "s", "d", "Sub", emptyList(), "")
        assertEquals("NATIVE-2", result.key)
    }

    @Test
    fun `createSubtask uses MCP with full args`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"key":"PROJ-300","id":"10300"}""", isError = false),
        )
        val result =
            adapter().createSubtask(
                cfg,
                "PROJ-100",
                "PROJ",
                "Sub summary",
                "Sub desc",
                "Sub-task",
                listOf("l1"),
                "Medium",
            )
        assertEquals("PROJ-300", result.key)
        assertEquals("10300", result.id)
        assertEquals("https://jira.example.com/browse/PROJ-300", result.url)
    }

    @Test
    fun `createSubtask uses MCP with minimal args`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"key":"PROJ-400","id":"10400"}""", isError = false),
        )
        val result = adapter().createSubtask(cfg, "PARENT", "PROJ", "s", "", "Sub", emptyList(), "")
        assertEquals("PROJ-400", result.key)
    }

    @Test
    fun `createSubtask falls back on MCP error`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = "fail", isError = true),
        )
        val result = adapter().createSubtask(cfg, "PARENT", "PROJ", "s", "d", "Sub", emptyList(), "")
        assertEquals("NATIVE-2", result.key)
    }

    @Test
    fun `createSubtask falls back on MCP exception`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenThrow(RuntimeException("boom"))
        val result = adapter().createSubtask(cfg, "PARENT", "PROJ", "s", "d", "Sub", emptyList(), "")
        assertEquals("NATIVE-2", result.key)
    }

    @Test
    fun `createSubtask returns empty url when key is empty`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"id":"10500"}""", isError = false),
        )
        val result = adapter().createSubtask(cfg, "PARENT", "PROJ", "s", "d", "Sub", emptyList(), "")
        assertEquals("", result.key)
        assertEquals("", result.url)
    }
}
