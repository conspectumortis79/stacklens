package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.database.scanner.DatabaseDialect
import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class DatabaseScanAdapterResourceFailureCoverageTest {
    private val config =
        DatabaseConfig(
            id = 3L,
            name = "analytics",
            dbType = DatabaseType.POSTGRESQL,
            host = "db.internal",
            port = 5432,
            databaseName = "app",
            username = "reader",
            password = "secret",
        )

    private fun adapter(
        connection: Connection,
        scanners: List<DatabaseObjectScanner> = emptyList(),
    ) = DatabaseScanAdapter(DatabaseScanConnection { connection }, scanners)

    @Test
    fun `a connection that cannot be closed is reported as a connection error`() {
        val connection = mock<Connection>()
        whenever(connection.close()).thenThrow(RuntimeException("socket already reset"))

        val result = adapter(connection).scanDatabase(config, "orders", 1L)

        assertEquals(0, result.results.size)
        assertEquals("analytics", result.connectionError?.configName)
    }

    @Test
    fun `a scanner that explodes is logged and the statement is closed`() {
        val connection = mock<Connection>()
        val scanner =
            object : DatabaseObjectScanner {
                override val name: String = "boom"

                override fun supports(dialect: DatabaseDialect) = true

                override fun scan(
                    conn: Connection,
                    cfg: DatabaseConfig,
                    keyword: String,
                    sessionId: Long,
                ): List<com.stacklens.domain.model.SearchResult> = throw IllegalStateException("dialect unsupported")
            }

        val result = adapter(connection, listOf(scanner)).scanDatabase(config, "orders", 1L)

        assertEquals(0, result.results.size)
    }

    @Test
    fun `a summary query that cannot be prepared ends with an empty summary`() {
        val connection = mock<Connection>()
        whenever(connection.prepareStatement(any<String>())).thenThrow(RuntimeException("no such table"))

        assertEquals("", adapter(connection).fetchSchemaSummary(config))
    }

    @Test
    fun `a summary query that fails on execution unwinds both resource blocks`() {
        val connection = mock<Connection>()
        val statement = mock<PreparedStatement>()
        whenever(connection.prepareStatement(any<String>())).thenReturn(statement)
        whenever(statement.executeQuery()).thenThrow(RuntimeException("canceling statement"))

        assertEquals("", adapter(connection).fetchSchemaSummary(config))
    }

    @Test
    fun `a result set that breaks while it is read unwinds the reader block`() {
        val connection = mock<Connection>()
        val statement = mock<PreparedStatement>()
        val resultSet = mock<ResultSet>()
        whenever(connection.prepareStatement(any<String>())).thenReturn(statement)
        whenever(statement.executeQuery()).thenReturn(resultSet)
        whenever(resultSet.next()).thenThrow(RuntimeException("stream closed"))

        assertEquals("", adapter(connection).fetchSchemaSummary(config))
    }
}
