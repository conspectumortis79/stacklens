package com.stacklens.application.port.out.database

import com.stacklens.domain.model.DatabaseConfig
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.SearchResult

data class DatabaseScanResult(
    val results: List<SearchResult>,
    val connectionError: DatabaseConnectionError? = null,
)

interface DatabaseSchemaPort {
    fun fetchSchemaSummary(config: DatabaseConfig): String
}

interface DatabaseScanPort : DatabaseSchemaPort {
    fun scanDatabase(
        config: DatabaseConfig,
        keyword: String,
        sessionId: Long,
    ): DatabaseScanResult

    fun extractCrossRefTerms(result: SearchResult): List<String>
}
