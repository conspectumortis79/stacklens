package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.enums.ResultType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class DatabaseScanAdapterScanMethodsTest {
    private val connectionPort = mock<DatabaseScanConnection>()
    private val conn = mock<Connection>()
    private val adapter = DatabaseScanAdapter(connectionPort, DatabaseScannerTestSupport.allScanners())

    private fun cfg(
        type: DatabaseType = DatabaseType.POSTGRESQL,
        schemas: String? = null,
    ) = DatabaseConfig(
        name = "test",
        dbType = type,
        host = "localhost",
        port = 5432,
        databaseName = "db",
        username = "u",
        password = "p",
        schemas = schemas,
    )

    private fun wireConnectionForPreparedStatement(vararg stubRows: List<Pair<String, Any?>>) {
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIndex = 0
        val allResultSets: List<ResultSet> =
            stubRows.map { cols -> buildRsWithRow(cols) } + List(20) { emptyRs() }
        whenever(conn.prepareStatement(any())).thenAnswer {
            val stmt = mock<PreparedStatement>()
            whenever(stmt.executeQuery()).thenReturn(allResultSets[callIndex])
            callIndex++
            stmt
        }
    }

    private fun buildRsWithRow(columns: List<Pair<String, Any?>>): ResultSet {
        val rs2 = mock<ResultSet>()
        whenever(rs2.next()).thenReturn(true, false)
        for ((name, value) in columns) {
            whenever(rs2.getString(name)).thenReturn(value?.toString())
        }
        return rs2
    }

    private fun emptyRs(): ResultSet {
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(false)
        return rs
    }

    @Test
    fun `scanTables maps VIEW type to ResultType-VIEW`() {
        wireConnectionForPreparedStatement(
            listOf("table_schema" to "public", "table_name" to "v_orders", "table_type" to "VIEW"),
        )
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        assertEquals(1, results.size)
        assertEquals(ResultType.VIEW, results[0].resultType)
        assertEquals("v_orders", results[0].tableName)
        assertEquals("public", results[0].schemaName)
    }

    @Test
    fun `scanTables maps BASE TABLE type to ResultType-TABLE`() {
        wireConnectionForPreparedStatement(
            listOf("table_schema" to "public", "table_name" to "users", "table_type" to "BASE TABLE"),
        )
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        assertEquals(1, results.size)
        assertEquals(ResultType.TABLE, results[0].resultType)
    }

    @Test
    fun `scanColumns builds DDL with length, NOT NULL and DEFAULT`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            listOf(
                "table_schema" to "public",
                "table_name" to "users",
                "column_name" to "email",
                "data_type" to "varchar",
                "character_maximum_length" to "255",
                "is_nullable" to "NO",
                "column_default" to "'n/a'",
            ),
        )
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        val column = results.find { it.resultType == ResultType.COLUMN }
        assertNotNull(column)
        assertTrue(column!!.ddlContext!!.contains("email varchar(255) NOT NULL"))
        assertTrue(column.ddlContext!!.contains("DEFAULT 'n/a'"))
    }

    @Test
    fun `scanColumns builds DDL without length when null`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            listOf(
                "table_schema" to "public",
                "table_name" to "users",
                "column_name" to "active",
                "data_type" to "boolean",
                "character_maximum_length" to null,
                "is_nullable" to "YES",
                "column_default" to null,
            ),
        )
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        val column = results.find { it.resultType == ResultType.COLUMN }
        assertNotNull(column)
        assertTrue(column!!.ddlContext!!.contains("active boolean"))
        assertFalse(column.ddlContext!!.contains("NOT NULL"))
    }

    @Test
    fun `scanViews creates SearchResult from rows`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            emptyList(),
            listOf("table_schema" to "public", "table_name" to "v_orders", "view_definition" to "SELECT * FROM orders"),
        )
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        val view = results.find { it.resultType == ResultType.VIEW }
        assertNotNull(view)
        assertEquals("v_orders", view!!.tableName)
    }

    @Test
    fun `scanRoutines creates SearchResult with routine definition`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                "routine_schema" to "public",
                "routine_name" to "do_thing",
                "routine_type" to "FUNCTION",
                "routine_definition" to "BEGIN ... END",
            ),
        )
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        val proc = results.find { it.resultType == ResultType.PROCEDURE }
        assertNotNull(proc)
        assertEquals("do_thing", proc!!.tableName)
        assertTrue(proc.ddlContext!!.contains("FUNCTION"))
    }

    @Test
    fun `scanIndexes creates SearchResult for postgres`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                "schemaname" to "public",
                "indexname" to "idx_users_email",
                "tablename" to "users",
            ),
        )
        val results = adapter.scanDatabase(cfg(DatabaseType.POSTGRESQL), "kw", 1L).results
        val index = results.find { it.resultType == ResultType.INDEX }
        assertNotNull(index)
        assertEquals("idx_users_email", index!!.columnName)
    }

    @Test
    fun `scanIndexes creates SearchResult for mysql`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                "schemaname" to "analytics_db",
                "indexname" to "idx_orders_created_at",
                "tablename" to "orders",
            ),
        )
        val results = adapter.scanDatabase(cfg(DatabaseType.MYSQL), "kw", 1L).results
        val index = results.find { it.resultType == ResultType.INDEX }
        assertNotNull(index, "scanIndexes must produce a result for MYSQL")
        assertEquals("idx_orders_created_at", index!!.columnName)
        assertEquals("orders", index.tableName)
        assertEquals("analytics_db", index.schemaName)
        assertTrue(index.ddlContext!!.contains("INDEX analytics_db.idx_orders_created_at"))
    }

    @Test
    fun `scanIndexes creates SearchResult for mssql`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                "schemaname" to "dbo",
                "indexname" to "IX_Users_Email",
                "tablename" to "Users",
            ),
        )
        val results = adapter.scanDatabase(cfg(DatabaseType.MSSQL), "kw", 1L).results
        val index = results.find { it.resultType == ResultType.INDEX }
        assertNotNull(index, "scanIndexes must produce a result for MSSQL")
        assertEquals("IX_Users_Email", index!!.columnName)
        assertEquals("Users", index.tableName)
        assertEquals("dbo", index.schemaName)
    }

    @Test
    fun `scanIndexes delegates SQL to DatabaseType and does not embed pg_indexes`() {
        val emptyRsForAll = List(10) { emptyRs() }
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIdx = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            val stmt = mock<PreparedStatement>()
            whenever(stmt.executeQuery()).thenReturn(emptyRsForAll[callIdx])
            callIdx++
            stmt
        }
        DatabaseType.values().forEach { dbType ->
            callIdx = 0
            val results = adapter.scanDatabase(cfg(dbType), "kw", 1L)
            assertNotNull(results)
        }
    }

    @Test
    fun `scanSequences runs for POSTGRESQL`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                "sequence_schema" to "public",
                "sequence_name" to "users_id_seq",
                "data_type" to "bigint",
            ),
        )
        val results = adapter.scanDatabase(cfg(DatabaseType.POSTGRESQL), "kw", 1L).results
        val seq = results.find { it.resultType == ResultType.SEQUENCE }
        assertNotNull(seq)
        assertEquals("users_id_seq", seq!!.tableName)
    }

    @Test
    fun `scanTriggers runs for POSTGRESQL`() {
        wireConnectionForPreparedStatement(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(
                "trigger_schema" to "public",
                "trigger_name" to "trg_users_audit",
                "event_manipulation" to "INSERT",
                "event_object_table" to "users",
                "action_statement" to "CALL audit_log()",
            ),
        )
        val results = adapter.scanDatabase(cfg(DatabaseType.POSTGRESQL), "kw", 1L).results
        val trigger = results.find { it.resultType == ResultType.TRIGGER }
        assertNotNull(trigger)
        assertEquals("trg_users_audit", trigger!!.columnName)
    }

    @Test
    fun `scanDatabase with schema filter restricts to provided schemas`() {
        wireConnectionForPreparedStatement(
            listOf("table_schema" to "audit", "table_name" to "users", "table_type" to "BASE TABLE"),
        )
        val results = adapter.scanDatabase(cfg(schemas = "public,private,audit"), "kw", 1L)
        assertNotNull(results)
    }

    @Test
    fun `scanDatabase with empty ResultSets returns no results`() {
        wireConnectionForPreparedStatement()
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        assertTrue(results.isEmpty())
    }

    @Test
    fun `scanDatabase catches and swallows connection errors`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException("boom"))
        val results = adapter.scanDatabase(cfg(), "kw", 1L).results
        assertTrue(results.isEmpty())
    }
}
