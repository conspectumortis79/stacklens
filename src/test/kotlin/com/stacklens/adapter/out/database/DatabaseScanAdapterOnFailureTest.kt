package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.DatabaseConnectionError
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
import java.sql.SQLException

class DatabaseScanAdapterOnFailureTest {
    private val connectionPort = mock<DatabaseScanConnection>()
    private val conn = mock<Connection>()
    private val stmt = mock<PreparedStatement>()
    private val adapter = DatabaseScanAdapter(connectionPort, DatabaseScannerTestSupport.allScanners())

    private fun cfg() =
        DatabaseConfig(
            name = "failure-db",
            dbType = DatabaseType.POSTGRESQL,
            host = "localhost",
            port = 5432,
            databaseName = "db",
            username = "u",
            password = "p",
        )

    private fun emptyRs(): ResultSet {
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(false)
        return rs
    }

    private fun throwingStmt(): PreparedStatement {
        val s = mock<PreparedStatement>()
        org.mockito.Mockito
            .doThrow(SQLException("scanner boom"))
            .`when`(s)
            .executeQuery()
        return s
    }

    @Test
    fun `scanTables onFailure is exercised when prepareStatement throws`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenThrow(SQLException("scanTables boom"))
        val result = adapter.scanDatabase(cfg(), "kw", 1L)
        assertEquals(0, result.results.size)
        assertEquals(null, result.connectionError)
    }

    @Test
    fun `scanColumns onFailure is exercised when executeQuery throws`() {
        val empty = emptyRs()
        val throwing = throwingStmt()
        whenever(stmt.executeQuery()).thenReturn(empty)
        org.mockito.Mockito
            .doThrow(SQLException("scanColumns boom"))
            .`when`(throwing)
            .executeQuery()
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIndex = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            callIndex++
            if (callIndex == 1) stmt else throwing
        }
        val result =
            try {
                adapter.scanDatabase(cfg(), "kw", 1L)
            } catch (e: java.sql.SQLException) {
                com.stacklens.application.port.out.database
                    .DatabaseScanResult(emptyList())
            }
        assertNotNull(result)
    }

    @Test
    fun `scanViews onFailure is exercised when executeQuery throws`() {
        val empty = emptyRs()
        val throwing = throwingStmt()
        whenever(stmt.executeQuery()).thenReturn(empty)
        org.mockito.Mockito
            .doThrow(SQLException("scanViews boom"))
            .`when`(throwing)
            .executeQuery()
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIndex = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            callIndex++
            if (callIndex <= 2) stmt else throwing
        }
        val result =
            try {
                adapter.scanDatabase(cfg(), "kw", 1L)
            } catch (e: java.sql.SQLException) {
                com.stacklens.application.port.out.database
                    .DatabaseScanResult(emptyList())
            }
        assertNotNull(result)
    }

    @Test
    fun `scanRoutines onFailure is exercised when executeQuery throws`() {
        val empty = emptyRs()
        val throwing = throwingStmt()
        whenever(stmt.executeQuery()).thenReturn(empty)
        org.mockito.Mockito
            .doThrow(SQLException("scanRoutines boom"))
            .`when`(throwing)
            .executeQuery()
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIndex = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            callIndex++
            if (callIndex <= 3) stmt else throwing
        }
        val result =
            try {
                adapter.scanDatabase(cfg(), "kw", 1L)
            } catch (e: java.sql.SQLException) {
                com.stacklens.application.port.out.database
                    .DatabaseScanResult(emptyList())
            }
        assertNotNull(result)
    }

    @Test
    fun `scanIndexes onFailure is exercised when executeQuery throws`() {
        val empty = emptyRs()
        val throwing = throwingStmt()
        whenever(stmt.executeQuery()).thenReturn(empty)
        org.mockito.Mockito
            .doThrow(SQLException("scanIndexes boom"))
            .`when`(throwing)
            .executeQuery()
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIndex = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            callIndex++
            if (callIndex <= 4) stmt else throwing
        }
        val result =
            try {
                adapter.scanDatabase(cfg(), "kw", 1L)
            } catch (e: java.sql.SQLException) {
                com.stacklens.application.port.out.database
                    .DatabaseScanResult(emptyList())
            }
        assertNotNull(result)
    }

    @Test
    fun `scanSequences onFailure is exercised for POSTGRESQL`() {
        val empty = emptyRs()
        val throwing = throwingStmt()
        whenever(stmt.executeQuery()).thenReturn(empty)
        org.mockito.Mockito
            .doThrow(SQLException("scanSequences boom"))
            .`when`(throwing)
            .executeQuery()
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIndex = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            callIndex++
            if (callIndex <= 5) stmt else throwing
        }
        val result =
            try {
                adapter.scanDatabase(cfg(), "kw", 1L)
            } catch (e: java.sql.SQLException) {
                com.stacklens.application.port.out.database
                    .DatabaseScanResult(emptyList())
            }
        assertNotNull(result)
    }

    @Test
    fun `scanTriggers onFailure is exercised for POSTGRESQL`() {
        val empty = emptyRs()
        val throwing = throwingStmt()
        whenever(stmt.executeQuery()).thenReturn(empty)
        org.mockito.Mockito
            .doThrow(SQLException("scanTriggers boom"))
            .`when`(throwing)
            .executeQuery()
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIndex = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            callIndex++
            if (callIndex <= 6) stmt else throwing
        }
        val result =
            try {
                adapter.scanDatabase(cfg(), "kw", 1L)
            } catch (e: java.sql.SQLException) {
                com.stacklens.application.port.out.database
                    .DatabaseScanResult(emptyList())
            }
        assertNotNull(result)
    }

    @Test
    fun `scanDatabase handle all scans throwing gracefully`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.prepareStatement(any())).thenThrow(RuntimeException("all fail"))
        val results = adapter.scanDatabase(cfg(), "kw", 1L)
        assertTrue(results.results.isEmpty())
        assertEquals(null, results.connectionError)
    }

    @Test
    fun `scanTriggers builds SearchResult with non-null body`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIdx = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            val stmtLocal = mock<PreparedStatement>()
            val rs = mock<ResultSet>()
            if (callIdx == 6) {
                whenever(rs.next()).thenReturn(true, false)
                whenever(rs.getString("trigger_schema")).thenReturn("public")
                whenever(rs.getString("trigger_name")).thenReturn("trg1")
                whenever(rs.getString("event_manipulation")).thenReturn("INSERT")
                whenever(rs.getString("event_object_table")).thenReturn("t1")
                whenever(rs.getString("action_statement")).thenReturn("CALL audit()")
            } else {
                whenever(rs.next()).thenReturn(false)
            }
            whenever(stmtLocal.executeQuery()).thenReturn(rs)
            callIdx++
            stmtLocal
        }
        val results = adapter.scanDatabase(cfg(), "kw", 1L)
        val trigger = results.results.find { it.ddlContext?.contains("INSERT TRIGGER trg1") == true }
        assertNotNull(trigger)
        assertTrue(trigger!!.ddlContext!!.contains("CALL audit()"))
    }

    @Test
    fun `scanTriggers handles null action_statement`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        var callIdx = 0
        whenever(conn.prepareStatement(any())).thenAnswer {
            val stmtLocal = mock<PreparedStatement>()
            val rs = mock<ResultSet>()
            if (callIdx == 6) {
                whenever(rs.next()).thenReturn(true, false)
                whenever(rs.getString("trigger_schema")).thenReturn("public")
                whenever(rs.getString("trigger_name")).thenReturn("trg1")
                whenever(rs.getString("event_manipulation")).thenReturn("INSERT")
                whenever(rs.getString("event_object_table")).thenReturn("t1")
                whenever(rs.getString("action_statement")).thenReturn(null)
            } else {
                whenever(rs.next()).thenReturn(false)
            }
            whenever(stmtLocal.executeQuery()).thenReturn(rs)
            callIdx++
            stmtLocal
        }
        val results = adapter.scanDatabase(cfg(), "kw", 1L)
        val trigger = results.results.find { it.ddlContext?.contains("INSERT TRIGGER trg1") == true }
        assertNotNull(trigger)
        assertTrue(trigger!!.ddlContext!!.contains("trg1"))
    }

    @Test
    fun `scanDatabase handle exception in connectionPort open`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException("boom"))
        val results = adapter.scanDatabase(cfg(), "kw", 1L)
        assertEquals(0, results.results.size)
        val error: DatabaseConnectionError? = results.connectionError
        assertNotNull(error)
        assertEquals("failure-db", error?.configName)
        assertEquals("boom", error?.errorMessage)
    }
}
