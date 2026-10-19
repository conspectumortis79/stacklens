package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareAdditionalAdaptersBranchTest {
    @Test
    fun `McpAwareJiraTicketFetchAdapter is constructible`() {
        val adp =
            com.stacklens.adapter.out.mcp.McpAwareJiraTicketFetchAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.jira.JiraTicketFetchPort>(),
            )
        assertNotNull(adp)
    }

    @Test
    fun `McpAwareJiraSprintFetchAdapter is constructible`() {
        val adp =
            com.stacklens.adapter.out.mcp.McpAwareJiraSprintFetchAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.jira.JiraSprintFetchPort>(),
            )
        assertNotNull(adp)
    }
}
