package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.testsupport.HashCollisions
import com.stacklens.testsupport.McpRoutingFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareDatabaseScanAdapterKindCollisionCoverageTest {
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<DatabaseScanPort>()
    private val adapter =
        McpAwareDatabaseScanAdapter(
            mcpRepo = McpRoutingFixtures.repoWith(McpRoutingFixtures.routed()),
            gateway = gateway,
            objectMapper = ObjectMapper(),
            delegate = delegate,
        )

    private val cfg =
        DatabaseConfig(
            name = "db",
            dbType = DatabaseType.POSTGRESQL,
            host = "h",
            port = 5432,
            databaseName = "mydb",
            username = "u",
            password = "p",
        )

    @Test
    fun `a kind that only collides with a known result type is dropped`() {
        val kinds = listOf("table", "column", "view").map { HashCollisions.sharingHashCodeOf(it) } + "unknown"
        val hits = kinds.joinToString(",") { """{"kind":"$it","tableName":"t"}""" }
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(McpToolResult(text = """{"databaseName":"mydb","hits":[$hits]}"""))

        assertEquals(0, adapter.scanDatabase(cfg, "mail", 9L).results.size)
    }
}
