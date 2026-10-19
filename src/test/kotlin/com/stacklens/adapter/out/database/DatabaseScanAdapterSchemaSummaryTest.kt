package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class DatabaseScanAdapterSchemaSummaryTest {
    private val connectionPort = mock<DatabaseScanConnection>()
    private val conn = mock<Connection>()
    private val stmt = mock<PreparedStatement>()
    private val rs = mock<ResultSet>()
    private val adapter = DatabaseScanAdapter(connectionPort, DatabaseScannerTestSupport.allScanners())

    private fun cfg() =
        DatabaseConfig(
            name = "summary-db",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "db",
            username = "u",
            password = "p",
        )

    private fun stubSingleRow(row: Map<String, String?>) {
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.getString("table_name")).thenReturn(row["table_name"])
        whenever(rs.getString("column_name")).thenReturn(row["column_name"])
        whenever(rs.getString("data_type")).thenReturn(row["data_type"])
        whenever(rs.getString("character_maximum_length")).thenReturn(row["character_maximum_length"])
        whenever(rs.getString("is_nullable")).thenReturn(row["is_nullable"])
    }

    private fun stubTwoRows(
        row1: Map<String, String?>,
        row2: Map<String, String?>,
    ) {
        whenever(rs.next()).thenReturn(true, true, false)
        whenever(rs.getString("table_name")).thenReturn(row1["table_name"], row2["table_name"])
        whenever(rs.getString("column_name")).thenReturn(row1["column_name"], row2["column_name"])
        whenever(rs.getString("data_type")).thenReturn(row1["data_type"], row2["data_type"])
        whenever(rs.getString("character_maximum_length")).thenReturn(row1["character_maximum_length"], row2["character_maximum_length"])
        whenever(rs.getString("is_nullable")).thenReturn(row1["is_nullable"], row2["is_nullable"])
    }

    @Test
    fun `fetchSchemaSummary returns empty string when connection throws`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException("conn boom"))
        assertEquals("", adapter.fetchSchemaSummary(cfg()))
    }

    @Test
    fun `fetchSchemaSummary returns empty string when no rows`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        whenever(rs.next()).thenReturn(false)
        assertEquals("", adapter.fetchSchemaSummary(cfg()))
    }

    @Test
    fun `fetchSchemaSummary formats rows grouped by table with all column metadata`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        stubTwoRows(
            mapOf("table_name" to "users", "column_name" to "email", "data_type" to "varchar", "character_maximum_length" to "255", "is_nullable" to "NO"),
            mapOf("table_name" to "users", "column_name" to "active", "data_type" to "boolean", "character_maximum_length" to null, "is_nullable" to "YES"),
        )

        val out = adapter.fetchSchemaSummary(cfg())
        assertNotNull(out)
        assertTrue(out.contains("## Database: summary-db"))
        assertTrue(out.contains("TABLE users"))
        assertTrue(out.contains("email varchar(255) NOT NULL"))
        assertTrue(out.contains("active boolean"))
    }

    @Test
    fun `fetchSchemaSummary skips rows with null table_name`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        stubSingleRow(
            mapOf("table_name" to "orders", "column_name" to "total", "data_type" to "numeric", "character_maximum_length" to null, "is_nullable" to "YES"),
        )

        val out = adapter.fetchSchemaSummary(cfg())
        assertNotNull(out)
        assertTrue(out.contains("TABLE orders"))
        assertTrue(out.contains("total numeric"))
    }

    @Test
    fun `fetchSchemaSummary skips rows with null column_name`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        stubSingleRow(
            mapOf("table_name" to "audit", "column_name" to "ts", "data_type" to "timestamp", "character_maximum_length" to null, "is_nullable" to "YES"),
        )

        val out = adapter.fetchSchemaSummary(cfg())
        assertNotNull(out)
        assertTrue(out.contains("TABLE audit"))
        assertTrue(out.contains("ts timestamp"))
    }

    @Test
    fun `fetchSchemaSummary defaults data_type to unknown when null`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.getString("table_name")).thenReturn("t")
        whenever(rs.getString("column_name")).thenReturn("c")
        whenever(rs.getString("data_type")).thenReturn(null)
        whenever(rs.getString("character_maximum_length")).thenReturn(null)
        whenever(rs.getString("is_nullable")).thenReturn(null)

        val out = adapter.fetchSchemaSummary(cfg())
        assertNotNull(out)
        assertTrue(out.contains("c unknown NOT NULL"))
    }
}
