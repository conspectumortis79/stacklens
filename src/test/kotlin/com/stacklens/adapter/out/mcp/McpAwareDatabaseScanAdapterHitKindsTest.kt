package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.testsupport.McpRoutingFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class McpAwareDatabaseScanAdapterHitKindsTest {
    private val gateway: McpGatewayPort = mock()
    private val delegate: DatabaseScanPort = mock()
    private val adapter =
        McpAwareDatabaseScanAdapter(
            mcpRepo = McpRoutingFixtures.repoWith(McpRoutingFixtures.routed()),
            gateway = gateway,
            objectMapper = ObjectMapper(),
            delegate = delegate,
        )

    private fun config() =
        DatabaseConfig(
            name = "db",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "app",
            username = "u",
            password = "p",
            active = true,
        )

    @Test
    fun `every supported hit kind is mapped and unknown kinds are dropped`() {
        whenever(gateway.callTool(any(), any(), any()))
            .thenReturn(
                McpToolResult(
                    text =
                        """
                        {"databaseName":"app","hits":[
                          {"kind":"table","tableName":"users"},
                          {"kind":"column","tableName":"users","columnName":"mail"},
                          {"kind":"view","tableName":"active_users"},
                          {"kind":"routine","tableName":"x"}
                        ]}
                        """.trimIndent(),
                ),
            )

        val result = adapter.scanDatabase(config(), "mail", 1L)

        assertEquals(3, result.results.size)
        assertEquals(listOf(ResultType.TABLE, ResultType.COLUMN, ResultType.VIEW), result.results.map { it.resultType })
        assertEquals("app", result.results.first().databaseName)
    }

    @Test
    fun `a missing route flag uses the delegate`() {
        val offline =
            McpAwareDatabaseScanAdapter(
                mcpRepo = McpRoutingFixtures.repoWith(McpRoutingFixtures.routed(routeDatabase = false)),
                gateway = gateway,
                objectMapper = ObjectMapper(),
                delegate = delegate,
            )
        whenever(delegate.scanDatabase(any(), any(), any())).thenReturn(
            com.stacklens.application.port.out.database
                .DatabaseScanResult(emptyList()),
        )

        assertEquals(0, offline.scanDatabase(config(), "mail", 1L).results.size)
    }
}
