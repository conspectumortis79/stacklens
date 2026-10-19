package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.domain.model.DatabaseConfig
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager

@Component
class DriverManagerDatabaseScanConnectionPort(
    private val jdbcUrls: JdbcUrlProvider,
) : DatabaseScanConnection {
    override fun open(config: DatabaseConfig): Connection {
        Class.forName(config.dbType.driverClass)
        return DriverManager.getConnection(jdbcUrls.urlFor(config), config.username, config.password)
    }
}
