package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareAdaptersBranchTest {
    @Test
    fun `McpAwareJiraSearchAdapter is constructible`() {
        val adp =
            McpAwareJiraSearchAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.search.JiraSearchPort>(),
            )
        assertNotNull(adp)
    }

    @Test
    fun `McpAwareGitlabMrAdapter is constructible`() {
        val adp =
            McpAwareGitlabMrAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.mr.GitlabMrPort>(),
            )
        assertNotNull(adp)
    }

    @Test
    fun `McpAwareTicketCreationJiraAdapter is constructible`() {
        val adp =
            McpAwareTicketCreationJiraAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.TicketCreationJiraPort>(),
            )
        assertNotNull(adp)
    }
}
