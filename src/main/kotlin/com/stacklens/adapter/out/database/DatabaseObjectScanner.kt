package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.database.scanner.DatabaseDialect
import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.SearchResult
import java.sql.Connection

interface DatabaseObjectScanner {
    val name: String

    fun supports(dialect: DatabaseDialect): Boolean

    fun scan(
        conn: Connection,
        cfg: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): List<SearchResult>
}
