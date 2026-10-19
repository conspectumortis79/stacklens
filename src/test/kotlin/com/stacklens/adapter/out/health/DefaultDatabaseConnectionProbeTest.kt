package com.stacklens.adapter.out.health

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.application.port.out.health.ConnectionProbeResult
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

class DefaultDatabaseConnectionProbeTest {
    private val connectionPort = mock<DatabaseScanConnection>()
    private val conn = mock<Connection>()
    private val probe = DefaultDatabaseConnectionProbe(connectionPort)

    private fun cfg(dbType: DatabaseType = DatabaseType.POSTGRESQL) =
        DatabaseConfig(
            name = "x",
            dbType = dbType,
            host = "127.0.0.1",
            port = 1,
            databaseName = "x",
            username = "u",
            password = "p",
        )

    @Test
    fun `probe returns Failure when the port throws an exception`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException("Connection refused"))
        val result = probe.probe(cfg())
        assertTrue(result is ConnectionProbeResult.Failure, "expected Failure, got $result")
        val failure = result as ConnectionProbeResult.Failure
        assertNotNull(failure.errorMessage)
        assertTrue(failure.errorMessage.isNotBlank())
        assertTrue(failure.errorMessage.length > 3, "message too short: '${failure.errorMessage}'")
    }

    @Test
    fun `probe returns Failure when isValid returns false`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.isValid(3)).thenReturn(false)
        val result = probe.probe(cfg())
        assertTrue(result is ConnectionProbeResult.Failure, "expected Failure, got $result")
        assertEquals("isValid(3) returned false", (result as ConnectionProbeResult.Failure).errorMessage)
    }

    @Test
    fun `probe returns Ok when isValid returns true`() {
        whenever(connectionPort.open(any())).thenReturn(conn)
        whenever(conn.isValid(3)).thenReturn(true)
        val result = probe.probe(cfg())
        assertTrue(result is ConnectionProbeResult.Ok, "expected Ok, got $result")
    }

    @Test
    fun `probe never throws - always returns Ok or Failure`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException("boom"))
        repeat(5) {
            val r = probe.probe(cfg(dbType = DatabaseType.MSSQL))
            assertTrue(
                r is ConnectionProbeResult.Ok || r is ConnectionProbeResult.Failure,
                "probe leaked: $r",
            )
            assertTrue(r is ConnectionProbeResult.Failure)
        }
    }

    @Test
    fun `probe handles exception with null message by using class name`() {
        whenever(connectionPort.open(any())).thenThrow(RuntimeException(null as String?))
        val result = probe.probe(cfg())
        assertTrue(result is ConnectionProbeResult.Failure)
        val failure = result as ConnectionProbeResult.Failure
        assertNotNull(failure.errorMessage)
        assertTrue(failure.errorMessage.isNotBlank())
    }
}
