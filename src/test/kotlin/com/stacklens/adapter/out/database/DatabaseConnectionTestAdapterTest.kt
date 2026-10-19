package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.application.port.model.ConnectionFailureKind
import com.stacklens.application.port.out.DatabaseConnectionTestPort.TestResult
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException

class DatabaseConnectionTestAdapterTest {
    private val i18n = FakeI18n()

    private val config =
        DatabaseConfig(
            name = "db",
            dbType = DatabaseType.POSTGRESQL,
            host = "h",
            port = 5432,
            databaseName = "d",
            username = "u",
            password = "p",
        )

    private fun adapter(connectionPort: DatabaseScanConnection) = DatabaseConnectionTestAdapter(connectionPort, i18n)

    private fun configWithDriver(driverClass: String): DatabaseConfig {
        val dbType: DatabaseType = mock()
        whenever(dbType.driverClass).thenReturn(driverClass)
        val mocked: DatabaseConfig = mock()
        whenever(mocked.dbType).thenReturn(dbType)
        whenever(mocked.username).thenReturn("sa")
        whenever(mocked.password).thenReturn("")
        return mocked
    }

    @Test
    fun `testConnection returns success payload and closes the connection`() {
        val connection: Connection = mock()
        val metaData: DatabaseMetaData = mock()
        whenever(connection.metaData).thenReturn(metaData)
        whenever(metaData.databaseProductName).thenReturn("H2")
        whenever(metaData.databaseProductVersion).thenReturn("2.2.224")

        val result = adapter(DatabaseScanConnection { connection }).testConnection(config)

        assertTrue(result is TestResult.Success)
        assertEquals(mapOf("success" to true, "message" to "i18n:config.be_db_conn_success|H2,2.2.224"), result.payload)
        verify(connection).close()
    }

    @Test
    fun `testConnection closes the connection when reading metadata fails`() {
        val connection: Connection = mock()
        whenever(connection.metaData).thenThrow(SQLException("meta failed"))

        val result = adapter(DatabaseScanConnection { connection }).testConnection(config)

        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.OTHER, (result as TestResult.Failure).kind)
        assertEquals(mapOf("success" to false, "message" to "i18n:config.be_db_error|meta failed"), result.payload)
        verify(connection).close()
    }

    @Test
    fun `testConnection succeeds against an h2 database`() {
        val result =
            adapter(DriverManagerDatabaseScanConnectionPort { "jdbc:h2:mem:db_conn_success;DB_CLOSE_DELAY=-1" })
                .testConnection(configWithDriver("java.lang.String"))

        assertTrue(result is TestResult.Success)
        assertEquals(true, result.payload["success"])
        assertTrue(result.payload["message"].toString().startsWith("i18n:config.be_db_conn_success|H2"))
    }

    @Test
    fun `testConnection maps a SQLException with message to a failure`() {
        val result = adapter(DatabaseScanConnection { throw SQLException("boom") }).testConnection(config)

        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.OTHER, (result as TestResult.Failure).kind)
        assertEquals(mapOf("success" to false, "message" to "i18n:config.be_db_error|boom"), result.payload)
    }

    @Test
    fun `testConnection maps a SQLException without message to its simple name`() {
        val result = adapter(DatabaseScanConnection { throw SQLException() }).testConnection(config)

        assertTrue(result is TestResult.Failure)
        assertEquals(mapOf("success" to false, "message" to "i18n:config.be_db_error|SQLException"), result.payload)
    }

    @Test
    fun `testConnection maps a generic exception with message to a failure`() {
        val result = adapter(DatabaseScanConnection { throw IllegalStateException("kaputt") }).testConnection(config)

        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.CONNECTION, (result as TestResult.Failure).kind)
        assertEquals(mapOf("success" to false, "message" to "i18n:config.be_conn_error|kaputt"), result.payload)
    }

    @Test
    fun `testConnection maps a generic exception without message to its simple name`() {
        val result = adapter(DatabaseScanConnection { throw RuntimeException() }).testConnection(config)

        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.CONNECTION, (result as TestResult.Failure).kind)
        assertEquals(mapOf("success" to false, "message" to "i18n:config.be_conn_error|RuntimeException"), result.payload)
    }

    @Test
    fun `testConnection maps a missing driver class to a failure`() {
        val result =
            adapter(DriverManagerDatabaseScanConnectionPort { "jdbc:postgresql://localhost:5432/db" })
                .testConnection(configWithDriver("com.nonexistent.FakeDriver"))

        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.CONNECTION, (result as TestResult.Failure).kind)
        assertEquals(false, result.payload["success"])
        assertEquals(listOf("config.be_conn_error"), i18n.translatedKeys)
    }

    @Test
    fun `testConnection maps a missing jdbc driver to a failure`() {
        val result =
            adapter(DriverManagerDatabaseScanConnectionPort { "jdbc:unknown://invalid:9999/db" })
                .testConnection(configWithDriver("java.lang.String"))

        assertTrue(result is TestResult.Failure)
        assertEquals(ConnectionFailureKind.OTHER, (result as TestResult.Failure).kind)
        assertEquals(false, result.payload["success"])
        assertEquals(listOf("config.be_db_error"), i18n.translatedKeys)
    }
}
