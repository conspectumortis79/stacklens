package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class DatabaseScanAdapterSchemaSummaryEdgeCaseTest {
    private val connectionPort = mock<DatabaseScanConnection>()
    private val conn = mock<Connection>()
    private val stmt = mock<PreparedStatement>()
    private val rs = mock<ResultSet>()
    private val adapter = DatabaseScanAdapter(connectionPort, DatabaseScannerTestSupport.allScanners())

    private fun cfg() =
        DatabaseConfig(
            name = "edge-db",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "db",
            username = "u",
            password = "p",
        )

    private fun wireMocks() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
    }

    @Test
    fun `fetchSchemaSummary continues when table_name is null (no row emitted)`() {
        wireMocks()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.getString("table_name")).thenReturn(null)
        whenever(rs.getString("column_name")).thenReturn("x")
        whenever(rs.getString("data_type")).thenReturn("int")
        whenever(rs.getString("character_maximum_length")).thenReturn(null)
        whenever(rs.getString("is_nullable")).thenReturn("YES")

        val out = adapter.fetchSchemaSummary(cfg())
        assertTrue(out.isEmpty() || out == "", "expected empty summary, got: $out")
    }

    @Test
    fun `fetchSchemaSummary continues when column_name is null`() {
        wireMocks()
        whenever(rs.next()).thenReturn(true, true, false)
        whenever(rs.getString("table_name")).thenReturn("t", "t2")
        whenever(rs.getString("column_name")).thenReturn(null, "real_col")
        whenever(rs.getString("data_type")).thenReturn("int", "varchar")
        whenever(rs.getString("character_maximum_length")).thenReturn(null, null)
        whenever(rs.getString("is_nullable")).thenReturn("YES", "YES")

        val out = adapter.fetchSchemaSummary(cfg())
        assertNotNull(out)
        assertTrue(out.contains("real_col"), "expected real_col in output, got: $out")
        assertTrue(!out.contains("null"))
    }

    @Test
    fun `fetchSchemaSummary defaults data_type to unknown`() {
        wireMocks()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.getString("table_name")).thenReturn("t")
        whenever(rs.getString("column_name")).thenReturn("c")
        whenever(rs.getString("data_type")).thenReturn(null)
        whenever(rs.getString("character_maximum_length")).thenReturn(null)
        whenever(rs.getString("is_nullable")).thenReturn("YES")
        val out = adapter.fetchSchemaSummary(cfg())
        assertTrue(out.contains("c unknown"))
    }

    @Test
    fun `fetchSchemaSummary defaults is_nullable to NOT NULL when null`() {
        wireMocks()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.getString("table_name")).thenReturn("t")
        whenever(rs.getString("column_name")).thenReturn("c")
        whenever(rs.getString("data_type")).thenReturn("int")
        whenever(rs.getString("character_maximum_length")).thenReturn(null)
        whenever(rs.getString("is_nullable")).thenReturn(null)
        val out = adapter.fetchSchemaSummary(cfg())
        assertTrue(out.contains("c int NOT NULL"))
    }

    @Test
    fun `fetchSchemaSummary handles is_nullable YES without NOT NULL clause`() {
        wireMocks()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.getString("table_name")).thenReturn("t")
        whenever(rs.getString("column_name")).thenReturn("c")
        whenever(rs.getString("data_type")).thenReturn("int")
        whenever(rs.getString("character_maximum_length")).thenReturn(null)
        whenever(rs.getString("is_nullable")).thenReturn("YES")
        val out = adapter.fetchSchemaSummary(cfg())
        assertTrue(out.contains("c int"))
        assertTrue(!out.contains("NOT NULL"))
    }

    @Test
    fun `fetchSchemaSummary appends maxLen in parentheses when present`() {
        wireMocks()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.getString("table_name")).thenReturn("t")
        whenever(rs.getString("column_name")).thenReturn("c")
        whenever(rs.getString("data_type")).thenReturn("varchar")
        whenever(rs.getString("character_maximum_length")).thenReturn("200")
        whenever(rs.getString("is_nullable")).thenReturn("YES")
        val out = adapter.fetchSchemaSummary(cfg())
        assertTrue(out.contains("varchar(200)"))
    }

    @Test
    fun `fetchSchemaSummary groups rows by table_name and emits section headers`() {
        wireMocks()
        whenever(rs.next()).thenReturn(true, true, true, false)
        whenever(rs.getString("table_name")).thenReturn("alpha", "alpha", "beta")
        whenever(rs.getString("column_name")).thenReturn("a1", "a2", "b1")
        whenever(rs.getString("data_type")).thenReturn("int", "varchar", "text")
        whenever(rs.getString("character_maximum_length")).thenReturn(null, "100", null)
        whenever(rs.getString("is_nullable")).thenReturn("YES", "NO", "YES")
        val out = adapter.fetchSchemaSummary(cfg())
        assertTrue(out.contains("TABLE alpha"))
        assertTrue(out.contains("TABLE beta"))
        assertTrue(out.contains("a1 int"))
        assertTrue(out.contains("b1 text"))
    }
}
