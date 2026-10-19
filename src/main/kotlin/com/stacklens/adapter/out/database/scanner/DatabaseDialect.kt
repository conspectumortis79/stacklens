package com.stacklens.adapter.out.database.scanner

import com.stacklens.domain.enums.DatabaseType

interface DatabaseDialect {
    fun viewsSql(): String

    fun indexesSql(): String

    fun indexParams(keyword: String): Array<Any>

    fun supportsSequences(): Boolean

    fun supportsTriggers(): Boolean
}

object DatabaseDialects {
    fun of(dbType: DatabaseType): DatabaseDialect =
        when (dbType) {
            DatabaseType.POSTGRESQL -> PostgresDialect
            DatabaseType.MYSQL -> MysqlDialect
            DatabaseType.MSSQL -> MssqlDialect
        }
}
