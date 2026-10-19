package com.stacklens.adapter.out.database.scanner

import com.stacklens.domain.enums.DatabaseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseDialectTest {
    @Test
    fun `POSTGRESQL dialect supports sequences and triggers`() {
        val dialect = DatabaseDialects.of(DatabaseType.POSTGRESQL)
        assertTrue(dialect.supportsSequences())
        assertTrue(dialect.supportsTriggers())
    }

    @Test
    fun `MYSQL dialect does not support sequences and triggers`() {
        val dialect = DatabaseDialects.of(DatabaseType.MYSQL)
        assertFalse(dialect.supportsSequences())
        assertFalse(dialect.supportsTriggers())
    }

    @Test
    fun `MSSQL dialect does not support sequences and triggers`() {
        val dialect = DatabaseDialects.of(DatabaseType.MSSQL)
        assertFalse(dialect.supportsSequences())
        assertFalse(dialect.supportsTriggers())
    }

    @Test
    fun `viewsSql is defined for every database type`() {
        assertTrue(DatabaseDialects.of(DatabaseType.POSTGRESQL).viewsSql().contains("information_schema.views"))
        assertTrue(DatabaseDialects.of(DatabaseType.MYSQL).viewsSql().contains("information_schema.views"))
        assertTrue(DatabaseDialects.of(DatabaseType.MSSQL).viewsSql().contains("information_schema.views"))
    }

    @Test
    fun `indexesSql is defined for every database type`() {
        assertTrue(DatabaseDialects.of(DatabaseType.POSTGRESQL).indexesSql().contains("pg_indexes"))
        assertTrue(DatabaseDialects.of(DatabaseType.MYSQL).indexesSql().contains("information_schema.STATISTICS"))
        assertTrue(DatabaseDialects.of(DatabaseType.MSSQL).indexesSql().contains("sys.indexes"))
    }

    @Test
    fun `POSTGRESQL indexParams returns two wildcarded params`() {
        val params = DatabaseDialects.of(DatabaseType.POSTGRESQL).indexParams("myIndex")
        assertEquals(2, params.size)
        assertEquals("%myIndex%", params[0])
        assertEquals("%myIndex%", params[1])
    }

    @Test
    fun `MYSQL and MSSQL indexParams return one wildcarded param`() {
        val mysql = DatabaseDialects.of(DatabaseType.MYSQL).indexParams("myIndex")
        assertEquals(1, mysql.size)
        assertEquals("%myIndex%", mysql[0])
        val mssql = DatabaseDialects.of(DatabaseType.MSSQL).indexParams("myIndex")
        assertEquals(1, mssql.size)
        assertEquals("%myIndex%", mssql[0])
    }

    @Test
    fun `resolver returns a dedicated implementation per database type`() {
        assertTrue(DatabaseDialects.of(DatabaseType.POSTGRESQL) is PostgresDialect)
        assertTrue(DatabaseDialects.of(DatabaseType.MYSQL) is MysqlDialect)
        assertTrue(DatabaseDialects.of(DatabaseType.MSSQL) is MssqlDialect)
    }
}
