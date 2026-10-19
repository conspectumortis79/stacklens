package com.stacklens.adapter.out.database

import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultJdbcUrlProviderTest {
    private val provider = DefaultJdbcUrlProvider()

    private fun config(
        dbType: DatabaseType,
        host: String = "localhost",
        port: Int = dbType.defaultPort,
        databaseName: String = "mydb",
    ) = DatabaseConfig(
        name = "db",
        dbType = dbType,
        host = host,
        port = port,
        databaseName = databaseName,
        username = "user",
        password = "pass",
    )

    @Test
    fun `returns postgresql url`() {
        assertEquals(
            "jdbc:postgresql://localhost:5432/mydb",
            provider.urlFor(config(DatabaseType.POSTGRESQL)),
        )
    }

    @Test
    fun `returns mysql url with ssl params`() {
        assertEquals(
            "jdbc:mysql://db.example.com:3306/shop?useSSL=false&allowPublicKeyRetrieval=true",
            provider.urlFor(config(DatabaseType.MYSQL, host = "db.example.com", databaseName = "shop")),
        )
    }

    @Test
    fun `returns mssql url without encrypt`() {
        assertEquals(
            "jdbc:sqlserver://sqlserver.local:1433;databaseName=erp;encrypt=false",
            provider.urlFor(config(DatabaseType.MSSQL, host = "sqlserver.local", databaseName = "erp")),
        )
    }
}
