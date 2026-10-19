package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareJiraSprintFetchAdapterBranchTest {
    @Test
    fun `McpAwareJiraSprintFetchAdapter is constructible`() {
        val adp =
            McpAwareJiraSprintFetchAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.jira.JiraSprintFetchPort>(),
            )
        assertNotNull(adp)
    }
}
