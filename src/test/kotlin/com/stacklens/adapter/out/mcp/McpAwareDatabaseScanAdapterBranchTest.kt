package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareDatabaseScanAdapterBranchTest {
    @Test
    fun `McpAwareDatabaseScanAdapter is constructible`() {
        val adp =
            McpAwareDatabaseScanAdapter(
                mcpRepo = mock<com.stacklens.application.port.out.McpServerConfigPort>(),
                gateway = mock<com.stacklens.application.port.out.McpGatewayPort>(),
                objectMapper = ObjectMapper(),
                delegate = mock<com.stacklens.application.port.out.database.DatabaseScanPort>(),
            )
        assertNotNull(adp)
    }
}
