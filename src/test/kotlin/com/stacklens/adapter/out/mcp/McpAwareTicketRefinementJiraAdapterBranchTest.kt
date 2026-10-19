package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.TicketRefinementJiraPort
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareTicketRefinementJiraAdapterBranchTest {
    @Test
    fun `McpAwareTicketRefinementJiraAdapter is constructible`() {
        val adp =
            McpAwareTicketRefinementJiraAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<TicketRefinementJiraPort>(),
            )
        assertNotNull(adp)
    }
}
