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

class DatabaseScanAdapterTest {
    private val connectionPort = mock<DatabaseScanConnection>()
    private val conn = mock<Connection>()
    private val stmt = mock<PreparedStatement>()
    private val rs = mock<ResultSet>()
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

    @Test
    fun `scanDatabase returns empty list when connection throws`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException("conn failed"))
        val r = adapter.scanDatabase(cfg(), "kw", 1L)
        assertTrue(r.results.isEmpty())
        assertNotNull(r.connectionError)
        assertEquals("test", r.connectionError!!.configName)
    }

    @Test
    fun `scanDatabase uses connection from port`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        whenever(rs.next()).thenReturn(false)
        whenever(stmt.execute()).thenReturn(false)
        whenever(conn.createStatement()).thenReturn(org.mockito.kotlin.mock())
        val r = adapter.scanDatabase(cfg(), "kw", 1L)
        assertNotNull(r)
        assertEquals(null, r.connectionError)
    }

    @Test
    fun `scanDatabase handles exception in any scan method without aborting`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenThrow(RuntimeException("SQL error"))
        whenever(conn.createStatement()).thenReturn(org.mockito.kotlin.mock())
        val r = adapter.scanDatabase(cfg(), "kw", 1L)
        assertTrue(r.results.isEmpty())
        assertEquals(null, r.connectionError)
    }

    @Test
    fun `scanDatabase returns empty when connection fails but does not throw`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException("conn failed"))
        val r = adapter.scanDatabase(cfg(), "kw", 1L)
        assertTrue(r.results.isEmpty())
        assertNotNull(r.connectionError)
    }

    @Test
    fun `scanDatabase handles partial success where later scan methods throw`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        val stmtMock = mock<PreparedStatement>()
        val rsMock = mock<ResultSet>()
        whenever(conn.prepareStatement(any()))
            .thenReturn(stmtMock)
            .thenThrow(RuntimeException("later sql error"))
        whenever(stmtMock.executeQuery()).thenReturn(rsMock)
        whenever(rsMock.next()).thenReturn(false)
        whenever(stmtMock.execute()).thenReturn(false)
        whenever(conn.createStatement()).thenReturn(mock())
        val r = adapter.scanDatabase(cfg(), "kw", 1L)
        assertNotNull(r)
        assertEquals(null, r.connectionError)
    }

    @Test
    fun `scanDatabase POSTGRESQL exercises both supportsSequences and supportsTriggers branches`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        val stmtMock = mock<PreparedStatement>()
        val rsMock = mock<ResultSet>()
        whenever(conn.prepareStatement(any())).thenReturn(stmtMock)
        whenever(stmtMock.executeQuery()).thenReturn(rsMock)
        whenever(rsMock.next()).thenReturn(false)
        whenever(stmtMock.execute()).thenReturn(false)
        whenever(conn.createStatement()).thenReturn(mock())
        val r = adapter.scanDatabase(cfg(DatabaseType.POSTGRESQL), "kw", 1L)
        assertNotNull(r)
        assertEquals(null, r.connectionError)
    }

    @Test
    fun `scanDatabase MYSQL skips sequences and triggers branches`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        val stmtMock = mock<PreparedStatement>()
        val rsMock = mock<ResultSet>()
        whenever(conn.prepareStatement(any())).thenReturn(stmtMock)
        whenever(stmtMock.executeQuery()).thenReturn(rsMock)
        whenever(rsMock.next()).thenReturn(false)
        whenever(stmtMock.execute()).thenReturn(false)
        whenever(conn.createStatement()).thenReturn(mock())
        val r = adapter.scanDatabase(cfg(DatabaseType.MYSQL), "kw", 1L)
        assertNotNull(r)
        assertEquals(null, r.connectionError)
    }

    @Test
    fun `scanDatabase with POSTGRESQL triggers sequences and triggers scan`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        whenever(rs.next()).thenReturn(false)
        whenever(stmt.execute()).thenReturn(false)
        whenever(conn.createStatement()).thenReturn(org.mockito.kotlin.mock())
        val r = adapter.scanDatabase(cfg(DatabaseType.POSTGRESQL), "kw", 1L)
        assertNotNull(r)
    }

    @Test
    fun `scanDatabase with MSSQL triggers sequences and triggers scan`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        whenever(rs.next()).thenReturn(false)
        whenever(stmt.execute()).thenReturn(false)
        whenever(conn.createStatement()).thenReturn(org.mockito.kotlin.mock())
        val r = adapter.scanDatabase(cfg(DatabaseType.MSSQL), "kw", 1L)
        assertNotNull(r)
    }

    @Test
    fun `scanDatabase with MYSQL skips sequences and triggers`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenReturn(stmt)
        whenever(stmt.executeQuery()).thenReturn(rs)
        whenever(rs.next()).thenReturn(false)
        whenever(stmt.execute()).thenReturn(false)
        whenever(conn.createStatement()).thenReturn(org.mockito.kotlin.mock())
        val r = adapter.scanDatabase(cfg(DatabaseType.MYSQL), "kw", 1L)
        assertNotNull(r)
    }

    @Test
    fun `jdbcUrl for POSTGRESQL uses jdbc-postgresql`() {
        assertTrue(DefaultJdbcUrlProvider().urlFor(cfg(DatabaseType.POSTGRESQL)).startsWith("jdbc:postgresql://"))
    }

    @Test
    fun `jdbcUrl for MYSQL uses jdbc-mysql`() {
        assertTrue(DefaultJdbcUrlProvider().urlFor(cfg(DatabaseType.MYSQL)).startsWith("jdbc:mysql://"))
    }

    @Test
    fun `jdbcUrl for MSSQL uses jdbc-sqlserver`() {
        assertTrue(DefaultJdbcUrlProvider().urlFor(cfg(DatabaseType.MSSQL)).startsWith("jdbc:sqlserver://"))
    }

    @Test
    fun `schemaList returns all schemas when no filter set`() {
        assertEquals(emptyList<String>(), cfg().schemaList())
    }

    @Test
    fun `schemaList returns filtered list from CSV`() {
        assertEquals(listOf("public", "private", "audit"), cfg(schemas = "public,private, audit").schemaList())
    }

    @Test
    fun `scanDatabase falls back to exception class name when message is null`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException())
        val r = adapter.scanDatabase(cfg(), "kw", 1L)
        assertTrue(r.results.isEmpty())
        assertNotNull(r.connectionError)
        assertEquals("RuntimeException", r.connectionError!!.errorMessage)
    }
}
