package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.domain.model.JiraConfig
import com.stacklens.domain.model.McpServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareJiraSprintFetchAdapterPaginationTest {
    private val objectMapper = ObjectMapper()

    private fun newAdapter(gateway: McpGatewayPort): McpAwareJiraSprintFetchAdapter =
        McpAwareJiraSprintFetchAdapter(
            mcpRepo =
                mock<McpServerConfigPort>().also {
                    whenever(it.findFirstActiveMcpServerConfig()).thenReturn(McpServerConfig(name = "mcp"))
                },
            gateway = gateway,
            objectMapper = objectMapper,
            delegate = mock<JiraSprintFetchPort>(),
        )

    @Test
    fun `fetchSprintIssues returns empty when issues is not an array`() {
        val gateway = mock<McpGatewayPort>()
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"total":0}""", isError = false),
        )
        val result =
            newAdapter(gateway).fetchSprintIssues(
                config = mockConfig(),
                sprintUrl = "https://jira.example.com/sprints/1",
            )
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchSprintIssues returns empty when issues array is empty`() {
        val gateway = mock<McpGatewayPort>()
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(text = """{"total":0,"issues":[]}""", isError = false),
        )
        val result =
            newAdapter(gateway).fetchSprintIssues(
                config = mockConfig(),
                sprintUrl = "https://jira.example.com/sprints/1",
            )
        assertEquals(0, result.size)
    }

    @Test
    fun `fetchSprintIssues breaks when issues count is less than pageSize`() {
        val gateway = mock<McpGatewayPort>()
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                text = """{"total":1,"issues":[{"key":"P-1","fields":{"summary":"a"}}]}""",
                isError = false,
            ),
        )
        val result =
            newAdapter(gateway).fetchSprintIssues(
                config = mockConfig(),
                sprintUrl = "https://jira.example.com/sprints/1",
            )
        assertTrue(result.size <= 1)
    }

    private fun mockConfig() =
        JiraConfig(
            name = "j",
            baseUrl = "https://jira.example.com",
            username = "u",
            apiToken = "t",
            active = true,
        )
}
