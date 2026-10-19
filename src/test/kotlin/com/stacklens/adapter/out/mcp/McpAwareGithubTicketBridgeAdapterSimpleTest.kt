package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.GithubTicketBridgePort
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareGithubTicketBridgeAdapterSimpleTest {
    @Test
    fun `McpAwareGithubTicketBridgeAdapter is constructible`() {
        val adp =
            McpAwareGithubTicketBridgeAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<GithubTicketBridgePort>(),
            )
        assertNotNull(adp)
    }
}
