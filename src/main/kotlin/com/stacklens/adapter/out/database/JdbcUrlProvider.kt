package com.stacklens.adapter.out.database

import com.stacklens.domain.enums.DatabaseType
import com.stacklens.domain.model.DatabaseConfig
import org.springframework.stereotype.Component

fun interface JdbcUrlProvider {
    fun urlFor(config: DatabaseConfig): String
}

@Component
class DefaultJdbcUrlProvider : JdbcUrlProvider {
    override fun urlFor(config: DatabaseConfig): String =
        when (config.dbType) {
            DatabaseType.POSTGRESQL -> "jdbc:postgresql://${config.host}:${config.port}/${config.databaseName}"
            DatabaseType.MYSQL ->
                "jdbc:mysql://${config.host}:${config.port}/${config.databaseName}?useSSL=false&allowPublicKeyRetrieval=true"
            DatabaseType.MSSQL ->
                "jdbc:sqlserver://${config.host}:${config.port};databaseName=${config.databaseName};encrypt=false"
        }
}
