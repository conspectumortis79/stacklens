package com.stacklens.adapter.out.database

import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DriverManagerDatabaseScanConnectionPortTest {
    private val port = DriverManagerDatabaseScanConnectionPort(DefaultJdbcUrlProvider())

    @Test
    fun `open returns a live H2 connection when given an H2 JDBC URL`() {
        Class.forName("org.h2.Driver")
        val dbId =
            java.util.UUID
                .randomUUID()
                .toString()
                .replace("-", "")
        val config =
            DatabaseConfig(
                name = "h2-test-$dbId",
                dbType = DatabaseType.POSTGRESQL,
                host = "mem",
                port = 0,
                databaseName = "h2open_$dbId;DB_CLOSE_DELAY=-1",
                username = "sa",
                password = "",
            )
        val h2Port =
            DriverManagerDatabaseScanConnectionPort { "jdbc:h2:mem:h2open_$dbId;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE" }
        val conn = h2Port.open(config)
        assertNotNull(conn)
        conn.close()
    }

    @Test
    fun `open throws when the database is unreachable`() {
        val config =
            DatabaseConfig(
                name = "no-conn",
                dbType = DatabaseType.POSTGRESQL,
                host = "127.0.0.1",
                port = 1,
                databaseName = "nope",
                username = "sa",
                password = "",
            )
        assertThrows(Exception::class.java) {
            port.open(config)
        }
    }

    @Test
    fun `open throws when the driver class cannot be loaded`() {
        val config =
            DatabaseConfig(
                name = "bad-driver",
                dbType = DatabaseType.POSTGRESQL,
                host = "x",
                port = 0,
                databaseName = "y",
                username = "u",
                password = "p",
            )
        assertThrows(Exception::class.java) {
            port.open(config)
        }
    }
}
