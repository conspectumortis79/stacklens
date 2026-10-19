package com.stacklens.domain.model

import com.stacklens.domain.enums.DatabaseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DatabaseConfigTest {
    @Test
    fun `schemaList returns empty list when schemas is null`() {
        val config =
            DatabaseConfig(
                name = "pg",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "pass",
                schemas = null,
            )
        assertEquals(emptyList<String>(), config.schemaList())
    }

    @Test
    fun `schemaList returns empty list when schemas is blank`() {
        val config =
            DatabaseConfig(
                name = "pg",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "pass",
                schemas = "  ",
            )
        assertEquals(emptyList<String>(), config.schemaList())
    }

    @Test
    fun `schemaList returns single schema`() {
        val config =
            DatabaseConfig(
                name = "pg",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "pass",
                schemas = "public",
            )
        assertEquals(listOf("public"), config.schemaList())
    }

    @Test
    fun `schemaList returns multiple schemas and trims whitespace`() {
        val config =
            DatabaseConfig(
                name = "pg",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "pass",
                schemas = "public, private , admin",
            )
        assertEquals(listOf("public", "private", "admin"), config.schemaList())
    }

    @Test
    fun `schemaList filters out blank entries`() {
        val config =
            DatabaseConfig(
                name = "pg",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "pass",
                schemas = "public,,admin",
            )
        assertEquals(listOf("public", "admin"), config.schemaList())
    }

    @Test
    fun `default active is true`() {
        val config =
            DatabaseConfig(
                name = "pg",
                dbType = DatabaseType.POSTGRESQL,
                host = "localhost",
                port = 5432,
                databaseName = "mydb",
                username = "user",
                password = "pass",
            )
        assertEquals(true, config.active)
    }
}
