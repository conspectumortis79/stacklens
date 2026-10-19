package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.McpToolResult
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.database.DatabaseScanResult
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.SearchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class McpAwareDatabaseScanAdapterTest {
    private val mcpRepo = mock<McpServerConfigPort>()
    private val gateway = mock<McpGatewayPort>()
    private val delegate = mock<DatabaseScanPort>()
    private val mapper = ObjectMapper()
    private lateinit var adapter: McpAwareDatabaseScanAdapter

    private val cfg =
        DatabaseConfig(
            name = "db",
            dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
            host = "h",
            port = 5432,
            databaseName = "mydb",
            username = "u",
            password = "p",
        )
    private val mcpCfg =
        McpServerConfig(
            active = true,
            routeDatabase = true,
        )

    @BeforeEach
    fun setUp() {
        adapter = McpAwareDatabaseScanAdapter(mcpRepo, gateway, mapper, delegate)
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(mcpCfg)
    }

    @Test
    fun `scanDatabase falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        val fallbackResults = listOf(SearchResult(sessionId = 1, keyword = "kw", sourceType = SourceType.DATABASE))
        val fallback = DatabaseScanResult(fallbackResults)
        whenever(delegate.scanDatabase(cfg, "kw", 1L)).thenReturn(fallback)
        val result = adapter.scanDatabase(cfg, "kw", 1L)
        assertEquals(fallback, result)
        verify(gateway, never()).callTool(any(), any(), any())
    }

    @Test
    fun `scanDatabase routes via MCP and parses hits`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"databaseName":"mydb","hits":[
                    {"kind":"column","tableName":"users","columnName":"email","dataType":"text","snippet":"alice@example"}
                ]}""",
            ),
        )
        val result = adapter.scanDatabase(cfg, "email", 1L)
        assertEquals(1, result.results.size)
        assertEquals("users", result.results[0].tableName)
    }

    @Test
    fun `scanDatabase parses table kind hit`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"databaseName":"mydb","hits":[
                    {"kind":"table","tableName":"orders","schemaName":"public","snippet":"orders table"}
                ]}""",
            ),
        )
        val result = adapter.scanDatabase(cfg, "orders", 1L)
        assertEquals(1, result.results.size)
        assertEquals("orders", result.results[0].tableName)
        assertEquals(com.stacklens.domain.enums.ResultType.TABLE, result.results[0].resultType)
    }

    @Test
    fun `scanDatabase parses view kind hit`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"databaseName":"mydb","hits":[
                    {"kind":"view","tableName":"user_view","schemaName":"public","snippet":"view def"}
                ]}""",
            ),
        )
        val result = adapter.scanDatabase(cfg, "user_view", 1L)
        assertEquals(1, result.results.size)
        assertEquals(com.stacklens.domain.enums.ResultType.VIEW, result.results[0].resultType)
    }

    @Test
    fun `scanDatabase skips hits with unknown kind`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"databaseName":"mydb","hits":[
                    {"kind":"unknown_kind","tableName":"x"}
                ]}""",
            ),
        )
        val result = adapter.scanDatabase(cfg, "x", 1L)
        assertEquals(0, result.results.size)
    }

    @Test
    fun `scanDatabase falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        val fallback = DatabaseScanResult(emptyList())
        whenever(delegate.scanDatabase(cfg, "kw", 1L)).thenReturn(fallback)
        val result = adapter.scanDatabase(cfg, "kw", 1L)
        assertEquals(fallback, result)
    }

    @Test
    fun `scanDatabase falls back when MCP returns invalid JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        val fallback = DatabaseScanResult(emptyList())
        whenever(delegate.scanDatabase(cfg, "kw", 1L)).thenReturn(fallback)
        val result = adapter.scanDatabase(cfg, "kw", 1L)
        assertEquals(fallback, result)
    }

    @Test
    fun `scanDatabase falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        val fallback = DatabaseScanResult(emptyList())
        whenever(delegate.scanDatabase(cfg, "kw", 1L)).thenReturn(fallback)
        val result = adapter.scanDatabase(cfg, "kw", 1L)
        assertEquals(fallback, result)
    }

    @Test
    fun `scanDatabase handles empty hits array`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = """{"databaseName":"mydb","hits":[]}"""),
        )
        val result = adapter.scanDatabase(cfg, "kw", 1L)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun `extractCrossRefTerms delegates`() {
        whenever(delegate.extractCrossRefTerms(any())).thenReturn(listOf("t1", "t2"))
        val r = SearchResult(sessionId = 1, keyword = "kw", sourceType = SourceType.DATABASE)
        val result = adapter.extractCrossRefTerms(r)
        assertEquals(listOf("t1", "t2"), result)
    }

    @Test
    fun `fetchSchemaSummary falls back when no MCP routing`() {
        whenever(mcpRepo.findFirstActiveMcpServerConfig()).thenReturn(null)
        whenever(delegate.fetchSchemaSummary(cfg)).thenReturn("schema-fb")
        assertEquals("schema-fb", adapter.fetchSchemaSummary(cfg))
    }

    @Test
    fun `fetchSchemaSummary routes via MCP and returns summary`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(
                isError = false,
                text = """{"columns":[
                    {"tableName":"users","columnName":"id","dataType":"int","nullable":"NO"},
                    {"tableName":"users","columnName":"email","dataType":"text","nullable":"YES"}
                ]}""",
            ),
        )
        val result = adapter.fetchSchemaSummary(cfg)
        assertTrue(result.contains("users"))
        assertTrue(result.contains("email"))
    }

    @Test
    fun `fetchSchemaSummary falls back when MCP returns error`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(McpToolResult(isError = true, text = "err"))
        whenever(delegate.fetchSchemaSummary(cfg)).thenReturn("schema-fb")
        assertEquals("schema-fb", adapter.fetchSchemaSummary(cfg))
    }

    @Test
    fun `fetchSchemaSummary falls back when MCP returns invalid JSON`() {
        whenever(gateway.callTool(any(), any(), any())).thenReturn(
            McpToolResult(isError = false, text = "not json"),
        )
        whenever(delegate.fetchSchemaSummary(cfg)).thenReturn("schema-fb")
        assertEquals("schema-fb", adapter.fetchSchemaSummary(cfg))
    }

    @Test
    fun `fetchSchemaSummary falls back when MCP gateway throws`() {
        whenever(gateway.callTool(any(), any(), any())).doThrow(RuntimeException("boom"))
        whenever(delegate.fetchSchemaSummary(cfg)).thenReturn("schema-fb")
        assertEquals("schema-fb", adapter.fetchSchemaSummary(cfg))
    }
}

class McpAwareDatabaseScanAdapterMapHitTest {
    private val mcpRepo: com.stacklens.application.port.out.McpServerConfigPort = mock()
    private val gateway: com.stacklens.application.port.out.McpGatewayPort = mock()
    private val om =
        com.fasterxml.jackson.databind
            .ObjectMapper()
    private val delegate: com.stacklens.application.port.out.database.DatabaseScanPort = mock()

    private fun adapter() = McpAwareDatabaseScanAdapter(mcpRepo, gateway, om, delegate)

    private fun method(): java.lang.reflect.Method {
        val m =
            McpAwareDatabaseScanAdapter::class.java.declaredMethods.first {
                it.name == "mapHitToSearchResult"
            }
        m.isAccessible = true
        return m
    }

    private fun hit(
        kind: String = "table",
        schemaName: String = "public",
        tableName: String = "users",
        columnName: String = "id",
        dataType: String = "int",
        ddlContext: String = "CREATE TABLE users (id int)",
    ): com.fasterxml.jackson.databind.JsonNode {
        val n = om.createObjectNode()
        n.put("kind", kind)
        n.put("schemaName", schemaName)
        n.put("tableName", tableName)
        n.put("columnName", columnName)
        n.put("dataType", dataType)
        n.put("ddlContext", ddlContext)
        return n
    }

    private fun cfg() =
        com.stacklens.domain.model.DatabaseConfig(
            name = "db",
            dbType = com.stacklens.domain.enums.DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "testdb",
            username = "u",
            password = "p",
        )

    @Test
    fun `mapHitToSearchResult for table kind returns TABLE result`() {
        val m = method()
        val result = m.invoke(adapter(), hit("table"), cfg(), "kw", 1L, "testdb") as com.stacklens.domain.model.SearchResult?
        assertNotNull(result)
        assertEquals(com.stacklens.domain.enums.ResultType.TABLE, result!!.resultType)
    }

    @Test
    fun `mapHitToSearchResult for column kind returns COLUMN result`() {
        val m = method()
        val result = m.invoke(adapter(), hit("column"), cfg(), "kw", 1L, "testdb") as com.stacklens.domain.model.SearchResult?
        assertNotNull(result)
        assertEquals(com.stacklens.domain.enums.ResultType.COLUMN, result!!.resultType)
    }

    @Test
    fun `mapHitToSearchResult for view kind returns VIEW result`() {
        val m = method()
        val result = m.invoke(adapter(), hit("view"), cfg(), "kw", 1L, "testdb") as com.stacklens.domain.model.SearchResult?
        assertNotNull(result)
        assertEquals(com.stacklens.domain.enums.ResultType.VIEW, result!!.resultType)
    }

    @Test
    fun `mapHitToSearchResult for unknown kind returns null`() {
        val m = method()
        val result = m.invoke(adapter(), hit("unknown_kind"), cfg(), "kw", 1L, "testdb")
        assertNull(result)
    }

    @Test
    fun `mapHitToSearchResult for blank kind returns null`() {
        val m = method()
        val result = m.invoke(adapter(), hit(kind = ""), cfg(), "kw", 1L, "testdb")
        assertNull(result)
    }

    @Test
    fun `mapHitToSearchResult with blank schemaName sets schemaName to null`() {
        val m = method()
        val result = m.invoke(adapter(), hit(schemaName = ""), cfg(), "kw", 1L, "testdb") as com.stacklens.domain.model.SearchResult?
        assertNotNull(result)
        assertNull(result!!.schemaName)
    }

    @Test
    fun `mapHitToSearchResult with blank columnName sets columnName to null`() {
        val m = method()
        val result = m.invoke(adapter(), hit(columnName = ""), cfg(), "kw", 1L, "testdb") as com.stacklens.domain.model.SearchResult?
        assertNotNull(result)
        assertNull(result!!.columnName)
    }
}

class McpAwareDatabaseScanAdapterSchemaSummaryTest {
    private val mcpRepo: com.stacklens.application.port.out.McpServerConfigPort = mock()
    private val gateway: com.stacklens.application.port.out.McpGatewayPort = mock()
    private val om =
        com.fasterxml.jackson.databind
            .ObjectMapper()
    private val delegate: com.stacklens.application.port.out.database.DatabaseScanPort = mock()

    private fun adapter() = McpAwareDatabaseScanAdapter(mcpRepo, gateway, om, delegate)

    private fun method(): java.lang.reflect.Method {
        val m =
            McpAwareDatabaseScanAdapter::class.java.declaredMethods.first {
                it.name == "buildSchemaSummary"
            }
        m.isAccessible = true
        return m
    }

    private fun node(columnsJson: String): com.fasterxml.jackson.databind.JsonNode {
        val n = om.createObjectNode()
        n.set<com.fasterxml.jackson.databind.JsonNode>("columns", om.readTree(columnsJson))
        return n
    }

    @Test
    fun `buildSchemaSummary returns empty when columns is not an array`() {
        val n = node("""{"not_columns": []}""")
        val result = method().invoke(adapter(), n) as String
        assertEquals("", result)
    }

    @Test
    fun `buildSchemaSummary returns empty when columns is empty array`() {
        val n = node("[]")
        val result = method().invoke(adapter(), n) as String
        assertEquals("", result)
    }

    @Test
    fun `buildSchemaSummary formats one column with NOT NULL marker`() {
        val n =
            node(
                """[{"tableName":"t","columnName":"c","dataType":"int","nullable":"NO"}]""",
            )
        val result = method().invoke(adapter(), n) as String
        assertTrue(result.contains("TABLE t"))
        assertTrue(result.contains("c int NOT NULL"))
    }

    @Test
    fun `buildSchemaSummary formats one column without NOT NULL marker when nullable is YES`() {
        val n =
            node(
                """[{"tableName":"t","columnName":"c","dataType":"int","nullable":"YES"}]""",
            )
        val result = method().invoke(adapter(), n) as String
        assertTrue(result.contains("c int"), "got: '$result'")
        assertFalse(result.contains("NOT NULL"), "got: '$result'")
    }

    @Test
    fun `buildSchemaSummary omits columns with blank tableName`() {
        val n =
            node(
                """[{"tableName":"","columnName":"c","dataType":"int"},{"tableName":"t2","columnName":"c2","dataType":"text"}]""",
            )
        val result = method().invoke(adapter(), n) as String
        assertTrue(result.contains("t2"))
        assertFalse(result.contains("TABLE \n"))
    }
}
